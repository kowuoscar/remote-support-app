import { notFound } from "next/navigation";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { ClientInvoiceDetailView } from "@/components/manager/client-invoice-detail-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { ClientInvoiceDetail, ContractListItem } from "@/lib/api/types";

export const metadata = { title: "Client Invoice" };

/**
 * One Client Invoice, addressed by its own id (manager-invoice-review-queue spec, Client Invoice
 * detail page) — reached from the Review Queue, for any billing month. The by-id read never
 * creates an invoice; an unknown or other-tenant id is a 404 here too.
 */
export default async function ManagerClientInvoicePage({
  params,
}: {
  params: Promise<{ invoiceId: string }>;
}) {
  await requireManager();
  const { invoiceId } = await params;

  const [invoiceResponse, contracts] = await Promise.all([
    backendFetch(`/api/client-invoices/${invoiceId}`),
    backendFetchList<ContractListItem>("/api/contracts"),
  ]);
  if (invoiceResponse.status === 404) {
    notFound();
  }
  if (!invoiceResponse.ok) {
    throw new Error(`Couldn't load Client Invoice ${invoiceId} (${invoiceResponse.status})`);
  }
  const invoice = (await invoiceResponse.json()) as ClientInvoiceDetail;
  const contract = contracts.find((c) => c.id === invoice.contractId);
  const subject = contract ? `${contract.clientName} — ${contract.agentName}` : "Client Invoice";

  return (
    <SurfacePage title={subject} subtitle="Client Invoice" viewerLabel="Manager">
      <Breadcrumb items={[{ label: "Invoices", href: "/manager/invoices" }, { label: subject }]} />
      <ClientInvoiceDetailView invoice={invoice} />
    </SurfacePage>
  );
}
