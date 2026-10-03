import { Money } from "@/components/ui/money";

/** The quiet review line under an edited line's billed amount; shown on a sent invoice too. */
export function EditedLineNote({ computedAmount, currency }: { computedAmount: number; currency: string }) {
  return (
    <p className="text-label-sm text-ink-mute">
      Edited · computed <Money amount={computedAmount} currency={currency} className="text-ink-mute" />
    </p>
  );
}

/**
 * A Client Invoice line's billed amount read-only, with the "Edited · computed" note beneath it
 * when the line was edited (edit-client-invoice-lines): the Manager's detail page and a sent
 * invoice on the Agent's page share it. The Agent's draft swaps it for the editing control.
 */
export function BilledAmount({
  amount,
  computedAmount,
  edited,
  currency,
}: {
  amount: number;
  computedAmount: number | null | undefined;
  edited: boolean | null | undefined;
  currency: string;
}) {
  return (
    <div className="flex shrink-0 flex-col items-end gap-1">
      <Money amount={amount} currency={currency} />
      {edited && computedAmount != null ? <EditedLineNote computedAmount={computedAmount} currency={currency} /> : null}
    </div>
  );
}
