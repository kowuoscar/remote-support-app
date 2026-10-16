import type { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

export function TableScroll({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("overflow-x-auto rounded-xl border border-hairline", className)}
      {...props}
    />
  );
}

/**
 * `dense` is for a table that must fit the area beside a side list (the Fleet tables): no 720px
 * minimum, so it narrows to its container instead of scrolling sideways, and 12px cell padding in
 * place of 16px.
 */
export function Table({ className, dense = false, ...props }: HTMLAttributes<HTMLTableElement> & { dense?: boolean }) {
  return (
    <table
      className={cn(
        "w-full border-collapse text-sm",
        dense ? "[&_td]:px-2.5 [&_th]:px-2.5" : "min-w-[720px]",
        className,
      )}
      {...props}
    />
  );
}

export function Thead({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn("bg-canvas-soft", className)} {...props} />;
}

export function Tbody({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn("divide-y divide-hairline", className)} {...props} />;
}

export function Tr({ className, ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr
      className={cn("transition-colors hover:bg-canvas-soft/70", className)}
      {...props}
    />
  );
}

export function Th({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={cn(
        "border-b border-hairline px-4 py-2.5 text-left text-[12px] font-medium uppercase tracking-wide text-ink-mute",
        className,
      )}
      {...props}
    />
  );
}

export function Td({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn("px-4 py-3 align-middle text-ink", className)} {...props} />;
}
