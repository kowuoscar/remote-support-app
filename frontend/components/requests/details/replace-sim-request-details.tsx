"use client";

import { SimCardPicker } from "@/components/requests/sim-card-picker";
import { OptionalDescriptionField } from "./optional-description-field";
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
      <OptionalDescriptionField disabled={disabled} />
    </>
  );
}
