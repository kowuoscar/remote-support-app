"use client";

import { useId, useRef, type KeyboardEvent, type ReactNode } from "react";
import { cn } from "@/lib/cn";

export type TabItem = {
  id: string;
  label: string;
  /** Shown beside the label in `ink-mute` `tnum`. */
  count?: number;
  icon?: ReactNode;
  /** The panel's content; only the selected tab's is rendered. */
  panel: ReactNode;
};

/**
 * A WAI-ARIA tablist with its panel (side-lists-and-tabs spec, "Tabs, shared"): tabs are buttons
 * with a roving `tabindex`; Left and Right move and wrap, Home and End jump, and activation is
 * automatic on focus because every panel's data is already on the page. Only the selected panel is
 * rendered. The look is the segmented strip the Requests status filter uses. It switches between
 * the tables of one record; it never scopes a Contract. `toolbar` sits at the right of the strip.
 */
export function Tabs({
  label,
  tabs,
  selectedId,
  onSelect,
  toolbar,
  headerClassName,
}: {
  /** The tablist's accessible name. */
  label: string;
  tabs: TabItem[];
  selectedId: string;
  onSelect: (id: string) => void;
  toolbar?: ReactNode;
  headerClassName?: string;
}) {
  const baseId = useId();
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({});
  const selected = tabs.find((tab) => tab.id === selectedId) ?? tabs[0];

  function move(index: number) {
    const next = tabs[(index + tabs.length) % tabs.length];
    if (!next) return;
    onSelect(next.id);
    tabRefs.current[next.id]?.focus();
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    switch (event.key) {
      case "ArrowRight":
        move(index + 1);
        break;
      case "ArrowLeft":
        move(index - 1);
        break;
      case "Home":
        move(0);
        break;
      case "End":
        move(tabs.length - 1);
        break;
      default:
        return;
    }
    event.preventDefault();
  }

  return (
    <>
      <div className={cn("flex flex-wrap items-center gap-x-3 gap-y-2", headerClassName)}>
        <div
          role="tablist"
          aria-label={label}
          className="inline-flex items-center gap-1 rounded-lg border border-hairline bg-canvas-soft p-1"
        >
          {tabs.map((tab, index) => {
            const isSelected = tab.id === selected?.id;
            return (
              <button
                key={tab.id}
                ref={(node) => {
                  tabRefs.current[tab.id] = node;
                }}
                id={`${baseId}-tab-${tab.id}`}
                role="tab"
                type="button"
                aria-selected={isSelected}
                aria-controls={`${baseId}-panel-${tab.id}`}
                tabIndex={isSelected ? 0 : -1}
                onClick={() => onSelect(tab.id)}
                onKeyDown={(event) => onKeyDown(event, index)}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-label font-medium transition-colors",
                  isSelected ? "bg-canvas text-ink shadow-sm" : "text-ink-mute hover:text-ink",
                )}
              >
                {tab.icon}
                <span>{tab.label}</span>
                {tab.count !== undefined ? (
                  <>
                    {" "}
                    <span className="tnum text-ink-mute">{tab.count}</span>
                  </>
                ) : null}
              </button>
            );
          })}
        </div>
        {toolbar ? <div className="ml-auto">{toolbar}</div> : null}
      </div>
      {selected ? (
        <div
          role="tabpanel"
          id={`${baseId}-panel-${selected.id}`}
          aria-labelledby={`${baseId}-tab-${selected.id}`}
        >
          {selected.panel}
        </div>
      ) : null}
    </>
  );
}
