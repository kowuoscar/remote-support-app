import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentClientInvoicesView } from "@/components/agent/client-invoices-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { countryLabel, type ClientInvoiceDetail, type ContractListItem } from "@/lib/api/types";

export const metadata = { title: "Client Invoices" };

/**
 * Real backend wiring for the Agent's Client Invoices (client-invoice-generation ticket AC:
 * "Agent can open a Contract's Client Invoice for the current month, created in status draft on
 * first access", user stories 22-23). GET /api/contracts already scopes to the caller's own
 * Contracts (fleet-management ticket); every Contract's current-month invoice is fetched up front
 * so the Contract switcher stays a client-side, no-refetch interaction, the same shape
 * Requests/Fleet already established. Fetching each one is itself what creates it on first
 * access — simply visiting this page is the "first view" the get-or-create mechanic (see
 * ClientInvoiceController's Javadoc) is built around.
 */
export default async function AgentClientInvoicesPage() {
  const [contracts, meResponse] = await Promise.all([
    backendFetchList<ContractListItem>("/api/contracts"),
    backendFetch("/api/me"),
  ]);
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string }) : {};

  const invoiceResponses = await Promise.all(
    contracts.map((contract) => backendFetch(`/api/contracts/${contract.id}/client-invoice`)),
  );
  const invoicesByContract: Record<string, ClientInvoiceDetail | null> = {};
  await Promise.all(
    contracts.map(async (contract, index) => {
      const response = invoiceResponses[index];
      invoicesByContract[contract.id] = response.ok ? ((await response.json()) as ClientInvoiceDetail) : null;
    }),
  );

  return (
    <SurfacePage
      title="Client Invoices"
      subtitle="One per Contract per month"
      viewerLabel={`${me.username ?? "Agent"} · Agent`}
    >
      <AgentClientInvoicesView
        contracts={contracts.map((c) => ({
          id: c.id,
          label: `${c.clientName} — ${countryLabel(c.country)}`,
          meta: countryLabel(c.country),
          currency: c.currency,
        }))}
        invoicesByContract={invoicesByContract}
      />
    </SurfacePage>
  );
}
