import { SurfacePage } from "@/components/app-shell/surface-page";
import { StockView } from "@/components/stock/stock-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import type { StockUnitItem } from "@/lib/api/types";

export const metadata = { title: "Stock" };

/**
 * The Agent's own Stock (returns-and-agent-stock spec, Solution's Agent Stock; agent-stock ticket
 * AC: "The Agent's navigation has a Stock page listing their Smartphones and SIM Cards in Stock").
 * `GET /api/stock` already scopes to the caller's own Agent for an AGENT-role token
 * (`StockController#resolveScopeAgentId`), so this fetches with no query param at all.
 */
export default async function AgentStockPage() {
  const [units, meResponse] = await Promise.all([
    backendFetchList<StockUnitItem>("/api/stock"),
    backendFetch("/api/me"),
  ]);
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string }) : {};

  return (
    <SurfacePage
      title="Stock"
      subtitle="Company-owned units you're holding, off every Contract"
      viewerLabel={`${me.username ?? "Agent"} · Agent`}
    >
      <StockView units={units} />
    </SurfacePage>
  );
}
