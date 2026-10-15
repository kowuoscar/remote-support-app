"use client";

import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EditedLineNote } from "@/components/ui/billed-amount";
import { Money } from "@/components/ui/money";
import { IconAlertTriangle } from "@/components/icons";
import { ACTIONS_NOTE_INSET, ACTIONS_SLOT, LINE_AREA, LINE_WRAPPER } from "@/components/agent/invoice-line-table";
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
 * (edit-client-invoice-lines ticket): the billed amount with a quiet Edit beside it, which opens an
 * inline amount field (Save, Cancel on one line, the refusal below — the Fleet correction
 * controls' layout, no dialog), and, once the line differs from its computed amount, a Reset
 * beside Edit and the "Edited · computed {amount}" note under them. Reset is saving the computed amount.
 * The line is addressed by its kind and source id (the SIM Card or the Fee), the way the backend
 * does; after a save the page refreshes so the base amount, Fees total and total follow.
 */
export function EditClientInvoiceLineControl({
  invoiceId,
  kind,
  sourceId,
  label,
  amount,
  computedAmount,
  edited,
  currency,
}: {
  invoiceId: string;
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
      const response = await fetch(`/api/client-invoices/${invoiceId}/lines`, {
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
    <p
      id={errorId}
      role="alert"
      className={`flex items-start gap-1.5 text-label-sm text-danger xl:max-w-72 xl:self-end ${LINE_AREA.error}`}
    >
      <IconAlertTriangle className="mt-px h-3.5 w-3.5 shrink-0" />
      {error}
    </p>
  ) : null;

  // Below `xl` the control's wrappers are `display: contents`, so its parts are grid items of the
  // line's row and name their own area (invoice-line-table); at `xl` they stack in the Billed cell.
  return (
    <div className={LINE_WRAPPER}>
      {editing ? (
        <form onSubmit={handleSubmit} onKeyDown={handleKeyDown} className="max-xl:contents xl:flex xl:flex-col xl:gap-1.5">
          <label htmlFor={inputId} className="sr-only">
            Billed amount for {label} ({currency})
          </label>
          <div className={`flex items-center gap-1.5 xl:justify-end ${LINE_AREA.editor}`}>
            <div className="w-28 max-xl:w-auto max-xl:min-w-0 max-xl:flex-1">
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
                // `h-7!`: Input's own `h-9` is joined, not merged, and comes later in the cascade.
                className="tnum h-7! text-right text-label-sm"
              />
            </div>
            <Button type="submit" variant="row" size="sm" {...busyProps} className={busyClass}>
              {pending ? <Spinner /> : null}
              Save
            </Button>
            <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={close}>
              Cancel
            </Button>
          </div>
          {errorNote}
        </form>
      ) : (
        <>
          <div className="max-xl:contents xl:flex xl:items-center xl:justify-end xl:gap-2">
            <Money amount={amount} currency={currency} className={LINE_AREA.amount} />
            {/* Names the line for the buttons' description without entering the cell's own name. */}
            <span id={labelId} hidden>
              {label}
            </span>
            {/* One width on every row at `xl` (w-32), so the amounts' right edges line up down the column. */}
            <div className={`flex items-center gap-2 ${ACTIONS_SLOT} ${LINE_AREA.actions}`}>
              <Button ref={editButton} type="button" variant="ghost" size="sm" aria-describedby={labelId} onClick={open}>
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
                  onClick={() =>
                    !pending && void save(computedAmount.toFixed(2), `${label} reset to its computed amount.`, "reset")
                  }
                >
                  {pending ? <Spinner /> : null}
                  Reset
                </Button>
              ) : null}
            </div>
          </div>
          {edited ? (
            <div className={`${LINE_AREA.edited} ${ACTIONS_NOTE_INSET}`}>
              <EditedLineNote computedAmount={computedAmount} currency={currency} />
            </div>
          ) : null}
          {errorNote}
        </>
      )}
      <output className="sr-only">{announcement}</output>
    </div>
  );
}
