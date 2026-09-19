"use client";

import { useState } from "react";
import type { RequestListItem, SimCardFlavorValue } from "@/lib/api/types";
import { NewSimCardFields } from "./new-sim-card-fields";
import { StockSimCardPicker } from "./stock-unit-pickers";
import type { CompletionBodyBuilder, CompletionFormProps } from "./types";

/**
 * Completing a Replace SIM Request (replace-requests ticket AC: "Completing a Replace SIM asks
 * for the new SIM Card's details, defaulted from the old one's; the old SIM Card is retired and
 * the new one is installed where the old one was"). Registered against `REPLACE_SIM` in
 * `registry.tsx`. Unlike Provision SIM, there is no "requested" Carrier/flavor/Plan stored on the
 * Request at submission — Replace SIM only ever names the SIM Card to replace (spec.md Solution's
 * table) — so this is always the one full-form shape, its Carrier/flavor/Plan defaulted from that
 * old SIM Card's own current values (looked up in `activeSimCards` by `request.targetSimCardId`),
 * never a read-only summary the way Provision SIM's new-style path is. A default is only applied
 * when it's still usable today (the old Carrier/Plan hasn't since been archived) — otherwise the
 * picker starts blank rather than pre-selecting a choice it can no longer offer.
 *
 * <p>fulfil-from-stock ticket: may instead name a Stock SIM Card matching the *old* SIM Card's own
 * Carrier, flavor and Plan — there's no "requested" Carrier/flavor/Plan on this type's Request to
 * match against the way Provision SIM's new-style path has, so the unit being replaced stands in
 * for "the Request" the ticket AC means here (the same unit this form's own defaults come from).
 */
export function ReplaceSimCompletion({
  request,
  carriers,
  carriersHref,
  currency,
  activeSimCards,
  stockSimCards = [],
  disabled,
}: CompletionFormProps) {
  const oldSimCard = activeSimCards.find((sim) => sim.id === request.targetSimCardId);
  const oldCarrier = oldSimCard?.carrierId
    ? carriers.find((carrier) => carrier.id === oldSimCard.carrierId && carrier.archivedAt === null)
    : undefined;
  const oldPlanStillActive =
    oldCarrier !== undefined &&
    oldSimCard?.postpaidPlanId !== undefined &&
    oldCarrier.postpaidPlans.some((plan) => plan.id === oldSimCard.postpaidPlanId && plan.archivedAt === null);

  const [carrierId, setCarrierId] = useState(oldCarrier?.id ?? "");
  const [flavor, setFlavor] = useState<SimCardFlavorValue>(oldSimCard?.flavor ?? "PREPAID");
  const [postpaidPlanId, setPostpaidPlanId] = useState(oldPlanStillActive ? (oldSimCard!.postpaidPlanId as string) : "");
  const [fulfillFromStockId, setFulfillFromStockId] = useState("");

  const matchingStock = stockSimCards.filter(
    (unit) =>
      (unit.carrierId ?? null) === (oldSimCard?.carrierId ?? null) &&
      unit.flavor === (oldSimCard?.flavor ?? null) &&
      (oldSimCard?.flavor !== "POSTPAID" || (unit.postpaidPlanId ?? null) === (oldSimCard?.postpaidPlanId ?? null)),
  );

  return (
    <>
      <p className="text-[11px] text-ink-mute">
        Retires <span className="font-medium text-ink-secondary">{request.targetSimCardNumber}</span>; the new SIM
        Card takes its Smartphone slot, if any.
      </p>
      <StockSimCardPicker
        matchingStock={matchingStock}
        value={fulfillFromStockId}
        onChange={setFulfillFromStockId}
        disabled={disabled}
      />
      {fulfillFromStockId ? null : (
        <NewSimCardFields
          carriers={carriers}
          carriersHref={carriersHref}
          currency={currency}
          carrierId={carrierId}
          onCarrierChange={(id) => {
            setCarrierId(id);
            setPostpaidPlanId("");
          }}
          flavor={flavor}
          onFlavorChange={setFlavor}
          postpaidPlanId={postpaidPlanId}
          onPostpaidPlanChange={setPostpaidPlanId}
          disabled={disabled}
        />
      )}
    </>
  );
}

/** @see CompletionBodyBuilder */
export const buildReplaceSimCompletionBody: CompletionBodyBuilder = (
  _request: RequestListItem,
  formData: FormData,
) => {
  const fulfillFromStockSimCardId = String(formData.get("fulfillFromStockSimCardId") ?? "");
  if (fulfillFromStockSimCardId) {
    return { fulfillFromStockSimCardId };
  }
  return {
    // replace-requests ticket: the new SIM Card's own details, defaulted from the old one's by
    // `ReplaceSimCompletion` — Replace SIM has no "requested" fields on the Request itself the way
    // Provision SIM does, so this is always the full form, never a narrow read-only summary.
    newSimCard: {
      number: String(formData.get("number")),
      carrierId: String(formData.get("carrierId")),
      flavor: formData.get("flavor"),
      postpaidPlanId: formData.get("flavor") === "POSTPAID" ? String(formData.get("postpaidPlanId")) : undefined,
    },
  };
};
