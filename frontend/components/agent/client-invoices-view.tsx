"use client";

import { useState } from "react";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconInvoices } from "@/components/icons";
import { AgentClientInvoiceCard } from "@/components/agent/agent-client-invoice-card";
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
