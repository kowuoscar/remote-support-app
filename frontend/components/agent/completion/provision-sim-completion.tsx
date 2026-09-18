"use client";

import { useState } from "react";
import { Input } from "@/components/ui/input";
import { CarrierPicker } from "@/components/fleet/carrier-picker";
import { PostpaidPlanPicker } from "@/components/fleet/postpaid-plan-picker";
import { SIM_CARD_FLAVOR_LABEL, type SimCardFlavorValue } from "@/lib/api/types";
import type { CompletionFormProps } from "./types";

/**
 * Completing a Provision SIM Request (provision-request-details ticket AC: "Completing a
 * Provision SIM asks only for the SIM number; Carrier, flavor, Plan and monthly fee come from the
 * Request"). Registered against `PROVISION_SIM` in `registry.tsx`. A Request submitted since this
 * ticket already carries its own `requestedFlavor`/Carrier/Plan (shown here, read-only) and needs
 * only the SIM number; one from before this ticket (`requestedFlavor` absent) falls back to the
 * previous full form (ticket AC: "completes through the previous full form").
 */
export function ProvisionSimCompletion({
  request,
  carriers,
  carriersHref,
  currency,
  activeSimCards,
  disabled,
}: CompletionFormProps) {
  const [carrierId, setCarrierId] = useState("");
  const [flavor, setFlavor] = useState<SimCardFlavorValue>("PREPAID");
  const [postpaidPlanId, setPostpaidPlanId] = useState("");

  if (request.requestedFlavor) {
    return (
      <>
        <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
          New SIM number
          <Input name="simCardNumber" required disabled={disabled} className="h-7 text-[12px]" />
        </label>
        <p className="text-[11px] text-ink-mute">
          {SIM_CARD_FLAVOR_LABEL[request.requestedFlavor]}
          {request.requestedCarrierName ? ` · ${request.requestedCarrierName}` : ""}
          {request.requestedPostpaidPlanName ? ` · ${request.requestedPostpaidPlanName}` : ""}
          {request.targetSmartphoneModel ? ` · into ${request.targetSmartphoneModel}` : ""}
        </p>
      </>
    );
  }

  return (
    <>
      <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
        New SIM number
        <Input name="number" required disabled={disabled} className="h-7 text-[12px]" />
      </label>
      <CarrierPicker
        name="carrierId"
        carriers={carriers}
        carriersHref={carriersHref}
        value={carrierId}
        onChange={(id) => {
          setCarrierId(id);
          setPostpaidPlanId("");
        }}
        disabled={disabled}
        size="sm"
      />
      <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
        Flavor
        <select
          name="flavor"
          required
          value={flavor}
          onChange={(event) => setFlavor(event.target.value as SimCardFlavorValue)}
          disabled={disabled}
          className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
        >
          <option value="POSTPAID">Postpaid</option>
          <option value="PREPAID">Prepaid</option>
        </select>
      </label>
      {flavor === "POSTPAID" ? (
        <PostpaidPlanPicker
          name="postpaidPlanId"
          carrier={carriers.find((carrier) => carrier.id === carrierId)}
          currency={currency}
          carriersHref={carriersHref}
          value={postpaidPlanId}
          onChange={setPostpaidPlanId}
          disabled={disabled}
          size="sm"
        />
      ) : null}
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
