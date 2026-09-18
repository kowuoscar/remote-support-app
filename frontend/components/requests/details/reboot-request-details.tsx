"use client";

import { SmartphonePicker } from "@/components/requests/smartphone-picker";
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
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Description <span className="font-normal text-ink-mute">(optional)</span>
        <textarea
          name="description"
          rows={3}
          disabled={disabled}
          placeholder="Anything the Agent should know"
          className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
        />
      </label>
    </>
  );
}
