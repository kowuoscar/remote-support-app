import { useMemo, useRef, useState } from "react";
import type { ContractOption } from "@/components/ui/contract-switcher";
import type { SideListItem } from "@/components/ui/side-list";
import type { SimCardListItem, SmartphoneListItem } from "@/lib/api/types";
import { fleetTabFromParam, type FleetTabId } from "@/components/fleet/fleet-tabs-card";

/** What a Fleet page passes per Contract: the switcher's option plus the parts the side list sorts and names by. */
export type FleetContract = ContractOption & {
  clientName: string;
  /** "Kenya", from `countryLabel(country)`. */
  countryLabel: string;
};

const collator = new Intl.Collator(undefined, { sensitivity: "base" });

/** The Agent's column order (the Client Invoices one): Client name, then Country. */
export function sortByClient(contracts: FleetContract[]): FleetContract[] {
  return [...contracts].sort(
    (a, b) => collator.compare(a.clientName, b.clientName) || collator.compare(a.countryLabel, b.countryLabel),
  );
}

/** The Tester's column order: by Country. */
export function sortByCountry(contracts: FleetContract[]): FleetContract[] {
  return [...contracts].sort((a, b) => collator.compare(a.countryLabel, b.countryLabel));
}

/** The Agent's rows: the Client name, with the Country only where another row shares the name. */
export function clientRows(ordered: FleetContract[]): SideListItem[] {
  const counts = new Map<string, number>();
  for (const c of ordered) {
    const key = c.clientName.toLocaleLowerCase();
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return ordered.map((c) => ({
    id: c.id,
    name: c.clientName,
    detail: (counts.get(c.clientName.toLocaleLowerCase()) ?? 0) > 1 ? c.countryLabel : undefined,
  }));
}

/** The Tester's rows: one per Contract, by Country. */
export function countryRows(ordered: FleetContract[]): SideListItem[] {
  return ordered.map((c) => ({ id: c.id, name: c.countryLabel }));
}

/** The address with one parameter set (or removed, for `null`), as a relative URL under `path`. */
function addressWith(path: string, params: Record<string, string | null>): string {
  const url = new URL(window.location.href);
  for (const [key, value] of Object.entries(params)) {
    if (value === null) url.searchParams.delete(key);
    else url.searchParams.set(key, value);
  }
  return `${path}${url.search}`;
}

/** The `tab` parameter's value: absent for SIM Cards, the default. */
function tabParam(tab: FleetTabId): string | null {
  return tab === "smartphones" ? "smartphones" : null;
}

/**
 * Scopes a Fleet's Smartphones and SIM Cards to the selected Contract, and tracks that selection and
 * the open tab — shared by AgentFleetView and ClientFleetView. `contracts` arrives in column order.
 * The Contract seeds from `initialContractId` when it names one of them, else from the first; the tab
 * seeds from `initialTab`. Both are written to the address with the native History API: no entry, no
 * server render, no request. The state argument is null so that Next syncs its router state to the
 * new URL and a later `router.refresh()` (after a mutation) keeps the query string.
 */
export function useContractScopedFleet(
  contracts: FleetContract[],
  smartphones: SmartphoneListItem[],
  simCards: SimCardListItem[],
  initialContractId?: string,
  initialTab?: string,
) {
  const [contractId, setContractId] = useState(
    () => contracts.find((c) => c.id === initialContractId)?.id ?? contracts[0]?.id ?? "",
  );
  const [tab, setTab] = useState<FleetTabId>(() => fleetTabFromParam(initialTab));

  const phones = useMemo(() => smartphones.filter((p) => p.contractId === contractId), [smartphones, contractId]);
  const sims = useMemo(() => simCards.filter((s) => s.contractId === contractId), [simCards, contractId]);

  function writeAddress(params: Record<string, string | null>) {
    window.history.replaceState(null, "", addressWith(window.location.pathname, params));
  }

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
    const query = new URLSearchParams({ contract: id });
    const param = tabParam(tab);
    if (param) query.set("tab", param);
    return `${path}?${query.toString()}`;
  }

  return { contractId, select, detailRef, tab, selectTab, hrefFor, phones, sims };
}
