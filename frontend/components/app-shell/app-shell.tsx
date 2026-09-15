import type { ReactNode } from "react";
import { NavRail } from "@/components/app-shell/nav-rail";
import type { Surface } from "@/lib/nav";

/**
 * Layout-level chrome: the left rail plus the column every page renders its
 * own TopBar + content + DemoNote into (see `SurfacePage`). Split this way
 * because Next's layout wraps every page under one route, but the page
 * title in the TopBar changes per page.
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
    <div className="flex h-dvh min-h-dvh bg-canvas">
      <NavRail surface={surface} roleLabel={roleLabel} />
      <div className="flex min-w-0 flex-1 flex-col">{children}</div>
    </div>
  );
}
