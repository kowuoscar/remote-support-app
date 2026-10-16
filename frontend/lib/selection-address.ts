"use client";

import { useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";

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
 * no entry, no server render of our own, no `router.push/replace`, which would re-run the page's reads.
 *
 * The state argument is null so that Next syncs its router state to the new URL (it dispatches a
 * restore): a later `router.refresh()` then keeps the query string instead of putting the old
 * address back. Passing the current state through would skip that sync.
 *
 * The restore has two costs, both paid by a refresh a mutation has just asked for. Next drops the
 * refresh that is running when a restore arrives. And a refresh asked for in the same tick as the
 * write is lost anyway: Next stores a refresh's pending result as the router's React state, and the
 * restore, settling a microtask later, overwrites that state, so the refresh lands nowhere (the row
 * kept its old plan until a reload). A refresh that may be in flight is therefore asked for again,
 * but only once Next has applied the restore, which shows as `useSearchParams` following the new
 * address.
 */
export function useSelectionAddress(): (params: Record<string, string | null>) => void {
  const router = useRouter();
  const search = useSearchParams().toString();
  const refreshOnceRestored = useRef(false);

  useEffect(() => {
    if (!refreshOnceRestored.current) return;
    refreshOnceRestored.current = false;
    refreshPage(router);
  }, [search, router]);

  return (params) => {
    const url = new URL(window.location.href);
    for (const [key, value] of Object.entries(params)) {
      if (value === null) url.searchParams.delete(key);
      else url.searchParams.set(key, value);
    }
    // An address that does not change is not written: Next would restore it all the same.
    if (url.search === window.location.search) return;
    refreshOnceRestored.current = Date.now() - lastRefreshAt < REFRESH_MAY_BE_IN_FLIGHT_MS;
    window.history.replaceState(null, "", `${url.pathname}${url.search}`);
  };
}
