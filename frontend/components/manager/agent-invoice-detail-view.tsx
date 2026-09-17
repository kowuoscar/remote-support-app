"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { AgentInvoiceOverrideControl } from "@/components/manager/agent-invoice-override-control";
import { ApproveAgentInvoiceControl } from "@/components/manager/approve-agent-invoice-control";
import { MarkAgentInvoicePaidControl } from "@/components/manager/mark-agent-invoice-paid-control";
import { agentInvoiceStatusLabelByValue, agentInvoiceStatusToneByValue } from "@/lib/status";
import { formatBillingMonth, formatDate } from "@/lib/format";
import type { AgentInvoiceDetail } from "@/lib/api/types";

function statusNote(invoice: AgentInvoiceDetail): string {
  switch (invoice.status) {
    case "DRAFT":
      return "Still being assembled by the Agent — nothing to review yet";
    case "SENT":
      return "Lines are locked to what the Agent sent — adjust Salary or the new advance before approving";
    case "APPROVED":
      return "Approved — mark it paid once the payout has gone out";
    case "PAID":
      return "Paid — this is the final record for the month";
  }
}

function timestamps(invoice: AgentInvoiceDetail): string | null {
  if (!invoice.sentAt) return null;
  return [
    `Sent ${formatDate(invoice.sentAt)}`,
    invoice.approvedAt ? `Approved ${formatDate(invoice.approvedAt)}` : null,
    invoice.paidAt ? `Paid ${formatDate(invoice.paidAt)}` : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

type LineKey = "localSupportFees" | "salary" | "rolloutAdvanceRepayment" | "rolloutAdvanceNewAdvance";

const LINES: { key: LineKey; label: string }[] = [
  { key: "localSupportFees", label: "Local Support Fees" },
  { key: "salary", label: "Salary" },
  { key: "rolloutAdvanceRepayment", label: "Rollout Advance repayment" },
  { key: "rolloutAdvanceNewAdvance", label: "Rollout Advance new" },
];

/**
 * One Agent Invoice, in full, for the Manager's review (manager-invoice-review-queue spec, Agent
 * Invoice detail page): Local Support Fees, Salary, both Rollout Advance lines, total, status and
 * timestamps, addressed by the invoice's own id so any billing month works. While `sent` the
 * Manager can override Salary or the new advance and approve; while `approved`, mark it paid; a
 * draft or paid invoice renders read-only. Each successful action replaces the invoice shown here
 * with the one the backend returns, so the result appears in place without navigating.
 */
export function AgentInvoiceDetailView({ invoice: initial }: { invoice: AgentInvoiceDetail }) {
  const [invoice, setInvoice] = useState(initial);
  const stamps = timestamps(invoice);

  return (
    <Card className="flex flex-col gap-6 p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-ink">{formatBillingMonth(invoice.billingMonth)}</h2>
            <Badge tone={agentInvoiceStatusToneByValue[invoice.status]}>
              {agentInvoiceStatusLabelByValue[invoice.status]}
            </Badge>
          </div>
          <p className="mt-1 text-[13px] text-ink-mute">{statusNote(invoice)}</p>
          {stamps ? <p className="mt-0.5 text-[12px] text-ink-mute">{stamps}</p> : null}
        </div>
        {invoice.status === "SENT" ? (
          <ApproveAgentInvoiceControl invoiceId={invoice.id} onApproved={setInvoice} />
        ) : null}
        {invoice.status === "APPROVED" ? (
          <MarkAgentInvoicePaidControl invoiceId={invoice.id} onPaid={setInvoice} />
        ) : null}
      </div>

      <dl className="grid grid-cols-2 gap-4 rounded-lg border border-hairline bg-canvas-soft p-4 sm:grid-cols-4">
        {LINES.map(({ key, label }) => (
          <div key={key}>
            <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">{label}</dt>
            <dd className="mt-1">
              <Money amount={invoice[key]} currency={invoice.currency} className="text-base" />
            </dd>
          </div>
        ))}
      </dl>

      <div className="flex items-baseline justify-between rounded-lg border border-hairline px-4 py-3">
        <span className="text-[13px] font-medium text-ink-secondary">Total</span>
        <Money amount={invoice.totalAmount} currency={invoice.currency} emphasize className="text-lg" />
      </div>

      {invoice.status === "SENT" ? (
        <div>
          <h3 className="mb-2 text-[13px] font-medium text-ink-secondary">Adjust before approving</h3>
          <AgentInvoiceOverrideControl
            invoiceId={invoice.id}
            salary={invoice.salary}
            rolloutAdvanceNewAdvance={invoice.rolloutAdvanceNewAdvance}
            currency={invoice.currency}
            onOverridden={setInvoice}
          />
        </div>
      ) : null}
    </Card>
  );
}
