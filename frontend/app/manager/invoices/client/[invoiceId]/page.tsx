import { notFound } from "next/navigation";
import { monthParam } from "@/lib/review-months";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { ClientInvoiceDetailView } from "@/components/manager/client-invoice-detail-view";
import { ReviewMonthNext } from "@/components/manager/review-month-next";
import { backendFetch, backendFetchJsonOrNull, backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { ClientInvoiceDetail, ContractListItem, ReviewMonth } from "@/lib/api/types";

export const metadata = { title: "Client Invoice" };

/**
 * One Client Invoice, addressed by its own id (manager-invoice-review-queue spec, Client Invoice
 * detail page) — reached from the Review Queue, for any billing month. The by-id read never
 * creates an invoice; an unknown or other-tenant id is a 404 here too. The Agent's month of the
 * invoice (manager-reviews-by-agent spec) gives the Agent crumb and "Next invoice →"; a failed read of
 * it leaves the page as it was before, without either.
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
  const review = contract
    ? await backendFetchJsonOrNull<ReviewMonth>(
        `/api/review-queue/agents/${contract.agentId}/months/${monthParam(invoice.billingMonth)}`,
        "Manager Client Invoice Agent month",
      )
    : null;
  const subject = contract ? `${contract.clientName} — ${contract.agentName}` : "Client Invoice";

  return (
    <SurfacePage title={subject} subtitle="Client Invoice" viewerLabel="Manager">
      <Breadcrumb
        items={
          contract
            ? [
                { label: "Invoices", href: "/manager/invoices" },
                {
                  label: contract.agentName,
                  href: `/manager/invoices?agent=${contract.agentId}&month=${monthParam(invoice.billingMonth)}`,
                },
                { label: contract.clientName },
              ]
            : [{ label: "Invoices", href: "/manager/invoices" }, { label: subject }]
        }
      />
      <ReviewMonthNext review={review} currentId={invoice.id} />
      <ClientInvoiceDetailView invoice={invoice} />
    </SurfacePage>
  );
}
