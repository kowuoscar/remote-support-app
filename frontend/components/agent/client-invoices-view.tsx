"use client";

import { useState } from "react";
import Link from "next/link";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconArrowRight, IconInvoices } from "@/components/icons";
import { Card } from "@/components/ui/card";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { AgentClientInvoiceCard } from "@/components/agent/agent-client-invoice-card";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { formatBillingMonth, formatDate } from "@/lib/format";
import { countryLabel, type ClientInvoiceDetail, type SentBackClientInvoice } from "@/lib/api/types";

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
  sentBack = [],
}: {
  contracts: ContractOption[];
  invoicesByContract: Record<string, ClientInvoiceDetail | null>;
  /** The Agent's sent-back invoices from any month, oldest first; the section is absent when empty. */
  sentBack?: SentBackClientInvoice[];
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
      <SentBackSection rows={sentBack} />
      <ContractSwitcher contracts={contracts} value={contractId} onChange={setContractId} />

      {!invoice ? (
        <EmptyState
          icon={<IconAlertTriangle className="h-5 w-5" />}
          title="Couldn't load this Contract's Client Invoice"
          description="Switch contracts or reload the page to try again."
        />
      ) : (
        <>
          {invoice.status === "DRAFT" && invoice.sentBackAt ? (
            <SentBackNotice sentBackAt={invoice.sentBackAt} reason={invoice.sentBackReason} />
          ) : null}
          <AgentClientInvoiceCard invoice={invoice} />
        </>
      )}
    </div>
  );
}

/**
 * The Manager's send-back as a warning-toned notice: when, the reason, and what to do next. Shown
 * on the by-id page and above a sent-back current-month card.
 */
function SentBackNotice({ sentBackAt, reason }: { sentBackAt: string; reason?: string | null }) {
  return (
    <section
      aria-labelledby="sent-back-notice"
      className="flex flex-col gap-1.5 rounded-lg border border-warning/40 bg-warning-bg px-4 py-3"
    >
      <h2 id="sent-back-notice" className="flex items-center gap-1.5 text-label font-semibold text-warning">
        <IconAlertTriangle className="h-4 w-4 shrink-0" />
        Sent back by the Manager on {formatDate(sentBackAt)}
      </h2>
      {reason ? <p className="whitespace-pre-wrap break-words text-label text-ink">{reason}</p> : null}
      <p className="text-label text-ink-secondary">Correct what is needed, then send it again.</p>
    </section>
  );
}

/** "Sent back to you": every invoice the Manager sent back, any month; renders nothing when none. */
function SentBackSection({ rows }: { rows: SentBackClientInvoice[] }) {
  if (rows.length === 0) return null;
  return (
    <section aria-labelledby="sent-back-to-you">
      <Card className="overflow-hidden">
        <h2 id="sent-back-to-you" className="px-4 py-3 text-sm font-semibold text-ink">
          Sent back to you
        </h2>
        <TableScroll className="rounded-none border-0 border-t">
          <Table>
            <Thead>
              <Tr>
                <Th>Contract</Th>
                <Th>Billing month</Th>
                <Th>Sent back</Th>
                <Th>Reason</Th>
                {/* relative: keeps the sr-only label inside the table's scroll container on narrow screens */}
                <Th className="relative text-right">
                  <span className="sr-only">Action</span>
                </Th>
              </Tr>
            </Thead>
            <Tbody>
              {rows.map((row) => {
                const month = formatBillingMonth(row.billingMonth);
                return (
                  <Tr key={row.id}>
                    <Td className="whitespace-nowrap font-medium text-ink">
                      {row.clientName} — {countryLabel(row.country)}
                    </Td>
                    <Td className="whitespace-nowrap text-ink-secondary">{month}</Td>
                    <Td className="whitespace-nowrap text-ink-mute">{formatDate(row.sentBackAt)}</Td>
                    <Td className="max-w-md">
                      <p className="line-clamp-2 break-words text-ink-secondary" title={row.sentBackReason ?? undefined}>
                        {row.sentBackReason}
                      </p>
                    </Td>
                    <Td className="text-right">
                      <Link
                        href={`/agent/client-invoices/${row.id}`}
                        aria-label={`Open ${row.clientName}, ${month}`}
                        className="inline-flex h-7 items-center gap-1.5 whitespace-nowrap rounded-lg bg-primary-soft-bg px-3 text-[13px] font-medium text-primary-soft-text transition-colors hover:bg-primary/20 active:bg-primary/25"
                      >
                        Open
                        <IconArrowRight className="h-3.5 w-3.5" />
                      </Link>
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        </TableScroll>
      </Card>
    </section>
  );
}

/**
 * One Client Invoice on its own page, `/agent/client-invoices/{invoiceId}` (send-a-client-invoice-back
 * spec, Frontend: Agent): any billing month, reached by id. A draft the Manager sent back opens with
 * the Manager's reason in a warning notice above the same card the current-month page uses; a draft of
 * a past month that was never sent is shown read-only, because the backend refuses the Agent's writes
 * to it. `invoice` is null for another Agent's id, an unknown id and another Tenant's id alike.
 * `currentBillingMonth` comes from the server so server and client agree on the month.
 */
export function AgentClientInvoicePageView({
  invoice,
  currentBillingMonth,
}: {
  invoice: ClientInvoiceDetail | null;
  currentBillingMonth: string;
}) {
  if (!invoice) {
    return (
      <EmptyState
        icon={<IconAlertTriangle className="h-5 w-5" />}
        title="Client Invoice not found"
        description="It may have been removed, or it isn't one of yours."
        action={
          <Link href="/agent/client-invoices" className="text-label font-medium text-primary hover:underline">
            Back to Client Invoices
          </Link>
        }
      />
    );
  }

  const sentBack = invoice.status === "DRAFT" && Boolean(invoice.sentBackAt);
  const closed = invoice.status === "DRAFT" && !sentBack && invoice.billingMonth < currentBillingMonth;

  return (
    <div className="flex flex-col gap-4">
      <Breadcrumb
        items={[
          { label: "Client Invoices", href: "/agent/client-invoices" },
          { label: formatBillingMonth(invoice.billingMonth) },
        ]}
      />
      {sentBack && invoice.sentBackAt ? (
        <SentBackNotice sentBackAt={invoice.sentBackAt} reason={invoice.sentBackReason} />
      ) : null}
      <AgentClientInvoiceCard invoice={invoice} readOnly={closed} />
    </div>
  );
}
