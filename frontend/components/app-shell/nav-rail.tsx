"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { navConfigs, type Surface } from "@/lib/nav";
import { useMobileNav } from "@/lib/mobile-nav";
import { cn } from "@/lib/cn";
import { IconClose } from "@/components/icons";

/**
 * Same rail width, item styling and active/hover treatment across all three
 * surfaces — only the item list differs per role. Below `md` it becomes an
 * off-canvas drawer (structural responsive behavior): fixed, off-screen by
 * default, slid in over a backdrop when the TopBar's menu button opens it.
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
  const { open, close } = useMobileNav();

  return (
    <>
      {open ? (
        <div
          aria-hidden="true"
          onClick={close}
          className="fixed inset-0 z-40 bg-ink/40 backdrop-blur-[1px] md:hidden"
        />
      ) : null}

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-60 shrink-0 flex-col border-r border-hairline bg-canvas-soft transition-transform duration-200 ease-out",
          "md:static md:z-auto md:translate-x-0",
          open ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="flex h-14 items-center gap-2 border-b border-hairline px-5">
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-primary text-[13px] font-semibold text-on-primary">
            N
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-[13px] font-semibold leading-tight text-ink">
              {tenantName}
            </p>
            <p className="truncate text-[12px] leading-tight text-ink-mute">{roleLabel}</p>
          </div>
          <button
            type="button"
            onClick={close}
            aria-label="Close navigation"
            className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-ink-mute hover:bg-canvas hover:text-ink md:hidden"
          >
            <IconClose className="h-4 w-4" />
          </button>
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
                onClick={close}
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
    </>
  );
}
