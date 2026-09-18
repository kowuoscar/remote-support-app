/**
 * Shared size variants for the Fleet form pickers (`CarrierPicker`, `PostpaidPlanPicker`): the
 * same labeled-select-plus-note shape appears at `md` (a dialog's own field) and `sm` (nested
 * inside RequestStatusControl's inline completion row).
 */
export const pickerSizes = {
  md: {
    label: "gap-1.5 text-[13px]",
    select: "h-9 rounded-lg px-3 text-sm",
    note: "text-[13px]",
  },
  sm: {
    label: "w-full gap-1 text-[11px]",
    select: "h-7 rounded-md px-2 text-[12px]",
    note: "text-[11px]",
  },
} as const;

export type PickerSize = keyof typeof pickerSizes;
