"use client";

import { StatCard, StatCardSkeleton } from "@/components/ui/stat-card";
import { Money } from "@/components/ui/money";
import { Badge } from "@/components/ui/badge";
import { useSimulatedLoad } from "@/lib/use-simulated-load";
import { agentInvoiceStatusLabel, agentInvoiceStatusTone } from "@/lib/status";
import type { AgentInvoiceStatus } from "@/lib/demo/types";

export function AgentDashboardStats({
  currentMonthLabel,
  runningLocalSupportFees,
  currency,
  openRequestsCount,
  latestInvoiceMonth,
  latestInvoiceStatus,
  salary,
  rolloutAdvance,
}: {
  currentMonthLabel: string;
  runningLocalSupportFees: number;
  currency: string;
  openRequestsCount: number;
  latestInvoiceMonth: string;
  latestInvoiceStatus: AgentInvoiceStatus;
  salary: number;
  rolloutAdvance: number;
}) {
  const loading = useSimulatedLoad();

  if (loading) {
    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" data-testid="dashboard-loading">
        {Array.from({ length: 4 }).map((_, i) => (
          <StatCardSkeleton key={i} />
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" data-testid="dashboard-ready">
      <StatCard
        label={`Local Support Fees — ${currentMonthLabel}`}
        value={<Money amount={runningLocalSupportFees} currency={currency} emphasize />}
        primary
        meta="Running total, all your Contracts"
      />
      <StatCard
        label="Open Requests"
        value={openRequestsCount}
        primary
        meta="Submitted or In Progress"
      />
      <StatCard
        label="My Invoice status"
        value={<Badge tone={agentInvoiceStatusTone[latestInvoiceStatus]}>{agentInvoiceStatusLabel[latestInvoiceStatus]}</Badge>}
        meta={latestInvoiceMonth}
      />
      <StatCard
        label="Standing salary + advance"
        value={<Money amount={salary} currency={currency} emphasize />}
        primary
        meta={`+ ${new Intl.NumberFormat("en-US", { style: "currency", currency, maximumFractionDigits: 0 }).format(rolloutAdvance)} Rollout Advance`}
      />
    </div>
  );
}
