import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/cn";
import type { SimCardListItem } from "@/lib/api/types";

/**
 * A SIM Card's Carrier in a Fleet table (sim-card-carrier ticket): its name, marked when the
 * Carrier has since been archived, or "—" for a SIM Card from before the catalog that never had one.
 */
export function SimCardCarrier({ sim }: { sim: SimCardListItem }) {
  if (!sim.carrierName) {
    return <span className="text-ink-mute">—</span>;
  }
  return (
    <span className="inline-flex items-center gap-2">
      <span
        translate="no"
        title={sim.carrierName}
        className={cn("max-w-30 truncate", sim.carrierArchived ? "text-ink-mute" : "text-ink-secondary")}
      >
        {sim.carrierName}
      </span>
      {sim.carrierArchived ? (
        <span className="shrink-0">
          <Badge tone="neutral">Archived</Badge>
        </span>
      ) : null}
    </span>
  );
}
