import { formatMoney } from "@/lib/format";
import { cn } from "@/lib/cn";

export function Money({
  amount,
  currency,
  emphasize = false,
  className,
}: {
  amount: number;
  currency: string;
  emphasize?: boolean;
  className?: string;
}) {
  const negative = amount < 0;
  return (
    <span
      className={cn(
        "tnum",
        emphasize ? "text-primary font-semibold" : "text-ink",
        negative && !emphasize && "text-danger",
        className,
      )}
    >
      {formatMoney(amount, currency)}
    </span>
  );
}
