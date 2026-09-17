import Link from "next/link";
import { Badge, type Tone } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { IconArrowRight, IconInvoices } from "@/components/icons";
import {
  agentInvoiceStatusLabelByValue,
  agentInvoiceStatusToneByValue,
  clientInvoiceStatusLabelByValue,
  clientInvoiceStatusToneByValue,
} from "@/lib/status";
import { formatBillingMonth } from "@/lib/format";
import type { AgentInvoiceStatusValue, ClientInvoiceStatusValue } from "@/lib/api/types";

interface SummaryFields<S> {
  id: string;
  billingMonth: string;
  status: S;
  totalAmount: number;
  currency: string;
}

type InvoiceSummaryCardProps =
  | { kind: "CLIENT_INVOICE"; invoice: SummaryFields<ClientInvoiceStatusValue> }
  | { kind: "AGENT_INVOICE"; invoice: SummaryFields<AgentInvoiceStatusValue> };

function describe(props: InvoiceSummaryCardProps): { title: string; href: string; statusLabel: string; tone: Tone } {
  if (props.kind === "AGENT_INVOICE") {
    return {
      title: "Agent Invoice",
      href: `/manager/invoices/agent/${props.invoice.id}`,
      statusLabel: agentInvoiceStatusLabelByValue[props.invoice.status],
      tone: agentInvoiceStatusToneByValue[props.invoice.status],
    };
  }
  return {
    title: "Client Invoice",
    href: `/manager/invoices/client/${props.invoice.id}`,
    statusLabel: clientInvoiceStatusLabelByValue[props.invoice.status],
    tone: clientInvoiceStatusToneByValue[props.invoice.status],
  };
}

/**
 * A Contract's or Agent's current-month invoice at a glance (manager-invoice-review-queue spec,
 * Contract and Agent pages): billing month, status and total, with a link to the invoice's own
 * detail page — the one place its review actions live. The link works for any status, draft
 * included, since the detail page renders a draft or final invoice read-only.
 */
export function InvoiceSummaryCard(props: InvoiceSummaryCardProps) {
  const { invoice } = props;
  const headingId = `invoice-summary-${invoice.id}`;
  const { title, href, statusLabel, tone } = describe(props);

  return (
    <Card className="p-0">
      <section aria-labelledby={headingId}>
        <div className="flex items-center gap-2 border-b border-hairline px-5 py-3.5">
          <IconInvoices className="h-4 w-4 text-ink-mute" />
          <h2 id={headingId} className="text-sm font-semibold text-ink">
            {title}
          </h2>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 px-5 py-4">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <p className="text-sm font-medium text-ink">{formatBillingMonth(invoice.billingMonth)}</p>
            <Badge tone={tone}>{statusLabel}</Badge>
          </div>
          <div className="flex items-center gap-4">
            <p className="flex items-baseline gap-2">
              <span className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Total</span>
              <Money amount={invoice.totalAmount} currency={invoice.currency} className="text-base font-semibold" />
            </p>
            <Link
              href={href}
              className="inline-flex h-7 shrink-0 items-center gap-1.5 rounded-lg border border-hairline-strong bg-canvas px-3 text-[13px] font-medium text-ink transition-colors hover:bg-canvas-soft"
            >
              Open invoice
              <IconArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      </section>
    </Card>
  );
}
