"use client";

import { useMemo, useTransition } from "react";
import { useRouter } from "next/navigation";
import { AgentMonthReview } from "@/components/manager/agent-month-review";
import { ReviewQueueView } from "@/components/manager/review-queue-view";
import { ContractSwitcher } from "@/components/ui/contract-switcher";
import { EmptyState } from "@/components/ui/empty-state";
import { SideList, SideListLayout, type SideListItem } from "@/components/ui/side-list";
import { IconAgents, IconAlertTriangle } from "@/components/icons";
import { selectionHref } from "@/lib/selection-address";
import type { ReviewAgent, ReviewMonth, ReviewMonthSummary, ReviewQueueItem } from "@/lib/api/types";
import type { ReviewQueueFilter } from "@/lib/review-queue-filter";

const PATH = "/manager/invoices";
const ALL = "all";

/** What the server reads for the one picked Agent; a null `months` or `review` is a failed read. */
export type SelectedAgent = {
  agentId: string;
  months: ReviewMonthSummary[] | null;
  /** The month shown, first-of-month ISO date. */
  billingMonth: string;
  review: ReviewMonth | null;
};

/** The count beside a row: plain `ink-mute` text, a dash with nothing waiting (read as such). */
function Waiting({ count }: { count: number }) {
  if (count === 0) {
    return (
      <span className="text-label text-ink-mute">
        <span aria-hidden="true">—</span>
        <span className="sr-only">nothing waiting</span>
      </span>
    );
  }
  return (
    <span className="tnum text-label text-ink-mute">
      {count} <span className="sr-only">waiting</span>
    </span>
  );
}

/**
 * The Manager's Invoices page (manager-reviews-by-agent spec, `ManagerInvoicesView`): an "Agents"
 * side list (a dropdown below `xl`) with "All Agents" first, which is today's Review Queue, then one
 * row per Agent with what waits on the Manager. A pick is a `router.replace` to `?agent=`, which
 * drops `?month=` and `?type=`; meanwhile the area keeps its content, `aria-busy`. With no Agent, or
 * when the Agents failed to load, the queue shows alone as before.
 */
export function ManagerInvoicesView({
  items,
  now,
  initialFilter,
  agents,
  selected,
}: {
  /** Null when the Review Queue failed to load. */
  items: ReviewQueueItem[] | null;
  now: string;
  initialFilter: ReviewQueueFilter;
  /** Null when the Agents read failed. */
  agents: ReviewAgent[] | null;
  /** Null under All Agents. */
  selected: SelectedAgent | null;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  const rows = useMemo(() => {
    const total = (agents ?? []).reduce((sum, agent) => sum + agent.waiting, 0);
    return [
      { id: ALL, name: "All Agents", waiting: total },
      ...(agents ?? []).map((agent) => ({ id: agent.agentId, name: agent.agentName, waiting: agent.waiting })),
    ];
  }, [agents]);

  const hrefFor = (id: string) => selectionHref(PATH, { agent: id === ALL ? null : id });

  function pick(id: string) {
    // Async, so the area stays busy until the navigation settles (React 19 holds an async transition open).
    startTransition(async () => {
      await router.replace(hrefFor(id), { scroll: false });
    });
  }

  const queue = items ? (
    <ReviewQueueView items={items} now={now} initialFilter={initialFilter} />
  ) : (
    <EmptyState
      icon={<IconAlertTriangle className="h-5 w-5" />}
      title="Couldn't load the Review Queue"
      description="Refresh the page to try again."
    />
  );

  if (!agents || agents.length === 0) {
    return (
      <SideListLayout list={null} dropdown={null}>
        {queue}
      </SideListLayout>
    );
  }

  const selectedId = selected?.agentId ?? ALL;
  const agentName = agents.find((agent) => agent.agentId === selectedId)?.agentName ?? "";
  const listItems: SideListItem[] = rows.map((row) => ({ id: row.id, name: row.name, badge: <Waiting count={row.waiting} /> }));

  return (
    <SideListLayout
      list={<SideList label="Agents" items={listItems} selectedId={selectedId} hrefFor={hrefFor} onSelect={pick} />}
      dropdown={
        <ContractSwitcher
          contracts={rows.map((row) => ({
            id: row.id,
            label: row.name,
            meta: row.waiting === 0 ? "Nothing waiting" : `${row.waiting} waiting`,
          }))}
          value={selectedId}
          onChange={pick}
          icon={IconAgents}
        />
      }
    >
      <div aria-busy={pending} className="transition-opacity aria-busy:opacity-70">
        {selected ? (
          <AgentMonthReview
            agentId={selected.agentId}
            agentName={agentName}
            months={selected.months}
            billingMonth={selected.billingMonth}
            review={selected.review}
          />
        ) : (
          queue
        )}
      </div>
    </SideListLayout>
  );
}
