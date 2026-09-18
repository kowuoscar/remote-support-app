"use client";

import Link from "next/link";
import { useId } from "react";
import { Money } from "@/components/ui/money";
import { cn } from "@/lib/cn";
import { pickerSizes, type PickerSize } from "@/components/fleet/picker-sizes";
import type { CatalogCarrierItem } from "@/lib/api/types";

/**
 * The Postpaid Plan a new Postpaid SIM names (postpaid-sim-plan ticket): the active Plans of the
 * Carrier already chosen, never an archived one. The Plan sets the SIM Card's monthly fee, which
 * is shown here and never typed — the price is copied at creation, so a later Plan price change
 * leaves the SIM Card alone. Mirrors {@link CarrierPicker}: a FormData field (`name`) or a
 * controlled one (`value`/`onChange`), and a pointer to the Carriers page rather than an empty list.
 */
export function PostpaidPlanPicker({
  carrier,
  currency,
  carriersHref,
  name,
  value,
  onChange,
  disabled,
  size = "md",
}: {
  carrier: CatalogCarrierItem | undefined;
  currency: string;
  carriersHref: string;
  name?: string;
  value?: string;
  onChange?: (planId: string) => void;
  disabled?: boolean;
  size?: PickerSize;
}) {
  const id = useId();
  const styles = pickerSizes[size];
  const active = (carrier?.postpaidPlans ?? [])
    .filter((plan) => plan.archivedAt === null)
    .sort((a, b) => a.price - b.price);
  const chosen = active.find((plan) => plan.id === value);

  if (!carrier) {
    return (
      <div className={cn("flex flex-col font-medium text-ink-secondary", styles.label)}>
        Postpaid plan
        <p className={cn("font-normal text-ink-mute", styles.note)}>Choose a carrier first.</p>
      </div>
    );
  }

  if (active.length === 0) {
    return (
      <div className={cn("flex flex-col font-medium text-ink-secondary", styles.label)}>
        Postpaid plan
        <p className={cn("font-normal text-ink-mute", styles.note)}>
          No active plan for this carrier yet.{" "}
          <Link
            href={carriersHref}
            className="font-medium text-ink underline decoration-hairline-strong underline-offset-2 hover:decoration-ink"
          >
            Add one on the Carriers page
          </Link>
        </p>
      </div>
    );
  }

  return (
    <div className={cn("flex flex-col font-medium text-ink-secondary", styles.label)}>
      <label htmlFor={id} className="flex flex-col gap-[inherit]">
        Postpaid plan
        <select
          id={id}
          name={name}
          required
          disabled={disabled}
          {...(value === undefined
            ? { defaultValue: "" }
            : { value, onChange: (event) => onChange?.(event.target.value) })}
          className={cn(
            "border border-hairline-strong bg-canvas text-ink focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70",
            styles.select,
          )}
        >
          <option value="" disabled>
            Choose a plan
          </option>
          {active.map((plan) => (
            <option key={plan.id} value={plan.id} translate="no">
              {plan.name}
            </option>
          ))}
        </select>
      </label>
      <p className={cn("flex items-baseline justify-between gap-2 pt-1 font-normal", styles.note)}>
        <span className="text-ink-mute">Monthly fee</span>
        {chosen ? (
          <Money amount={chosen.price} currency={currency} />
        ) : (
          <span className="text-ink-mute">Set by the plan</span>
        )}
      </p>
    </div>
  );
}
