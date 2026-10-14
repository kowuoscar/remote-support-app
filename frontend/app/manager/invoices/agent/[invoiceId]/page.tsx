import { notFound } from "next/navigation";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { AgentInvoiceDetailView } from "@/components/manager/agent-invoice-detail-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { AgentInvoiceDetail, AgentListItem } from "@/lib/api/types";

export const metadata = { title: "Agent Invoice" };

/**
 * One Agent Invoice, addressed by its own id (manager-invoice-review-queue spec, Agent Invoice
 * detail page) — reached from the Review Queue, for any billing month. The by-id read never
 * creates an invoice; an unknown or other-tenant id is a 404 here too.
 */
export default async function ManagerAgentInvoicePage({
  params,
}: {
  params: Promise<{ invoiceId: string }>;
}) {
  await requireManager();
  const { invoiceId } = await params;

  const [invoiceResponse, agents] = await Promise.all([
    backendFetch(`/api/agent-invoices/${invoiceId}`),
    backendFetchList<AgentListItem>("/api/agents"),
  ]);
  if (invoiceResponse.status === 404) {
    notFound();
  }
  if (!invoiceResponse.ok) {
    throw new Error(`Couldn't load Agent Invoice ${invoiceId} (${invoiceResponse.status})`);
  }
  const invoice = (await invoiceResponse.json()) as AgentInvoiceDetail;
  const subject = agents.find((a) => a.id === invoice.agentId)?.name ?? "Agent Invoice";

  return (
    <SurfacePage title={subject} subtitle="Agent Invoice" viewerLabel="Manager">
      <Breadcrumb items={[{ label: "Invoices", href: "/manager/invoices" }, { label: subject }]} />
      <AgentInvoiceDetailView invoice={invoice} />
    </SurfacePage>
  );
}
