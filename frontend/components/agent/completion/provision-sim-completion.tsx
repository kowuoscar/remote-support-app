"use client";

import { useState } from "react";
import { Input } from "@/components/ui/input";
import { SIM_CARD_FLAVOR_LABEL, type RequestListItem, type SimCardFlavorValue } from "@/lib/api/types";
import { NewSimCardFields } from "./new-sim-card-fields";
import { StockSimCardPicker } from "./stock-unit-pickers";
import type { CompletionBodyBuilder, CompletionFormProps } from "./types";

/**
 * Completing a Provision SIM Request (provision-request-details ticket AC: "Completing a
 * Provision SIM asks only for the SIM number; Carrier, flavor, Plan and monthly fee come from the
 * Request"). Registered against `PROVISION_SIM` in `registry.tsx`. A Request submitted since this
 * ticket already carries its own `requestedFlavor`/Carrier/Plan (shown here, read-only) and needs
 * only the SIM number; one from before this ticket (`requestedFlavor` absent) falls back to the
 * previous full form (ticket AC: "completes through the previous full form").
 *
 * <p>fulfil-from-stock ticket: the new-style branch may instead name a matching SIM Card from the
 * Agent's own Stock (ticket AC: "with the Request's Carrier and flavor and, for postpaid, its
 * Postpaid Plan") — offered only when at least one Stock SIM Card actually matches (ticket AC).
 * Not offered on the legacy branch: a Request that predates `requestedCarrier`/`requestedFlavor`
 * has nothing of its own to match a Stock SIM Card against.
 */
export function ProvisionSimCompletion({
  request,
  carriers,
  carriersHref,
  currency,
  activeSimCards,
  stockSimCards = [],
  disabled,
}: CompletionFormProps) {
  const [carrierId, setCarrierId] = useState("");
  const [flavor, setFlavor] = useState<SimCardFlavorValue>("PREPAID");
  const [postpaidPlanId, setPostpaidPlanId] = useState("");
  const [fulfillFromStockId, setFulfillFromStockId] = useState("");

  if (request.requestedFlavor) {
    const matchingStock = stockSimCards.filter(
      (unit) =>
        (unit.carrierId ?? null) === (request.requestedCarrierId ?? null) &&
        unit.flavor === request.requestedFlavor &&
        (request.requestedFlavor !== "POSTPAID" ||
          (unit.postpaidPlanId ?? null) === (request.requestedPostpaidPlanId ?? null)),
    );

    return (
      <>
        {fulfillFromStockId ? (
          <p className="text-[11px] text-ink-mute">
            Moves the picked SIM Card from your Stock onto this Contract, keeping its own number and fee.
          </p>
        ) : (
          <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
            New SIM number
            <Input name="simCardNumber" required disabled={disabled} className="h-7 text-[12px]" />
          </label>
        )}
        <p className="text-[11px] text-ink-mute">
          {SIM_CARD_FLAVOR_LABEL[request.requestedFlavor]}
          {request.requestedCarrierName ? ` · ${request.requestedCarrierName}` : ""}
          {request.requestedPostpaidPlanName ? ` · ${request.requestedPostpaidPlanName}` : ""}
          {request.targetSmartphoneModel ? ` · into ${request.targetSmartphoneModel}` : ""}
        </p>
        <StockSimCardPicker
          matchingStock={matchingStock}
          value={fulfillFromStockId}
          onChange={setFulfillFromStockId}
          disabled={disabled}
        />
      </>
    );
  }

  return (
    <>
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
      {activeSimCards.length > 0 ? (
        <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
          Retiring which SIM? (optional)
          <select
            name="replacesSimCardId"
            disabled={disabled}
            defaultValue=""
            className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
          >
            <option value="">None — first-time provisioning</option>
            {activeSimCards.map((sim) => (
              <option key={sim.id} value={sim.id}>
                {sim.number}
              </option>
            ))}
          </select>
        </label>
      ) : null}
    </>
  );
}

/** @see CompletionBodyBuilder */
export const buildProvisionSimCompletionBody: CompletionBodyBuilder = (
  request: RequestListItem,
  formData: FormData,
) => {
  if (request.requestedFlavor) {
    const fulfillFromStockSimCardId = String(formData.get("fulfillFromStockSimCardId") ?? "");
    if (fulfillFromStockSimCardId) {
      return { fulfillFromStockSimCardId };
    }
    return { simCardNumber: String(formData.get("simCardNumber")) };
  }

  const body: Record<string, unknown> = {
    newSimCard: {
      number: String(formData.get("number")),
      carrierId: String(formData.get("carrierId")),
      flavor: formData.get("flavor"),
      postpaidPlanId: formData.get("flavor") === "POSTPAID" ? String(formData.get("postpaidPlanId")) : undefined,
    },
  };
  const replaces = String(formData.get("replacesSimCardId") ?? "");
  if (replaces) body.replacesSimCardId = replaces;
  return body;
};
