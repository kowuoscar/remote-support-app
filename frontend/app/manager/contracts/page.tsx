import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerContractsView, type SelectedContract } from "@/components/manager/contracts-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { loadActiveCarriers } from "@/lib/api/carriers";
import { requireManager } from "@/lib/api/guard";
import { resolveSelectedContract } from "@/lib/manager-contracts";
import type {
  AgentListItem,
  ClientInvoiceDetail,
  ClientListItem,
  ContractListItem,
  Country,
  SimCardListItem,
  SmartphoneListItem,
} from "@/lib/api/types";

export const metadata = { title: "Contracts" };

/**
 * The Manager's Contracts on one page (side-lists-and-tabs spec): reads the three lists, resolves
 * the selected Contract (`?contract=`, else the first Client's first Agent), then reads that Contract
 * only: its Fleet, current-month Client Invoice (which get-or-creates the draft, so no other
 * Contract's is ever read) and its Country's active Carriers.
 */
export default async function ManagerContractsPage({
  searchParams,
}: {
  searchParams: Promise<{ contract?: string | string[]; tab?: string | string[] }>;
}) {
  await requireManager();
  const { contract: contractParam, tab } = await searchParams;
  const first = (value: string | string[] | undefined) => (Array.isArray(value) ? value[0] : value);
  const [contracts, clients, agents] = await Promise.all([
    backendFetchList<ContractListItem>("/api/contracts"),
    backendFetchList<ClientListItem>("/api/clients"),
    backendFetchList<AgentListItem>("/api/agents"),
  ]);

  const contract = resolveSelectedContract(contracts, first(contractParam));
  let selected: SelectedContract | null = null;
  if (contract) {
    const [smartphones, simCards, invoiceResponse, carriers] = await Promise.all([
      backendFetchList<SmartphoneListItem>(`/api/contracts/${contract.id}/smartphones`),
      backendFetchList<SimCardListItem>(`/api/contracts/${contract.id}/sim-cards`),
      backendFetch(`/api/contracts/${contract.id}/client-invoice`),
      // A new SIM Card names one of the Contract's Country's active Carriers (sim-card-carrier).
      loadActiveCarriers(contract.country as Country),
    ]);
    const invoice = invoiceResponse.ok ? ((await invoiceResponse.json()) as ClientInvoiceDetail) : null;
    selected = { contract, smartphones, simCards, invoice, carriers };
  }

  return (
    <SurfacePage title="Contracts" subtitle={`${contracts.length} in this tenant`} viewerLabel="Manager" bleed>
      <ManagerContractsView
        contracts={contracts}
        clients={clients}
        agents={agents}
        selected={selected}
        initialTab={first(tab)}
      />
    </SurfacePage>
  );
}
