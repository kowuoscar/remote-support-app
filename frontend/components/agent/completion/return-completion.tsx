"use client";

import { Input } from "@/components/ui/input";
import type { RequestListItem } from "@/lib/api/types";
import type { CompletionBodyBuilder, CompletionFormProps } from "./types";

/**
 * Completing a Return that cancels one or more SIM Cards (returns-and-agent-stock spec, Solution's
 * Completion table; manager-decides-return-disposition ticket AC: "Completing the Return asks the
 * Agent for an effective cancellation date for each SIM Card being cancelled, past or future, and
 * is refused without one"). Registered against `RETURN` in `registry.tsx`. A Smartphone posted to
 * the Client or the company needs no Agent input at all, so this only ever renders a field per
 * cancelled SIM Card — {@link returnCompletionNeedsOwnForm} is `false`, and this renders nothing,
 * for a Return with no cancelled unit, which is how `RequestStatusControl` decides whether to open
 * the completing form at all for a type that can never carry a Fee (unlike every other registered
 * completion piece so far).
 */
export function ReturnCompletion({ request, disabled }: CompletionFormProps) {
  const cancelledUnits = cancelledSimCardUnits(request);
  if (cancelledUnits.length === 0) {
    return null;
  }

  return (
    <>
      {cancelledUnits.map((unit) => (
        <label
          key={unit.id}
          className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary"
        >
          Cancellation date — SIM {unit.simCardNumber ?? unit.simCardId}
          <Input
            type="date"
            name={`cancellationDate-${unit.simCardId}`}
            required
            disabled={disabled}
            className="h-7 text-[12px]"
          />
        </label>
      ))}
    </>
  );
}

function cancelledSimCardUnits(request: RequestListItem) {
  return (request.returnedUnits ?? []).filter((unit) => unit.simCardId && unit.disposition === "CANCELLED");
}

/**
 * Whether completing this Return needs its own form at all — the trigger `RequestStatusControl`
 * checks alongside `requestTypeCanCarryFee` (a Return can never carry a Fee, so that check alone
 * would never open the form) before showing the Fee-less completing step just for this field.
 */
export function returnCompletionNeedsOwnForm(request: RequestListItem): boolean {
  return cancelledSimCardUnits(request).length > 0;
}

/** @see CompletionBodyBuilder */
export const buildReturnCompletionBody: CompletionBodyBuilder = (request: RequestListItem, formData: FormData) => {
  const cancelledUnits = cancelledSimCardUnits(request);
  if (cancelledUnits.length === 0) {
    return {};
  }
  return {
    simCardCancellations: cancelledUnits.map((unit) => ({
      simCardId: unit.simCardId,
      effectiveDate: String(formData.get(`cancellationDate-${unit.simCardId}`)),
    })),
  };
};
