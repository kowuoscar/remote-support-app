import { StatCard } from "@/components/ui/stat-card";
import { Money } from "@/components/ui/money";
import { Badge } from "@/components/ui/badge";
import { agentInvoiceStatusLabelByValue, agentInvoiceStatusToneByValue } from "@/lib/status";
import type { AgentInvoiceStatusValue } from "@/lib/api/types";

const UNAVAILABLE = "—";

/**
 * The Agent dashboard's four stat cards. Presentational: the page loads the figures and passes
 * `null` for any region that could not be read, which renders `—` with a "Couldn't load…" meta
 * (the `ManagerDashboardStats` contract). Renders at once — nothing here is loading.
 */
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
  currentMonthLabel: string | null;
  runningLocalSupportFees: number | null;
  currency: string;
  openRequestsCount: number | null;
  latestInvoiceMonth: string | null;
  latestInvoiceStatus: AgentInvoiceStatusValue | null;
  salary: number;
  rolloutAdvance: number;
}) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" data-testid="dashboard-ready">
      <StatCard
        label={currentMonthLabel ? `Local Support Fees — ${currentMonthLabel}` : "Local Support Fees"}
        value={
          runningLocalSupportFees === null ? (
            UNAVAILABLE
          ) : (
            <Money amount={runningLocalSupportFees} currency={currency} emphasize />
          )
        }
        primary
        meta={runningLocalSupportFees === null ? "Couldn't load your invoice" : "Running total, all your Contracts"}
      />
      <StatCard
        label="Open Requests"
        value={openRequestsCount ?? UNAVAILABLE}
        primary
        meta={openRequestsCount === null ? "Couldn't load your Requests" : "Submitted or In Progress"}
      />
      <StatCard
        label="My Invoice status"
        value={
          latestInvoiceStatus === null ? (
            UNAVAILABLE
          ) : (
            <Badge tone={agentInvoiceStatusToneByValue[latestInvoiceStatus]}>
              {agentInvoiceStatusLabelByValue[latestInvoiceStatus]}
            </Badge>
          )
        }
        meta={latestInvoiceStatus === null ? "Couldn't load your invoice" : latestInvoiceMonth}
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
