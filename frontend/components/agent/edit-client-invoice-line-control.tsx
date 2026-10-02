"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Money } from "@/components/ui/money";
import { IconAlertTriangle } from "@/components/icons";
import { useAnnouncement } from "@/components/manager/use-announcement";
import type { EditableClientInvoiceLineKind } from "@/lib/api/types";

const SENT_MESSAGE = "This invoice was sent. Refresh to see it.";
const RETRY_MESSAGE = "Couldn't save. Try again.";
const INVALID_AMOUNT_MESSAGE = "Enter an amount of zero or more, with at most two decimals.";

/** The quiet review line under an edited line's billed amount; shown on a sent invoice too. */
export function EditedLineNote({ computedAmount, currency }: { computedAmount: number; currency: string }) {
  return (
    <p className="text-label-sm text-ink-mute">
      Edited · computed <Money amount={computedAmount} currency={currency} className="text-ink-mute" />
    </p>
  );
}

async function failureMessage(response: Response): Promise<string> {
  if (response.status === 409) return SENT_MESSAGE;
  if (response.status === 400) {
    const body = (await response.json().catch(() => null)) as { message?: unknown } | null;
    return typeof body?.message === "string" && body.message ? body.message : INVALID_AMOUNT_MESSAGE;
  }
  return RETRY_MESSAGE;
}

/**
 * The amount cell of one editable line (a Postpaid SIM or a Fee) on a draft Client Invoice
 * (edit-client-invoice-lines ticket): the billed amount, with an Edit row action that opens an
 * inline amount field (Save, Cancel — no dialog, copied from the Manager's
 * AgentInvoiceOverrideControl), and, once the line differs from its computed amount, the
 * "Edited · computed {amount}" note with a Reset row action. Reset is saving the computed amount.
 * The line is addressed by its kind and source id (the SIM Card or the Fee), the way the backend
 * does; after a save the page refreshes so the base amount, Fees total and total follow.
 */
export function EditClientInvoiceLineControl({
  contractId,
  kind,
  sourceId,
  label,
  amount,
  computedAmount,
  edited,
  currency,
}: {
  contractId: string;
  kind: EditableClientInvoiceLineKind;
  sourceId: string;
  /** Names the line for assistive tech, e.g. "SIM +1-555-0100" or "Topup Fee". */
  label: string;
  amount: number;
  computedAmount: number;
  edited: boolean;
  currency: string;
}) {
  const router = useRouter();
  const inputId = useId();
  const labelId = useId();
  const editButton = useRef<HTMLButtonElement>(null);
  const restoreFocus = useRef(false);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [announcement, announce] = useAnnouncement();

  // Closing the field unmounts it; hand keyboard focus back to the line's Edit action.
  useEffect(() => {
    if (!editing && restoreFocus.current) {
      restoreFocus.current = false;
      editButton.current?.focus();
    }
  }, [editing]);

  function open() {
    setValue(amount.toFixed(2));
    setError(null);
    setEditing(true);
  }

  function close() {
    restoreFocus.current = true;
    setEditing(false);
    setError(null);
  }

  async function save(newAmount: string, announced: string): Promise<boolean> {
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/contracts/${contractId}/client-invoice/lines`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ kind, sourceId, amount: newAmount.trim() }),
      });
      if (!response.ok) {
        setError(await failureMessage(response));
        setPending(false);
        return false;
      }
      setPending(false);
      announce(announced);
      router.refresh();
      return true;
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
      return false;
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (await save(value, `${label} saved.`)) close();
  }

  const errorNote = error ? (
    <p role="alert" className="flex items-start gap-1.5 text-label-sm text-danger">
      <IconAlertTriangle className="mt-px h-3.5 w-3.5 shrink-0" />
      {error}
    </p>
  ) : null;

  return (
    <div className="flex flex-col items-end gap-1.5">
      {editing ? (
        <form onSubmit={handleSubmit} className="flex flex-col items-end gap-2">
          <label htmlFor={inputId} className="sr-only">
            Billed amount for {label} ({currency})
          </label>
          <Input
            id={inputId}
            autoFocus
            autoComplete="off"
            inputMode="decimal"
            value={value}
            onChange={(event) => setValue(event.target.value)}
            disabled={pending}
            invalid={Boolean(error)}
            className="tnum w-28 text-right"
          />
          <div className="flex items-center gap-1.5">
            <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={close}>
              Cancel
            </Button>
            <Button type="submit" variant="secondary" size="sm" loading={pending}>
              Save
            </Button>
          </div>
          {errorNote}
        </form>
      ) : (
        <>
          <Money amount={amount} currency={currency} />
          {/* Names the line for the buttons' description without entering the cell's own name. */}
          <span id={labelId} hidden>
            {label}
          </span>
          {edited ? <EditedLineNote computedAmount={computedAmount} currency={currency} /> : null}
          <div className="flex items-center gap-1.5">
            <Button ref={editButton} type="button" variant="row" size="sm" aria-describedby={labelId} onClick={open}>
              Edit
            </Button>
            {edited ? (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                loading={pending}
                aria-describedby={labelId}
                onClick={() => void save(computedAmount.toFixed(2), `${label} reset to its computed amount.`)}
              >
                Reset
              </Button>
            ) : null}
          </div>
          {errorNote}
        </>
      )}
      <output className="sr-only">{announcement}</output>
    </div>
  );
}
