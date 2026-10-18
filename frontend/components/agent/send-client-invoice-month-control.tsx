"use client";

import { useId } from "react";
import type { ClientInvoiceMonthState } from "@/lib/api/client-invoice-month";
import { forCount, formatBillingMonth, formatDate } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { IconArrowRight, IconCheckCircle } from "@/components/icons";
import { SendErrorLine, useTwoStepConfirm } from "@/components/ui/use-two-step-confirm";

/**
 * The Agent's month bar (agent-sends-the-month): one hairline row above the Client Invoices page
 * that says where the month stands and, while it is unsent, sends every Client Invoice of the month
 * together. Three states, derived by `clientInvoiceMonth` from the invoices the page loaded:
 * unsent (a count and the send), sent (the date, no button) and incomplete (an invoice failed to
 * load, so the month cannot be sent from this page).
 *
 * <p>The send is the two-step inline confirm of {@link useTwoStepConfirm}, shared with the per-invoice send,
 * because it is the same one-way door for every invoice at once. The confirm swaps only the action
 * column; the month and its count stay on the left. The request carries the ids of the invoices the page
 * loaded, so the server refuses (`MONTH_CHANGED`) rather than send a different set than the Agent
 * saw. After a send the control stays mounted ("Sending…") until the refreshed page replaces it.
 */
export function SendClientInvoiceMonthControl({ month }: { month: ClientInvoiceMonthState }) {
  const noteId = useId();
  const { trigger, confirmButton, confirming, pending, error, open, close, submit, onKeyDown } = useTwoStepConfirm();

  const label = formatBillingMonth(month.billingMonth);
  // Bare text, not its own element: the month shares one text run with its dot, so it never stands
  // alone as "September 2026" (the invoice card's header says exactly that).
  const heading = `${label} · `;

  if (month.kind === "sent") {
    return (
      <BarShell>
        <p className="text-sm font-semibold text-ink">
          {heading}
          {/* Inline with the sentence, not a box of its own: the sentence wraps as text on one baseline. */}
          <span className="font-normal text-ink-secondary">
            <IconCheckCircle className="mr-1.5 inline h-3.5 w-3.5 shrink-0 align-[-2px] text-success" />
            Sent to Manager on {formatDate(month.sentAt)}
          </span>
        </p>
      </BarShell>
    );
  }

  if (month.kind === "incomplete") {
    return (
      <BarShell>
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-sm font-semibold text-ink">
            {heading}
            <span className="font-normal text-ink-secondary">Couldn&apos;t load every invoice.</span>
          </p>
          <p id={noteId} className="text-label-sm text-ink-mute">
            Reload the page to send the month.
          </p>
        </div>
        <Button type="button" variant="primary" size="sm" className="ml-auto" disabled aria-describedby={noteId}>
          <IconArrowRight className="h-4 w-4" />
          Send all to Manager
        </Button>
      </BarShell>
    );
  }

  const { total, ready } = month;
  const notReady = total - ready;
  const sendLabel = forCount(total, "Send 1 to Manager", "Send all {n} to Manager");
  const confirmLabel = forCount(total, "Confirm send", "Confirm send all {n}");
  const confirmCopy = forCount(
    total,
    "Sends this Client Invoice to the Manager and the Client, and locks its numbers. Only the Manager can send it back to you.",
    "Sends all {n} Client Invoices to the Manager and the Clients, and locks their numbers. Only the Manager can send one back to you.",
  );
  const sending = confirming && notReady === 0;

  return (
    <BarShell>
      <div className="flex min-w-0 flex-col gap-1">
        <p className="text-sm font-semibold text-ink">
          {heading}
          <span className="tnum font-normal text-ink-secondary">
            {ready} of {total} ready
          </span>
        </p>
        {notReady > 0 ? (
          <p id={noteId} className="text-label-sm text-ink-mute">
            {forCount(
              notReady,
              "1 invoice still needs its carrier invoices.",
              "{n} invoices still need their carrier invoices.",
            )}
          </p>
        ) : null}
      </div>
      {sending ? (
        <div className="ml-auto flex min-w-0 flex-col gap-1.5 sm:items-end" onKeyDown={onKeyDown}>
          <p className="text-label-sm text-ink-mute sm:max-w-96 sm:text-right">{confirmCopy}</p>
          <div className="flex items-center justify-end gap-1.5 self-end">
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
              onClick={() =>
                submit(() =>
                  fetch(`/api/me/client-invoice-months/${month.billingMonth.slice(0, 7)}/send`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ invoiceIds: month.invoiceIds }),
                  }),
                )
              }
            >
              {pending ? "Sending…" : confirmLabel}
            </Button>
          </div>
          <SendErrorLine error={error} />
        </div>
      ) : (
        <Button
          ref={trigger}
          type="button"
          variant="primary"
          size="sm"
          className="ml-auto"
          disabled={notReady > 0}
          aria-describedby={notReady > 0 ? noteId : undefined}
          onClick={open}
        >
          <IconArrowRight className="h-4 w-4" />
          {sendLabel}
        </Button>
      )}
    </BarShell>
  );
}

/** The bar's one hairline row: text on the left, the action on the right (wrapped under the text below `md`). */
function BarShell({ children }: { children: React.ReactNode }) {
  return (
    <section aria-label="Month">
      <Card className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3">
        {children}
      </Card>
    </section>
  );
}
