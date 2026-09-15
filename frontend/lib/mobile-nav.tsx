"use client";

import { createContext, useContext, useState, type ReactNode } from "react";

interface MobileNavState {
  open: boolean;
  toggle: () => void;
  close: () => void;
}

const MobileNavContext = createContext<MobileNavState | null>(null);

/**
 * Below the `md` breakpoint the left rail becomes an off-canvas drawer
 * (structural responsive behavior, not fluid shrinking — the rail is a
 * fixed 240px everywhere it's visible). This context is the one piece of
 * state the TopBar's menu button and the NavRail drawer share; it wraps the
 * whole shell in `ShellFrame` so both a Server Component page's TopBar and
 * the Client Component NavRail can reach it.
 */
export function MobileNavProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <MobileNavContext.Provider
      value={{
        open,
        toggle: () => setOpen((v) => !v),
        close: () => setOpen(false),
      }}
    >
      {children}
    </MobileNavContext.Provider>
  );
}

export function useMobileNav(): MobileNavState {
  const ctx = useContext(MobileNavContext);
  if (!ctx) {
    throw new Error("useMobileNav must be used within a MobileNavProvider");
  }
  return ctx;
}
