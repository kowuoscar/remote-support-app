import { useMemo, useRef, useState } from "react";
import type { ContractOption } from "@/components/ui/contract-switcher";
import type { ClientContractRow } from "@/components/ui/side-list-rows";
import type { SimCardListItem, SmartphoneListItem } from "@/lib/api/types";
import { tabParam, type FleetTabId } from "@/components/fleet/fleet-tabs-card";
import { selectionHref, useSelectionAddress } from "@/lib/selection-address";

/** What a Fleet page passes per Contract: the switcher's option plus the parts the side list sorts and names by. */
export type FleetContract = ContractOption & ClientContractRow;

/**
 * Scopes a Fleet's Smartphones and SIM Cards to the selected Contract, and tracks that selection and
 * the open tab — shared by AgentFleetView and ClientFleetView. `contracts` arrives in column order.
 * The Contract seeds from `initialContractId` when it names one of them, else from the first; the tab
 * seeds from `initialTab`. Both follow the selection into the address with no request
 * (`useSelectionAddress`).
 */
export function useContractScopedFleet(
  contracts: FleetContract[],
  smartphones: SmartphoneListItem[],
  simCards: SimCardListItem[],
  initialContractId: string | undefined,
  initialTab: FleetTabId,
) {
  const [contractId, setContractId] = useState(
    () => contracts.find((c) => c.id === initialContractId)?.id ?? contracts[0]?.id ?? "",
  );
  const [tab, setTab] = useState<FleetTabId>(initialTab);
  const writeAddress = useSelectionAddress();

  const phones = useMemo(() => smartphones.filter((p) => p.contractId === contractId), [smartphones, contractId]);
  const sims = useMemo(() => simCards.filter((s) => s.contractId === contractId), [simCards, contractId]);

  // The detail area, so a pick can bring its top back into view (the Client Invoices rule).
  const detailRef = useRef<HTMLDivElement>(null);

  function select(id: string) {
    setContractId(id);
    writeAddress({ contract: id, tab: tabParam(tab) });
    // The document scrolls the Fleet; return to its top when the new Fleet would start off-screen.
    if (detailRef.current && detailRef.current.getBoundingClientRect().top < 0) {
      window.scrollTo({ top: 0 });
    }
  }

  function selectTab(next: FleetTabId) {
    setTab(next);
    writeAddress({ tab: tabParam(next) });
  }

  /** A row's real link, keeping the open tab. */
  function hrefFor(path: string, id: string): string {
    return selectionHref(path, { contract: id, tab: tabParam(tab) });
  }

  return { contractId, select, detailRef, tab, selectTab, hrefFor, phones, sims };
}
