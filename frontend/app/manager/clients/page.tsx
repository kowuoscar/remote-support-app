import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerClientsView } from "@/components/manager/clients-view";
import { clients } from "@/lib/demo/manager";

export const metadata = { title: "Clients" };

export default function ManagerClientsPage() {
  return (
    <SurfacePage title="Clients" subtitle={`${clients.length} in this tenant`} viewerLabel="Priya Ashford · Manager">
      <ManagerClientsView clients={clients} />
    </SurfacePage>
  );
}
