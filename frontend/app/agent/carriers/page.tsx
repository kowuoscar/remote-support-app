import { SurfacePage } from "@/components/app-shell/surface-page";
import { CarriersView } from "@/components/carriers/carriers-view";
import { backendFetch } from "@/lib/api/backend";
import { loadCarrierCatalog } from "@/lib/api/carriers";
import { countryLabel } from "@/lib/api/types";

export const metadata = { title: "Carriers" };

/**
 * The Agent's own Country's Carriers (agent-maintains-carriers ticket). The backend decides which
 * Country that is — an Agent never picks one — and says so in the catalog it returns.
 */
export default async function AgentCarriersPage() {
  const [catalog, meResponse] = await Promise.all([
    loadCarrierCatalog(),
    backendFetch("/api/me").catch(() => null),
  ]);
  const me = meResponse?.ok ? ((await meResponse.json()) as { username?: string }) : {};
  const country = catalog?.country ?? "UNITED_STATES";

  return (
    <SurfacePage
      title="Carriers"
      subtitle={catalog ? `${countryLabel(catalog.country)} catalog` : "Your country's catalog"}
      viewerLabel={`${me.username ?? "Agent"} · Agent`}
    >
      <CarriersView country={country} catalog={catalog} />
    </SurfacePage>
  );
}
