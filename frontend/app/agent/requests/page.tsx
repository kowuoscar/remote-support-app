import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentRequestsView } from "@/components/agent/requests-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { countryLabel, type ContractListItem, type RequestListItem } from "@/lib/api/types";

export const metadata = { title: "Requests" };

/**
 * Real backend wiring for the Agent's incoming Requests (tester-request-submission ticket AC:
 * "Agent can see incoming Requests for their own Contracts, filtered by Contract"). GET
 * /api/contracts already scopes to the caller's own Contracts (fleet-management ticket); every
 * Contract's Requests are fetched up front so the Contract switcher stays a client-side, no-
 * refetch interaction, exactly like the Agent Fleet page.
 */
export default async function AgentRequestsPage() {
  const [contracts, meResponse] = await Promise.all([
    backendFetchList<ContractListItem>("/api/contracts"),
    backendFetch("/api/me"),
  ]);
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string }) : {};

  const requestsByContract = await Promise.all(
    contracts.map((contract) =>
      backendFetchList<RequestListItem>(`/api/contracts/${contract.id}/requests`),
    ),
  );

  return (
    <SurfacePage
      title="Requests"
      subtitle="Scoped to one Contract at a time"
      viewerLabel={`${me.username ?? "Agent"} · Agent`}
    >
      <AgentRequestsView
        requests={requestsByContract.flat()}
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
