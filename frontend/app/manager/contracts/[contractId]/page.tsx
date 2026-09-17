import { notFound } from "next/navigation";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { ManagerContractFleetView } from "@/components/manager/contract-fleet-view";
import { InvoiceSummaryCard } from "@/components/manager/invoice-summary-card";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import {
  countryLabel,
  type ClientInvoiceDetail,
  type ContractListItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

export const metadata = { title: "Contract" };

/**
 * A Contract's detail view, added for its Fleet (fleet-management ticket ACs: "Manager can add a
 * Smartphone/SIM Card to a Contract's Fleet") — mirrors how `/manager/clients/[clientId]` was
 * added in manager-entity-setup for Testers. There's no single-Contract GET endpoint yet, so
 * (like that ticket's Client detail view) this finds the Contract in the full list. Its
 * current-month Client Invoice shows only as a summary linking to the invoice's detail page, where
 * the Manager reviews it (manager-invoice-review-queue spec, Contract and Agent pages).
 */
export default async function ManagerContractDetailPage({
  params,
}: {
  params: Promise<{ contractId: string }>;
}) {
  await requireManager();
  const { contractId } = await params;

  const contracts = await backendFetchList<ContractListItem>("/api/contracts");
  const contract = contracts.find((c) => c.id === contractId);
  if (!contract) {
    notFound();
  }

  const [smartphones, simCards, invoiceResponse] = await Promise.all([
    backendFetchList<SmartphoneListItem>(`/api/contracts/${contractId}/smartphones`),
    backendFetchList<SimCardListItem>(`/api/contracts/${contractId}/sim-cards`),
    backendFetch(`/api/contracts/${contractId}/client-invoice`),
  ]);
  const invoice = invoiceResponse.ok ? ((await invoiceResponse.json()) as ClientInvoiceDetail) : null;

  const title = `${contract.clientName} — ${contract.agentName}`;

  return (
    <SurfacePage
      title={title}
      subtitle={`${countryLabel(contract.country)} · ${contract.currency} · ${smartphones.length} smartphone${smartphones.length === 1 ? "" : "s"} · ${simCards.length} SIM card${simCards.length === 1 ? "" : "s"}`}
      viewerLabel="Manager"
    >
      <Breadcrumb items={[{ label: "Contracts", href: "/manager/contracts" }, { label: title }]} />
      <div className="flex flex-col gap-5">
        {invoice ? <InvoiceSummaryCard kind="CLIENT_INVOICE" invoice={invoice} /> : null}
        <ManagerContractFleetView
          contractId={contract.id}
          currency={contract.currency}
          smartphones={smartphones}
          simCards={simCards}
        />
      </div>
    </SurfacePage>
  );
}
