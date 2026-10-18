"use client";

import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { readErrorCode } from "@/lib/api/errors";
import type { ClientInvoiceMonthState } from "@/lib/api/client-invoice-month";
import { formatBillingMonth, formatDate } from "@/lib/format";
import { refreshPage } from "@/lib/selection-address";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { IconAlertTriangle, IconArrowRight, IconCheckCircle } from "@/components/icons";

const REFUSALS: Record<string, string> = {
  CARRIER_INVOICES_MISSING: "An invoice is no longer ready. Reload to see which.",
  MONTH_CHANGED: "Your Contracts changed since this page loaded. Reload and try again.",
  MONTH_ALREADY_SENT: "This month was already sent. Reload to see it.",
  NOT_THE_CURRENT_MONTH: "The month has changed. Reload to see this month's invoices.",
};

/**
 * The Agent's month bar (agent-sends-the-month): one hairline row above the Client Invoices page
 * that says where the month stands and, while it is unsent, sends every Client Invoice of the month
 * together. Three states, derived by `clientInvoiceMonth` from the invoices the page loaded:
 * unsent (a count and the send), sent (the date, no button) and incomplete (an invoice failed to
 * load, so the month cannot be sent from this page).
 *
 * <p>The send is a two-step inline confirm like {@link SendClientInvoiceControl}'s, because it is
 * the same one-way door for every invoice at once: Confirm takes focus when the confirm opens, the
 * trigger gets it back on Back or Escape, and a pending Confirm stays focusable (aria-disabled +
 * aria-busy, repeat activation ignored). The request carries the ids of the invoices the page
 * loaded, so the server refuses (`MONTH_CHANGED`) rather than send a different set than the Agent
 * saw. After a send the control stays mounted ("Sending…") until the refreshed page replaces it.
 */
export function SendClientInvoiceMonthControl({ month }: { month: ClientInvoiceMonthState }) {
  const router = useRouter();
  const noteId = useId();
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

  const label = month.billingMonth ? formatBillingMonth(month.billingMonth) : null;
  // Bare text, not its own element: the month shares one text run with its dot, so it never stands
  // alone as "September 2026" (the invoice card's header says exactly that).
  const heading = label ? `${label} · ` : "";

  if (month.kind === "sent") {
    return (
      <BarShell>
        <p className="text-sm font-semibold text-ink">
          {heading}
          <span className="inline-flex items-center gap-1.5 font-normal text-ink-secondary">
            <IconCheckCircle className="h-3.5 w-3.5 shrink-0 text-success" />
            Sent to Manager on {formatDate(month.sentAt)}
          </span>
        </p>
      </BarShell>
    );
  }

  function close() {
    refocusTrigger.current = true;
    setConfirming(false);
    setError(null);
  }

  async function send(state: Extract<ClientInvoiceMonthState, { kind: "unsent" }>) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/me/client-invoice-months/${state.billingMonth.slice(0, 7)}/send`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ invoiceIds: state.invoiceIds }),
      });
      if (!response.ok) {
        const code = response.status === 409 ? await readErrorCode(response) : null;
        setError((code && REFUSALS[code]) || "Couldn't send. Try again.");
        setPending(false);
        return;
      }
      // Stays pending ("Sending…") until the refreshed page replaces this control with the sent bar.
      refreshPage(router);
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape" && !pending) close();
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
  const sendLabel = total === 1 ? "Send 1 to Manager" : `Send all ${total} to Manager`;
  const confirmLabel = total === 1 ? "Confirm send" : `Confirm send all ${total}`;
  const errorLine = error ? (
    <p role="alert" className="flex items-center gap-1.5 text-label-sm text-danger">
      <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
      {error}
    </p>
  ) : null;

  if (confirming && notReady === 0) {
    return (
      <BarShell>
        <div className="ml-auto flex min-w-0 flex-1 flex-col gap-1.5 sm:items-end" onKeyDown={handleKeyDown}>
          <p className="text-label-sm text-ink-mute sm:max-w-96 sm:text-right">
            Sends all {total} Client {total === 1 ? "Invoice" : "Invoices"} to the Manager and the Clients, and locks
            their numbers. Only the Manager can send one back to you.
          </p>
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
              onClick={() => send(month)}
            >
              {pending ? "Sending…" : confirmLabel}
            </Button>
          </div>
          {errorLine}
        </div>
      </BarShell>
    );
  }

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
            {notReady === 1
              ? "1 invoice still needs its carrier invoices."
              : `${notReady} invoices still need their carrier invoices.`}
          </p>
        ) : null}
      </div>
      <Button
        ref={trigger}
        type="button"
        variant="primary"
        size="sm"
        className="ml-auto"
        disabled={notReady > 0}
        aria-describedby={notReady > 0 ? noteId : undefined}
        onClick={() => setConfirming(true)}
      >
        <IconArrowRight className="h-4 w-4" />
        {sendLabel}
      </Button>
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
