import type { SideListItem } from "@/components/ui/side-list";

/** What a Contract-per-row side list sorts and names by. */
export type ClientContractRow = {
  id: string;
  clientName: string;
  /** "Kenya", from `countryLabel(country)`. */
  countryLabel: string;
};

const collator = new Intl.Collator(undefined, { sensitivity: "base" });

/** The Agent's column order (Client Invoices, Fleet): Client name, then Country. */
export function sortByClient<T extends ClientContractRow>(contracts: T[]): T[] {
  return [...contracts].sort(
    (a, b) => collator.compare(a.clientName, b.clientName) || collator.compare(a.countryLabel, b.countryLabel),
  );
}

/** The Tester's column order: by Country. */
export function sortByCountry<T extends ClientContractRow>(contracts: T[]): T[] {
  return [...contracts].sort((a, b) => collator.compare(a.countryLabel, b.countryLabel));
}

/** The Agent's rows: the Client name, with the Country only where another row shares the name. */
export function clientRows(ordered: ClientContractRow[]): SideListItem[] {
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
export function countryRows(ordered: ClientContractRow[]): SideListItem[] {
  return ordered.map((c) => ({ id: c.id, name: c.countryLabel }));
}
