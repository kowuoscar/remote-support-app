"use client";

import { Input } from "@/components/ui/input";
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
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Description <span className="font-normal text-ink-mute">(optional)</span>
        <textarea
          name="description"
          rows={2}
          disabled={disabled}
          placeholder="Anything the Agent should know"
          className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
        />
      </label>
    </>
  );
}
