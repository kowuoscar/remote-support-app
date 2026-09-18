import { Badge } from "@/components/ui/badge";
import type { SimCardListItem } from "@/lib/api/types";

/**
 * A SIM Card's Postpaid Plan in a Fleet table (postpaid-sim-plan ticket): its name, marked when
 * the Plan has since been archived, or "—" for a Prepaid SIM and for a Postpaid SIM from before
 * the catalog, which keeps its own monthly fee. Mirrors {@link SimCardCarrier}.
 */
export function SimCardPlan({ sim }: { sim: SimCardListItem }) {
  if (!sim.postpaidPlanName) {
    return <span className="text-ink-mute">—</span>;
  }
  return (
    <span className="inline-flex items-center gap-2">
      <span translate="no" className={sim.postpaidPlanArchived ? "text-ink-mute" : "text-ink-secondary"}>
        {sim.postpaidPlanName}
      </span>
      {sim.postpaidPlanArchived ? <Badge tone="neutral">Archived</Badge> : null}
    </span>
  );
}
