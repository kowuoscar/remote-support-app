import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientFleetView } from "@/components/client/fleet-view";
import { clientContracts, clientSimCards, clientSmartphones, currentClient } from "@/lib/demo/client";

export const metadata = { title: "Fleet" };

export default function ClientFleetPage() {
  return (
    <SurfacePage
      title="Fleet"
      subtitle="Loaded per Contract"
      viewerLabel={`${currentClient.currentTester} · Tester`}
    >
      <ClientFleetView
        smartphones={clientSmartphones}
        simCards={clientSimCards}
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
