import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Money } from "@/components/ui/money";
import { IconAlertTriangle, IconArrowRight } from "@/components/icons";
import { formatBillingMonth } from "@/lib/format";
import { clientInvoiceStatusLabelByValue, clientInvoiceStatusToneByValue } from "@/lib/status";
import { countryLabel } from "@/lib/api/types";
import type { ClientInvoiceSummary, ContractListItem } from "@/lib/api/types";

/**
 * The Client dashboard's Latest Client Invoice card: one row per Contract, joined to the latest
 * sent or approved invoice by `contractId`. Presentational: the page passes `null` for either
 * read that failed, which renders one unavailable state under the intact header.
 */
export function LatestInvoicesCard({
  contracts,
  latestInvoices,
}: {
  contracts: ContractListItem[] | null;
  latestInvoices: ClientInvoiceSummary[] | null;
}) {
  const latestByContract = new Map((latestInvoices ?? []).map((invoice) => [invoice.contractId, invoice]));
  return (
    <Card className="p-0" data-testid="latest-invoices-card">
      <div className="flex items-center justify-between border-b border-hairline px-5 py-4">
        <div>
          <h2 className="text-sm font-semibold text-ink">Latest Client Invoice</h2>
          <p className="text-label text-ink-mute">Most recent month per Contract</p>
        </div>
        <Link
          href="/client/invoices"
          className="inline-flex items-center gap-1 text-label font-medium text-primary hover:text-primary-hover"
        >
          Open Invoices
          <IconArrowRight className="h-3.5 w-3.5" />
        </Link>
      </div>
      {contracts === null || latestInvoices === null ? (
        <div className="p-5">
          <EmptyState
            icon={<IconAlertTriangle className="h-5 w-5" />}
            title="Couldn't load your invoices"
            description="Reload the page to try again."
          />
        </div>
      ) : contracts.length === 0 ? (
        <div className="p-5">
          <EmptyState
            title="No Contracts yet"
            description="Your Contracts and their invoices will show up here once the Manager sets them up."
          />
        </div>
      ) : (
        <ul className="divide-y divide-hairline">
          {contracts.map((contract) => {
            const latest = latestByContract.get(contract.id);
            return (
              <li key={contract.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-5 py-3.5">
                <div className="min-w-[10rem] flex-1">
                  <p className="text-sm font-medium text-ink">
                    {contract.clientName} — {countryLabel(contract.country)}
                  </p>
                  {latest ? (
                    <p className="truncate text-label-sm text-ink-mute">{formatBillingMonth(latest.billingMonth)}</p>
                  ) : null}
                </div>
                {latest ? (
                  <div className="flex shrink-0 items-center gap-3">
                    <Badge tone={clientInvoiceStatusToneByValue[latest.status]}>
                      {clientInvoiceStatusLabelByValue[latest.status]}
                    </Badge>
                    <Money amount={latest.totalAmount} currency={latest.currency} className="text-sm font-medium" />
                  </div>
                ) : (
                  <span className="text-label text-ink-mute">No invoices yet</span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
