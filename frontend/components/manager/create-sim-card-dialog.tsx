"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle, IconPlus } from "@/components/icons";
import type { SimCardFlavorValue } from "@/lib/api/types";

/**
 * Manager adds a SIM Card, Postpaid or Prepaid, to a Contract's Fleet (fleet-management ticket
 * AC: "Manager can add a SIM Card (Postpaid or Prepaid) to a Contract's Fleet"). The monthly fee
 * field only appears for Postpaid — mirrors CreateAgentDialog's "derived/conditional field
 * follows the choice" pattern, here as visibility rather than a read-only derivation.
 */
export function CreateSimCardDialog({
  contractId,
  currency,
}: {
  contractId: string;
  currency: string;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [number, setNumber] = useState("");
  const [carrier, setCarrier] = useState("");
  const [flavor, setFlavor] = useState<SimCardFlavorValue>("POSTPAID");
  const [monthlyFeeAmount, setMonthlyFeeAmount] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setNumber("");
    setCarrier("");
    setFlavor("POSTPAID");
    setMonthlyFeeAmount("");
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
          carrier: carrier || undefined,
          flavor,
          monthlyFeeAmount: flavor === "POSTPAID" ? Number(monthlyFeeAmount) : undefined,
        }),
      });

      if (!response.ok) {
        setError(
          response.status === 400
            ? "A Postpaid SIM needs a monthly fee; a Prepaid SIM can't have one."
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

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Carrier (optional)
            <Input
              value={carrier}
              onChange={(event) => setCarrier(event.target.value)}
              disabled={submitting}
              placeholder="Verizon"
            />
          </label>

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
            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Monthly fee ({currency})
              <Input
                type="number"
                min="0"
                step="0.01"
                required
                value={monthlyFeeAmount}
                onChange={(event) => setMonthlyFeeAmount(event.target.value)}
                disabled={submitting}
                invalid={Boolean(error)}
                placeholder="25.00"
              />
            </label>
          ) : null}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Add SIM card
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}
