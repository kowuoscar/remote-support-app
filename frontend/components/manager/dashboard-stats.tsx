"use client";

import Link from "next/link";
import { StatCard, StatCardSkeleton } from "@/components/ui/stat-card";
import { Money } from "@/components/ui/money";
import { IconArrowRight } from "@/components/icons";
import { useSimulatedLoad } from "@/lib/use-simulated-load";

export function ManagerDashboardStats({
  pendingApprovalsCount,
  pendingRequestsCount,
  billedThisMonth,
  payoutThisMonth,
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
  billedThisMonth: number;
  payoutThisMonth: number;
  clientCount: number;
  agentCount: number;
  contractCount: number;
}) {
  const loading = useSimulatedLoad();

  if (loading) {
    return (
      <div
        className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
        data-testid="dashboard-loading"
      >
        {Array.from({ length: 7 }).map((_, i) => (
          <StatCardSkeleton key={i} />
        ))}
      </div>
    );
  }

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
        value={<Money amount={billedThisMonth} currency="USD" emphasize />}
        primary
        meta="Tenant-wide, all Contracts"
      />
      <StatCard
        label="Payout this month"
        value={<Money amount={payoutThisMonth} currency="USD" emphasize />}
        primary
        meta="Tenant-wide, all Agents"
      />
      <StatCard label="Clients" value={clientCount} primary meta="Active this tenant" />
      <StatCard label="Agents" value={agentCount} primary meta="Across all countries" />
      <StatCard label="Contracts" value={contractCount} primary meta="Client × Agent pairs" />
    </div>
  );
}
