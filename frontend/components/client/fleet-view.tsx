"use client";

import { useMemo } from "react";
import { ContractSwitcher } from "@/components/ui/contract-switcher";
import { SideList, SideListLayout } from "@/components/ui/side-list";
import { SubmitRequestDialog } from "@/components/client/submit-request-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { FleetTabsCard, type FleetTabId } from "@/components/fleet/fleet-tabs-card";
import { SmartphoneFleetTable } from "@/components/fleet/smartphone-fleet-table";
import { SimCardFleetTable } from "@/components/fleet/sim-card-fleet-table";
import { useContractScopedFleet, type FleetContract } from "@/components/fleet/use-contract-scoped-fleet";
import { countryRows, sortByCountry } from "@/components/ui/side-list-rows";
import { IconContracts } from "@/components/icons";
import { PAGE_PADDING, PAGE_WIDTH } from "@/components/app-shell/surface-page";
import { type SimCardListItem, type SmartphoneListItem } from "@/lib/api/types";

const FLEET_PATH = "/client/fleet";

/**
 * The Tester's Fleet (side-lists-and-tabs spec, "Fleet"). With several Contracts, a "Contracts"
 * side list by Country (a dropdown below `xl`); with one, no column and today's chip above the tabs.
 * "Submit request" stays at the right of the detail area's first row either way.
 */
export function ClientFleetView({
  smartphones,
  simCards,
  contracts,
  initialContractId,
  initialTab,
}: {
  smartphones: SmartphoneListItem[];
  simCards: SimCardListItem[];
  contracts: FleetContract[];
  /** The page's `contract` search parameter, unvalidated: used only when it names one of `contracts`. */
  initialContractId?: string;
  /** The page's `tab` search parameter, parsed at the page. */
  initialTab: FleetTabId;
}) {
  const ordered = useMemo(() => sortByCountry(contracts), [contracts]);
  const { contractId, select, detailRef, tab, selectTab, hrefFor, phones, sims } = useContractScopedFleet(
    ordered,
    smartphones,
    simCards,
    initialContractId,
    initialTab,
  );

  if (ordered.length === 0) {
    return (
      <div className={PAGE_PADDING}>
        <div className={PAGE_WIDTH}>
          <EmptyState
            icon={<IconContracts className="h-5 w-5" />}
            title="No contracts yet"
            description="Once your company has a contract in place, its fleet of smartphones and SIM cards shows up here."
          />
        </div>
      </div>
    );
  }

  const several = ordered.length > 1;

  return (
    <SideListLayout
      list={
        several ? (
          <SideList
            label="Contracts"
            items={countryRows(ordered)}
            selectedId={contractId}
            hrefFor={(id) => hrefFor(FLEET_PATH, id)}
            onSelect={select}
          />
        ) : null
      }
      // The switcher (or, with one Contract, the static chip) shares the first row with "Submit
      // request", so the button stays at the top at every width; at `xl` the column replaces the switcher.
      dropdown={null}
      detailRef={detailRef}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        {/* min-w-0 / max-w-full: a long Client name is cut inside the row, never widening the page. */}
        <div className={several ? "min-w-0 max-w-full xl:hidden" : "min-w-0 max-w-full"}>
          <ContractSwitcher contracts={ordered} value={contractId} onChange={select} />
        </div>
        <div className="ml-auto">
          <SubmitRequestDialog contracts={ordered} />
        </div>
      </div>

      <FleetTabsCard
        tab={tab}
        onTab={selectTab}
        simCards={{
          count: sims.length,
          emptyTitle: "No SIM cards on this contract yet",
          emptyDescription: "Your Agent provisions SIM cards onto this contract's fleet.",
          table: (
            <SimCardFleetTable sims={sims} currency={ordered.find((contract) => contract.id === contractId)?.currency} />
          ),
        }}
        smartphones={{
          count: phones.length,
          emptyTitle: "No smartphones on this contract yet",
          emptyDescription: "Your Agent provisions smartphones onto this contract's fleet.",
          table: <SmartphoneFleetTable phones={phones} simCards={sims} />,
        }}
      />
    </SideListLayout>
  );
}
