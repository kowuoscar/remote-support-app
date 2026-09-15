import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/cn";

/**
 * Dashboard stat cards — the deliberate, brief-earned exception to the
 * usual hero-metric-template ban. Tabular figures, indigo accent reserved
 * for the primary number in each card, restrained everywhere else.
 */
export function StatCard({
  label,
  value,
  primary = false,
  meta,
  className,
}: {
  label: string;
  value: ReactNode;
  primary?: boolean;
  meta?: ReactNode;
  className?: string;
}) {
  return (
    <Card className={cn("flex flex-col gap-2 p-5", className)}>
      <span className="text-[13px] font-medium text-ink-mute">{label}</span>
      <span
        className={cn(
          "tnum text-[28px] font-semibold leading-tight",
          primary ? "text-primary" : "text-ink",
        )}
      >
        {value}
      </span>
      {meta ? <span className="text-[13px] text-ink-mute">{meta}</span> : null}
    </Card>
  );
}

export function StatCardSkeleton() {
  return (
    <Card className="flex flex-col gap-3 p-5">
      <div className="h-3.5 w-24 animate-pulse rounded bg-canvas-soft" />
      <div className="h-7 w-32 animate-pulse rounded bg-canvas-soft" />
      <div className="h-3 w-20 animate-pulse rounded bg-canvas-soft" />
    </Card>
  );
}
