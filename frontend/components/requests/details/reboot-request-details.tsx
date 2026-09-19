"use client";

import { SmartphonePicker } from "@/components/requests/smartphone-picker";
import { OptionalDescriptionField } from "./optional-description-field";
import type { RequestDetailsProps } from "./types";

/**
 * A Reboot Request's own details (request-types-and-flow spec's table: "Reboot — Required: target
 * Smartphone"; reboot-and-topup-details ticket). Registered against `REBOOT` in `registry.tsx` —
 * used verbatim by both the Tester's submit dialog and the Agent's log-Request dialog.
 */
export function RebootRequestDetails({ smartphones, disabled }: RequestDetailsProps) {
  return (
    <>
      <SmartphonePicker
        smartphones={smartphones}
        label="Smartphone to reboot"
        name="targetSmartphoneId"
        disabled={disabled}
      />
      <OptionalDescriptionField disabled={disabled} rows={3} />
    </>
  );
}
