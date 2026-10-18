"use client";

import { useEffect, useId, useRef, type MouseEvent, type ReactNode, type Ref } from "react";
import Link from "next/link";
import { PAGE_PADDING, PAGE_WIDTH } from "@/components/app-shell/surface-page";
import { cn } from "@/lib/cn";

export type SideListItem = {
  id: string;
  /** Line 1, left: one line, cut with "…", the full name in `title`. */
  name: string;
  /** Line 2, left, muted (the Country on a name collision). */
  detail?: string;
  /** Line 1, right; never shrinks. */
  badge?: ReactNode;
  /** Line 2, right, or line 1 when there is no badge. */
  mark?: ReactNode;
  /** Line 2, left, after `detail` and last in DOM order (Client Invoices' readiness). */
  status?: ReactNode;
  /** The name in `ink-mute` (an archived Carrier). */
  muted?: boolean;
};

/**
 * A column of links beside the detail it selects (side-lists-and-tabs spec, "The side list,
 * shared"; first built for Client Invoices). Presentational: the page owns the selection. A plain
 * click is taken for `onSelect`; a modified click (Cmd/Ctrl/Shift/Alt) and the middle button fall
 * through to the link's `href`, so "open in a new tab" works. `items` arrives in column order.
 * The link's accessible name is its DOM text in the order name, detail, badge, mark, status; the
 * grid places each part.
 */
export function SideList({
  label,
  items,
  selectedId,
  hrefFor,
  onSelect,
}: {
  /** The landmark's name and visible heading: "Clients", "Carriers", "Contracts". */
  label: string;
  items: SideListItem[];
  selectedId: string;
  hrefFor: (id: string) => string;
  onSelect: (id: string) => void;
}) {
  const headingId = useId();
  // On arrival, bring the selected row into the column's own viewport. Scrolls the column, never the document (scrollIntoView would move both).
  const navRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const nav = navRef.current;
    const row = nav?.querySelector<HTMLElement>("[aria-current]");
    if (!nav || !row) return;
    const navBox = nav.getBoundingClientRect();
    const rowBox = row.getBoundingClientRect();
    if (rowBox.bottom > navBox.bottom) nav.scrollTop += rowBox.bottom - navBox.bottom;
    else if (rowBox.top < navBox.top) nav.scrollTop -= navBox.top - rowBox.top;
    // Arrival only: a later pick is of a row the viewer can already see.
  }, []);

  function onClick(event: MouseEvent<HTMLAnchorElement>, id: string) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    onSelect(id);
  }

  return (
    <nav
      ref={navRef}
      aria-labelledby={headingId}
      className="min-h-0 flex-1 overflow-y-auto border-r border-hairline bg-canvas px-3 py-5"
    >
      <h2 id={headingId} className="px-3 pb-2 text-label font-medium text-ink-mute">
        {label}
      </h2>
      <ul className="flex flex-col gap-0.5">
        {items.map((item) => {
          const selected = item.id === selectedId;
          return (
            <li key={item.id}>
              <Link
                href={hrefFor(item.id)}
                // No prefetch: on some pages a render of the target creates a draft.
                prefetch={false}
                aria-current={selected ? "true" : undefined}
                onClick={(event) => onClick(event, item.id)}
                className={cn(
                  // Four columns: the detail and the status size to their text (cut with "…" only when the row is
                  // too narrow), so the status sits right after the detail; a 1fr spacer takes the free width and
                  // the badges size to themselves in the last. The name spans the first three. DOM order stays
                  // name, detail, badge, mark, status (the link's accessible name); the grid places them.
                  "grid grid-cols-[minmax(0,auto)_auto_minmax(0,1fr)_auto] items-center gap-x-2 gap-y-1 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  selected
                    ? "bg-primary-soft-bg text-primary-soft-text"
                    : cn("hover:bg-canvas-soft", item.muted ? "text-ink-mute" : "text-ink-secondary"),
                )}
              >
                <span className="col-span-3 col-start-1 row-start-1 truncate" title={item.name}>
                  {item.name}
                </span>
                {item.detail ? (
                  <>
                    {" "}
                    <span className="col-start-1 row-start-2 truncate text-label-sm text-ink-mute">{item.detail}</span>
                  </>
                ) : null}
                {item.badge ? (
                  <>
                    {" "}
                    <span className="col-start-4 row-start-1 flex justify-end">{item.badge}</span>
                  </>
                ) : null}
                {item.mark ? (
                  <>
                    {" "}
                    <span className={cn("col-start-4 flex justify-end", item.badge ? "row-start-2" : "row-start-1")}>
                      {item.mark}
                    </span>
                  </>
                ) : null}
                {item.status ? (
                  <>
                    {" "}
                    <span
                      className={cn(
                        "row-start-2 flex min-w-max items-center gap-1 text-label-sm text-ink-mute",
                        item.detail ? "col-start-2 -ml-1" : "col-span-3 col-start-1",
                      )}
                    >
                      {item.detail ? (
                        <>
                          <span aria-hidden="true">·</span>{" "}
                        </>
                      ) : null}
                      {item.status}
                    </span>
                  </>
                ) : null}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/**
 * The page body around a `SideList`; used with `SurfacePage`'s `bleed`. At `xl` (1280px) the column
 * sits flush to the rail with the detail area beside it, in the shell's padding and `max-w-6xl` cap,
 * aligned left. Below `xl` it is the shell's padded, centred page: `lead`, `header`, `dropdown`, then
 * the detail. Both the column and the dropdown are rendered and CSS shows one, so no script measures
 * the window. A `null` list renders no column at any width, and `header` then stays on top of the
 * detail at every width.
 */
export function SideListLayout({
  list,
  header,
  lead,
  dropdown,
  detailRef,
  children,
}: {
  /** The `SideList`; null renders no column at any width. */
  list: ReactNode | null;
  /** Above the list at `xl`, above the dropdown below `xl`. */
  header?: ReactNode;
  /** Above the dropdown at every width (Client Invoices' "Sent back to you" section). */
  lead?: ReactNode;
  /** Shown below `xl` only. */
  dropdown: ReactNode | null;
  /** The detail area, for a page that scrolls back to its top on a pick. */
  detailRef?: Ref<HTMLDivElement>;
  children: ReactNode;
}) {
  return (
    <div className="xl:flex xl:items-start">
      {list ? (
        <div
          // top-14 / 3.5rem is the 56px sticky top bar; a calc, not a token, because the column's
          // height is the viewport's minus that bar.
          className="hidden w-64 shrink-0 flex-col xl:sticky xl:top-14 xl:flex xl:h-[calc(100dvh-3.5rem)]"
        >
          {header ? <div className="shrink-0 border-r border-hairline bg-canvas px-6 pt-5">{header}</div> : null}
          {list}
        </div>
      ) : null}
      <div ref={detailRef} className={cn(PAGE_PADDING, "xl:flex-1")}>
        <div className={cn(PAGE_WIDTH, "xl:mx-0")}>
          <div className="flex flex-col gap-4">
            {lead}
            {header ? <div className={list ? "xl:hidden" : undefined}>{header}</div> : null}
            {dropdown ? <div className="xl:hidden">{dropdown}</div> : null}
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}
