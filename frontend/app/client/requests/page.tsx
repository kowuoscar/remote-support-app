import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientRequestsView } from "@/components/client/requests-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { countryLabel, type ContractListItem, type RequestListItem } from "@/lib/api/types";

export const metadata = { title: "Requests" };

/**
 * Real backend wiring for a Tester's Requests (tester-request-submission ticket AC: "Any Tester
 * at the same Client sees every Request raised by anyone at that Client"). GET /api/contracts
 * already scopes to the caller's own Client's Contracts (fleet-management ticket); every
 * Contract's Requests are fetched up front and flattened, exactly like the Fleet views do, so
 * "every Contract" visibility falls out of the same fetch-per-Contract shape rather than a
 * separate Client-wide endpoint.
 */
export default async function ClientRequestsPage() {
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
      />
    </SurfacePage>
  );
}
