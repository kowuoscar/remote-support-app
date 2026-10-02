import { StatCard } from "@/components/ui/stat-card";

/** An unreadable figure: a muted dash (not the card's reserved indigo) that a screen reader hears as "Unavailable". */
const UNAVAILABLE = (
  <>
    <span aria-hidden="true" className="text-ink-mute">
      —
    </span>
    <span className="sr-only">Unavailable</span>
  </>
);

/**
 * The Client dashboard's two stat cards. Presentational: the page loads the counts and passes
 * `null` for any region that could not be read, which renders `—` with a "Couldn't load…" meta
 * (the `AgentDashboardStats` contract). Renders at once — nothing here is loading.
 */
export function ClientDashboardStats({
  activeFleetCount,
  openRequestsCount,
}: {
  activeFleetCount: number | null;
  openRequestsCount: number | null;
}) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2" data-testid="dashboard-ready">
      <StatCard
        data-testid="active-fleet-stat"
        label="Active Fleet"
        value={activeFleetCount ?? UNAVAILABLE}
        primary
        meta={activeFleetCount === null ? "Couldn't load your Fleet" : "Smartphones + SIM Cards, all Contracts"}
      />
      <StatCard
        data-testid="open-requests-stat"
        label="Open Requests"
        value={openRequestsCount ?? UNAVAILABLE}
        primary
        meta={
          openRequestsCount === null ? "Couldn't load your Requests" : "Pending Approval, Submitted or In Progress"
        }
      />
    </div>
  );
}
