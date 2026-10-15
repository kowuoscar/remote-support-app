import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Tabs } from "@/components/ui/tabs";
import { TableScroll } from "@/components/ui/table";
import { IconSim, IconSmartphone } from "@/components/icons";

export type FleetTabId = "sim-cards" | "smartphones";

/** The `tab` search parameter, unvalidated: only `smartphones` names the second tab; SIM Cards is the default. */
export function fleetTabFromParam(value: string | undefined): FleetTabId {
  return value === "smartphones" ? "smartphones" : "sim-cards";
}

/** What one tab holds: its count, its table, the empty state when there is none, and a per-role action. */
export type FleetTabContent = {
  count: number;
  table: ReactNode;
  emptyTitle: string;
  emptyDescription: string;
  /** The selected tab's action, at the right of the header (Manager only: "Add SIM card"). */
  action?: ReactNode;
};

/**
 * The card shell of a Contract's Fleet (side-lists-and-tabs spec, "Tabs, shared"): its header row
 * is the "Fleet" tablist, "SIM Cards {n}" then "Smartphones {n}", with the selected tab's action at
 * the right; the body is that tab's table, or its empty state. Only the selected tab renders.
 * Replaces `FleetTableCard` on the Agent's and the Tester's Fleet.
 */
export function FleetTabsCard({
  tab,
  onTab,
  simCards,
  smartphones,
}: {
  tab: FleetTabId;
  onTab: (tab: FleetTabId) => void;
  simCards: FleetTabContent;
  smartphones: FleetTabContent;
}) {
  const body = (content: FleetTabContent, icon: ReactNode) =>
    content.count === 0 ? (
      <div className="px-5 py-8">
        <EmptyState icon={icon} title={content.emptyTitle} description={content.emptyDescription} />
      </div>
    ) : (
      <TableScroll className="rounded-none border-0">{content.table}</TableScroll>
    );

  return (
    <Card className="p-0">
      <Tabs
        label="Fleet"
        selectedId={tab}
        onSelect={(id) => onTab(fleetTabFromParam(id))}
        headerClassName="border-b border-hairline px-5 py-3"
        toolbar={tab === "smartphones" ? smartphones.action : simCards.action}
        tabs={[
          {
            id: "sim-cards",
            label: "SIM Cards",
            count: simCards.count,
            icon: <IconSim className="h-4 w-4 text-ink-mute" />,
            panel: body(simCards, <IconSim className="h-5 w-5" />),
          },
          {
            id: "smartphones",
            label: "Smartphones",
            count: smartphones.count,
            icon: <IconSmartphone className="h-4 w-4 text-ink-mute" />,
            panel: body(smartphones, <IconSmartphone className="h-5 w-5" />),
          },
        ]}
      />
    </Card>
  );
}
