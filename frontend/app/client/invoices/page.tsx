import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientInvoicesView } from "@/components/client/invoices-view";
import { clientContracts, clientInvoices, currentClient } from "@/lib/demo/client";

export const metadata = { title: "Invoices" };

export default function ClientInvoicesPage() {
  return (
    <SurfacePage
      title="Invoices"
      subtitle="Sent by your Agent, one per Contract per month"
      viewerLabel={`${currentClient.currentTester} · Tester`}
    >
      <ClientInvoicesView
        invoices={clientInvoices}
        contracts={clientContracts.map((c) => ({
          id: c.id,
          label: c.label,
          meta: c.country,
          currency: c.currency,
        }))}
      />
    </SurfacePage>
  );
}
