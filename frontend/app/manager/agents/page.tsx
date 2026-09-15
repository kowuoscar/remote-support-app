import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerAgentsView, type AgentRow } from "@/components/manager/agents-view";
import { backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import { countryLabel, type AgentListItem } from "@/lib/api/types";

export const metadata = { title: "Agents" };

export default async function ManagerAgentsPage() {
  await requireManager();
  const agents = await backendFetchList<AgentListItem>("/api/agents");

  const rows: AgentRow[] = agents.map((agent) => ({
    id: agent.id,
    name: agent.name,
    country: countryLabel(agent.country),
    currency: agent.currency,
    contractCount: agent.contractCount,
  }));

  return (
    <SurfacePage title="Agents" subtitle={`${rows.length} in this tenant`} viewerLabel="Manager">
      <ManagerAgentsView agents={rows} />
    </SurfacePage>
  );
}
