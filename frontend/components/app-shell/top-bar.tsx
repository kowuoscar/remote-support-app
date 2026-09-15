import type { ReactNode } from "react";
import Link from "next/link";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { MobileNavToggle } from "@/components/app-shell/mobile-nav-toggle";
import { IconChevronRight } from "@/components/icons";

export function TopBar({
  title,
  subtitle,
  actions,
  viewerLabel,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  viewerLabel: string;
}) {
  return (
    <header className="sticky top-0 z-10 flex h-14 shrink-0 items-center justify-between gap-4 border-b border-hairline bg-canvas px-4 sm:px-6">
      <div className="flex min-w-0 items-center gap-1">
        <MobileNavToggle />
        <div className="min-w-0">
          <h1 className="truncate text-[15px] font-semibold text-ink">{title}</h1>
          {subtitle ? (
            <p className="truncate text-[12px] text-ink-mute">{subtitle}</p>
          ) : null}
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        {actions}
        <span className="hidden items-center gap-1 rounded-full border border-hairline bg-canvas-soft px-3 py-1 text-[12px] text-ink-mute sm:inline-flex">
          {viewerLabel}
        </span>
        <ThemeToggle />
      </div>
    </header>
  );
}

export function Breadcrumb({ items }: { items: { label: string; href?: string }[] }) {
  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-[13px] text-ink-mute">
      {items.map((item, index) => (
        <span key={item.label} className="flex items-center gap-1.5">
          {index > 0 ? <IconChevronRight className="h-3.5 w-3.5" /> : null}
          {item.href ? (
            <Link href={item.href} className="hover:text-ink">
              {item.label}
            </Link>
          ) : (
            <span className="text-ink">{item.label}</span>
          )}
        </span>
      ))}
    </nav>
  );
}
