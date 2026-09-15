import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientFleetView } from "@/components/client/fleet-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import {
  countryLabel,
  type ContractListItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

export const metadata = { title: "Fleet" };

/**
 * Real backend wiring for a Tester's Client Fleet (fleet-management ticket AC: "Tester can view
 * their Client's Fleet, loaded per Contract"). GET /api/contracts already scopes to the caller's
 * own Client's Contracts (fleet-management ticket).
 */
export default async function ClientFleetPage() {
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
      subtitle="Loaded per Contract"
      viewerLabel={`${me.username ?? "Tester"} · Tester`}
    >
      <ClientFleetView
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
