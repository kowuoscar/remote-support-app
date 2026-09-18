import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientRequestsView } from "@/components/client/requests-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { loadContractCarrierCatalog } from "@/lib/api/carriers";
import {
  countryLabel,
  type CatalogCarrierItem,
  type ContractListItem,
  type RequestListItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

export const metadata = { title: "Requests" };

/**
 * Real backend wiring for a Tester's Requests (tester-request-submission ticket AC: "Any Tester
 * at the same Client sees every Request raised by anyone at that Client"). GET /api/contracts
 * already scopes to the caller's own Client's Contracts (fleet-management ticket); every
 * Contract's Requests are fetched up front and flattened, exactly like the Fleet views do, so
 * "every Contract" visibility falls out of the same fetch-per-Contract shape rather than a
 * separate Client-wide endpoint.
 *
 * <p>reboot-and-topup-details ticket: the submit dialog's per-type details need each Contract's
 * Active Smartphones/SIM Cards and Carrier catalog. A Tester has no Country of their own, so the
 * catalog comes from the Contract-scoped read (`GET /api/contracts/{id}/carriers`) rather than the
 * Agent/Manager-only Country-scoped one `loadActiveCarriers` calls.
 */
export default async function ClientRequestsPage() {
  const [contracts, meResponse] = await Promise.all([
    backendFetchList<ContractListItem>("/api/contracts"),
    backendFetch("/api/me"),
  ]);
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string }) : {};

  const [requestsByContract, smartphonesByContractArrays, simCardsByContractArrays, catalogsByContract] =
    await Promise.all([
      Promise.all(
        contracts.map((contract) =>
          backendFetchList<RequestListItem>(`/api/contracts/${contract.id}/requests`),
        ),
      ),
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
      Promise.all(contracts.map((contract) => loadContractCarrierCatalog(contract.id))),
    ]);

  const smartphonesByContract: Record<string, SmartphoneListItem[]> = {};
  const simCardsByContract: Record<string, SimCardListItem[]> = {};
  const carriersByContract: Record<string, CatalogCarrierItem[]> = {};
  contracts.forEach((contract, index) => {
    smartphonesByContract[contract.id] = smartphonesByContractArrays[index].filter(
      (phone) => phone.status === "ACTIVE",
    );
    simCardsByContract[contract.id] = simCardsByContractArrays[index].filter((sim) => sim.status === "ACTIVE");
    carriersByContract[contract.id] = catalogsByContract[index];
  });

  return (
    <SurfacePage
      title="Requests"
      subtitle="Every Request raised by anyone at your company"
      viewerLabel={`${me.username ?? "Tester"} · Tester`}
    >
      <ClientRequestsView
        requests={requestsByContract.flat()}
        contracts={contracts.map((c) => ({
          id: c.id,
          label: `${c.clientName} — ${countryLabel(c.country)}`,
          meta: countryLabel(c.country),
          currency: c.currency,
        }))}
        smartphonesByContract={smartphonesByContract}
        simCardsByContract={simCardsByContract}
        carriersByContract={carriersByContract}
      />
    </SurfacePage>
  );
}
