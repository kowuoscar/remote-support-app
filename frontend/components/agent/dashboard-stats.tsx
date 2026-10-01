import { StatCard } from "@/components/ui/stat-card";
import { Money } from "@/components/ui/money";
import { Badge } from "@/components/ui/badge";
import { agentInvoiceStatusLabelByValue, agentInvoiceStatusToneByValue } from "@/lib/status";
import type { AgentInvoiceStatusValue } from "@/lib/api/types";

/** An unreadable figure: a muted dash (not the card's reserved indigo) that a screen reader hears as "Unavailable". */
const UNAVAILABLE = (
  <>
    <span aria-hidden="true" className="text-ink-mute">
      —
    </span>
    <span className="sr-only">Unavailable</span>
  </>
);

// Label slot two lines tall from sm up, so a wrapped label ("Local Support Fees — September 2026")
// does not push its value below the other cards' values.
const CARD_CLASS = "sm:[&>span:first-child]:min-h-10";

/** While Draft the line is live across the Contracts; from Sent onward it is frozen (ADR 0003). */
function localSupportFeesMeta(fees: number | null, status: AgentInvoiceStatusValue | null): string {
  if (fees === null) return "Couldn't load your invoice";
  return status === "DRAFT" ? "Running total, all your Contracts" : "As sent on your invoice";
}

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
        className={CARD_CLASS}
        data-testid="local-support-fees-stat"
        label={currentMonthLabel ? `Local Support Fees — ${currentMonthLabel}` : "Local Support Fees"}
        value={
          runningLocalSupportFees === null ? (
            UNAVAILABLE
          ) : (
            <Money amount={runningLocalSupportFees} currency={currency} emphasize />
          )
        }
        primary
        meta={localSupportFeesMeta(runningLocalSupportFees, latestInvoiceStatus)}
      />
      <StatCard
        className={CARD_CLASS}
        data-testid="open-requests-stat"
        label="Open Requests"
        value={openRequestsCount ?? UNAVAILABLE}
        primary
        meta={openRequestsCount === null ? "Couldn't load your Requests" : "Submitted or In Progress"}
      />
      <StatCard
        className={CARD_CLASS}
        data-testid="invoice-status-stat"
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
        className={CARD_CLASS}
        label="Standing salary + advance"
        value={<Money amount={salary} currency={currency} emphasize />}
        primary
        meta={`+ ${new Intl.NumberFormat("en-US", { style: "currency", currency, maximumFractionDigits: 0 }).format(rolloutAdvance)} Rollout Advance`}
      />
    </div>
  );
}
