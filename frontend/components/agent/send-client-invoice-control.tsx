"use client";

import { useId } from "react";
import { forCount } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { IconArrowRight } from "@/components/icons";
import { SendErrorLine, useTwoStepSend } from "./use-two-step-send";

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
 *
 * <p>The send gate (carrier-invoice-per-sim): while `missingCarrierInvoices` Postpaid SIM lines
 * still lack their Carrier Invoice File the trigger is disabled and a note, linked by
 * `aria-describedby`, says how many. The server stays authoritative: a send it refuses with
 * `CARRIER_INVOICES_MISSING` (a SIM began billing after the page loaded) says so, and one it refuses
 * with `MONTH_NOT_SENT` (agent-sends-the-month: the month was never sent) points at the month bar.
 *
 * <p>The card mounts this only for a draft that is `sendableOnItsOwn`: a sent-back draft (`resend`)
 * or a late Contract's.
 */
/** The count line shared by the control's gate and the card's month-send note. */
export function missingCarrierInvoicesNote(missing: number): string {
  return forCount(missing, "1 Postpaid SIM still needs its carrier invoice.", "{n} Postpaid SIMs still need their carrier invoice.");
}

export function SendClientInvoiceControl({
  invoiceId,
  missingCarrierInvoices = 0,
  resend = false,
}: {
  invoiceId: string;
  missingCarrierInvoices?: number;
  /** The draft was sent back: "Resend Client Invoice" / "Confirm resend" instead of "Send" / "Confirm send". */
  resend?: boolean;
}) {
  const noteId = useId();
  const { trigger, confirmButton, confirming, pending, error, open, close, submit, onKeyDown } = useTwoStepSend();
  const errorLine = <SendErrorLine error={error} />;

  if (confirming && missingCarrierInvoices === 0) {
    return (
      <div className="flex flex-col items-end gap-1.5" onKeyDown={onKeyDown}>
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
            onClick={() => submit(() => fetch(`/api/client-invoices/${invoiceId}/send`, { method: "POST" }))}
          >
            {pending ? "Sending…" : resend ? "Confirm resend" : "Confirm send"}
          </Button>
        </div>
        {errorLine}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button
        ref={trigger}
        type="button"
        variant="primary"
        size="sm"
        disabled={missingCarrierInvoices > 0}
        aria-describedby={missingCarrierInvoices > 0 ? noteId : undefined}
        onClick={open}
      >
        <IconArrowRight className="h-4 w-4" />
        {resend ? "Resend Client Invoice" : "Send Client Invoice"}
      </Button>
      {missingCarrierInvoices > 0 ? (
        <p id={noteId} className="max-w-65 text-right text-label-sm text-ink-mute">
          {missingCarrierInvoicesNote(missingCarrierInvoices)}
        </p>
      ) : null}
      {errorLine}
    </div>
  );
}
