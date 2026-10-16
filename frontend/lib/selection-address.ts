"use client";

import { useRouter } from "next/navigation";

/** A relative link to `path` carrying the given parameters; a `null` one is left out. */
export function selectionHref(path: string, params: Record<string, string | null>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null) query.set(key, value);
  }
  const search = query.toString();
  return search ? `${path}?${search}` : path;
}

/**
 * How long after `refreshPage` a refresh may still be on its way. A refresh is a server render of
 * the page, a second or so; the margin covers a slow backend. A write that lands later than this
 * has nothing to lose, and one that lands sooner costs at most a refresh too many.
 */
const REFRESH_MAY_BE_IN_FLIGHT_MS = 8_000;

let lastRefreshAt = Number.NEGATIVE_INFINITY;

/**
 * `router.refresh()` that remembers when it ran. Every mutation on a page with a selection goes
 * through it, so that {@link useSelectionAddress} knows a refresh may still be in flight.
 */
export function refreshPage(router: { refresh: () => void }): void {
  lastRefreshAt = Date.now();
  router.refresh();
}

/**
 * Writes the selection (a Contract, a Carrier, a tab) to the address with the native History API:
 * no entry, no server render, no request. Never `router.push/replace`, which would re-run the
 * page's reads.
 *
 * The state argument is null so that Next syncs its router state to the new URL (it dispatches a
 * restore): a later `router.refresh()` then keeps the query string instead of putting the old
 * address back. Passing the current state through would skip that sync. The restore, though,
 * discards whatever action Next is running, a refresh after a mutation included, so the table
 * would keep its old values until a reload. A refresh that may be in flight is therefore issued
 * again after the write.
 */
export function useSelectionAddress(): (params: Record<string, string | null>) => void {
  const router = useRouter();
  return (params) => {
    const url = new URL(window.location.href);
    for (const [key, value] of Object.entries(params)) {
      if (value === null) url.searchParams.delete(key);
      else url.searchParams.set(key, value);
    }
    window.history.replaceState(null, "", `${url.pathname}${url.search}`);
    if (Date.now() - lastRefreshAt < REFRESH_MAY_BE_IN_FLIGHT_MS) refreshPage(router);
  };
}
