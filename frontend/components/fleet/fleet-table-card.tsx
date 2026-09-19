import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { TableScroll } from "@/components/ui/table";
import { cn } from "@/lib/cn";

/**
 * The card shell shared by every Fleet table — icon + title + count in the header (with an
 * optional per-role action, e.g. a Manager's "Add smartphone"/"Add SIM card" dialog trigger),
 * an empty state when there's nothing yet, or the table itself scoped to a scrollable card body.
 * Extracted from the near-identical copies in agent/fleet-view.tsx, client/fleet-view.tsx and
 * manager/contract-fleet-view.tsx — same markup and classes each caller had inline, so rendering
 * is unchanged.
 */
export function FleetTableCard({
  icon,
  title,
  count,
  action,
  isEmpty,
  emptyIcon,
  emptyTitle,
  emptyDescription,
  children,
}: {
  icon: ReactNode;
  title: string;
  count: number;
  action?: ReactNode;
  isEmpty: boolean;
  emptyIcon: ReactNode;
  emptyTitle: string;
  emptyDescription: string;
  children: ReactNode;
}) {
  return (
    <Card className="p-0">
      <div className="flex items-center gap-2 border-b border-hairline px-5 py-3.5">
        {icon}
        <h2 className="text-sm font-semibold text-ink">{title}</h2>
        <span className={cn("tnum text-[13px] text-ink-mute", action ? undefined : "ml-auto")}>{count}</span>
        {action ? <div className="ml-auto">{action}</div> : null}
      </div>
      {isEmpty ? (
        <div className="px-5 py-8">
          <EmptyState icon={emptyIcon} title={emptyTitle} description={emptyDescription} />
        </div>
      ) : (
        <TableScroll className="rounded-none border-0">{children}</TableScroll>
      )}
    </Card>
  );
}
