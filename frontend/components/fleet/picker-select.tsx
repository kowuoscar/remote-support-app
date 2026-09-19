import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { pickerSizes, type PickerSize } from "@/components/fleet/picker-sizes";

/**
 * The `<select>` markup shared by CarrierPicker and PostpaidPlanPicker: a FormData field (`name`)
 * or a controlled one (`value`/`onChange`), sized by `pickerSizes`. Extracted from the identical
 * copies, with no change to markup or behaviour.
 */
export function PickerSelect({
  id,
  name,
  value,
  onChange,
  disabled,
  size,
  placeholder,
  children,
}: {
  id: string;
  name?: string;
  value?: string;
  onChange?: (value: string) => void;
  disabled?: boolean;
  size: PickerSize;
  placeholder: string;
  children: ReactNode;
}) {
  const styles = pickerSizes[size];
  return (
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
        {placeholder}
      </option>
      {children}
    </select>
  );
}
