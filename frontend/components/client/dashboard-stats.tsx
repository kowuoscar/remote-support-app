"use client";

import { StatCard, StatCardSkeleton } from "@/components/ui/stat-card";
import { useSimulatedLoad } from "@/lib/use-simulated-load";

export function ClientDashboardStats({
  activeFleetCount,
  openRequestsCount,
}: {
  activeFleetCount: number;
  openRequestsCount: number;
}) {
  const loading = useSimulatedLoad();

  if (loading) {
    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2" data-testid="dashboard-loading">
        <StatCardSkeleton />
        <StatCardSkeleton />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2" data-testid="dashboard-ready">
      <StatCard
        label="Active Fleet"
        value={activeFleetCount}
        primary
        meta="Smartphones + SIM Cards, all Contracts"
      />
      <StatCard
        label="Open Requests"
        value={openRequestsCount}
        primary
        meta="Submitted or In Progress"
      />
    </div>
  );
}
