"use client";

import { useEffect, useRef, useState } from "react";
import type { FocusEvent, KeyboardEvent } from "react";
import { useRouter } from "next/navigation";

const ITEM_COUNT = 2;

/**
 * The top bar's viewer identity chip, now the trigger for this design system's first actions
 * menu (self-service-password-change spec, `## Design direction`). Its open panel is pinned to
 * `ContractSwitcher`'s appearance only — rounded-xl, canvas-overlay, hairline border,
 * shadow-elevated, items at the 8px control radius, `ink-secondary` at rest, `canvas-soft` on
 * hover, no indigo fill — but none of `ContractSwitcher`'s code is reused: that component has no
 * focus management, mounts its dismissal listeners unconditionally, and arrow keys do nothing in
 * it. This component owns its own behaviour instead: focus moves into the panel on open and back
 * to the trigger on close by any means (Escape, an outside pointer press, or activating an item),
 * arrow keys cycle between the two items, and every dismissal listener is bound only while the
 * menu is open — closed, an outside press or Escape has no effect on the page.
 *
 * "Change password" is a real `menuitem`, keyboard-reachable, but inert until
 * `change-password-dialog` swaps `handleChangePassword` for one that opens the dialog; today
 * activating it only closes the menu, the same as Escape. "Log out" absorbs the standalone
 * `LogoutButton` this component replaces, keeping its shipped accessible name "Log out".
 */
export function ViewerMenu({ viewerLabel }: { viewerLabel: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  // Focus into the panel on open; the outside-pointer-press listener is bound only while open —
  // closed, this effect adds nothing and a press anywhere has no menu to dismiss.
  useEffect(() => {
    if (!open) return;
    itemRefs.current[0]?.focus();

    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        // Prevent the pointer's own default action (focusing whatever it landed on) so the
        // AC's "returns focus to the chip trigger" holds for an outside press too, not only
        // Escape and activating an item.
        event.preventDefault();
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  function closeMenu() {
    setOpen(false);
    triggerRef.current?.focus();
  }

  function onPanelKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      closeMenu();
      return;
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const current = itemRefs.current.findIndex((item) => item === document.activeElement);
      const delta = event.key === "ArrowDown" ? 1 : -1;
      const next = (current + delta + ITEM_COUNT) % ITEM_COUNT;
      itemRefs.current[next]?.focus();
    }
  }

  // A menu is not a trap (story 16): tabbing focus out of the panel entirely closes it, rather
  // than leaving an open panel behind while focus has already moved elsewhere on the page.
  function onContainerBlur(event: FocusEvent<HTMLDivElement>) {
    if (!containerRef.current?.contains(event.relatedTarget as Node)) {
      setOpen(false);
    }
  }

  function handleChangePassword() {
    // Inert until change-password-dialog wires the real handler — closing here is deliberate,
    // not a stub left half-built: it behaves exactly like Escape (see ticket `## Context`).
    closeMenu();
  }

  async function handleLogout() {
    closeMenu();
    try {
      await fetch("/api/session", { method: "DELETE" });
    } finally {
      router.push("/login");
      router.refresh();
    }
  }

  return (
    <div ref={containerRef} onBlur={onContainerBlur} className="relative inline-block text-left">
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="inline-flex max-w-[45vw] items-center gap-1 truncate rounded-full border border-hairline bg-canvas-soft px-3 py-1 text-[12px] text-ink-mute transition-colors hover:border-hairline-strong hover:text-ink sm:max-w-none"
      >
        <span className="truncate">{viewerLabel}</span>
      </button>
      {open ? (
        <div
          role="menu"
          onKeyDown={onPanelKeyDown}
          className="absolute right-0 z-30 mt-2 w-56 max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl border border-hairline bg-canvas-overlay py-1 shadow-elevated"
        >
          <button
            ref={(element) => {
              itemRefs.current[0] = element;
            }}
            type="button"
            role="menuitem"
            onClick={handleChangePassword}
            className="flex w-full items-center rounded-lg px-3 py-2 text-left text-sm font-medium text-ink-secondary transition-colors hover:bg-canvas-soft"
          >
            Change password
          </button>
          <button
            ref={(element) => {
              itemRefs.current[1] = element;
            }}
            type="button"
            role="menuitem"
            onClick={handleLogout}
            className="flex w-full items-center rounded-lg px-3 py-2 text-left text-sm font-medium text-ink-secondary transition-colors hover:bg-canvas-soft"
          >
            Log out
          </button>
        </div>
      ) : null}
    </div>
  );
}
