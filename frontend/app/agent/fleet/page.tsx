import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentFleetView } from "@/components/agent/fleet-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import {
  countryLabel,
  type ContractListItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

export const metadata = { title: "Fleet" };

/**
 * Real backend wiring for the Agent's Fleet (fleet-management ticket AC: "Agent can view the
 * Fleet of their own Contracts, filtered by Contract"). GET /api/contracts already scopes to the
 * caller's own Contracts (fleet-management ticket); every Contract's Fleet is fetched up front
 * (typically few Contracts per Agent) so the Contract switcher stays a client-side, no-refetch
 * interaction like the design-system ticket's demo version.
 */
export default async function AgentFleetPage() {
  const [contracts, meResponse] = await Promise.all([
    backendFetchList<ContractListItem>("/api/contracts"),
    backendFetch("/api/me"),
  ]);
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string }) : {};

  const fleets = await Promise.all(
    contracts.map(async (contract) => {
      const [smartphones, simCards] = await Promise.all([
        backendFetchList<SmartphoneListItem>(`/api/contracts/${contract.id}/smartphones`),
        backendFetchList<SimCardListItem>(`/api/contracts/${contract.id}/sim-cards`),
      ]);
      return { smartphones, simCards };
    }),
  );

  return (
    <SurfacePage
      title="Fleet"
      subtitle="Scoped to one Contract at a time"
      viewerLabel={`${me.username ?? "Agent"} · Agent`}
    >
      <AgentFleetView
        smartphones={fleets.flatMap((fleet) => fleet.smartphones)}
        simCards={fleets.flatMap((fleet) => fleet.simCards)}
        contracts={contracts.map((c) => ({
          id: c.id,
          label: `${c.clientName} — ${countryLabel(c.country)}`,
          meta: countryLabel(c.country),
          currency: c.currency,
        }))}
      />
    </SurfacePage>
  );
}
