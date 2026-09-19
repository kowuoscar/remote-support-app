"use client";

import { Input } from "@/components/ui/input";
import { CarrierPicker } from "@/components/fleet/carrier-picker";
import { PostpaidPlanPicker } from "@/components/fleet/postpaid-plan-picker";
import type { CatalogCarrierItem, SimCardFlavorValue } from "@/lib/api/types";

/**
 * The full "new SIM Card" form — number, Carrier, Flavor and (Postpaid) Plan — shared by
 * ProvisionSimCompletion's legacy-Request branch and ReplaceSimCompletion, the two completion
 * forms that build a brand-new SIM Card entirely from Agent input (unlike a new-style Provision
 * SIM's read-only summary). Sized for the completion form's smaller inline controls. Extracted
 * with no change to markup, classes or field names.
 */
export function NewSimCardFields({
  carriers,
  carriersHref,
  currency,
  carrierId,
  onCarrierChange,
  flavor,
  onFlavorChange,
  postpaidPlanId,
  onPostpaidPlanChange,
  disabled,
}: {
  carriers: CatalogCarrierItem[];
  carriersHref: string;
  currency: string;
  carrierId: string;
  onCarrierChange: (carrierId: string) => void;
  flavor: SimCardFlavorValue;
  onFlavorChange: (flavor: SimCardFlavorValue) => void;
  postpaidPlanId: string;
  onPostpaidPlanChange: (postpaidPlanId: string) => void;
  disabled?: boolean;
}) {
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
        onChange={onCarrierChange}
        disabled={disabled}
        size="sm"
      />
      <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
        Flavor
        <select
          name="flavor"
          required
          value={flavor}
          onChange={(event) => onFlavorChange(event.target.value as SimCardFlavorValue)}
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
          onChange={onPostpaidPlanChange}
          disabled={disabled}
          size="sm"
        />
      ) : null}
    </>
  );
}
