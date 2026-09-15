import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentClientInvoicesView } from "@/components/agent/client-invoices-view";
import { agentClientInvoices, agentContracts, currentAgent } from "@/lib/demo/agent";

export const metadata = { title: "Client Invoices" };

export default function AgentClientInvoicesPage() {
  return (
    <SurfacePage
      title="Client Invoices"
      subtitle="One per Contract per month"
      viewerLabel={`${currentAgent.name} · Agent`}
    >
      <AgentClientInvoicesView
        invoices={agentClientInvoices}
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
