"use client";

import Link from "next/link";
import { useId } from "react";
import { cn } from "@/lib/cn";
import { pickerSizes, type PickerSize } from "@/components/fleet/picker-sizes";
import { PickerSelect } from "@/components/fleet/picker-select";
import type { CarrierItem } from "@/lib/api/types";

/**
 * The Carrier a new SIM Card names (sim-card-carrier ticket): the active Carriers of the
 * Contract's Country, never an archived one. When that Country has none, the form can't be
 * completed, so the picker says where to add one instead of showing an empty list.
 *
 * Works inside a FormData form (`name`) or as a controlled field (`value`/`onChange`).
 */
export function CarrierPicker({
  carriers,
  carriersHref,
  name,
  value,
  onChange,
  disabled,
  size = "md",
}: {
  carriers: CarrierItem[];
  carriersHref: string;
  name?: string;
  value?: string;
  onChange?: (carrierId: string) => void;
  disabled?: boolean;
  size?: PickerSize;
}) {
  const id = useId();
  const styles = pickerSizes[size];
  const active = carriers
    .filter((carrier) => carrier.archivedAt === null)
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" }));

  if (active.length === 0) {
    return (
      <div className={cn("flex flex-col font-medium text-ink-secondary", styles.label)}>
        Carrier
        <p className={cn("font-normal text-ink-mute", styles.note)}>
          No active carrier for this country yet.{" "}
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
    <label htmlFor={id} className={cn("flex flex-col font-medium text-ink-secondary", styles.label)}>
      Carrier
      <PickerSelect
        id={id}
        name={name}
        value={value}
        onChange={onChange}
        disabled={disabled}
        size={size}
        placeholder="Choose a carrier"
      >
        {active.map((carrier) => (
          <option key={carrier.id} value={carrier.id} translate="no">
            {carrier.name}
          </option>
        ))}
      </PickerSelect>
    </label>
  );
}
