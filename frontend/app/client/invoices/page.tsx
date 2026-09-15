import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientInvoicesView } from "@/components/client/invoices-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { countryLabel, type ClientInvoiceDetail, type ContractListItem } from "@/lib/api/types";

export const metadata = { title: "Invoices" };

/**
 * Real backend wiring for a Tester's Client Invoices (client-invoice-submission-and-visibility
 * ticket AC: "Once sent, every Tester at that Contract's Client can view the Client Invoice
 * read-only"; user stories 34-35). Mirrors app/agent/client-invoices/page.tsx's shape: every
 * Contract's current-month invoice is fetched up front so the Contract switcher stays a
 * client-side, no-refetch interaction. A Contract whose current-month invoice is still `draft` (or
 * doesn't exist yet) comes back as a 403 from the backend — see ClientInvoiceAccessGuard — which
 * this simply treats as "nothing to show for this Contract yet", never a page-level error.
 */
export default async function ClientInvoicesPage() {
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
      title="Invoices"
      subtitle="Sent by your Agent, one per Contract per month"
      viewerLabel={`${me.username ?? "Tester"} · Tester`}
    >
      <ClientInvoicesView
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
