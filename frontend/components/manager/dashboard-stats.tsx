"use client";

import { StatCard, StatCardSkeleton } from "@/components/ui/stat-card";
import { Money } from "@/components/ui/money";
import { useSimulatedLoad } from "@/lib/use-simulated-load";

export function ManagerDashboardStats({
  pendingApprovalsCount,
  billedThisMonth,
  payoutThisMonth,
  clientCount,
  agentCount,
  contractCount,
}: {
  /** The Review Queue's size, or `null` when it couldn't be loaded. */
  pendingApprovalsCount: number | null;
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
        {Array.from({ length: 6 }).map((_, i) => (
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
