"use client";

import { useState } from "react";
import Link from "next/link";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconInvoices } from "@/components/icons";
import { AgentClientInvoiceCard } from "@/components/agent/agent-client-invoice-card";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { formatBillingMonth, formatDate } from "@/lib/format";
import type { ClientInvoiceDetail } from "@/lib/api/types";

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
        <AgentClientInvoiceCard invoice={invoice} />
      )}
    </div>
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
        <section
          aria-labelledby="sent-back-notice"
          className="flex flex-col gap-1.5 rounded-lg border border-warning/40 bg-warning-bg px-4 py-3"
        >
          <h2 id="sent-back-notice" className="flex items-center gap-1.5 text-label font-semibold text-warning">
            <IconAlertTriangle className="h-4 w-4 shrink-0" />
            Sent back by the Manager on {formatDate(invoice.sentBackAt)}
          </h2>
          {invoice.sentBackReason ? (
            <p className="whitespace-pre-wrap break-words text-label text-ink">{invoice.sentBackReason}</p>
          ) : null}
          <p className="text-label text-ink-secondary">Correct what is needed, then send it again.</p>
        </section>
      ) : null}
      <AgentClientInvoiceCard invoice={invoice} readOnly={closed} />
    </div>
  );
}
