"use client";

import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EditedLineNote } from "@/components/ui/billed-amount";
import { Money } from "@/components/ui/money";
import { IconAlertTriangle } from "@/components/icons";
import { useAnnouncement } from "@/components/ui/use-announcement";
import type { EditableClientInvoiceLineKind } from "@/lib/api/types";

const SENT_MESSAGE = "This invoice was sent. Refresh to see it.";
const RETRY_MESSAGE = "Couldn't save. Try again.";
const INVALID_AMOUNT_MESSAGE = "Enter an amount of zero or more, with at most two decimals.";

async function failureMessage(response: Response): Promise<string> {
  if (response.status === 409) return SENT_MESSAGE;
  if (response.status === 400) {
    const body = (await response.json().catch(() => null)) as { message?: unknown } | null;
    return typeof body?.message === "string" && body.message ? body.message : INVALID_AMOUNT_MESSAGE;
  }
  return RETRY_MESSAGE;
}

function Spinner() {
  return (
    <svg className="h-3.5 w-3.5 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2.5" opacity="0.25" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
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
  const errorId = useId();
  const editButton = useRef<HTMLButtonElement>(null);
  const resetButton = useRef<HTMLButtonElement>(null);
  const field = useRef<HTMLInputElement>(null);
  // Where keyboard focus goes once the next render has settled; a control that disables itself
  // or unmounts while saving would otherwise drop focus to the page.
  const nextFocus = useRef<"edit" | "reset" | "field" | null>(null);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [announcement, announce] = useAnnouncement();

  useEffect(() => {
    const target = nextFocus.current;
    if (!target || pending) return;
    nextFocus.current = null;
    const element = { edit: editButton, reset: resetButton, field }[target].current;
    element?.focus();
  });

  function open() {
    setValue(amount.toFixed(2));
    setError(null);
    setEditing(true);
  }

  function close() {
    nextFocus.current = "edit";
    setEditing(false);
    setError(null);
  }

  async function save(newAmount: string, announced: string, onRefused: "field" | "reset"): Promise<boolean> {
    setPending(true);
    nextFocus.current = onRefused;
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
      nextFocus.current = "edit";
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
    if (pending) return;
    if (await save(value, `${label} saved.`, "field")) close();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === "Escape" && !pending) close();
  }

  // A pending control stays focusable (native `disabled` would drop keyboard focus to the page):
  // it is marked aria-disabled + aria-busy and its repeat activation is ignored instead.
  const busyProps = pending ? ({ "aria-disabled": true, "aria-busy": true } as const) : {};
  const busyClass = pending ? "cursor-not-allowed opacity-50" : undefined;

  const errorNote = error ? (
    <p id={errorId} role="alert" className="flex items-start gap-1.5 text-label-sm text-danger">
      <IconAlertTriangle className="mt-px h-3.5 w-3.5 shrink-0" />
      {error}
    </p>
  ) : null;

  return (
    <div className="relative flex flex-col items-end gap-1.5">
      {editing ? (
        <form onSubmit={handleSubmit} onKeyDown={handleKeyDown} className="flex flex-col items-end gap-2">
          <label htmlFor={inputId} className="sr-only">
            Billed amount for {label} ({currency})
          </label>
          <div className="w-28">
            <Input
              ref={field}
              id={inputId}
              name="amount"
              autoFocus
              autoComplete="off"
              inputMode="decimal"
              value={value}
              onChange={(event) => setValue(event.target.value)}
              readOnly={pending}
              aria-busy={pending || undefined}
              aria-describedby={error ? errorId : undefined}
              invalid={Boolean(error)}
              className="tnum text-right"
            />
          </div>
          <div className="flex items-center gap-1.5">
            <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={close}>
              Cancel
            </Button>
            <Button type="submit" variant="secondary" size="sm" {...busyProps} className={busyClass}>
              {pending ? <Spinner /> : null}
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
                ref={resetButton}
                type="button"
                variant="ghost"
                size="sm"
                {...busyProps}
                className={busyClass}
                aria-describedby={error ? `${labelId} ${errorId}` : labelId}
                onClick={() => !pending && void save(computedAmount.toFixed(2), `${label} reset to its computed amount.`, "reset")}
              >
                {pending ? <Spinner /> : null}
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
