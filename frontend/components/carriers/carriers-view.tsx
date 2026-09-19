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

const LIST_FIELD = { "topup-options": "topupOptions", "postpaid-plans": "postpaidPlans" } as const;

/** What "Show archived" would reveal: archived Carriers, and archived entries of active ones. */
function countArchived(carriers: CatalogCarrierItem[]): number {
  return carriers.reduce(
    (count, carrier) =>
      carrier.archivedAt !== null
        ? count + 1
        : count +
          carrier.topupOptions.filter((entry) => entry.archivedAt !== null).length +
          carrier.postpaidPlans.filter((entry) => entry.archivedAt !== null).length,
    0,
  );
}

/**
 * One Country's Carriers, maintained in place (agent-maintains-carriers ticket): the Agent's own
 * Country, or — with `countryFilter` — any Country the Company Manager picks. The page fetches
 * archived Carriers too; "Show archived" only reveals them. Both the Country and the toggle live in
 * the URL (`?country=`, `?archived=1`), so a view can be linked to. Every change refreshes the
 * server-rendered list without leaving the page. Each active Carrier lists its Topup Options and
 * Postpaid Plans with their prices (topup-options-and-postpaid-plans ticket); an archived Carrier
 * shows neither, since nothing under it can be picked or changed any more.
 */
export function CarriersView({
  country,
  catalog,
  countryFilter = false,
}: {
  country: Country;
  catalog: CarrierCatalog | null;
  countryFilter?: boolean;
}) {
  const nameDialogRef = useRef<CarrierNameDialogHandle>(null);
  const archiveDialogRef = useRef<ArchiveCarrierDialogHandle>(null);
  const offerDialogRef = useRef<CarrierOfferDialogHandle>(null);
  const archiveOfferDialogRef = useRef<ArchiveCarrierOfferDialogHandle>(null);
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryWith = useQueryWith();
  // Seeded from the URL, then flipped at once on click; the URL follows behind, so the checkbox
  // never waits on the navigation to show its new state.
  const [showArchived, setShowArchived] = useState(searchParams.get("archived") === "1");
  const countryName = countryLabel(country);

  const carriers = catalog?.carriers ?? [];
  const active = carriers.filter((carrier) => carrier.archivedAt === null);
  const archivedCount = countArchived(carriers);
  const visible = showArchived ? carriers : active;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
          {countryFilter ? <CountryFilter country={country} /> : null}
          <label className="flex items-center gap-2 text-[13px] font-medium text-ink-secondary">
            <input
              type="checkbox"
              checked={showArchived}
              onChange={(event) => {
                setShowArchived(event.target.checked);
                router.replace(queryWith("archived", event.target.checked ? "1" : null), { scroll: false });
              }}
              className="h-4 w-4 rounded border-hairline-strong accent-primary"
            />
            Show archived
            {archivedCount > 0 ? <span className="tnum text-ink-mute">({archivedCount})</span> : null}
          </label>
        </div>
        <div className="flex items-center gap-3">
          <span className="tnum shrink-0 text-[13px] text-ink-mute">{active.length} active</span>
          <Button variant="primary" onClick={() => nameDialogRef.current?.openCreate()} disabled={!catalog}>
            <IconPlus className="h-4 w-4" />
            Add carrier
          </Button>
        </div>
      </div>

      {!catalog ? (
        <EmptyState
          icon={<IconAlertTriangle className="h-5 w-5" />}
          title="Couldn't load carriers"
          description="The catalog didn't load. Reload the page to try again."
        />
      ) : visible.length === 0 ? (
        <EmptyState
          icon={<IconCarrier className="h-5 w-5" />}
          title={carriers.length > 0 ? `Every carrier in ${countryName} is archived` : `No carriers in ${countryName} yet`}
          description={
            carriers.length > 0
              ? "Add the carriers you work with today, or turn on Show archived to see the old ones."
              : `Add the mobile carriers you buy SIM cards and topups from. Prices will be in ${catalog.currency}.`
          }
        />
      ) : (
        <Card>
          <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b border-hairline px-5 py-4">
            <h2 className="text-sm font-semibold text-ink">{countryName}</h2>
            <p className="text-[13px] text-ink-mute">
              Prices in <span className="tnum">{catalog.currency}</span> · shared by every agent in {countryName}
            </p>
          </div>
          <ul aria-label="Carriers" className="divide-y divide-hairline">
            {visible.map((carrier) => (
              <CarrierRow
                key={carrier.id}
                carrier={carrier}
                currency={catalog.currency}
                showArchived={showArchived}
                onRename={() => nameDialogRef.current?.openRename(carrier)}
                onArchive={() => archiveDialogRef.current?.open(carrier)}
                onAddEntry={(list) => offerDialogRef.current?.openCreate(list, carrier)}
                onEditEntry={(list, entry) => offerDialogRef.current?.openEdit(list, carrier, entry)}
                onArchiveEntry={(list, entry) => archiveOfferDialogRef.current?.open(list, carrier, entry)}
              />
            ))}
          </ul>
        </Card>
      )}

      <CarrierNameDialog ref={nameDialogRef} country={country} countryName={countryName} />
      <ArchiveCarrierDialog ref={archiveDialogRef} />
      <CarrierOfferDialog ref={offerDialogRef} currency={catalog?.currency ?? ""} />
      <ArchiveCarrierOfferDialog ref={archiveOfferDialogRef} />
    </div>
  );
}

function CarrierRow({
  carrier,
  currency,
  showArchived,
  onRename,
  onArchive,
  onAddEntry,
  onEditEntry,
  onArchiveEntry,
}: {
  carrier: CatalogCarrierItem;
  currency: string;
  showArchived: boolean;
  onRename: () => void;
  onArchive: () => void;
  onAddEntry: (list: CarrierOfferList) => void;
  onEditEntry: (list: CarrierOfferList, entry: CarrierOfferItem) => void;
  onArchiveEntry: (list: CarrierOfferList, entry: CarrierOfferItem) => void;
}) {
  const archived = carrier.archivedAt !== null;
  return (
    <li className="px-5 py-3">
      <div className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-canvas-soft text-ink-mute">
            <IconCarrier className="h-4 w-4" />
          </span>
          <div className="min-w-0">
            <h3
              translate="no"
              className={`block truncate text-sm font-medium ${archived ? "text-ink-mute" : "text-ink"}`}
            >
              {carrier.name}
            </h3>
            {archived ? (
              <span className="block text-[12px] text-ink-mute">
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

      {archived ? null : (
        <div className="mt-3 grid gap-x-8 gap-y-4 pb-1 md:grid-cols-2 md:pl-11">
          {CARRIER_OFFER_LISTS.map((list) => (
            <OfferList
              key={list}
              list={list}
              carrierName={carrier.name}
              entries={carrier[LIST_FIELD[list]].filter((entry) => showArchived || entry.archivedAt === null)}
              currency={currency}
              onAdd={() => onAddEntry(list)}
              onEdit={(entry) => onEditEntry(list, entry)}
              onArchive={(entry) => onArchiveEntry(list, entry)}
            />
          ))}
        </div>
      )}
    </li>
  );
}

/** One of a Carrier's two offer lists: each entry's name and price, and its actions. */
function OfferList({
  list,
  carrierName,
  entries,
  currency,
  onAdd,
  onEdit,
  onArchive,
}: {
  list: CarrierOfferList;
  carrierName: string;
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
        <h4 id={headingId} className="text-[12px] font-medium tracking-[0.02em] text-ink-mute">
          {copy.heading}
        </h4>
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
      </div>
      {entries.length === 0 ? (
        <p className="py-2.5 text-[13px] text-ink-mute">No {copy.heading.toLowerCase()} yet.</p>
      ) : (
        <ul aria-label={`${carrierName} ${copy.heading.toLowerCase()}`} className="divide-y divide-hairline">
          {entries.map((entry) => (
            <OfferRow
              key={entry.id}
              entry={entry}
              currency={currency}
              perMonth={copy.perMonth}
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
  onEdit,
  onArchive,
}: {
  entry: CarrierOfferItem;
  currency: string;
  perMonth: boolean;
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
        {perMonth ? <span className="text-[12px] text-ink-mute">/mo</span> : null}
      </span>
      {archived ? (
        <Badge tone="neutral">Archived</Badge>
      ) : (
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
      <label htmlFor={id} className="text-[13px] font-medium text-ink-secondary">
        Country
      </label>
      <select
        id={id}
        value={country}
        onChange={(event) => router.push(queryWith("country", event.target.value))}
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
