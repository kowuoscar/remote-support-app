import { CarrierPicker } from "@/components/fleet/carrier-picker";
import { PostpaidPlanPicker } from "@/components/fleet/postpaid-plan-picker";
import { cn } from "@/lib/cn";
import type { CatalogCarrierItem, SimCardFlavorValue } from "@/lib/api/types";

/**
 * The Carrier + Flavor + (conditionally) Postpaid Plan field group a new SIM Card needs, shared by
 * LogFeeDialog's Provision SIM fields and CreateSimCardDialog — extracted from the identical
 * copies, with no change to markup or behaviour.
 */
export function SimCardFlavorFields({
  carrierFieldName,
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
  flavorSelectClassName,
}: {
  /** Name for the underlying Carrier `<select>` — set only when a caller reads it via FormData. */
  carrierFieldName?: string;
  carriers: CatalogCarrierItem[];
  carriersHref: string;
  currency: string;
  carrierId: string;
  onCarrierChange: (carrierId: string) => void;
  flavor: SimCardFlavorValue;
  onFlavorChange: (flavor: SimCardFlavorValue) => void;
  postpaidPlanId: string;
  onPostpaidPlanChange: (postpaidPlanId: string) => void;
  disabled: boolean;
  /** Extra classes for the Flavor `<select>` — callers' disabled-state styling predates this shared component and differs slightly between them. */
  flavorSelectClassName?: string;
}) {
  return (
    <>
      <CarrierPicker
        name={carrierFieldName}
        carriers={carriers}
        carriersHref={carriersHref}
        value={carrierId}
        onChange={onCarrierChange}
        disabled={disabled}
      />
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Flavor
        <select
          required
          value={flavor}
          onChange={(event) => onFlavorChange(event.target.value as SimCardFlavorValue)}
          disabled={disabled}
          className={cn(
            "h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary",
            flavorSelectClassName,
          )}
        >
          <option value="POSTPAID">Postpaid</option>
          <option value="PREPAID">Prepaid</option>
        </select>
      </label>
      {flavor === "POSTPAID" ? (
        <PostpaidPlanPicker
          carrier={carriers.find((carrier) => carrier.id === carrierId)}
          currency={currency}
          carriersHref={carriersHref}
          value={postpaidPlanId}
          onChange={onPostpaidPlanChange}
          disabled={disabled}
        />
      ) : null}
    </>
  );
}
