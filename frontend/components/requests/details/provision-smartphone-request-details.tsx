"use client";

import { Input } from "@/components/ui/input";
import { OptionalDescriptionField } from "./optional-description-field";
import type { RequestDetailsProps } from "./types";

/**
 * A Provision Smartphone Request's own details (request-types-and-flow spec's table: "Provision
 * Smartphone — Required: requested model (brand and model, free text)"; provision-request-details
 * ticket). Registered against `PROVISION_SMARTPHONE` in `registry.tsx`. No unit picker: Provision
 * never names an existing Fleet unit — that's what Replace is for (spec.md Solution).
 */
export function ProvisionSmartphoneRequestDetails({ disabled }: RequestDetailsProps) {
  return (
    <>
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Requested model
        <Input name="requestedModel" required disabled={disabled} placeholder="e.g. iPhone 15" />
      </label>
      <OptionalDescriptionField disabled={disabled} />
    </>
  );
}
