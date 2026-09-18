import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentRequestsView } from "@/components/agent/requests-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { loadCarrierCatalog } from "@/lib/api/carriers";
import {
  countryLabel,
  type ContractListItem,
  type ContractTesterListItem,
  type RequestListItem,
  type SimCardListItem,
  type SmartphoneListItem,
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
  // sim-card-carrier ticket: a SIM Card provisioned here names one of the Agent's own Country's
  // active Carriers — every Contract of theirs is in that Country, so one list serves them all.
  // topup-fee-from-option ticket: the log-Fee dialog's Topup Option picker reads the same
  // catalog. A catalog that fails to load only hides the Option picker; a Fee needs no Option,
  // and CarrierPicker shows its own "add one" fallback when there's no active Carrier.
  const [contracts, meResponse, catalog] = await Promise.all([
    backendFetchList<ContractListItem>("/api/contracts"),
    backendFetch("/api/me"),
    loadCarrierCatalog(),
  ]);
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string }) : {};

  const [requestsByContract, testersByContractArrays, smartphonesByContractArrays, simCardsByContractArrays] =
    await Promise.all([
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
      // fee-logging-and-provisioning ticket: completing a Provision Smartphone/SIM Request lets
      // the Agent optionally name an existing unit it retires — fetched up front alongside
      // Testers so RequestStatusControl's completion form can offer it as a picker with no
      // additional round-trip.
      Promise.all(
        contracts.map((contract) =>
          backendFetchList<SmartphoneListItem>(`/api/contracts/${contract.id}/smartphones`),
        ),
      ),
      Promise.all(
        contracts.map((contract) =>
          backendFetchList<SimCardListItem>(`/api/contracts/${contract.id}/sim-cards`),
        ),
      ),
    ]);

  const testersByContract: Record<string, ContractTesterListItem[]> = {};
  const smartphonesByContract: Record<string, SmartphoneListItem[]> = {};
  const simCardsByContract: Record<string, SimCardListItem[]> = {};
  contracts.forEach((contract, index) => {
    testersByContract[contract.id] = testersByContractArrays[index];
    smartphonesByContract[contract.id] = smartphonesByContractArrays[index];
    simCardsByContract[contract.id] = simCardsByContractArrays[index];
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
        smartphonesByContract={smartphonesByContract}
        simCardsByContract={simCardsByContract}
        carriers={catalog?.carriers ?? []}
      />
    </SurfacePage>
  );
}
