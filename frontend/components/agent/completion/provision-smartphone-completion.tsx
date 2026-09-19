"use client";

import { useState } from "react";
import { Input } from "@/components/ui/input";
import type { RequestListItem } from "@/lib/api/types";
import type { CompletionBodyBuilder, CompletionFormProps } from "./types";

/**
 * Completing a Provision Smartphone Request (provision-request-details ticket AC: "Completing a
 * Provision Smartphone needs no Agent input and adds a company-owned Smartphone with the requested
 * model and no serial"). Registered against `PROVISION_SMARTPHONE` in `registry.tsx`. A Request
 * submitted since this ticket already carries its own `requestedModel` (shown here, read-only) and
 * needs nothing further; one from before this ticket (`requestedModel` absent) falls back to the
 * previous full form (ticket AC: "completes through the previous full form").
 *
 * <p>fulfil-from-stock ticket: either branch may instead name a Smartphone from the Agent's own
 * Stock (ticket AC: "offers a 'from my Stock' picker only when a matching unit exists" — a
 * Smartphone has no matching rule beyond ownership, so "matching" here just means "non-empty").
 * Picking one hides the legacy branch's own model/serial inputs; the "retiring which smartphone"
 * picker stays available either way, since it's an orthogonal, legacy-only choice.
 */
export function ProvisionSmartphoneCompletion({
  request,
  activeSmartphones,
  stockSmartphones = [],
  disabled,
}: CompletionFormProps) {
  const [fulfillFromStockId, setFulfillFromStockId] = useState("");
  const hasStock = stockSmartphones.length > 0;

  const stockPicker = hasStock ? (
    <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
      From my Stock (optional)
      <select
        name="fulfillFromStockSmartphoneId"
        value={fulfillFromStockId}
        onChange={(event) => setFulfillFromStockId(event.target.value)}
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
  ) : null;

  if (request.requestedModel) {
    return (
      <>
        {fulfillFromStockId ? (
          <p className="text-[11px] text-ink-mute">
            Moves the picked Smartphone from your Stock onto this Contract, company-owned.
          </p>
        ) : (
          <p className="text-[11px] text-ink-mute">
            Adds <span className="font-medium text-ink-secondary">{request.requestedModel}</span> to the Fleet,
            company-owned. Set its serial later from the Fleet page.
          </p>
        )}
        {stockPicker}
      </>
    );
  }

  return (
    <>
      {fulfillFromStockId ? null : (
        <>
          <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
            New smartphone model
            <Input name="model" required disabled={disabled} className="h-7 text-[12px]" />
          </label>
          <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
            New smartphone serial (optional)
            <Input name="serial" disabled={disabled} className="h-7 text-[12px]" />
          </label>
        </>
      )}
      {stockPicker}
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

/** @see CompletionBodyBuilder */
export const buildProvisionSmartphoneCompletionBody: CompletionBodyBuilder = (
  request: RequestListItem,
  formData: FormData,
) => {
  const fulfillFromStockSmartphoneId = String(formData.get("fulfillFromStockSmartphoneId") ?? "");
  if (fulfillFromStockSmartphoneId) {
    return { fulfillFromStockSmartphoneId };
  }

  if (request.requestedModel) {
    // provision-request-details ticket: no Agent input needed — the model already came from
    // submission.
    return {};
  }

  const body: Record<string, unknown> = {
    newSmartphone: {
      model: String(formData.get("model")),
      serial: String(formData.get("serial") ?? "") || undefined,
    },
  };
  const replaces = String(formData.get("replacesSmartphoneId") ?? "");
  if (replaces) body.replacesSmartphoneId = replaces;
  return body;
};
