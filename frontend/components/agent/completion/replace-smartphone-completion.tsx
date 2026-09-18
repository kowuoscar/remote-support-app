"use client";

import type { CompletionFormProps } from "./types";

/**
 * Completing a Replace Smartphone Request (replace-requests ticket AC: "Completing a Replace
 * Smartphone retires the named Smartphone, adds a company-owned one with the requested or the
 * same model, and moves the old one's SIM Cards into it; no Agent input"). Registered against
 * `REPLACE_SMARTPHONE` in `registry.tsx`. Always this one, read-only shape — unlike Provision
 * Smartphone, this type has no Request predating it to fall back for.
 */
export function ReplaceSmartphoneCompletion({ request }: CompletionFormProps) {
  const newModel = request.requestedModel ?? request.targetSmartphoneModel;
  return (
    <p className="text-[11px] text-ink-mute">
      Retires <span className="font-medium text-ink-secondary">{request.targetSmartphoneModel}</span> and adds{" "}
      <span className="font-medium text-ink-secondary">{newModel}</span> to the Fleet, company-owned, with its SIM
      Cards carried over. Set its serial later from the Fleet page.
    </p>
  );
}
