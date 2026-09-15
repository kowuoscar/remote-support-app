"use client";

import { useEffect, useRef, useState } from "react";
import { IconChevronDown, IconContracts } from "@/components/icons";
import { cn } from "@/lib/cn";

export interface ContractOption {
  id: string;
  label: string;
  meta?: string;
  currency?: string;
}

/**
 * A dropdown, never a tab: scopes the Requests / Fleet / Client Invoices
 * pages (Agent Console) or the dashboard and lists (Client Portal, when the
 * Client holds more than one Contract) to a single Contract at a time.
 */
export function ContractSwitcher({
  contracts,
  value,
  onChange,
}: {
  contracts: ContractOption[];
  value: string;
  onChange: (id: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const current = contracts.find((c) => c.id === value) ?? contracts[0];

  useEffect(() => {
    function onPointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, []);

  if (contracts.length <= 1) {
    return (
      <div className="inline-flex items-center gap-2 rounded-lg border border-hairline bg-canvas-soft px-3 py-2 text-sm text-ink-secondary">
        <IconContracts className="h-4 w-4 text-ink-mute" />
        {current?.label ?? "No contract"}
      </div>
    );
  }

  return (
    <div ref={rootRef} className="relative inline-block text-left">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className="inline-flex min-w-[220px] items-center justify-between gap-2 rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink transition-colors hover:bg-canvas-soft"
      >
        <span className="flex items-center gap-2 truncate">
          <IconContracts className="h-4 w-4 shrink-0 text-ink-mute" />
          <span className="truncate font-medium">{current?.label}</span>
        </span>
        <IconChevronDown
          className={cn("h-4 w-4 shrink-0 text-ink-mute transition-transform", open && "rotate-180")}
        />
      </button>
      {open ? (
        <div
          role="listbox"
          className="absolute left-0 z-30 mt-1.5 w-72 overflow-hidden rounded-xl border border-hairline bg-canvas-overlay py-1 shadow-elevated"
        >
          {contracts.map((contract) => (
            <button
              key={contract.id}
              type="button"
              role="option"
              aria-selected={contract.id === value}
              onClick={() => {
                onChange(contract.id);
                setOpen(false);
              }}
              className={cn(
                "flex w-full flex-col items-start gap-0.5 px-3.5 py-2.5 text-left text-sm transition-colors hover:bg-canvas-soft",
                contract.id === value && "bg-primary-soft-bg",
              )}
            >
              <span className={cn("font-medium", contract.id === value ? "text-primary-soft-text" : "text-ink")}>
                {contract.label}
              </span>
              {contract.meta ? (
                <span className="text-[12px] text-ink-mute">{contract.meta}</span>
              ) : null}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
