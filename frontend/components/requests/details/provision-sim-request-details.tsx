"use client";

import { useState } from "react";
import { CarrierPicker } from "@/components/fleet/carrier-picker";
import { PostpaidPlanPicker } from "@/components/fleet/postpaid-plan-picker";
import { SmartphonePicker } from "@/components/requests/smartphone-picker";
import type { SimCardFlavorValue } from "@/lib/api/types";
import type { RequestDetailsProps } from "./types";

/**
 * A Provision SIM Request's own details (request-types-and-flow spec's table: "Provision SIM —
 * Required: flavor, Carrier, and a Postpaid Plan of that Carrier when postpaid; Optional: target
 * Smartphone"; provision-request-details ticket). Registered against `PROVISION_SIM` in
 * `registry.tsx`. Mirrors `TopupRequestDetails`'s "Plan follows the Carrier" pattern: the Postpaid
 * Plan picker only ever lists the chosen Carrier's own active Plans.
 */
export function ProvisionSimRequestDetails({
  smartphones,
  carriers,
  carriersHref,
  currency,
  disabled,
}: RequestDetailsProps) {
  const [carrierId, setCarrierId] = useState("");
  const [flavor, setFlavor] = useState<SimCardFlavorValue>("PREPAID");
  const [postpaidPlanId, setPostpaidPlanId] = useState("");

  return (
    <>
      <CarrierPicker
        carriers={carriers}
        carriersHref={carriersHref}
        name="requestedCarrierId"
        value={carrierId}
        onChange={(id) => {
          setCarrierId(id);
          setPostpaidPlanId("");
        }}
        disabled={disabled}
      />
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Flavor
        <select
          name="requestedFlavor"
          required
          value={flavor}
          onChange={(event) => setFlavor(event.target.value as SimCardFlavorValue)}
          disabled={disabled}
          className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70"
        >
          <option value="PREPAID">Prepaid</option>
          <option value="POSTPAID">Postpaid</option>
        </select>
      </label>
      {flavor === "POSTPAID" ? (
        <PostpaidPlanPicker
          name="requestedPostpaidPlanId"
          carrier={carriers.find((carrier) => carrier.id === carrierId)}
          currency={currency}
          carriersHref={carriersHref}
          value={postpaidPlanId}
          onChange={setPostpaidPlanId}
          disabled={disabled}
        />
      ) : null}
      <SmartphonePicker
        smartphones={smartphones}
        label="Target Smartphone"
        name="targetSmartphoneId"
        required={false}
        disabled={disabled}
      />
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Description <span className="font-normal text-ink-mute">(optional)</span>
        <textarea
          name="description"
          rows={2}
          disabled={disabled}
          placeholder="Anything the Agent should know"
          className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
        />
      </label>
    </>
  );
}
