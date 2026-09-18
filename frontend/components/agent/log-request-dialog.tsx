"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle, IconPlus } from "@/components/icons";
import { REQUEST_DETAILS_COMPONENTS } from "@/components/requests/details/registry";
import {
  REQUEST_TYPE_LABEL,
  type CatalogCarrierItem,
  type ContractTesterListItem,
  type RequestStatusValue,
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
 * Agent logs a Request proactively, on a Tester's behalf (agent-request-fulfillment ticket AC:
 * "Agent can log a Request directly ... for one of their own Contracts, starting at Submitted or
 * immediately at Completed"). Scoped to the Contract currently selected in the Requests view — the
 * same "Contract already chosen via the switcher, then act within it" shape as CreateSmartphoneDialog.
 * Distinct from the Tester's own SubmitRequestDialog: this one also names which Tester it's for and
 * lets the Agent choose the starting status, since it's Agent-authored, not Tester-authored.
 *
 * <p>reboot-and-topup-details ticket: the type-specific fields below the type picker come from
 * `REQUEST_DETAILS_COMPONENTS` — the same registry and components `SubmitRequestDialog` uses —
 * scoped to this dialog's own fixed `contractId`.
 */
export function LogRequestDialog({
  contractId,
  testers,
  smartphones = [],
  simCards = [],
  carriers = [],
  currency = "",
  carriersHref = "/agent/carriers",
}: {
  contractId: string;
  testers: ContractTesterListItem[];
  smartphones?: SmartphoneListItem[];
  simCards?: SimCardListItem[];
  carriers?: CatalogCarrierItem[];
  currency?: string;
  carriersHref?: string;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [type, setType] = useState<RequestTypeValue>(requestTypes[0]);
  const [startingStatus, setStartingStatus] = useState<RequestStatusValue>("SUBMITTED");

  const DetailsComponent = REQUEST_DETAILS_COMPONENTS[type];
  // provision-request-details ticket AC: "one that starts at Completed also gives the SIM
  // number" — only Provision SIM needs this extra field, and only when logged straight to
  // Completed rather than queued at Submitted.
  const needsSimCardNumber = type === "PROVISION_SIM" && startingStatus === "COMPLETED";

  function open() {
    setError(null);
    setType(requestTypes[0]);
    setStartingStatus("SUBMITTED");
    dialogRef.current?.showModal();
  }

  function close() {
    dialogRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const formData = new FormData(event.currentTarget);
    const testerId = String(formData.get("testerId"));
    const description = String(formData.get("description") ?? "").trim();
    const targetSmartphoneId = String(formData.get("targetSmartphoneId") ?? "");
    const targetSimCardId = String(formData.get("targetSimCardId") ?? "");
    const topupOptionId = String(formData.get("topupOptionId") ?? "");
    const requestedModel = String(formData.get("requestedModel") ?? "").trim();
    const requestedFlavor = String(formData.get("requestedFlavor") ?? "");
    const requestedCarrierId = String(formData.get("requestedCarrierId") ?? "");
    const requestedPostpaidPlanId = String(formData.get("requestedPostpaidPlanId") ?? "");
    // provision-request-details ticket AC: an Agent logging a Provision SIM proactively that
    // starts immediately Completed also gives the SIM number right here.
    const simCardNumber = String(formData.get("simCardNumber") ?? "");

    setSubmitting(true);
    try {
      const response = await fetch(`/api/contracts/${contractId}/requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type,
          testerId,
          startingStatus,
          description: description || undefined,
          targetSmartphoneId: targetSmartphoneId || undefined,
          targetSimCardId: targetSimCardId || undefined,
          topupOptionId: topupOptionId || undefined,
          requestedModel: requestedModel || undefined,
          requestedFlavor: requestedFlavor || undefined,
          requestedCarrierId: requestedCarrierId || undefined,
          requestedPostpaidPlanId: requestedPostpaidPlanId || undefined,
          simCardNumber: simCardNumber || undefined,
        }),
      });

      if (!response.ok) {
        setError("Couldn't log the Request. Try again.");
        setSubmitting(false);
        return;
      }

      setSubmitting(false);
      close();
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setSubmitting(false);
    }
  }

  return (
    <>
      <Button variant="secondary" onClick={open}>
        <IconPlus className="h-4 w-4" />
        Log a request
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(480px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 className="text-base font-semibold text-ink">Log a request</h2>
            <p className="text-[13px] text-ink-mute">
              Record support you performed proactively, on a Tester&rsquo;s behalf.
            </p>
          </div>

          {error ? (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2.5 text-[13px] text-danger"
            >
              <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          ) : null}

          {testers.length === 0 ? (
            <p className="text-[13px] text-ink-mute">
              No Testers on this Contract yet — a Manager needs to add one first.
            </p>
          ) : (
            <>
              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Tester
                <select
                  name="testerId"
                  required
                  defaultValue={testers[0]?.id}
                  disabled={submitting}
                  className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
                >
                  {testers.map((tester) => (
                    <option key={tester.id} value={tester.id}>
                      {tester.username}
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
                  disabled={submitting}
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
                  key={type}
                  smartphones={smartphones}
                  simCards={simCards}
                  carriers={carriers}
                  carriersHref={carriersHref}
                  currency={currency}
                  disabled={submitting}
                />
              ) : (
                <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                  Description{" "}
                  {type === "OTHER" ? null : <span className="font-normal text-ink-mute">(optional)</span>}
                  <textarea
                    name="description"
                    required={type === "OTHER"}
                    rows={3}
                    disabled={submitting}
                    placeholder={
                      type === "OTHER" ? "What did you help with?" : "Anything worth recording"
                    }
                    className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
                  />
                </label>
              )}

              <fieldset className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                <legend className="mb-0.5">Starting status</legend>
                <label className="flex items-center gap-2 font-normal text-ink">
                  <input
                    type="radio"
                    name="startingStatus"
                    value="SUBMITTED"
                    checked={startingStatus === "SUBMITTED"}
                    onChange={() => setStartingStatus("SUBMITTED")}
                    disabled={submitting}
                  />
                  Submitted — queue it for follow-up
                </label>
                <label className="flex items-center gap-2 font-normal text-ink">
                  <input
                    type="radio"
                    name="startingStatus"
                    value="COMPLETED"
                    checked={startingStatus === "COMPLETED"}
                    onChange={() => setStartingStatus("COMPLETED")}
                    disabled={submitting}
                  />
                  Completed — already handled
                </label>
              </fieldset>

              {needsSimCardNumber ? (
                <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                  New SIM number
                  <Input name="simCardNumber" required disabled={submitting} placeholder="+1-555-0100" />
                </label>
              ) : null}
            </>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting} disabled={testers.length === 0}>
              Log request
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}
