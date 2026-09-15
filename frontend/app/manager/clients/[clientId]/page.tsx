import { notFound } from "next/navigation";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { ManagerTestersView } from "@/components/manager/testers-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { ClientListItem, TesterListItem } from "@/lib/api/types";

export const metadata = { title: "Client" };

export default async function ManagerClientDetailPage({
  params,
}: {
  params: Promise<{ clientId: string }>;
}) {
  await requireManager();
  const { clientId } = await params;

  const [clients, testersResponse] = await Promise.all([
    backendFetchList<ClientListItem>("/api/clients"),
    backendFetch(`/api/clients/${clientId}/testers`),
  ]);

  const client = clients.find((c) => c.id === clientId);
  if (!client) {
    notFound();
  }

  const testers: TesterListItem[] = testersResponse.ok ? await testersResponse.json() : [];

  return (
    <SurfacePage
      title={client.name}
      subtitle={`${client.contractCount} contract${client.contractCount === 1 ? "" : "s"} · ${testers.length} tester${testers.length === 1 ? "" : "s"}`}
      viewerLabel="Manager"
    >
      <Breadcrumb items={[{ label: "Clients", href: "/manager/clients" }, { label: client.name }]} />
      <ManagerTestersView clientId={client.id} testers={testers} />
    </SurfacePage>
  );
}
