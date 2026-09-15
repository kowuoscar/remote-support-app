import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentFleetView } from "@/components/agent/fleet-view";
import { agentContracts, agentSimCards, agentSmartphones, currentAgent } from "@/lib/demo/agent";

export const metadata = { title: "Fleet" };

export default function AgentFleetPage() {
  return (
    <SurfacePage
      title="Fleet"
      subtitle="Scoped to one Contract at a time"
      viewerLabel={`${currentAgent.name} · Agent`}
    >
      <AgentFleetView
        smartphones={agentSmartphones}
        simCards={agentSimCards}
        contracts={agentContracts.map((c) => ({
          id: c.id,
          label: c.label,
          meta: c.country,
          currency: c.currency,
        }))}
      />
    </SurfacePage>
  );
}
