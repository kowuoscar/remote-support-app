"use client";

import { SimCardPicker } from "@/components/requests/sim-card-picker";
import type { RequestDetailsProps } from "./types";

/**
 * A Replace SIM Request's own details (request-types-and-flow spec's table: "Replace SIM —
 * Required: the SIM Card to replace"; replace-requests ticket). Registered against `REPLACE_SIM`
 * in `registry.tsx`. Reuses `targetSimCardId` — the same field Topup's own target uses; no type
 * ever sets both a Topup target and a Replace SIM target on the same Request. Unlike Provision
 * SIM, the new SIM Card's Carrier/flavor/Plan are never named here — they're decided at
 * completion, defaulted from this SIM Card's own current values (see `ReplaceSimCompletion`).
 */
export function ReplaceSimRequestDetails({ simCards, disabled }: RequestDetailsProps) {
  return (
    <>
      <SimCardPicker simCards={simCards} label="SIM Card to replace" name="targetSimCardId" disabled={disabled} />
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
