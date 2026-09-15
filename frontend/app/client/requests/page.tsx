import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientRequestsView } from "@/components/client/requests-view";
import { clientContracts, clientRequests, currentClient } from "@/lib/demo/client";

export const metadata = { title: "Requests" };

export default function ClientRequestsPage() {
  return (
    <SurfacePage
      title="Requests"
      subtitle="Every Request raised by anyone at your company"
      viewerLabel={`${currentClient.currentTester} · Tester`}
    >
      <ClientRequestsView
        requests={clientRequests}
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
