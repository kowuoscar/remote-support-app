"use client";

import { useState } from "react";
import { StockSmartphonePicker } from "./stock-unit-pickers";
import type { CompletionBodyBuilder, CompletionFormProps } from "./types";

/**
 * Completing a Replace Smartphone Request (replace-requests ticket AC: "Completing a Replace
 * Smartphone retires the named Smartphone, adds a company-owned one with the requested or the
 * same model, and moves the old one's SIM Cards into it; no Agent input"). Registered against
 * `REPLACE_SMARTPHONE` in `registry.tsx`. Read-only, unless the Agent picks a Smartphone from
 * their own Stock (fulfil-from-stock ticket AC): a Smartphone has no matching rule beyond
 * ownership, so the picker shows whenever the Agent's own Stock has any Smartphone at all.
 */
export function ReplaceSmartphoneCompletion({ request, stockSmartphones = [], disabled }: CompletionFormProps) {
  const [fulfillFromStockId, setFulfillFromStockId] = useState("");
  const newModel = request.requestedModel ?? request.targetSmartphoneModel;

  return (
    <>
      {fulfillFromStockId ? (
        <p className="text-[11px] text-ink-mute">
          Retires <span className="font-medium text-ink-secondary">{request.targetSmartphoneModel}</span> and moves
          the picked Smartphone from your Stock onto this Contract instead, with its SIM Cards carried over.
        </p>
      ) : (
        <p className="text-[11px] text-ink-mute">
          Retires <span className="font-medium text-ink-secondary">{request.targetSmartphoneModel}</span> and adds{" "}
          <span className="font-medium text-ink-secondary">{newModel}</span> to the Fleet, company-owned, with its
          SIM Cards carried over. Set its serial later from the Fleet page.
        </p>
      )}
      <StockSmartphonePicker
        stockSmartphones={stockSmartphones}
        value={fulfillFromStockId}
        onChange={setFulfillFromStockId}
        disabled={disabled}
      />
    </>
  );
}

/** @see CompletionBodyBuilder */
export const buildReplaceSmartphoneCompletionBody: CompletionBodyBuilder = (_request, formData) => {
  const fulfillFromStockSmartphoneId = String(formData.get("fulfillFromStockSmartphoneId") ?? "");
  if (fulfillFromStockSmartphoneId) {
    return { fulfillFromStockSmartphoneId };
  }
  return {};
};
