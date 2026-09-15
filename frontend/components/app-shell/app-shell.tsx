import type { ReactNode } from "react";
import { NavRail } from "@/components/app-shell/nav-rail";
import { MobileNavProvider } from "@/lib/mobile-nav";
import type { Surface } from "@/lib/nav";

/**
 * Layout-level chrome: the left rail plus the column every page renders its
 * own TopBar + content + DemoNote into (see `SurfacePage`). Split this way
 * because Next's layout wraps every page under one route, but the page
 * title in the TopBar changes per page. MobileNavProvider wraps both so a
 * page's TopBar (rendered inside `children`) can open the NavRail drawer.
 */
export function ShellFrame({
  surface,
  roleLabel,
  children,
}: {
  surface: Surface;
  roleLabel: string;
  children: ReactNode;
}) {
  return (
    <MobileNavProvider>
      {/*
        min-h-dvh, not h-dvh: the shell floors at one viewport but grows
        with content. The rail and TopBar are sticky (not a height-capped
        flex column with an internally-scrolling <main>) so the *document*
        scrolls — the rail and TopBar stay pinned by position: sticky, and a
        full-page screenshot captures everything, not just the first
        viewport's worth of an internal scroll container.
      */}
      <div className="flex min-h-dvh bg-canvas">
        <NavRail surface={surface} roleLabel={roleLabel} />
        <div className="flex min-w-0 flex-1 flex-col">{children}</div>
      </div>
    </MobileNavProvider>
  );
}
