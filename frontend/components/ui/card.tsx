import type { HTMLAttributes } from "react";
import { cn } from "@/lib/cn";

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "rounded-xl border border-hairline bg-canvas-raised",
        className,
      )}
      {...props}
    />
  );
}

export function Panel({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  // Elevated layer — modals, popovers, dropdown panels. Soft single-direction
  // shadow lives here only, per the direction's surface rule.
  return (
    <div
      className={cn(
        "rounded-xl border border-hairline bg-canvas-overlay shadow-elevated",
        className,
      )}
      {...props}
    />
  );
}
