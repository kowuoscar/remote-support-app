import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentRequestsView } from "@/components/agent/requests-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import {
  countryLabel,
  type ContractListItem,
  type ContractTesterListItem,
  type RequestListItem,
} from "@/lib/api/types";

export const metadata = { title: "Requests" };

/**
 * Real backend wiring for the Agent's incoming Requests (tester-request-submission ticket AC:
 * "Agent can see incoming Requests for their own Contracts, filtered by Contract";
 * agent-request-fulfillment ticket adds status changes and proactive logging). GET
 * /api/contracts already scopes to the caller's own Contracts (fleet-management ticket); every
 * Contract's Requests and Testers are fetched up front so the Contract switcher stays a
 * client-side, no-refetch interaction, exactly like the Agent Fleet page. Testers are needed so
 * the "Log a request" dialog can name whose behalf a proactively-logged Request is raised on.
 */
export default async function AgentRequestsPage() {
  const [contracts, meResponse] = await Promise.all([
    backendFetchList<ContractListItem>("/api/contracts"),
    backendFetch("/api/me"),
  ]);
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string }) : {};

  const [requestsByContract, testersByContractArrays] = await Promise.all([
    Promise.all(
      contracts.map((contract) =>
        backendFetchList<RequestListItem>(`/api/contracts/${contract.id}/requests`),
      ),
    ),
    Promise.all(
      contracts.map((contract) =>
        backendFetchList<ContractTesterListItem>(`/api/contracts/${contract.id}/testers`),
      ),
    ),
  ]);

  const testersByContract: Record<string, ContractTesterListItem[]> = {};
  contracts.forEach((contract, index) => {
    testersByContract[contract.id] = testersByContractArrays[index];
  });

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
        testersByContract={testersByContract}
      />
    </SurfacePage>
  );
}
