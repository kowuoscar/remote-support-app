"use client";

import type { StockUnitItem } from "@/lib/api/types";

/**
 * The "From my Stock (optional)" `<select name="fulfillFromStockSmartphoneId">` shared by
 * `ProvisionSmartphoneCompletion` and `ReplaceSmartphoneCompletion` (fulfil-from-stock ticket AC:
 * "offers a 'from my Stock' picker only when a matching unit exists" — a Smartphone has no
 * matching rule beyond ownership, so the caller passes its own Agent's whole `stockSmartphones`).
 * Renders nothing when empty, mirroring both callers' own prior inline `hasStock` guard.
 */
export function StockSmartphonePicker({
  stockSmartphones,
  value,
  onChange,
  disabled,
}: {
  stockSmartphones: StockUnitItem[];
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}) {
  if (stockSmartphones.length === 0) return null;
  return (
    <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
      From my Stock (optional)
      <select
        name="fulfillFromStockSmartphoneId"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
        className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
      >
        <option value="">None — add a new Smartphone</option>
        {stockSmartphones.map((unit) => (
          <option key={unit.id} value={unit.id}>
            {unit.model}
            {unit.serial ? ` — ${unit.serial}` : ""}
          </option>
        ))}
      </select>
    </label>
  );
}

/**
 * The "From my Stock (optional)" `<select name="fulfillFromStockSimCardId">` shared by
 * `ProvisionSimCompletion` and `ReplaceSimCompletion` — the caller has already filtered
 * `matchingStock` down to the SIM Cards whose Carrier/flavor/(postpaid) Plan match the Request's
 * own requested fields (Provision SIM) or the old SIM Card being replaced (Replace SIM), since the
 * two types match against different reference points. Renders nothing when the filtered list is
 * empty, mirroring both callers' own prior inline guard.
 */
export function StockSimCardPicker({
  matchingStock,
  value,
  onChange,
  disabled,
}: {
  matchingStock: StockUnitItem[];
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}) {
  if (matchingStock.length === 0) return null;
  return (
    <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
      From my Stock (optional)
      <select
        name="fulfillFromStockSimCardId"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
        className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
      >
        <option value="">None — add a new SIM Card</option>
        {matchingStock.map((unit) => (
          <option key={unit.id} value={unit.id}>
            {unit.number}
          </option>
        ))}
      </select>
    </label>
  );
}
