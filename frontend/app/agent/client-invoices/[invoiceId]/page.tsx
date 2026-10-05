import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentClientInvoicePageView } from "@/components/agent/client-invoices-view";
import { backendFetch } from "@/lib/api/backend";
import { clientInvoiceFromByIdResponse } from "@/lib/api/client-invoice";
import { requireAgent } from "@/lib/api/guard";

export const metadata = { title: "Client Invoice" };

/**
 * One Client Invoice of the Agent's own, addressed by its id (send-a-client-invoice-back spec,
 * Frontend: Agent) — reached from the "Sent back to you" list, for any billing month. The by-id read
 * never creates an invoice; another Agent's id, an unknown id and another Tenant's id all render the
 * same not-found state.
 */
export default async function AgentClientInvoicePage({
  params,
}: {
  params: Promise<{ invoiceId: string }>;
}) {
  await requireAgent();
  const { invoiceId } = await params;

  const [invoiceResponse, meResponse] = await Promise.all([
    backendFetch(`/api/client-invoices/${encodeURIComponent(invoiceId)}`),
    backendFetch("/api/me"),
  ]);
  const invoice = await clientInvoiceFromByIdResponse(invoiceResponse);
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string }) : {};
  const currentBillingMonth = `${new Date().toISOString().slice(0, 7)}-01`;

  return (
    <SurfacePage
      title="Client Invoice"
      subtitle="One per Contract per month"
      viewerLabel={`${me.username ?? "Agent"} · Agent`}
    >
      <AgentClientInvoicePageView invoice={invoice} currentBillingMonth={currentBillingMonth} />
    </SurfacePage>
  );
}
