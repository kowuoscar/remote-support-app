"use client";

import { useId } from "react";
import { cn } from "@/lib/cn";
import { pickerSizes, type PickerSize } from "@/components/fleet/picker-sizes";
import type { SmartphoneListItem } from "@/lib/api/types";

/**
 * The Smartphone a Request names — shared by every per-type details section that needs one
 * (reboot-and-topup-details ticket: Reboot today, Provision SIM's optional target and Replace
 * Smartphone later reuse this same picker rather than each rolling their own). Only Active
 * Smartphones of the Contract are ever offered (spec.md: "pick only Active units of the chosen
 * Contract's Fleet"). Mirrors {@link CarrierPicker}: a FormData field (`name`) or a controlled one
 * (`value`/`onChange`), and its own empty state rather than a blank list.
 */
export function SmartphonePicker({
  smartphones,
  label = "Smartphone",
  name,
  value,
  onChange,
  disabled,
  size = "md",
}: {
  smartphones: SmartphoneListItem[];
  label?: string;
  name?: string;
  value?: string;
  onChange?: (smartphoneId: string) => void;
  disabled?: boolean;
  size?: PickerSize;
}) {
  const id = useId();
  const styles = pickerSizes[size];
  const active = smartphones.filter((phone) => phone.status === "ACTIVE");

  if (active.length === 0) {
    return (
      <div className={cn("flex flex-col font-medium text-ink-secondary", styles.label)}>
        {label}
        <p className={cn("font-normal text-ink-mute", styles.note)}>
          No Active Smartphones on this Contract yet.
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
          Choose a Smartphone
        </option>
        {active.map((phone) => (
          <option key={phone.id} value={phone.id}>
            {phone.model}
            {phone.serial ? ` — ${phone.serial}` : ""}
          </option>
        ))}
      </select>
    </label>
  );
}
