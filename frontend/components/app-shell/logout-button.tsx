"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { IconLogOut } from "@/components/icons";

/**
 * Same square icon-button treatment as ThemeToggle, so the two sit together in the top bar
 * without a new control shape. Clears the session cookie server-side, then routes to /login —
 * router.refresh() re-runs the layout tree so no stale authenticated content flashes.
 */
export function LogoutButton() {
  const router = useRouter();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await fetch("/api/session", { method: "DELETE" });
    } finally {
      router.push("/login");
      router.refresh();
    }
  }

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={loggingOut}
      aria-label="Log out"
      className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-hairline text-ink-mute transition-colors hover:border-hairline-strong hover:text-ink active:bg-canvas-soft disabled:opacity-50"
    >
      <IconLogOut className="h-[18px] w-[18px]" />
    </button>
  );
}
