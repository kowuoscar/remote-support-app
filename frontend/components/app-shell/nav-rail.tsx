"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { navConfigs, type Surface } from "@/lib/nav";
import { cn } from "@/lib/cn";

/**
 * Same rail width, item styling and active/hover treatment across all three
 * surfaces — only the item list differs per role.
 */
export function NavRail({
  surface,
  roleLabel,
  tenantName = "Northwind Field Services",
}: {
  surface: Surface;
  roleLabel: string;
  tenantName?: string;
}) {
  const pathname = usePathname();
  const items = navConfigs[surface];

  return (
    <aside className="flex h-full w-60 shrink-0 flex-col border-r border-hairline bg-canvas-soft">
      <div className="flex h-14 items-center gap-2 border-b border-hairline px-5">
        <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-primary text-[13px] font-semibold text-on-primary">
          N
        </div>
        <div className="min-w-0">
          <p className="truncate text-[13px] font-semibold leading-tight text-ink">
            {tenantName}
          </p>
          <p className="truncate text-[12px] leading-tight text-ink-mute">{roleLabel}</p>
        </div>
      </div>

      <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 py-4">
        {items.map((item) => {
          const active =
            item.href === pathname ||
            (item.href !== "/manager" &&
              item.href !== "/agent" &&
              item.href !== "/client" &&
              pathname.startsWith(item.href));
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "group flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                active
                  ? "bg-primary-soft-bg text-primary-soft-text"
                  : "text-ink-secondary hover:bg-canvas hover:text-ink",
              )}
            >
              <Icon
                className={cn(
                  "h-[18px] w-[18px] shrink-0",
                  active ? "text-primary-soft-text" : "text-ink-mute group-hover:text-ink-secondary",
                )}
              />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
