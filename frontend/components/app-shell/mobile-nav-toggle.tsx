"use client";

import { useMobileNav } from "@/lib/mobile-nav";

export function MobileNavToggle() {
  const { toggle } = useMobileNav();
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label="Open navigation"
      className="-ml-1.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-ink-mute hover:bg-canvas-soft hover:text-ink md:hidden"
    >
      <svg viewBox="0 0 24 24" fill="none" className="h-5 w-5" aria-hidden="true">
        <path
          d="M4 7h16M4 12h16M4 17h16"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinecap="round"
        />
      </svg>
    </button>
  );
}
