"use client";

import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle } from "@/components/icons";
import type { ClientInvoiceDetail } from "@/lib/api/types";

const CONFLICT_MESSAGE = "This invoice is no longer awaiting approval. Refresh to see its current status.";
const BLANK_MESSAGE = "Say why you are sending it back.";
const RETRY_MESSAGE = "Couldn't send it back. Try again.";
const OFFLINE_MESSAGE = "Couldn't reach the server. Check your connection and try again.";
const MAX_REASON_LENGTH = 1000;

/**
 * The Manager's "Send back" action on a sent Client Invoice (send-a-client-invoice-back spec,
 * Frontend: Manager): a secondary trigger that expands a small inline reason form — no dialog,
 * the same shape as `PendingRequestDecisionControls`' reject flow. It takes the endpoint's URL
 * rather than an invoice id so the Agent Invoice's send-back can mount the same control.
 * `onSentBack` receives the returned invoice so a detail view can show its draft state in place.
 *
 * <p>A pending Confirm stays focusable (aria-disabled + aria-busy, repeat activation ignored)
 * instead of natively disabled, so keyboard focus never falls to the page; Escape closes the form
 * from any of its controls.
 */
export function SendBackClientInvoiceControl({
  endpoint,
  onSentBack,
  onOpenChange,
}: Readonly<{
  endpoint: string;
  onSentBack?: (invoice: ClientInvoiceDetail) => void;
  /** Told whenever the form opens or closes, so the view can hide the sibling Approve while it is open. */
  onOpenChange?: (open: boolean) => void;
}>) {
  const router = useRouter();
  const reasonId = useId();
  const hintId = useId();
  const errorId = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const field = useRef<HTMLTextAreaElement>(null);
  const refocusTrigger = useRef(false);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    onOpenChange?.(open);
  }, [open, onOpenChange]);

  useEffect(() => {
    if (!open && refocusTrigger.current) {
      refocusTrigger.current = false;
      trigger.current?.focus();
    }
  }, [open]);

  function close() {
    refocusTrigger.current = true;
    setOpen(false);
    setReason("");
    setError(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const trimmed = reason.trim();
    if (!trimmed) {
      setError(BLANK_MESSAGE);
      field.current?.focus();
      return;
    }
    setPending(true);
    setError(null);
    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: trimmed }),
      });
      if (!response.ok) {
        setError(response.status === 409 ? CONFLICT_MESSAGE : RETRY_MESSAGE);
        setPending(false);
        return;
      }
      // Stays pending ("Sending back…") until the view swaps this control out; the view's status
      // note then takes focus.
      onSentBack?.((await response.json()) as ClientInvoiceDetail);
      router.refresh();
    } catch {
      setError(OFFLINE_MESSAGE);
      setPending(false);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === "Escape" && !pending) close();
  }

  if (!open) {
    return (
      <Button
        ref={trigger}
        type="button"
        variant="secondary"
        size="sm"
        onClick={() => setOpen(true)}
      >
        Send back
      </Button>
    );
  }

  const busyProps = pending ? ({ "aria-disabled": true, "aria-busy": true } as const) : {};

  return (
    <form
      onSubmit={submit}
      onKeyDown={handleKeyDown}
      className="flex w-full basis-full flex-col gap-2"
    >
      <label htmlFor={reasonId} className="text-label font-medium text-ink">
        Reason for sending back
      </label>
      <textarea
        ref={field}
        id={reasonId}
        autoFocus
        rows={3}
        maxLength={MAX_REASON_LENGTH}
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        readOnly={pending}
        aria-required="true"
        aria-invalid={error === BLANK_MESSAGE || undefined}
        aria-describedby={error ? `${hintId} ${errorId}` : hintId}
        className={`w-full max-w-lg resize-y rounded-lg border bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary ${
          error === BLANK_MESSAGE ? "border-danger" : "border-hairline-strong"
        }`}
      />
      <p id={hintId} className="max-w-lg text-label-sm text-ink-mute">
        Tell the Agent what is wrong or missing. They can change any line and attach files before sending it again.
      </p>
      <div className="flex items-center gap-1.5">
        <Button
          type="submit"
          variant="danger"
          size="sm"
          {...busyProps}
          className={pending ? "cursor-not-allowed opacity-50" : undefined}
        >
          {pending ? "Sending back…" : "Confirm send back"}
        </Button>
        <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={close}>
          Back
        </Button>
      </div>
      {error ? (
        <p id={errorId} role="alert" className="flex items-start gap-1.5 text-label-sm text-danger">
          <IconAlertTriangle className="mt-px h-3.5 w-3.5 shrink-0" />
          {error}
        </p>
      ) : null}
    </form>
  );
}
