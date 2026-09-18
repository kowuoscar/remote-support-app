"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import type { ContractOption } from "@/components/ui/contract-switcher";
import { REQUEST_DETAILS_COMPONENTS } from "@/components/requests/details/registry";
import {
  REQUEST_TYPE_LABEL,
  type CatalogCarrierItem,
  type RequestTypeValue,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

const requestTypes: RequestTypeValue[] = [
  "REBOOT",
  "TOPUP",
  "SIM_SWAP",
  "PROVISION_SMARTPHONE",
  "PROVISION_SIM",
  "OTHER",
];

/**
 * Tester Request submission (tester-request-submission ticket AC: "Tester can submit a Request
 * ... against one of their Client's Contracts"). A dialog, not an inline form: matches the
 * established pattern for every other "add a thing" action in this app (Add client, Add
 * smartphone, Add contract — fleet-management/manager-entity-setup tickets), and a Request
 * submission is exactly that shape — a few required fields, no protected background task to
 * interrupt.
 *
 * <p>reboot-and-topup-details ticket: the type-specific fields below the type picker come from
 * `REQUEST_DETAILS_COMPONENTS` — one component per type, sharing the Smartphone/SIM Card pickers —
 * scoped to whichever Contract is chosen right here in the dialog (its own picker, independent of
 * whatever Contract the surrounding Requests list is filtered to). A type with no registered
 * component keeps today's plain, generic description field.
 */
export function SubmitRequestDialog({
  contracts,
  smartphonesByContract = {},
  simCardsByContract = {},
  carriersByContract = {},
  // A Tester has no Carrier-management page of their own to link to (only an Agent or Manager
  // maintains the catalog) — a per-type details component that needs this only for its own "add
  // one" fallback link, which neither Reboot's nor Topup's own component renders today.
  carriersHref = "",
}: {
  contracts: ContractOption[];
  smartphonesByContract?: Record<string, SmartphoneListItem[]>;
  simCardsByContract?: Record<string, SimCardListItem[]>;
  carriersByContract?: Record<string, CatalogCarrierItem[]>;
  carriersHref?: string;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [submitted, setSubmitted] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(false);
  const [type, setType] = useState<RequestTypeValue>(requestTypes[0]);
  const [contractId, setContractId] = useState(contracts[0]?.id ?? "");

  const DetailsComponent = REQUEST_DETAILS_COMPONENTS[type];
  const currency = contracts.find((c) => c.id === contractId)?.currency ?? "";

  function open() {
    setSubmitted(false);
    setError(false);
    setType(requestTypes[0]);
    setContractId(contracts[0]?.id ?? "");
    dialogRef.current?.showModal();
  }

  function close() {
    dialogRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const submittedContractId = String(formData.get("contractId"));
    const description = String(formData.get("description") ?? "").trim();
    const targetSmartphoneId = String(formData.get("targetSmartphoneId") ?? "");
    const targetSimCardId = String(formData.get("targetSimCardId") ?? "");
    const topupOptionId = String(formData.get("topupOptionId") ?? "");
    const requestedModel = String(formData.get("requestedModel") ?? "").trim();
    const requestedFlavor = String(formData.get("requestedFlavor") ?? "");
    const requestedCarrierId = String(formData.get("requestedCarrierId") ?? "");
    const requestedPostpaidPlanId = String(formData.get("requestedPostpaidPlanId") ?? "");
    const secondSimCardId = String(formData.get("secondSimCardId") ?? "");

    setPending(true);
    setError(false);
    try {
      const response = await fetch(`/api/contracts/${submittedContractId}/requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type,
          description: description || undefined,
          targetSmartphoneId: targetSmartphoneId || undefined,
          targetSimCardId: targetSimCardId || undefined,
          topupOptionId: topupOptionId || undefined,
          requestedModel: requestedModel || undefined,
          requestedFlavor: requestedFlavor || undefined,
          requestedCarrierId: requestedCarrierId || undefined,
          requestedPostpaidPlanId: requestedPostpaidPlanId || undefined,
          secondSimCardId: secondSimCardId || undefined,
        }),
      });
      if (!response.ok) {
        setError(true);
        setPending(false);
        return;
      }
      setSubmitted(true);
      setPending(false);
      router.refresh();
    } catch {
      setError(true);
      setPending(false);
    }
  }

  return (
    <>
      <Button variant="primary" onClick={open}>
        Submit Request
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(480px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        {submitted ? (
          <div className="flex flex-col items-center gap-3 p-8 text-center">
            <div className="flex h-11 w-11 items-center justify-center rounded-full bg-success-bg text-success">
              <svg viewBox="0 0 24 24" fill="none" className="h-5 w-5" aria-hidden="true">
                <path
                  d="m6.5 12.5 3.5 3.5 7.5-8"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </div>
            <p className="text-sm font-medium text-ink">Request submitted</p>
            <p className="text-[13px] text-ink-mute">
              Your Agent will pick this up and update its status here as they work on it.
            </p>
            <Button variant="secondary" onClick={close}>
              Close
            </Button>
          </div>
        ) : (
          <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
            <div>
              <h2 className="text-base font-semibold text-ink">Submit a Request</h2>
              <p className="text-[13px] text-ink-mute">
                Your Agent is notified immediately and it queues for action.
              </p>
            </div>

            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Contract
              <select
                name="contractId"
                required
                value={contractId}
                onChange={(event) => setContractId(event.target.value)}
                className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
              >
                {contracts.map((contract) => (
                  <option key={contract.id} value={contract.id}>
                    {contract.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Request type
              <select
                name="type"
                required
                value={type}
                onChange={(event) => setType(event.target.value as RequestTypeValue)}
                className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
              >
                {requestTypes.map((option) => (
                  <option key={option} value={option}>
                    {REQUEST_TYPE_LABEL[option]}
                  </option>
                ))}
              </select>
            </label>

            {DetailsComponent ? (
              <DetailsComponent
                key={`${type}-${contractId}`}
                smartphones={smartphonesByContract[contractId] ?? []}
                simCards={simCardsByContract[contractId] ?? []}
                carriers={carriersByContract[contractId] ?? []}
                carriersHref={carriersHref}
                currency={currency}
                disabled={pending}
              />
            ) : (
              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Description{" "}
                {type === "OTHER" ? null : <span className="font-normal text-ink-mute">(optional)</span>}
                <textarea
                  name="description"
                  required={type === "OTHER"}
                  rows={3}
                  placeholder={
                    type === "OTHER" ? "What do you need help with?" : "Anything the Agent should know"
                  }
                  className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
                />
              </label>
            )}

            {error ? (
              <p className="text-[13px] text-danger">Couldn&rsquo;t submit the Request. Try again.</p>
            ) : null}

            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="secondary" onClick={close}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" loading={pending}>
                {pending ? "Submitting…" : "Submit Request"}
              </Button>
            </div>
          </form>
        )}
      </dialog>
    </>
  );
}
