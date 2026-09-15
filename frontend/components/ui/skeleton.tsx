import type { CSSProperties } from "react";
import { cn } from "@/lib/cn";

export function Skeleton({ className, style }: { className?: string; style?: CSSProperties }) {
  return <div className={cn("animate-pulse rounded bg-canvas-soft", className)} style={style} />;
}

export function TableSkeleton({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="rounded-xl border border-hairline">
      <div className="border-b border-hairline bg-canvas-soft px-4 py-2.5">
        <Skeleton className="h-3 w-24" />
      </div>
      <div className="divide-y divide-hairline">
        {Array.from({ length: rows }).map((_, row) => (
          <div key={row} className="flex items-center gap-6 px-4 py-3.5">
            {Array.from({ length: cols }).map((_, col) => (
              <Skeleton
                key={col}
                className="h-3.5"
                style={{ width: col === 0 ? "34%" : `${100 / cols / 1.6}%` }}
              />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
