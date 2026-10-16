"use client";

import { useMemo, useRef, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { ContractSwitcher } from "@/components/ui/contract-switcher";
import { EmptyState } from "@/components/ui/empty-state";
import { SideList, SideListLayout } from "@/components/ui/side-list";
import { FleetTabsCard, tabParam, type FleetTabId } from "@/components/fleet/fleet-tabs-card";
import { SmartphoneFleetTable } from "@/components/fleet/smartphone-fleet-table";
import { SimCardFleetTable } from "@/components/fleet/sim-card-fleet-table";
import { IconClients, IconContracts } from "@/components/icons";
import { CreateContractDialog } from "@/components/manager/create-contract-dialog";
import { CreateSimCardDialog } from "@/components/manager/create-sim-card-dialog";
import { CreateSmartphoneDialog } from "@/components/manager/create-smartphone-dialog";
import { InvoiceSummaryCard } from "@/components/manager/invoice-summary-card";
import {
  countryLabel,
  type AgentListItem,
  type CatalogCarrierItem,
  type ClientInvoiceDetail,
  type ClientListItem,
  type ContractListItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";
import { groupByClient } from "@/lib/manager-contracts";
import { selectionHref, useSelectionAddress } from "@/lib/selection-address";

const PATH = "/manager/contracts";

/** What the server reads for the one selected Contract: its Fleet, current-month invoice and Country's Carriers. */
export type SelectedContract = {
  contract: ContractListItem;
  smartphones: SmartphoneListItem[];
  simCards: SimCardListItem[];
  /** Null when the invoice read failed: the summary is then absent. */
  invoice: ClientInvoiceDetail | null;
  carriers: CatalogCarrierItem[];
};

/**
 * The Manager's Contracts, on one page (side-lists-and-tabs spec, "The Manager's Contracts: one
 * page"): a "Clients" side list (a dropdown below `xl`) of the Clients that hold a Contract, beside
 * the selected Contract: the Client's name, an Agent select over that Client's Contracts, the
 * current-month invoice summary and the tabbed Fleet. A pick is a `router.replace` to `?contract=`
 * in a transition, so the server renders that Contract only; meanwhile the area keeps its content,
 * `aria-busy`. The open tab is local state written to the address with the History API.
 */
export function ManagerContractsView({
  contracts,
  clients,
  agents,
  selected,
  initialTab,
}: {
  contracts: ContractListItem[];
  clients: ClientListItem[];
  agents: AgentListItem[];
  /** Null exactly when there are no Contracts. */
  selected: SelectedContract | null;
  /** The page's `tab` search parameter, parsed at the page. */
  initialTab: FleetTabId;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [tab, setTab] = useState<FleetTabId>(initialTab);
  const writeAddress = useSelectionAddress();
  const detailRef = useRef<HTMLDivElement>(null);
  const groups = useMemo(() => groupByClient(contracts), [contracts]);

  const newContract = <CreateContractDialog clients={clients} agents={agents} />;

  if (!selected) {
    return (
      <SideListLayout list={null} dropdown={null} header={newContract}>
        <EmptyState
          icon={<IconContracts className="h-5 w-5" />}
          title="No contracts yet"
          description="Add a contract to link one client to one agent — its currency is copied from the agent automatically."
        />
      </SideListLayout>
    );
  }

  const { contract, smartphones, simCards, invoice, carriers } = selected;
  const group = groups.find((g) => g.clientId === contract.clientId);
  const siblings = group?.contracts ?? [contract];
  // A Client's row stands for its first Contract by Agent name.
  const rowIdOf = (clientId: string) => groups.find((g) => g.clientId === clientId)?.contracts[0]?.id ?? "";
  const selectedRowId = rowIdOf(contract.clientId);
  const carriersHref = `/manager/carriers?country=${contract.country}`;

  function pick(id: string) {
    // Async, so the area stays busy until the navigation settles (React 19 holds an async transition open).
    startTransition(async () => {
      await router.replace(selectionHref(PATH, { contract: id, tab: tabParam(tab) }), { scroll: false });
    });
    // The document scrolls the page; return to the area's top when it would start off-screen.
    if (detailRef.current && detailRef.current.getBoundingClientRect().top < 0) {
      window.scrollTo({ top: 0 });
    }
  }

  function selectTab(next: FleetTabId) {
    setTab(next);
    writeAddress({ tab: tabParam(next) });
  }

  return (
    <SideListLayout
      header={newContract}
      list={
        <SideList
          label="Clients"
          items={groups.map((g) => ({ id: g.contracts[0].id, name: g.clientName }))}
          selectedId={selectedRowId}
          hrefFor={(id) => selectionHref("", { contract: id, tab: tabParam(tab) })}
          onSelect={pick}
        />
      }
      dropdown={
        <ContractSwitcher
          contracts={groups.map((g) => ({ id: g.contracts[0].id, label: g.clientName }))}
          value={selectedRowId}
          onChange={pick}
          icon={IconClients}
        />
      }
      detailRef={detailRef}
    >
      <div aria-busy={pending} className="flex flex-col gap-5 transition-opacity aria-busy:opacity-70">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <h2 className="min-w-0 truncate text-sm font-semibold text-ink" title={contract.clientName}>
            {contract.clientName}
          </h2>
          <label className="flex items-center gap-2 text-label font-medium text-ink-secondary">
            Agent
            <select
              value={contract.id}
              onChange={(event) => pick(event.target.value)}
              className="h-9 max-w-56 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
            >
              {siblings.map((sibling) => (
                <option key={sibling.id} value={sibling.id}>
                  {sibling.agentName}
                </option>
              ))}
            </select>
          </label>
          <p className="text-label text-ink-mute">
            {countryLabel(contract.country)} · {contract.currency}
          </p>
        </div>

        {invoice ? <InvoiceSummaryCard kind="CLIENT_INVOICE" invoice={invoice} /> : null}

        <FleetTabsCard
          tab={tab}
          onTab={selectTab}
          simCards={{
            count: simCards.length,
            emptyTitle: "No SIM cards yet",
            emptyDescription: "Add this contract's first SIM card — Postpaid needs a plan, Prepaid doesn't.",
            action: (
              <CreateSimCardDialog
                contractId={contract.id}
                currency={contract.currency}
                carriers={carriers}
                carriersHref={carriersHref}
              />
            ),
            table: (
              <SimCardFleetTable
                sims={simCards}
                currency={contract.currency}
                showMonthlyFee
                contractId={contract.id}
                smartphones={smartphones}
                canCorrect
                carriers={carriers}
                carriersHref={carriersHref}
              />
            ),
          }}
          smartphones={{
            count: smartphones.length,
            emptyTitle: "No smartphones yet",
            emptyDescription: "Add this contract's first smartphone — it starts Active.",
            action: <CreateSmartphoneDialog contractId={contract.id} />,
            table: (
              <SmartphoneFleetTable
                phones={smartphones}
                simCards={simCards}
                contractId={contract.id}
                canEditSerial
                canCorrect
              />
            ),
          }}
        />
      </div>
    </SideListLayout>
  );
}
