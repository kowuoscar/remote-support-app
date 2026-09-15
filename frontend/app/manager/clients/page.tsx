import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerClientsView, type ClientRow } from "@/components/manager/clients-view";
import { backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { ClientListItem } from "@/lib/api/types";

export const metadata = { title: "Clients" };

export default async function ManagerClientsPage() {
  await requireManager();
  const clients = await backendFetchList<ClientListItem>("/api/clients");

  const rows: ClientRow[] = clients.map((client) => ({
    id: client.id,
    name: client.name,
    primaryContact: client.primaryContactUsername ?? "—",
    contractCount: client.contractCount,
  }));

  return (
    <SurfacePage title="Clients" subtitle={`${rows.length} in this tenant`} viewerLabel="Manager">
      <ManagerClientsView clients={rows} />
    </SurfacePage>
  );
}
