"use client";

import { Input } from "@/components/ui/input";
import type { CompletionFormProps } from "./types";

/**
 * Completing a Provision Smartphone Request (provision-request-details ticket AC: "Completing a
 * Provision Smartphone needs no Agent input and adds a company-owned Smartphone with the requested
 * model and no serial"). Registered against `PROVISION_SMARTPHONE` in `registry.tsx`. A Request
 * submitted since this ticket already carries its own `requestedModel` (shown here, read-only) and
 * needs nothing further; one from before this ticket (`requestedModel` absent) falls back to the
 * previous full form (ticket AC: "completes through the previous full form").
 */
export function ProvisionSmartphoneCompletion({ request, activeSmartphones, disabled }: CompletionFormProps) {
  if (request.requestedModel) {
    return (
      <p className="text-[11px] text-ink-mute">
        Adds <span className="font-medium text-ink-secondary">{request.requestedModel}</span> to the Fleet,
        company-owned. Set its serial later from the Fleet page.
      </p>
    );
  }

  return (
    <>
      <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
        New smartphone model
        <Input name="model" required disabled={disabled} className="h-7 text-[12px]" />
      </label>
      <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
        New smartphone serial (optional)
        <Input name="serial" disabled={disabled} className="h-7 text-[12px]" />
      </label>
      {activeSmartphones.length > 0 ? (
        <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
          Retiring which smartphone? (optional)
          <select
            name="replacesSmartphoneId"
            disabled={disabled}
            defaultValue=""
            className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
          >
            <option value="">None — first-time provisioning</option>
            {activeSmartphones.map((phone) => (
              <option key={phone.id} value={phone.id}>
                {phone.model} — {phone.serial}
              </option>
            ))}
          </select>
        </label>
      ) : null}
    </>
  );
}
