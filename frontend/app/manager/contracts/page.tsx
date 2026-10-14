import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerContractsView, type ContractRow } from "@/components/manager/contracts-view";
import { backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import { countryLabel, type AgentListItem, type ClientListItem, type ContractListItem } from "@/lib/api/types";

export const metadata = { title: "Contracts" };

export default async function ManagerContractsPage() {
  await requireManager();
  const [contracts, clients, agents] = await Promise.all([
    backendFetchList<ContractListItem>("/api/contracts"),
    backendFetchList<ClientListItem>("/api/clients"),
    backendFetchList<AgentListItem>("/api/agents"),
  ]);

  const rows: ContractRow[] = contracts.map((contract) => ({
    id: contract.id,
    clientName: contract.clientName,
    agentName: contract.agentName,
    country: countryLabel(contract.country),
    currency: contract.currency,
  }));

  return (
    <SurfacePage
      title="Contracts"
      subtitle={`${rows.length} in this tenant`}
      viewerLabel="Manager"
    >
      <ManagerContractsView contracts={rows} clients={clients} agents={agents} />
    </SurfacePage>
  );
}
