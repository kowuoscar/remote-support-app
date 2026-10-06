"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconArrowRight } from "@/components/icons";

/**
 * Agent sends their own draft Client Invoice (client-invoice-submission-and-visibility ticket AC:
 * "Agent can send a draft Client Invoice, moving it to status sent; a sent invoice is no longer
 * editable by the Agent"). A two-step inline confirm rather than a single click — mirrors
 * RequestStatusControl's cancel flow — because sending is a one-way door: there is no "un-send"
 * anywhere in this app, it opens the invoice to the Client and the Manager's review queue, and it
 * freezes the numbers permanently (see ClientInvoice's Javadoc on the backend).
 *
 * <p>Focus follows the flow: Confirm takes it when the confirm opens, the trigger gets it back on
 * Back or Escape, and a pending Confirm stays focusable (aria-disabled + aria-busy, repeat
 * activation ignored) instead of natively disabled, so it never falls to the page. After a send the
 * control stays mounted ("Sending…") until the refreshed page replaces it, and the card's status
 * note takes focus then.
 */
export function SendClientInvoiceControl({ invoiceId }: { invoiceId: string }) {
  const router = useRouter();
  const trigger = useRef<HTMLButtonElement>(null);
  const confirmButton = useRef<HTMLButtonElement>(null);
  const refocusTrigger = useRef(false);
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (confirming) {
      confirmButton.current?.focus();
    } else if (refocusTrigger.current) {
      refocusTrigger.current = false;
      trigger.current?.focus();
    }
  }, [confirming]);

  function close() {
    refocusTrigger.current = true;
    setConfirming(false);
    setError(null);
  }

  async function send() {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/client-invoices/${invoiceId}/send`, { method: "POST" });
      if (!response.ok) {
        setError("Couldn't send. Try again.");
        setPending(false);
        return;
      }
      // Stays pending ("Sending…") until the refreshed page replaces this control; the card's
      // status note then takes focus (ClientInvoiceStatusNote).
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape" && !pending) close();
  }

  const errorLine = error ? (
    <p role="alert" className="flex items-center gap-1.5 text-label-sm text-danger">
      <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
      {error}
    </p>
  ) : null;

  if (confirming) {
    return (
      <div className="flex flex-col items-end gap-1.5" onKeyDown={handleKeyDown}>
        <p className="max-w-65 text-right text-label-sm text-ink-mute">
          Sends to the Manager and Client, and locks the numbers. Only the Manager can send it back to you.
        </p>
        <div className="flex items-center gap-1.5">
          <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={close}>
            Back
          </Button>
          <Button
            ref={confirmButton}
            type="button"
            variant="primary"
            size="sm"
            aria-disabled={pending || undefined}
            aria-busy={pending || undefined}
            className={pending ? "cursor-not-allowed opacity-60" : undefined}
            onClick={send}
          >
            {pending ? "Sending…" : "Confirm send"}
          </Button>
        </div>
        {errorLine}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button ref={trigger} type="button" variant="primary" size="sm" onClick={() => setConfirming(true)}>
        <IconArrowRight className="h-4 w-4" />
        Send Client Invoice
      </Button>
      {errorLine}
    </div>
  );
}
