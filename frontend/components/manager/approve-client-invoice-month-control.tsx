"use client";

import { forCount, formatBillingMonth } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { IconCheckCircle } from "@/components/icons";
import { monthParam } from "@/lib/review-months";
import { SendErrorLine, useTwoStepConfirm } from "@/components/ui/use-two-step-confirm";

const APPROVE_REFUSALS: Record<string, string> = {
  MONTH_CHANGED: "This month changed since the page loaded. Reload and try again.",
  NOTHING_TO_APPROVE: "Nothing is left to review. Reload to see the month.",
};
const APPROVE_FAILED = "Couldn't approve. Try again.";
const COPY = { refusals: APPROVE_REFUSALS, failed: APPROVE_FAILED };

/**
 * The month header's one pill (manager-reviews-by-agent): approves every Client Invoice of an Agent's
 * month still to review, together. The two-step inline confirm of `useTwoStepConfirm` (shared with the
 * Agent's month send, with this action's refusal wording), because an approval is final. The request
 * carries the ids of the To review rows the page loaded, so the server refuses (`MONTH_CHANGED`)
 * rather than approve a different set than the Manager saw. After an approval the control stays
 * mounted ("Approving…") until the refreshed page, with nothing left to review, replaces it.
 */
export function ApproveClientInvoiceMonthControl({
  agentId,
  agentName,
  billingMonth,
  invoiceIds,
}: {
  agentId: string;
  agentName: string;
  /** First-of-month ISO date. */
  billingMonth: string;
  /** The ids of the month's To review rows. */
  invoiceIds: string[];
}) {
  const { trigger, confirmButton, confirming, pending, error, open, close, submit, onKeyDown } = useTwoStepConfirm(COPY);
  const count = invoiceIds.length;
  const monthLabel = formatBillingMonth(billingMonth);

  if (!confirming) {
    return (
      <Button ref={trigger} type="button" variant="primary" size="sm" className="ml-auto" onClick={open}>
        <IconCheckCircle className="h-4 w-4" />
        Approve the {count} left to review
      </Button>
    );
  }

  return (
    <div className="ml-auto flex min-w-0 flex-col gap-1.5 sm:items-end" onKeyDown={onKeyDown}>
      <p className="text-label-sm text-ink-mute sm:max-w-96 sm:text-right">
        {forCount(
          count,
          `Approves the Client Invoice of ${agentName} for ${monthLabel} still to review. An approved invoice is final.`,
          `Approves the {n} Client Invoices of ${agentName} for ${monthLabel} still to review. An approved invoice is final.`,
        )}
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
          onClick={() =>
            submit(() =>
              fetch(`/api/agents/${agentId}/client-invoice-months/${monthParam(billingMonth)}/approve`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ invoiceIds }),
              }),
            )
          }
        >
          {pending ? "Approving…" : forCount(count, "Confirm approve", "Confirm approve {n}")}
        </Button>
      </div>
      <SendErrorLine error={error} />
    </div>
  );
}
