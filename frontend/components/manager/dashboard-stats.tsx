import Link from "next/link";
import { StatCard, UNAVAILABLE_FIGURE } from "@/components/ui/stat-card";
import { Money } from "@/components/ui/money";
import { IconArrowRight } from "@/components/icons";
import type { CurrencyAmount } from "@/lib/api/types";
import { formatBillingMonth } from "@/lib/format";

/**
 * One Money line per currency, in the read's order (never a sum); `[]` is a plain `0` with no
 * currency mark; `null` is the unavailable figure.
 */
function moneyFigure(lines: CurrencyAmount[] | null) {
  if (lines === null) return UNAVAILABLE_FIGURE;
  if (lines.length === 0) return "0";
  return (
    <span className="flex flex-col">
      {lines.map((line) => (
        <Money key={line.currency} amount={line.amount} currency={line.currency} emphasize />
      ))}
    </span>
  );
}

function moneyMeta(lines: CurrencyAmount[] | null, rule: string, billingMonth: string | null) {
  if (lines === null) return "Couldn't load this month's totals";
  return billingMonth ? `${rule} · ${formatBillingMonth(billingMonth)}` : rule;
}

export function ManagerDashboardStats({
  pendingApprovalsCount,
  pendingRequestsCount,
  billedThisMonth,
  payoutThisMonth,
  billingMonth,
  clientCount,
  agentCount,
  contractCount,
}: {
  /** The Review Queue's size, or `null` when it couldn't be loaded. */
  pendingApprovalsCount: number | null;
  /**
   * The Pending Requests list's size, or `null` when it couldn't be loaded
   * (manager-approves-requests ticket AC: "The Manager's dashboard shows the pending count,
   * linking to the page"). Distinct from `pendingApprovalsCount` above: that one counts invoices
   * waiting for review (the Review Queue), this counts Requests waiting for approval — the two
   * stay separate stats since the Review Queue itself stays invoices-only (spec.md).
   */
  pendingRequestsCount: number | null;
  /** Per-currency totals from `GET /api/tenant-totals`: `null` = unavailable, `[]` = a plain 0. */
  billedThisMonth: CurrencyAmount[] | null;
  payoutThisMonth: CurrencyAmount[] | null;
  /** "YYYY-MM-01" from the same read, for the cards' meta; never the wall clock. */
  billingMonth: string | null;
  /** The Tenant's Client, Agent and Contract list lengths, or `null` when that list couldn't be loaded. */
  clientCount: number | null;
  agentCount: number | null;
  contractCount: number | null;
}) {
  return (
    <div
      className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
      data-testid="dashboard-ready"
    >
      <StatCard
        label="Pending approvals"
        value={pendingApprovalsCount ?? "—"}
        primary
        meta={
          pendingApprovalsCount === null
            ? "Couldn’t load the Review Queue"
            : "Client + Agent Invoices awaiting review"
        }
        data-testid="pending-approvals-stat"
      />
      <StatCard
        label="Pending Requests"
        value={pendingRequestsCount ?? "—"}
        primary
        meta={
          <Link
            href="/manager/requests"
            className="inline-flex items-center gap-1 text-primary hover:text-primary-hover"
          >
            {pendingRequestsCount === null ? "Couldn’t load — open Pending Requests" : "Review requests"}
            <IconArrowRight className="h-3 w-3" />
          </Link>
        }
        data-testid="pending-requests-stat"
      />
      <StatCard
        label="Billed this month"
        value={moneyFigure(billedThisMonth)}
        primary
        meta={moneyMeta(billedThisMonth, "Sent or approved", billingMonth)}
        data-testid="billed-stat"
      />
      <StatCard
        label="Payout this month"
        value={moneyFigure(payoutThisMonth)}
        primary
        meta={moneyMeta(payoutThisMonth, "Approved or paid", billingMonth)}
        data-testid="payout-stat"
      />
      <StatCard
        label="Clients"
        value={clientCount ?? UNAVAILABLE_FIGURE}
        primary
        meta={clientCount === null ? "Couldn't load your Clients" : "In this tenant"}
        data-testid="clients-stat"
      />
      <StatCard
        label="Agents"
        value={agentCount ?? UNAVAILABLE_FIGURE}
        primary
        meta={agentCount === null ? "Couldn't load your Agents" : "Across all countries"}
        data-testid="agents-stat"
      />
      <StatCard
        label="Contracts"
        value={contractCount ?? UNAVAILABLE_FIGURE}
        primary
        meta={contractCount === null ? "Couldn't load your Contracts" : "Client × Agent pairs"}
        data-testid="contracts-stat"
      />
    </div>
  );
}
