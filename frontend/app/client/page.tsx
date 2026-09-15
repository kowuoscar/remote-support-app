import Link from "next/link";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientDashboardStats } from "@/components/client/dashboard-stats";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Money } from "@/components/ui/money";
import { IconArrowRight } from "@/components/icons";
import { clientInvoiceStatusLabel, clientInvoiceStatusTone } from "@/lib/status";
import {
  clientContracts,
  clientInvoices,
  clientRequests,
  clientSimCards,
  clientSmartphones,
  currentClient,
} from "@/lib/demo/client";

export const metadata = { title: "Dashboard" };

export default function ClientDashboardPage() {
  const activeFleetCount =
    clientSmartphones.filter((p) => p.status !== "Retired").length +
    clientSimCards.filter((s) => s.status === "Active").length;
  const openRequests = clientRequests.filter(
    (r) => r.status === "Submitted" || r.status === "In Progress",
  );

  // A Client Invoice is visible to Testers only once the Agent has sent it —
  // drafts never appear here, matching the Invoices list.
  const latestByContract = clientContracts.map((contract) => {
    const invoicesForContract = clientInvoices
      .filter((inv) => inv.contractId === contract.id && inv.status !== "draft")
      .sort((a, b) => b.month.localeCompare(a.month));
    return { contract, latest: invoicesForContract[0] };
  });

  return (
    <SurfacePage
      title="Dashboard"
      subtitle={currentClient.name}
      viewerLabel={`${currentClient.currentTester} · Tester`}
    >
      <ClientDashboardStats
        activeFleetCount={activeFleetCount}
        openRequestsCount={openRequests.length}
      />

      <Card className="p-0">
        <div className="flex items-center justify-between border-b border-hairline px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-ink">Latest Client Invoice</h2>
            <p className="text-[13px] text-ink-mute">Most recent month per Contract</p>
          </div>
          <Link
            href="/client/invoices"
            className="inline-flex items-center gap-1 text-[13px] font-medium text-primary hover:text-primary-hover"
          >
            View all
            <IconArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
        <ul className="divide-y divide-hairline">
          {latestByContract.map(({ contract, latest }) => (
            <li key={contract.id} className="flex items-center justify-between gap-4 px-5 py-3.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-ink">{contract.label}</p>
                {latest ? (
                  <p className="truncate text-[12px] text-ink-mute">{latest.month}</p>
                ) : null}
              </div>
              {latest ? (
                <div className="flex shrink-0 items-center gap-3">
                  <Badge tone={clientInvoiceStatusTone[latest.status]}>
                    {clientInvoiceStatusLabel[latest.status]}
                  </Badge>
                  <Money amount={latest.totalAmount} currency={latest.currency} className="text-sm font-medium" />
                </div>
              ) : (
                <span className="text-[13px] text-ink-mute">No invoices yet</span>
              )}
            </li>
          ))}
        </ul>
      </Card>
    </SurfacePage>
  );
}
