"use client";

import { useId } from "react";
import { cn } from "@/lib/cn";
import { pickerSizes, type PickerSize } from "@/components/fleet/picker-sizes";
import type { SimCardListItem } from "@/lib/api/types";

/**
 * The SIM Card a Request names — shared by every per-type details section that needs one
 * (reboot-and-topup-details ticket: Topup today, Replace SIM later reuses it). Only Active SIM
 * Cards of the Contract are ever offered (spec.md: "pick only Active units of the chosen
 * Contract's Fleet"). Mirrors {@link SmartphonePicker}/{@link CarrierPicker}: a FormData field
 * (`name`) or a controlled one (`value`/`onChange`).
 */
export function SimCardPicker({
  simCards,
  label = "SIM Card",
  name,
  value,
  onChange,
  disabled,
  size = "md",
}: {
  simCards: SimCardListItem[];
  label?: string;
  name?: string;
  value?: string;
  onChange?: (simCardId: string) => void;
  disabled?: boolean;
  size?: PickerSize;
}) {
  const id = useId();
  const styles = pickerSizes[size];
  const active = simCards.filter((sim) => sim.status === "ACTIVE");

  if (active.length === 0) {
    return (
      <div className={cn("flex flex-col font-medium text-ink-secondary", styles.label)}>
        {label}
        <p className={cn("font-normal text-ink-mute", styles.note)}>
          No Active SIM Cards on this Contract yet.
        </p>
      </div>
    );
  }

  return (
    <label htmlFor={id} className={cn("flex flex-col font-medium text-ink-secondary", styles.label)}>
      {label}
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
          Choose a SIM Card
        </option>
        {active.map((sim) => (
          <option key={sim.id} value={sim.id}>
            {sim.number}
            {sim.carrierName ? ` — ${sim.carrierName}` : ""}
          </option>
        ))}
      </select>
    </label>
  );
}
