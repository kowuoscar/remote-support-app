"use client";

import { useTransition } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Money } from "@/components/ui/money";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { ApproveClientInvoiceMonthControl } from "./approve-client-invoice-month-control";
import { IconAlertTriangle, IconArrowRight, IconInbox } from "@/components/icons";
import { formatBillingMonth, formatDate } from "@/lib/format";
import { monthParam } from "@/lib/review-months";
import { selectionHref } from "@/lib/selection-address";
import {
  agentInvoiceStatusLabelByValue,
  agentInvoiceStatusToneByValue,
  reviewStatusLabelByValue,
  reviewStatusToneByValue,
} from "@/lib/status";
import type { ReviewMonth, ReviewMonthSummary } from "@/lib/api/types";

const PATH = "/manager/invoices";

/** The row-tone "Review →" link of the Review Queue, so the page's two views read as one family. */
function ReviewLink({ href, label }: { href: string; label: string }) {
  return (
    <Link
      href={href}
      aria-label={label}
      className="inline-flex h-7 items-center gap-1.5 whitespace-nowrap rounded-lg bg-primary-soft-bg px-3 text-label font-medium text-primary-soft-text transition-colors hover:bg-primary/20 active:bg-primary/25"
    >
      Review
      <IconArrowRight className="h-3.5 w-3.5" />
    </Link>
  );
}

/**
 * An Agent's month as the Manager reviews it (manager-reviews-by-agent spec, `AgentMonthReview`):
 * a hairline header (the month, when it was sent, how far through it the Manager is, and the Month
 * select), the Client Invoices that reached the Manager one per row, and the Agent's own Agent Invoice
 * once sent. Every row opens its detail page, where Approve and Send back stay; the header holds the one
 * pill, the approval of every row still to review at once. `months` is
 * null when the Agent's months failed to load; `review` is null when the month read failed (or the
 * months read did).
 */
export function AgentMonthReview({
  agentId,
  agentName,
  months,
  billingMonth,
  review,
}: {
  agentId: string;
  agentName: string;
  months: ReviewMonthSummary[] | null;
  /** The month shown, first-of-month ISO date. */
  billingMonth: string;
  review: ReviewMonth | null;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  if (months === null || (months.length > 0 && review === null)) {
    return (
      <EmptyState
        icon={<IconAlertTriangle className="h-5 w-5" />}
        title={`Couldn't load ${agentName}'s invoices.`}
        description="Refresh the page to try again."
      />
    );
  }
  if (months.length === 0) {
    return (
      <EmptyState
        icon={<IconInbox className="h-5 w-5" />}
        title={`${agentName} hasn't sent a month yet.`}
        description="Their Client Invoices appear here once they send their month."
      />
    );
  }
  if (review === null) return null; // not reached: months is non-empty here, so a null review failed above

  const monthLabel = formatBillingMonth(billingMonth);
  const invoices = review.clientInvoices;
  const toReview = invoices.filter((invoice) => invoice.reviewStatus === "TO_REVIEW").length;
  const approved = invoices.filter((invoice) => invoice.reviewStatus === "APPROVED").length;
  const sentBack = invoices.filter((invoice) => invoice.reviewStatus === "SENT_BACK").length;
  const unsent = invoices.length === 0 && review.agentInvoice === null;

  let sentence: string | null = null;
  if (invoices.length === 0) {
    sentence = unsent ? null : `No Client Invoice sent for ${monthLabel}.`;
  } else {
    const sentOn = review.monthSentAt ? `Sent to you on ${formatDate(review.monthSentAt)} · ` : "";
    sentence =
      toReview > 0
        ? `${sentOn}${toReview} of ${invoices.length} to review`
        : `${sentOn}${approved} of ${invoices.length} approved${sentBack > 0 ? ` · ${sentBack} sent back` : ""}`;
  }

  // The shown month is selectable even when the Agent never sent it.
  const options = months.some((month) => month.billingMonth === billingMonth)
    ? months
    : [...months, { billingMonth, waiting: 0 }].sort((a, b) => b.billingMonth.localeCompare(a.billingMonth));

  function pick(month: string) {
    startTransition(async () => {
      await router.replace(selectionHref(PATH, { agent: agentId, month }), { scroll: false });
    });
  }

  return (
    <div aria-busy={pending} className="flex flex-col gap-4 transition-opacity aria-busy:opacity-70">
      <section aria-label="Month">
        <Card className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3">
          <div className="flex min-w-0 flex-col gap-1.5">
            <div className="text-sm font-semibold text-ink">
              <h2 className="inline">{monthLabel}</h2>
              {sentence ? <span className="tnum font-normal text-ink-secondary"> · {sentence}</span> : null}
            </div>
            <label className="flex items-center gap-2 text-label font-medium text-ink-secondary">
              Month
              <select
                value={monthParam(billingMonth)}
                onChange={(event) => pick(event.target.value)}
                className="h-9 max-w-full rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
              >
                {options.map((month) => (
                  <option key={month.billingMonth} value={monthParam(month.billingMonth)}>
                    {formatBillingMonth(month.billingMonth)}
                    {month.waiting > 0 ? ` · ${month.waiting} waiting` : ""}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {toReview > 0 ? (
            <ApproveClientInvoiceMonthControl
              agentId={agentId}
              agentName={agentName}
              billingMonth={billingMonth}
              invoiceIds={invoices.filter((invoice) => invoice.reviewStatus === "TO_REVIEW").map((invoice) => invoice.id)}
            />
          ) : null}
        </Card>
      </section>

      {unsent ? (
        <EmptyState
          icon={<IconInbox className="h-5 w-5" />}
          title={`${agentName} hasn't sent ${monthLabel} yet.`}
        />
      ) : null}

      {invoices.length > 0 ? (
        <TableScroll>
          <Table>
            <Thead>
              <Tr>
                <Th>Client</Th>
                <Th>Status</Th>
                <Th className="text-right">Total</Th>
                {/* relative: keeps the sr-only label inside the table's scroll container on narrow screens */}
                <Th className="relative text-right">
                  <span className="sr-only">Action</span>
                </Th>
              </Tr>
            </Thead>
            <Tbody>
              {invoices.map((invoice) => {
                return (
                  <Tr key={invoice.id}>
                    <Td className="whitespace-nowrap font-medium text-ink">{invoice.clientName}</Td>
                    <Td>
                      <Badge tone={reviewStatusToneByValue[invoice.reviewStatus]} dot={invoice.reviewStatus !== "SENT_BACK"}>
                        {invoice.reviewStatus === "SENT_BACK" ? <IconAlertTriangle className="h-3 w-3 shrink-0" /> : null}
                        {reviewStatusLabelByValue[invoice.reviewStatus]}
                      </Badge>
                    </Td>
                    <Td className="text-right">
                      <Money amount={invoice.totalAmount} currency={invoice.currency} />
                    </Td>
                    <Td className="text-right">
                      <ReviewLink
                        href={`/manager/invoices/client/${invoice.id}`}
                        label={`Review Client Invoice: ${invoice.clientName} — ${agentName}, ${monthLabel}`}
                      />
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        </TableScroll>
      ) : null}

      {review.agentInvoice ? <AgentInvoiceRow agentName={agentName} monthLabel={monthLabel} invoice={review.agentInvoice} /> : null}
    </div>
  );
}

/** The Agent's own pay invoice for the month, once sent: reviewed on its own page, never part of the month's list. */
function AgentInvoiceRow({
  agentName,
  monthLabel,
  invoice,
}: {
  agentName: string;
  monthLabel: string;
  invoice: NonNullable<ReviewMonth["agentInvoice"]>;
}) {
  return (
    <section aria-label="Agent Invoice">
      <Card className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <span className="text-sm font-semibold text-ink">Agent Invoice</span>
          <Badge tone={agentInvoiceStatusToneByValue[invoice.status]}>{agentInvoiceStatusLabelByValue[invoice.status]}</Badge>
        </div>
        <div className="ml-auto flex items-center gap-4">
          <Money amount={invoice.totalAmount} currency={invoice.currency} className="text-sm" />
          <ReviewLink
            href={`/manager/invoices/agent/${invoice.id}`}
            label={`Review Agent Invoice: ${agentName}, ${monthLabel}`}
          />
        </div>
      </Card>
    </section>
  );
}
