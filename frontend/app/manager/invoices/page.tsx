import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerInvoicesView, type SelectedAgent } from "@/components/manager/manager-invoices-view";
import { backendFetch, backendFetchJsonOrNull } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { ReviewAgent, ReviewMonth, ReviewMonthSummary, ReviewQueueItem } from "@/lib/api/types";
import { reviewQueueFilterFromParam } from "@/lib/review-queue-filter";
import { reviewMonthFor } from "@/lib/review-months";
import { firstParam } from "@/lib/search-params";

export const metadata = { title: "Invoices" };

/**
 * The Manager's Invoices (CONTEXT.md; manager-reviews-by-agent spec, "The page"), read from the
 * backend on every request. `?agent=`, `?month=` and `?type=` are read once, here. The Review Queue
 * and the Agents list are read in parallel; when `?agent=` names a listed Agent, that Agent's months
 * and the resolved month are read too. An Agent the list does not hold opens All Agents.
 */
export default async function ManagerInvoicesPage({
  searchParams,
}: {
  searchParams: Promise<{ agent?: string | string[]; month?: string | string[]; type?: string | string[] }>;
}) {
  await requireManager();
  const params = await searchParams;
  const agentParam = firstParam(params.agent);
  const monthParam = firstParam(params.month);
  const filter = reviewQueueFilterFromParam(params.type);

  const [queueResponse, agents] = await Promise.all([
    backendFetch("/api/review-queue"),
    backendFetchJsonOrNull<ReviewAgent[]>("/api/review-queue/agents", "Manager Invoices Agents"),
  ]);
  const items = queueResponse.ok ? ((await queueResponse.json()) as ReviewQueueItem[]) : null;

  const agent = agentParam ? agents?.find((candidate) => candidate.agentId === agentParam) : undefined;
  let selected: SelectedAgent | null = null;
  if (agent) {
    const base = `/api/review-queue/agents/${agent.agentId}/months`;
    const months = await backendFetchJsonOrNull<ReviewMonthSummary[]>(base, "Manager Invoices Agent months");
    const currentMonth = `${new Date().toISOString().slice(0, 7)}-01`;
    const billingMonth = reviewMonthFor(months ?? [], monthParam, currentMonth);
    const review =
      months && months.length > 0
        ? await backendFetchJsonOrNull<ReviewMonth>(`${base}/${billingMonth.slice(0, 7)}`, "Manager Invoices Agent month")
        : null;
    selected = { agentId: agent.agentId, months, billingMonth, review };
  }

  return (
    <SurfacePage
      title="Invoices"
      subtitle={agent ? `${agent.agentName}'s invoices, month by month` : "Waiting on you, longest waiting first"}
      viewerLabel="Manager"
      bleed
    >
      <ManagerInvoicesView
        items={items}
        now={new Date().toISOString()}
        initialFilter={filter}
        agents={agents}
        selected={selected}
      />
    </SurfacePage>
  );
}
