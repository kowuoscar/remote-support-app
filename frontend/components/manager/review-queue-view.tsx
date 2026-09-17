"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Money } from "@/components/ui/money";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { EmptyState } from "@/components/ui/empty-state";
import { IconArrowRight, IconInbox } from "@/components/icons";
import { formatBillingMonth, formatDate, formatWaitingTime } from "@/lib/format";
import type { ReviewQueueItem, ReviewQueueKind } from "@/lib/api/types";
import { reviewQueueFilterSearch, type ReviewQueueFilter } from "@/lib/review-queue-filter";

type Filter = ReviewQueueFilter;

const FILTERS: { value: Filter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "CLIENT_INVOICE", label: "Client Invoices" },
  { value: "AGENT_INVOICE", label: "Agent Invoices" },
];

const KIND_LABEL: Record<ReviewQueueKind, string> = {
  CLIENT_INVOICE: "Client Invoice",
  AGENT_INVOICE: "Agent Invoice",
};

/** A Client Invoice is for a Contract ("Client — Agent"); an Agent Invoice is for its Agent. */
function subject(item: ReviewQueueItem): string {
  return item.kind === "CLIENT_INVOICE" ? `${item.clientName} — ${item.agentName}` : item.agentName;
}

function detailHref(item: ReviewQueueItem): string {
  return item.kind === "CLIENT_INVOICE"
    ? `/manager/invoices/client/${item.id}`
    : `/manager/invoices/agent/${item.id}`;
}

/** Only an approved Agent Invoice waits for payment; everything else in the queue waits for approval. */
function awaiting(item: ReviewQueueItem): { label: string; since: string } {
  return item.kind === "AGENT_INVOICE" && item.status === "APPROVED"
    ? { label: "Payment", since: "Approved" }
    : { label: "Approval", since: "Sent" };
}

/**
 * The Manager's Review Queue (CONTEXT.md; manager-invoice-review-queue spec, Invoices page): every
 * invoice waiting on a Manager action, in the backend's longest-waiting-first order, each row
 * opening that invoice's detail page where the action is taken. `now` is passed in by the server
 * page so "how long it has waited" renders the same on server and client. The type filter is
 * reflected in `?type=` so a filtered queue survives a reload and can be linked.
 */
export function ReviewQueueView({
  items,
  now,
  initialFilter = "ALL",
}: {
  items: ReviewQueueItem[];
  now: string;
  initialFilter?: ReviewQueueFilter;
}) {
  const [filter, setFilter] = useState<Filter>(initialFilter);

  function selectFilter(next: Filter) {
    setFilter(next);
    window.history.replaceState(null, "", reviewQueueFilterSearch(next) || window.location.pathname);
  }

  const filtered = useMemo(
    () => (filter === "ALL" ? items : items.filter((item) => item.kind === filter)),
    [items, filter],
  );

  return (
    <div className="flex flex-col gap-4">
      <div
        role="tablist"
        aria-label="Filter the Review Queue by invoice type"
        className="inline-flex w-fit items-center gap-1 rounded-lg border border-hairline bg-canvas-soft p-1"
      >
        {FILTERS.map(({ value, label }) => (
          <button
            key={value}
            role="tab"
            type="button"
            aria-selected={filter === value}
            onClick={() => selectFilter(value)}
            className={`rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors ${
              filter === value ? "bg-canvas text-ink shadow-sm" : "text-ink-mute hover:text-ink"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          icon={<IconInbox className="h-5 w-5" />}
          title="Nothing is waiting on you"
          description="Invoices land here as soon as an Agent sends them, longest waiting first."
        />
      ) : (
        <TableScroll>
          <Table>
            <Thead>
              <Tr>
                <Th>Waiting</Th>
                <Th>Type</Th>
                <Th>Subject</Th>
                <Th>Waiting for</Th>
                <Th>Billing month</Th>
                <Th className="text-right">Total</Th>
                {/* relative: keeps the sr-only label inside the table's scroll container on narrow screens */}
                <Th className="relative text-right">
                  <span className="sr-only">Action</span>
                </Th>
              </Tr>
            </Thead>
            <Tbody>
              {filtered.map((item) => (
                <Tr key={item.id}>
                  <Td className="whitespace-nowrap">
                    <time
                      dateTime={item.waitingSince}
                      title={`${awaiting(item).since} ${formatDate(item.waitingSince)}`}
                    >
                      {formatWaitingTime(item.waitingSince, now)}
                    </time>
                  </Td>
                  <Td>
                    <Badge tone="info">{KIND_LABEL[item.kind]}</Badge>
                  </Td>
                  <Td className="whitespace-nowrap font-medium text-ink">{subject(item)}</Td>
                  <Td className="whitespace-nowrap text-ink-secondary">{awaiting(item).label}</Td>
                  <Td className="whitespace-nowrap text-ink-mute">{formatBillingMonth(item.billingMonth)}</Td>
                  <Td className="text-right">
                    <Money amount={item.totalAmount} currency={item.currency} />
                  </Td>
                  <Td className="text-right">
                    <Link
                      href={detailHref(item)}
                      aria-label={`Review ${KIND_LABEL[item.kind]}: ${subject(item)}, ${formatBillingMonth(item.billingMonth)}`}
                      className="inline-flex h-7 items-center gap-1.5 whitespace-nowrap rounded-lg bg-primary-soft-bg px-3 text-[13px] font-medium text-primary-soft-text transition-colors hover:bg-primary/20 active:bg-primary/25"
                    >
                      Review
                      <IconArrowRight className="h-3.5 w-3.5" />
                    </Link>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      )}
    </div>
  );
}
