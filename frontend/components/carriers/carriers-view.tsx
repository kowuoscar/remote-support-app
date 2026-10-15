"use client";

import { useId, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconCarrier, IconPlus } from "@/components/icons";
import { ArchiveCarrierDialog, type ArchiveCarrierDialogHandle } from "@/components/carriers/archive-carrier-dialog";
import { CarrierNameDialog, type CarrierNameDialogHandle } from "@/components/carriers/carrier-name-dialog";
import { CarrierOfferDialog, type CarrierOfferDialogHandle } from "@/components/carriers/carrier-offer-dialog";
import {
  ArchiveCarrierOfferDialog,
  type ArchiveCarrierOfferDialogHandle,
} from "@/components/carriers/archive-carrier-offer-dialog";
import { OFFER_COPY } from "@/components/carriers/carrier-offer-copy";
import { Money } from "@/components/ui/money";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { SideList, SideListLayout, type SideListItem } from "@/components/ui/side-list";
import { formatMoney } from "@/lib/format";
import { CARRIER_OFFER_LISTS, type CarrierOfferList } from "@/lib/api/carrier-offers";
import {
  COUNTRIES,
  countryLabel,
  type CarrierCatalog,
  type CarrierOfferItem,
  type CatalogCarrierItem,
  type Country,
} from "@/lib/api/types";

const archivedDate = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
  year: "numeric",
  // Server and browser must agree on the day, or hydration mismatches.
  timeZone: "UTC",
});

/** The current query string with one parameter set (or removed, for `null`). */
function useQueryWith() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  return (key: string, value: string | null) => {
    const params = new URLSearchParams(searchParams.toString());
    if (value === null) params.delete(key);
    else params.set(key, value);
    const query = params.toString();
    return query ? `${pathname}?${query}` : pathname;
  };
}

/** A relative URL with one query parameter removed. */
function queryWithout(url: string, key: string): string {
  const [path, query = ""] = url.split("?");
  const params = new URLSearchParams(query);
  params.delete(key);
  const rest = params.toString();
  return rest ? `${path}?${rest}` : path;
}

const LIST_FIELD = { "topup-options": "topupOptions", "postpaid-plans": "postpaidPlans" } as const;

/** What "Show archived" would reveal for the selected Carrier: its archived Topup Options and Postpaid Plans. */
function countArchived(carrier: CatalogCarrierItem): number {
  return (
    carrier.topupOptions.filter((entry) => entry.archivedAt !== null).length +
    carrier.postpaidPlans.filter((entry) => entry.archivedAt !== null).length
  );
}

/**
 * One Country's Carriers, maintained in place (agent-maintains-carriers ticket): the Agent's own
 * Country, or — with `countryFilter` — any Country the Company Manager picks. Every Carrier of the
 * Country, active and archived, is a row of the "Carriers" side list (archived last, as the server
 * orders them); the selected one shows its Topup Options and Postpaid Plans beside it, in place
 * (side-lists-and-tabs spec). The selection is `?carrier=`, written without a history entry or a
 * request; the Country and "Show archived" (which now governs only the selected Carrier's archived
 * offers) live in the URL too (`?country=`, `?archived=1`). Every mutation refreshes the
 * server-rendered catalog without leaving the page. An archived Carrier shows its lists read-only,
 * since nothing under it can be picked or changed any more. Below `xl` a Carrier dropdown takes the
 * column's place.
 */
export function CarriersView({
  country,
  catalog,
  countryFilter = false,
  initialCarrierId,
}: {
  country: Country;
  catalog: CarrierCatalog | null;
  countryFilter?: boolean;
  /** The page's `carrier` search parameter, unvalidated: used only when it names one of the catalog's Carriers. */
  initialCarrierId?: string;
}) {
  const nameDialogRef = useRef<CarrierNameDialogHandle>(null);
  const archiveDialogRef = useRef<ArchiveCarrierDialogHandle>(null);
  const offerDialogRef = useRef<CarrierOfferDialogHandle>(null);
  const archiveOfferDialogRef = useRef<ArchiveCarrierOfferDialogHandle>(null);
  const detailRef = useRef<HTMLDivElement>(null);
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryWith = useQueryWith();
  // Seeded from the URL, then flipped at once on click; the URL follows behind, so the checkbox
  // never waits on the navigation to show its new state.
  const [showArchived, setShowArchived] = useState(searchParams.get("archived") === "1");
  const [carrierId, setCarrierId] = useState(initialCarrierId ?? "");
  const countryName = countryLabel(country);

  const carriers = catalog?.carriers ?? [];
  const active = carriers.filter((carrier) => carrier.archivedAt === null);
  // The first row stands in for a selection that names nothing in the catalog (an unknown address
  // value, or a just-added Carrier until the refreshed catalog lands).
  const selected = carriers.find((carrier) => carrier.id === carrierId) ?? carriers[0];

  function select(id: string) {
    setCarrierId(id);
    // The address follows the selection with the native History API: no entry, no server render,
    // no request. Never router.push/replace, which would re-run the page's reads. The state
    // argument is null so that Next syncs its own router state to the new URL: a later
    // router.refresh() (after a mutation) then keeps `carrier` instead of restoring the old address.
    const url = new URL(window.location.href);
    url.searchParams.set("carrier", id);
    window.history.replaceState(null, "", url);
    // The document scrolls the detail; return to its top when it would start off-screen.
    if (detailRef.current && detailRef.current.getBoundingClientRect().top < 0) {
      window.scrollTo({ top: 0 });
    }
  }

  const header = (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
      {countryFilter ? <CountryFilter country={country} /> : null}
      <div className="flex items-center gap-3">
        <Button variant="primary" onClick={() => nameDialogRef.current?.openCreate()} disabled={!catalog}>
          <IconPlus className="h-4 w-4" />
          Add carrier
        </Button>
        <span className="tnum shrink-0 text-label text-ink-mute">{active.length} active</span>
      </div>
    </div>
  );

  const items: SideListItem[] = carriers.map((carrier) => ({
    id: carrier.id,
    name: carrier.name,
    muted: carrier.archivedAt !== null,
    badge: carrier.archivedAt !== null ? <Badge tone="neutral">Archived</Badge> : undefined,
  }));
  const options: ContractOption[] = carriers.map((carrier) => ({
    id: carrier.id,
    label: carrier.name,
    meta: carrier.archivedAt !== null ? "Archived" : undefined,
  }));

  return (
    <SideListLayout
      list={
        catalog && selected ? (
          <SideList
            label="Carriers"
            items={items}
            selectedId={selected.id}
            hrefFor={(id) => queryWith("carrier", id)}
            onSelect={select}
          />
        ) : null
      }
      header={header}
      dropdown={
        catalog && selected ? (
          <ContractSwitcher contracts={options} value={selected.id} onChange={select} icon={IconCarrier} />
        ) : null
      }
      detailRef={detailRef}
    >
      {!catalog ? (
        <EmptyState
          icon={<IconAlertTriangle className="h-5 w-5" />}
          title="Couldn't load carriers"
          description="The catalog didn't load. Reload the page to try again."
        />
      ) : !selected ? (
        <EmptyState
          icon={<IconCarrier className="h-5 w-5" />}
          title={`No carriers in ${countryName} yet`}
          description={`Add the mobile carriers you buy SIM cards and topups from. Prices will be in ${catalog.currency}.`}
        />
      ) : (
        <CarrierDetail
          carrier={selected}
          currency={catalog.currency}
          countryName={countryName}
          showArchived={showArchived}
          onToggleArchived={(checked) => {
            setShowArchived(checked);
            router.replace(queryWith("archived", checked ? "1" : null), { scroll: false });
          }}
          onRename={() => nameDialogRef.current?.openRename(selected)}
          onArchive={() => archiveDialogRef.current?.open(selected)}
          onAddEntry={(list) => offerDialogRef.current?.openCreate(list, selected)}
          onEditEntry={(list, entry) => offerDialogRef.current?.openEdit(list, selected, entry)}
          onArchiveEntry={(list, entry) => archiveOfferDialogRef.current?.open(list, selected, entry)}
        />
      )}

      <CarrierNameDialog ref={nameDialogRef} country={country} countryName={countryName} onCreated={setCarrierId} />
      <ArchiveCarrierDialog ref={archiveDialogRef} />
      <CarrierOfferDialog ref={offerDialogRef} currency={catalog?.currency ?? ""} />
      <ArchiveCarrierOfferDialog ref={archiveOfferDialogRef} />
    </SideListLayout>
  );
}

/** The selected Carrier as one Card: its name and actions, the "Show archived" toggle, and its two offer lists. */
function CarrierDetail({
  carrier,
  currency,
  countryName,
  showArchived,
  onToggleArchived,
  onRename,
  onArchive,
  onAddEntry,
  onEditEntry,
  onArchiveEntry,
}: {
  carrier: CatalogCarrierItem;
  currency: string;
  countryName: string;
  showArchived: boolean;
  onToggleArchived: (checked: boolean) => void;
  onRename: () => void;
  onArchive: () => void;
  onAddEntry: (list: CarrierOfferList) => void;
  onEditEntry: (list: CarrierOfferList, entry: CarrierOfferItem) => void;
  onArchiveEntry: (list: CarrierOfferList, entry: CarrierOfferItem) => void;
}) {
  const archived = carrier.archivedAt !== null;
  const archivedCount = countArchived(carrier);
  return (
    <Card>
      <div className="flex flex-col gap-1 border-b border-hairline px-5 py-4">
        <div className="flex items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-canvas-soft text-ink-mute">
              <IconCarrier className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <h2
                translate="no"
                className={`block truncate text-sm font-semibold ${archived ? "text-ink-mute" : "text-ink"}`}
              >
                {carrier.name}
              </h2>
              {archived ? (
                <span className="block text-label-sm text-ink-mute">
                  Archived {archivedDate.format(new Date(carrier.archivedAt!))}
                </span>
              ) : null}
            </div>
          </div>
          {archived ? (
            <Badge tone="neutral">Archived</Badge>
          ) : (
            <span className="flex shrink-0 items-center gap-1.5">
              <Button variant="row" size="sm" onClick={onRename} aria-label={`Rename ${carrier.name}`}>
                Rename
              </Button>
              <Button variant="ghost" size="sm" onClick={onArchive} aria-label={`Archive ${carrier.name}`}>
                Archive
              </Button>
            </span>
          )}
        </div>
        <p className="text-label text-ink-mute md:pl-11">
          Prices in <span className="tnum">{currency}</span> · shared by every agent in {countryName}
        </p>
      </div>

      <div className="px-5 pt-3">
        <label className="flex w-fit items-center gap-2 text-label font-medium text-ink-secondary">
          <input
            type="checkbox"
            checked={showArchived}
            onChange={(event) => onToggleArchived(event.target.checked)}
            className="h-4 w-4 rounded border-hairline-strong accent-primary"
          />
          Show archived
          {archivedCount > 0 ? <span className="tnum text-ink-mute">({archivedCount})</span> : null}
        </label>
      </div>

      <div className="grid gap-x-8 gap-y-4 px-5 pb-4 pt-3 md:grid-cols-2">
        {CARRIER_OFFER_LISTS.map((list) => (
          <OfferList
            key={list}
            list={list}
            carrierName={carrier.name}
            readOnly={archived}
            entries={carrier[LIST_FIELD[list]].filter((entry) => showArchived || entry.archivedAt === null)}
            currency={currency}
            onAdd={() => onAddEntry(list)}
            onEdit={(entry) => onEditEntry(list, entry)}
            onArchive={(entry) => onArchiveEntry(list, entry)}
          />
        ))}
      </div>
    </Card>
  );
}

/** One of a Carrier's two offer lists: each entry's name and price, and its actions. */
function OfferList({
  list,
  carrierName,
  readOnly,
  entries,
  currency,
  onAdd,
  onEdit,
  onArchive,
}: {
  list: CarrierOfferList;
  carrierName: string;
  /** An archived Carrier's lists: entries only, with no Add, Edit or Archive. */
  readOnly: boolean;
  entries: CarrierOfferItem[];
  currency: string;
  onAdd: () => void;
  onEdit: (entry: CarrierOfferItem) => void;
  onArchive: (entry: CarrierOfferItem) => void;
}) {
  const copy = OFFER_COPY[list];
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <div className="flex items-center justify-between gap-3 border-b border-hairline pb-1.5">
        <h4 id={headingId} className="text-label-sm font-medium tracking-[0.02em] text-ink-mute">
          {copy.heading}
        </h4>
        {readOnly ? null : (
          <Button
            variant="ghost"
            size="sm"
            onClick={onAdd}
            aria-label={`Add a ${copy.singular} to ${carrierName}`}
            className="-mr-2"
          >
            <IconPlus className="h-3.5 w-3.5" />
            Add
          </Button>
        )}
      </div>
      {entries.length === 0 ? (
        <p className="py-2.5 text-label text-ink-mute">No {copy.heading.toLowerCase()} yet.</p>
      ) : (
        <ul aria-label={`${carrierName} ${copy.heading.toLowerCase()}`} className="divide-y divide-hairline">
          {entries.map((entry) => (
            <OfferRow
              key={entry.id}
              entry={entry}
              currency={currency}
              perMonth={copy.perMonth}
              readOnly={readOnly}
              onEdit={() => onEdit(entry)}
              onArchive={() => onArchive(entry)}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function OfferRow({
  entry,
  currency,
  perMonth,
  readOnly,
  onEdit,
  onArchive,
}: {
  entry: CarrierOfferItem;
  currency: string;
  perMonth: boolean;
  readOnly: boolean;
  onEdit: () => void;
  onArchive: () => void;
}) {
  const archived = entry.archivedAt !== null;
  return (
    <li className="flex min-h-11 items-center gap-3 py-1.5">
      <span translate="no" className={`min-w-0 flex-1 break-words text-sm ${archived ? "text-ink-mute" : "text-ink"}`}>
        {entry.name}
      </span>
      <span className="shrink-0 text-sm">
        {archived ? (
          // Money always inks its figure; an archived price reads muted, like its name.
          <span className="tnum text-ink-mute">{formatMoney(entry.price, currency)}</span>
        ) : (
          <Money amount={entry.price} currency={currency} />
        )}
        {perMonth ? <span className="text-label-sm text-ink-mute">/mo</span> : null}
      </span>
      {archived ? (
        <Badge tone="neutral">Archived</Badge>
      ) : readOnly ? null : (
        <span className="flex shrink-0 items-center gap-1">
          <Button variant="row" size="sm" onClick={onEdit} aria-label={`Edit ${entry.name}`}>
            Edit
          </Button>
          <Button variant="ghost" size="sm" onClick={onArchive} aria-label={`Archive ${entry.name}`}>
            Archive
          </Button>
        </span>
      )}
    </li>
  );
}

/** The Manager's Country picker: the choice lives in the URL, so the page renders that catalog. */
function CountryFilter({ country }: { country: Country }) {
  const router = useRouter();
  const queryWith = useQueryWith();
  const id = useId();
  return (
    <div className="flex items-center gap-2">
      <label htmlFor={id} className="text-label font-medium text-ink-secondary">
        Country
      </label>
      <select
        id={id}
        value={country}
        // A new Country opens on its first Carrier: `carrier` is dropped; `archived` is kept.
        onChange={(event) => router.push(queryWithout(queryWith("country", event.target.value), "carrier"))}
        className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
      >
        {COUNTRIES.map((c) => (
          <option key={c.value} value={c.value}>
            {c.label}
          </option>
        ))}
      </select>
    </div>
  );
}
