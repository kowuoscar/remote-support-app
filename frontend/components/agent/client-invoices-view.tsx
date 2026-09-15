"use client";

import { useState } from "react";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconAlertTriangle, IconDownload, IconInvoices, IconPaperclip } from "@/components/icons";
import { AttachCarrierInvoiceFileControl } from "@/components/agent/attach-carrier-invoice-file-control";
import { clientInvoiceStatusLabelByValue, clientInvoiceStatusToneByValue } from "@/lib/status";
import { formatDateShort } from "@/lib/format";
import { FEE_TYPE_LABEL, type ClientInvoiceDetail } from "@/lib/api/types";

function billingMonthLabel(billingMonth: string): string {
  // billingMonth is always a first-of-month ISO date (e.g. "2026-09-01") — parsed as UTC so it
  // never rolls back to the previous month in a timezone behind UTC.
  return new Date(`${billingMonth}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

/**
 * Agent opens a Contract's current-month Client Invoice draft (client-invoice-generation ticket
 * AC: "Agent can open a Contract's Client Invoice for the current month ... The draft view is
 * scannable at a glance: base amount, each Fee line, and attached files are all visible
 * together"). Mirrors the Contract-switcher shape Requests/Fleet already established; the summary
 * row (base/fees/total) is the "at a glance" part, with Fee lines and files as simple lists below
 * rather than buried behind further navigation.
 */
export function AgentClientInvoicesView({
  contracts,
  invoicesByContract,
}: {
  contracts: ContractOption[];
  invoicesByContract: Record<string, ClientInvoiceDetail | null>;
}) {
  const [contractId, setContractId] = useState(contracts[0]?.id ?? "");
  const invoice = invoicesByContract[contractId];

  if (contracts.length === 0) {
    return (
      <EmptyState
        icon={<IconInvoices className="h-5 w-5" />}
        title="No contracts yet"
        description="Once a manager creates a contract for you, its monthly Client Invoice shows up here."
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <ContractSwitcher contracts={contracts} value={contractId} onChange={setContractId} />

      {!invoice ? (
        <EmptyState
          icon={<IconAlertTriangle className="h-5 w-5" />}
          title="Couldn't load this Contract's Client Invoice"
          description="Switch contracts or reload the page to try again."
        />
      ) : (
        <Card className="flex flex-col gap-6 p-5">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <p className="text-sm font-semibold text-ink">{billingMonthLabel(invoice.billingMonth)}</p>
                <Badge tone={clientInvoiceStatusToneByValue[invoice.status]}>
                  {clientInvoiceStatusLabelByValue[invoice.status]}
                </Badge>
              </div>
              <p className="mt-1 text-[13px] text-ink-mute">
                This Contract&rsquo;s postpaid base amount plus this month&rsquo;s Fees
              </p>
            </div>
            <AttachCarrierInvoiceFileControl contractId={contractId} />
          </div>

          <dl className="grid grid-cols-2 gap-4 rounded-lg border border-hairline bg-canvas-soft p-4 sm:grid-cols-3">
            <div>
              <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Base amount</dt>
              <dd className="mt-1">
                <Money amount={invoice.baseAmount} currency={invoice.currency} className="text-base" />
              </dd>
            </div>
            <div>
              <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Fees this month</dt>
              <dd className="mt-1">
                <Money
                  amount={invoice.totalAmount - invoice.baseAmount}
                  currency={invoice.currency}
                  className="text-base"
                />
              </dd>
            </div>
            <div>
              <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Total</dt>
              <dd className="mt-1">
                <Money amount={invoice.totalAmount} currency={invoice.currency} emphasize className="text-lg" />
              </dd>
            </div>
          </dl>

          <div>
            <h3 className="mb-2 text-[13px] font-medium text-ink-secondary">Fee lines</h3>
            {invoice.feeLines.length === 0 ? (
              <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-[13px] text-ink-mute">
                No Fees logged against this Contract yet this month.
              </p>
            ) : (
              <TableScroll>
                <Table>
                  <Thead>
                    <Tr>
                      <Th>Type</Th>
                      <Th>Description</Th>
                      <Th>Logged</Th>
                      <Th className="text-right">Amount</Th>
                    </Tr>
                  </Thead>
                  <Tbody>
                    {invoice.feeLines.map((fee) => (
                      <Tr key={fee.id}>
                        <Td className="font-medium text-ink">{FEE_TYPE_LABEL[fee.feeType]}</Td>
                        <Td className="text-ink-secondary">{fee.description ?? "—"}</Td>
                        <Td className="whitespace-nowrap text-ink-mute">{formatDateShort(fee.createdAt)}</Td>
                        <Td className="text-right">
                          <Money amount={fee.amount} currency={fee.currency} />
                        </Td>
                      </Tr>
                    ))}
                  </Tbody>
                </Table>
              </TableScroll>
            )}
          </div>

          <div>
            <h3 className="mb-2 flex items-center gap-1.5 text-[13px] font-medium text-ink-secondary">
              <IconPaperclip className="h-4 w-4" />
              Carrier invoice files
            </h3>
            {invoice.files.length === 0 ? (
              <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-[13px] text-ink-mute">
                No carrier invoice files attached yet.
              </p>
            ) : (
              <ul className="flex flex-col gap-1.5">
                {invoice.files.map((file) => (
                  <li
                    key={file.id}
                    className="flex items-center justify-between gap-3 rounded-lg border border-hairline bg-canvas px-3.5 py-2.5"
                  >
                    <div className="flex min-w-0 items-center gap-2">
                      <IconPaperclip className="h-4 w-4 shrink-0 text-ink-mute" />
                      <span className="truncate text-[13px] text-ink">{file.filename}</span>
                    </div>
                    <a
                      href={`/api/contracts/${contractId}/client-invoice/files/${file.id}`}
                      className="inline-flex shrink-0 items-center gap-1 text-[12px] font-medium text-primary hover:underline"
                    >
                      <IconDownload className="h-3.5 w-3.5" />
                      Download
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      )}
    </div>
  );
}
