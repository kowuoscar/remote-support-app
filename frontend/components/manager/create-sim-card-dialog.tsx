"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle, IconPlus } from "@/components/icons";
import { CarrierPicker } from "@/components/fleet/carrier-picker";
import { PostpaidPlanPicker } from "@/components/fleet/postpaid-plan-picker";
import type { CatalogCarrierItem, SimCardFlavorValue } from "@/lib/api/types";

/**
 * Manager adds a SIM Card, Postpaid or Prepaid, to a Contract's Fleet (fleet-management ticket
 * AC: "Manager can add a SIM Card (Postpaid or Prepaid) to a Contract's Fleet"). The Carrier is
 * picked from the Contract's Country's active Carriers (sim-card-carrier ticket); a Postpaid SIM
 * also names one of that Carrier's active Postpaid Plans, which sets its monthly fee
 * (postpaid-sim-plan ticket) — CreateAgentDialog's "derived field follows the choice" pattern,
 * here as a read-only fee the Plan decides rather than a number to type.
 */
export function CreateSimCardDialog({
  contractId,
  currency,
  carriers,
  carriersHref,
}: {
  contractId: string;
  currency: string;
  carriers: CatalogCarrierItem[];
  carriersHref: string;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [number, setNumber] = useState("");
  const [carrierId, setCarrierId] = useState("");
  const [flavor, setFlavor] = useState<SimCardFlavorValue>("POSTPAID");
  const [postpaidPlanId, setPostpaidPlanId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const hasActiveCarrier = carriers.some((carrier) => carrier.archivedAt === null);

  function open() {
    setNumber("");
    setCarrierId("");
    setFlavor("POSTPAID");
    setPostpaidPlanId("");
    setError(null);
    dialogRef.current?.showModal();
  }

  function close() {
    dialogRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch(`/api/contracts/${contractId}/sim-cards`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          number,
          carrierId,
          flavor,
          postpaidPlanId: flavor === "POSTPAID" ? postpaidPlanId : undefined,
        }),
      });

      if (!response.ok) {
        setError(
          response.status === 400
            ? "Check the carrier and the plan: both must still be active, and a Postpaid SIM needs a plan."
            : "Couldn't add the SIM card. Try again.",
        );
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
      <Button variant="primary" onClick={open}>
        <IconPlus className="h-4 w-4" />
        Add SIM card
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(420px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 className="text-base font-semibold text-ink">Add a SIM card</h2>
            <p className="text-[13px] text-ink-mute">Starts Active on this contract&rsquo;s fleet.</p>
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

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Number
            <Input
              autoFocus
              required
              value={number}
              onChange={(event) => setNumber(event.target.value)}
              disabled={submitting}
              invalid={Boolean(error)}
              placeholder="+1-555-0100"
            />
          </label>

          <CarrierPicker
            carriers={carriers}
            carriersHref={carriersHref}
            value={carrierId}
            onChange={(id) => {
              setCarrierId(id);
              setPostpaidPlanId("");
            }}
            disabled={submitting}
          />

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Flavor
            <select
              required
              value={flavor}
              onChange={(event) => setFlavor(event.target.value as SimCardFlavorValue)}
              disabled={submitting}
              className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70"
            >
              <option value="POSTPAID">Postpaid</option>
              <option value="PREPAID">Prepaid</option>
            </select>
          </label>

          {flavor === "POSTPAID" ? (
            <PostpaidPlanPicker
              carrier={carriers.find((carrier) => carrier.id === carrierId)}
              currency={currency}
              carriersHref={carriersHref}
              value={postpaidPlanId}
              onChange={setPostpaidPlanId}
              disabled={submitting}
            />
          ) : null}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting} disabled={!hasActiveCarrier}>
              Add SIM card
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}
