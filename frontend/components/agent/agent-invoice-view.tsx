import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { IconMyInvoice } from "@/components/icons";
import { SendAgentInvoiceControl } from "@/components/agent/send-agent-invoice-control";
import { agentInvoiceStatusLabelByValue, agentInvoiceStatusToneByValue } from "@/lib/status";
import { formatDate } from "@/lib/format";
import type { AgentInvoiceDetail } from "@/lib/api/types";

function billingMonthLabel(billingMonth: string): string {
  // billingMonth is always a first-of-month ISO date — parsed as UTC so it never rolls back to
  // the previous month in a timezone behind UTC (mirrors ManagerContractClientInvoiceView's
  // helper).
  return new Date(`${billingMonth}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

/**
 * An Agent's monthly invoice, all four line items scannable at a glance (spec.md Solution's Agent
 * Invoice entity; agent-standing-amounts-and-invoice-generation ticket, user story 26: "auto-
 * populate ... so that I only need to review before submitting"; agent-invoice-submission-and-
 * approval ticket, user story 28: "see the status history of ... my own Agent Invoice"). Mirrors
 * ManagerContractClientInvoiceView's summary-grid and status-timestamp treatment. Used from both
 * `/agent/my-invoice` (the owning Agent, with `showSendAction` opted in) and reused read-only
 * from the Manager's per-Agent review view — kept as its own component rather than inlined in
 * either page for exactly that reason.
 */
export function AgentInvoiceView({
  invoice,
  showSendAction = false,
}: {
  invoice: AgentInvoiceDetail;
  /** Only the owning Agent's own page passes this — sending is never the Manager's act (ticket AC). */
  showSendAction?: boolean;
}) {
  return (
    <Card className="flex flex-col gap-6 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-hairline pb-3.5">
        <div>
          <div className="flex items-center gap-2">
            <IconMyInvoice className="h-4 w-4 text-ink-mute" />
            <h2 className="text-sm font-semibold text-ink">{billingMonthLabel(invoice.billingMonth)}</h2>
            <Badge tone={agentInvoiceStatusToneByValue[invoice.status]}>
              {agentInvoiceStatusLabelByValue[invoice.status]}
            </Badge>
          </div>
          {invoice.sentAt ? (
            <p className="mt-1 text-[12px] text-ink-mute">
              Sent {formatDate(invoice.sentAt)}
              {invoice.approvedAt ? ` · Approved ${formatDate(invoice.approvedAt)}` : ""}
              {invoice.paidAt ? ` · Paid ${formatDate(invoice.paidAt)}` : ""}
            </p>
          ) : null}
        </div>
        {showSendAction && invoice.status === "DRAFT" ? (
          <SendAgentInvoiceControl agentId={invoice.agentId} />
        ) : null}
      </div>

      <dl className="grid grid-cols-2 gap-4 rounded-lg border border-hairline bg-canvas-soft p-4 sm:grid-cols-4">
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Local Support Fees</dt>
          <dd className="mt-1">
            <Money amount={invoice.localSupportFees} currency={invoice.currency} className="text-base" />
          </dd>
        </div>
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Salary</dt>
          <dd className="mt-1">
            <Money amount={invoice.salary} currency={invoice.currency} className="text-base" />
          </dd>
        </div>
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Rollout Advance repayment</dt>
          <dd className="mt-1">
            <Money amount={invoice.rolloutAdvanceRepayment} currency={invoice.currency} className="text-base" />
          </dd>
        </div>
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Rollout Advance new</dt>
          <dd className="mt-1">
            <Money amount={invoice.rolloutAdvanceNewAdvance} currency={invoice.currency} className="text-base" />
          </dd>
        </div>
      </dl>

      <div className="flex items-baseline justify-between rounded-lg border border-hairline px-4 py-3">
        <span className="text-[13px] font-medium text-ink-secondary">Total</span>
        <Money amount={invoice.totalAmount} currency={invoice.currency} emphasize className="text-lg" />
      </div>
    </Card>
  );
}
