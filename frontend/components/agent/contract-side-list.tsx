"use client";

import { useEffect, useRef, type MouseEvent } from "react";
import Link from "next/link";
import { Badge, type Tone } from "@/components/ui/badge";
import { cn } from "@/lib/cn";

export type SideListContract = {
  id: string;
  clientName: string;
  /** "Kenya", from `countryLabel(country)`. */
  countryLabel: string;
  /** `clientInvoiceBadge` of the Contract's current-month invoice; null when that invoice failed to load. */
  status: { label: string; tone: Tone } | null;
};

/**
 * The Agent's Contracts as a column of links beside the invoice (client-invoices-side-list spec,
 * "The side list"). Presentational: the view owns the selection. A plain click is taken for
 * `onSelect`; a modified click (Cmd/Ctrl/Shift/Alt) and the middle button fall through to the
 * link's `href`, so "open in a new tab" works. `contracts` arrives in column order. The Country
 * shows only on rows whose Client name another row shares.
 */
export function ContractSideList({
  contracts,
  selectedId,
  onSelect,
  className,
}: {
  contracts: SideListContract[];
  selectedId: string;
  onSelect: (id: string) => void;
  className?: string;
}) {
  const nameCounts = new Map<string, number>();
  for (const contract of contracts) {
    const key = contract.clientName.toLocaleLowerCase();
    nameCounts.set(key, (nameCounts.get(key) ?? 0) + 1);
  }

  // On arrival, bring the selected row into the column's own viewport. Scrolls the column, never
  // the document (scrollIntoView would move both).
  const navRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const nav = navRef.current;
    const row = nav?.querySelector<HTMLElement>("[aria-current]");
    if (!nav || !row) return;
    const navBox = nav.getBoundingClientRect();
    const rowBox = row.getBoundingClientRect();
    if (rowBox.bottom > navBox.bottom) nav.scrollTop += rowBox.bottom - navBox.bottom;
    else if (rowBox.top < navBox.top) nav.scrollTop -= navBox.top - rowBox.top;
    // Arrival only: a later pick is of a row the Agent can already see.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function onClick(event: MouseEvent<HTMLAnchorElement>, id: string) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    onSelect(id);
  }

  return (
    <nav ref={navRef} aria-labelledby="client-side-list-heading" className={className}>
      <h2 id="client-side-list-heading" className="px-3 pb-2 text-label font-medium text-ink-mute">
        Clients
      </h2>
      <ul className="flex flex-col gap-0.5">
        {contracts.map((contract) => {
          const selected = contract.id === selectedId;
          const shared = (nameCounts.get(contract.clientName.toLocaleLowerCase()) ?? 0) > 1;
          return (
            <li key={contract.id}>
              <Link
                href={`/agent/client-invoices?contract=${encodeURIComponent(contract.id)}`}
                // No prefetch: the page's reads create a draft per Contract on first view.
                prefetch={false}
                aria-current={selected ? "true" : undefined}
                onClick={(event) => onClick(event, contract.id)}
                className={cn(
                  "flex flex-col gap-1 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  selected ? "bg-primary-soft-bg text-primary-soft-text" : "text-ink-secondary hover:bg-canvas-soft",
                )}
              >
                <span className="line-clamp-2 min-w-0 break-words" title={contract.clientName}>
                  {contract.clientName}
                </span>
                {" "}
                {shared || contract.status ? (
                  <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    {shared ? <span className="text-label-sm text-ink-mute">{contract.countryLabel}</span> : null}
                    {shared && contract.status ? " " : null}
                    {contract.status ? <Badge tone={contract.status.tone}>{contract.status.label}</Badge> : null}
                  </span>
                ) : null}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
