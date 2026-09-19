"use client";

import { Input } from "@/components/ui/input";
import { SmartphonePicker } from "@/components/requests/smartphone-picker";
import { OptionalDescriptionField } from "./optional-description-field";
import type { RequestDetailsProps } from "./types";

/**
 * A Replace Smartphone Request's own details (request-types-and-flow spec's table: "Replace
 * Smartphone — Required: the Smartphone to replace; Optional: requested model (defaults to the
 * old one's)"; replace-requests ticket). Registered against `REPLACE_SMARTPHONE` in
 * `registry.tsx`. Reuses `targetSmartphoneId` — the same field Reboot's own (required) target and
 * Provision SIM's own (optional) target use — and `requestedModel` — the same field Provision
 * Smartphone's own (required) model uses, here optional: no type ever sets both a Reboot/
 * Provision-SIM target and a Replace target on the same Request, nor both a Provision Smartphone's
 * required model and a Replace Smartphone's optional one.
 */
export function ReplaceSmartphoneRequestDetails({ smartphones, disabled }: RequestDetailsProps) {
  return (
    <>
      <SmartphonePicker
        smartphones={smartphones}
        label="Smartphone to replace"
        name="targetSmartphoneId"
        disabled={disabled}
      />
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Requested model <span className="font-normal text-ink-mute">(optional — defaults to the same model)</span>
        <Input name="requestedModel" disabled={disabled} placeholder="e.g. iPhone 15" />
      </label>
      <OptionalDescriptionField disabled={disabled} />
    </>
  );
}
