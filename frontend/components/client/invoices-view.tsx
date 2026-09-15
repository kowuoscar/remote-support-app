"use client";

import { useMemo, useState } from "react";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Money } from "@/components/ui/money";
import { IconDownload, IconInvoices, IconPaperclip } from "@/components/icons";
import { clientInvoiceStatusLabel, clientInvoiceStatusTone } from "@/lib/status";
import type { ClientInvoiceRecord } from "@/lib/demo/types";

export function ClientInvoicesView({
  invoices,
  contracts,
}: {
  invoices: ClientInvoiceRecord[];
  contracts: ContractOption[];
}) {
  const [contractId, setContractId] = useState(contracts[0]?.id ?? "");

  // A Client Invoice becomes visible to Testers only once the Agent has sent
  // it — drafts stay off this list entirely.
  const filtered = useMemo(
    () =>
      invoices
        .filter((inv) => inv.contractId === contractId && inv.status !== "draft")
        .sort((a, b) => b.month.localeCompare(a.month)),
    [invoices, contractId],
  );

  return (
    <div className="flex flex-col gap-4">
      <ContractSwitcher contracts={contracts} value={contractId} onChange={setContractId} />

      {filtered.length === 0 ? (
        <EmptyState
          icon={<IconInvoices className="h-5 w-5" />}
          title="No Invoices sent yet for this Contract"
          description="Your Agent hasn't sent a Client Invoice for this Contract yet. It will appear here as soon as they do."
        />
      ) : (
        <div className="flex flex-col gap-3">
          {filtered.map((invoice) => (
            <Card
              key={invoice.id}
              className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between"
            >
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-semibold text-ink">{invoice.month}</p>
                  <Badge tone={clientInvoiceStatusTone[invoice.status]}>
                    {clientInvoiceStatusLabel[invoice.status]}
                  </Badge>
                </div>
                <dl className="mt-2 grid grid-cols-2 gap-x-6 gap-y-1 text-[13px] text-ink-mute sm:grid-cols-3">
                  <div className="flex items-center gap-1.5">
                    <dt>Base</dt>
                    <dd className="tnum text-ink-secondary">
                      <Money amount={invoice.baseAmount} currency={invoice.currency} />
                    </dd>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <dt>Fees</dt>
                    <dd className="tnum text-ink-secondary">
                      <Money amount={invoice.feesAmount} currency={invoice.currency} />
                    </dd>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <IconPaperclip className="h-3.5 w-3.5" />
                    <dd className="text-ink-secondary">
                      {invoice.carrierFilesAttached} carrier file
                      {invoice.carrierFilesAttached === 1 ? "" : "s"}
                    </dd>
                  </div>
                </dl>
              </div>
              <div className="flex shrink-0 items-center gap-4 sm:flex-col sm:items-end sm:gap-2">
                <Money
                  amount={invoice.totalAmount}
                  currency={invoice.currency}
                  emphasize
                  className="text-lg"
                />
                <Button variant="secondary" size="sm">
                  <IconDownload className="h-3.5 w-3.5" />
                  Download PDF
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
