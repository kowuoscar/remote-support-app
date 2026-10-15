"use client";

import { useMemo } from "react";
import { ContractSwitcher } from "@/components/ui/contract-switcher";
import { EmptyState } from "@/components/ui/empty-state";
import { SideList, SideListLayout } from "@/components/ui/side-list";
import { FleetTabsCard } from "@/components/fleet/fleet-tabs-card";
import { SmartphoneFleetTable } from "@/components/fleet/smartphone-fleet-table";
import { SimCardFleetTable } from "@/components/fleet/sim-card-fleet-table";
import {
  clientRows,
  sortByClient,
  useContractScopedFleet,
  type FleetContract,
} from "@/components/fleet/use-contract-scoped-fleet";
import { IconClients, IconContracts } from "@/components/icons";
import { PAGE_PADDING, PAGE_WIDTH } from "@/components/app-shell/surface-page";
import { type CatalogCarrierItem, type SimCardListItem, type SmartphoneListItem } from "@/lib/api/types";
import { SimCardStatusControl, SmartphoneStatusControl } from "@/components/agent/fleet-status-controls";

const FLEET_PATH = "/agent/fleet";

/**
 * The Agent's Fleet (side-lists-and-tabs spec, "Fleet"): a "Clients" side list by Client name
 * (a dropdown below `xl`) beside one card whose header is the "SIM Cards | Smartphones" tablist.
 * `?contract=` and `?tab=` seed the selection and follow it, with no request.
 */
export function AgentFleetView({
  smartphones,
  simCards,
  contracts,
  carriers,
  initialContractId,
  initialTab,
}: {
  smartphones: SmartphoneListItem[];
  simCards: SimCardListItem[];
  contracts: FleetContract[];
  /** The Agent's own Country's active Carriers with their Postpaid Plans: serves every Contract. */
  carriers: CatalogCarrierItem[];
  /** The page's `contract` search parameter, unvalidated: used only when it names one of `contracts`. */
  initialContractId?: string;
  /** The page's `tab` search parameter, unvalidated. */
  initialTab?: string;
}) {
  const ordered = useMemo(() => sortByClient(contracts), [contracts]);
  const { contractId, select, detailRef, tab, selectTab, hrefFor, phones, sims } = useContractScopedFleet(
    ordered,
    smartphones,
    simCards,
    initialContractId,
    initialTab,
  );
  const currency = ordered.find((c) => c.id === contractId)?.currency ?? "";

  if (ordered.length === 0) {
    return (
      <div className={PAGE_PADDING}>
        <div className={PAGE_WIDTH}>
          <EmptyState
            icon={<IconContracts className="h-5 w-5" />}
            title="No contracts yet"
            description="Once a manager creates a contract for you, its fleet of smartphones and SIM cards shows up here."
          />
        </div>
      </div>
    );
  }

  return (
    <SideListLayout
      list={
        <SideList
          label="Clients"
          items={clientRows(ordered)}
          selectedId={contractId}
          hrefFor={(id) => hrefFor(FLEET_PATH, id)}
          onSelect={select}
        />
      }
      dropdown={<ContractSwitcher contracts={ordered} value={contractId} onChange={select} icon={IconClients} />}
      detailRef={detailRef}
    >
      <FleetTabsCard
        tab={tab}
        onTab={selectTab}
        simCards={{
          count: sims.length,
          emptyTitle: "No SIM cards on this contract yet",
          emptyDescription: "A manager provisions SIM cards onto this contract's fleet.",
          table: (
            <SimCardFleetTable
              sims={sims}
              currency={currency}
              showMonthlyFee
              canCorrect
              carriers={carriers}
              carriersHref="/agent/carriers"
              contractId={contractId}
              smartphones={phones}
              changeStatus={(sim) => (
                <SimCardStatusControl contractId={contractId} simCardId={sim.id} status={sim.status} />
              )}
            />
          ),
        }}
        smartphones={{
          count: phones.length,
          emptyTitle: "No smartphones on this contract yet",
          emptyDescription: "A manager provisions smartphones onto this contract's fleet.",
          table: (
            <SmartphoneFleetTable
              phones={phones}
              simCards={sims}
              contractId={contractId}
              canEditSerial
              canCorrect
              changeStatus={(phone) => (
                <SmartphoneStatusControl contractId={contractId} smartphoneId={phone.id} status={phone.status} />
              )}
            />
          ),
        }}
      />
    </SideListLayout>
  );
}
