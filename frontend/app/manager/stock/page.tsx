import { SurfacePage } from "@/components/app-shell/surface-page";
import { StockView } from "@/components/stock/stock-view";
import { StockAgentFilter } from "@/components/manager/stock-agent-filter";
import { backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { AgentListItem, StockUnitItem } from "@/lib/api/types";

export const metadata = { title: "Stock" };

/**
 * Every Agent's Stock, filterable by Agent (agent-stock ticket AC: "The Manager's navigation has
 * a Stock page showing every Agent's Stock, filterable by Agent"). The filter lives in the URL
 * (`?agentId=`), mirroring `ManagerCarriersPage`'s own Country-filter shape — including where it
 * renders: inline in the page body, not `SurfacePage`'s `actions` slot (design-review finding on
 * this feature's finisher pass: the `actions` slot sits in the sticky top bar alongside the page
 * title, which has no spare width for a label+select at mobile widths and was truncating "Stock"
 * to "St…"; `CarriersView`'s own Country filter has always rendered inline for exactly this
 * reason — `actions` was otherwise unused anywhere in the app).
 */
export default async function ManagerStockPage({
  searchParams,
}: {
  searchParams: Promise<{ agentId?: string | string[] }>;
}) {
  await requireManager();
  const rawAgentId = (await searchParams).agentId;
  const agentId = Array.isArray(rawAgentId) ? rawAgentId[0] : rawAgentId;

  const [units, agents] = await Promise.all([
    backendFetchList<StockUnitItem>(agentId ? `/api/stock?agentId=${agentId}` : "/api/stock"),
    backendFetchList<AgentListItem>("/api/agents"),
  ]);

  return (
    <SurfacePage title="Stock" subtitle="Every Agent's company-owned units, off every Contract" viewerLabel="Manager">
      <StockAgentFilter agents={agents} agentId={agentId} />
      <StockView units={units} showAgentColumn />
    </SurfacePage>
  );
}
