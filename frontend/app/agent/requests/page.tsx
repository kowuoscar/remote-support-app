import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentRequestsView } from "@/components/agent/requests-view";
import { agentContracts, agentRequests, currentAgent } from "@/lib/demo/agent";

export const metadata = { title: "Requests" };

export default function AgentRequestsPage() {
  return (
    <SurfacePage
      title="Requests"
      subtitle="Scoped to one Contract at a time"
      viewerLabel={`${currentAgent.name} · Agent`}
    >
      <AgentRequestsView
        requests={agentRequests}
        contracts={agentContracts.map((c) => ({ id: c.id, label: c.label, meta: c.country }))}
      />
    </SurfacePage>
  );
}
