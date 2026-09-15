"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  SMARTPHONE_STATUS_LABEL,
  nextSmartphoneStatuses,
  type SimCardStatusValue,
  type SmartphoneStatusValue,
} from "@/lib/api/types";

/**
 * Agent quick action to change a Smartphone's status (fleet-management ticket AC: "Agent can
 * change a Smartphone's status"). A `<select>` of only the valid next statuses, not a dialog —
 * craft-floor.md: no modal for a task that needs neither interruption nor protected focus.
 */
export function SmartphoneStatusControl({
  contractId,
  smartphoneId,
  status,
}: {
  contractId: string;
  smartphoneId: string;
  status: SmartphoneStatusValue;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(false);
  const options = nextSmartphoneStatuses(status);

  async function changeStatus(next: SmartphoneStatusValue) {
    setPending(true);
    setError(false);
    try {
      const response = await fetch(
        `/api/contracts/${contractId}/smartphones/${smartphoneId}/status`,
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status: next }),
        },
      );
      if (!response.ok) {
        setError(true);
        setPending(false);
        return;
      }
      router.refresh();
    } catch {
      setError(true);
      setPending(false);
    }
  }

  if (options.length === 0) {
    return <span className="text-[12px] text-ink-mute">No further changes</span>;
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <select
        aria-label="Change smartphone status"
        value=""
        disabled={pending}
        onChange={(event) => {
          const next = event.target.value as SmartphoneStatusValue;
          if (next) changeStatus(next);
        }}
        className="h-7 rounded-lg border border-hairline-strong bg-canvas px-2 text-[12px] text-ink-secondary focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70"
      >
        <option value="">{pending ? "Updating…" : "Change status…"}</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {SMARTPHONE_STATUS_LABEL[option]}
          </option>
        ))}
      </select>
      {error ? <span className="text-[11px] text-danger">Couldn&rsquo;t update. Try again.</span> : null}
    </div>
  );
}

/**
 * Agent quick action to toggle a SIM Card's status (fleet-management ticket AC: "Agent can
 * change a SIM Card's status (Active or Retired)"). Only two states exist, so a single toggle
 * button reads clearer than a one-option dropdown.
 */
export function SimCardStatusControl({
  contractId,
  simCardId,
  status,
}: {
  contractId: string;
  simCardId: string;
  status: SimCardStatusValue;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(false);
  const next: SimCardStatusValue = status === "ACTIVE" ? "RETIRED" : "ACTIVE";

  async function changeStatus() {
    setPending(true);
    setError(false);
    try {
      const response = await fetch(`/api/contracts/${contractId}/sim-cards/${simCardId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: next }),
      });
      if (!response.ok) {
        setError(true);
        setPending(false);
        return;
      }
      router.refresh();
    } catch {
      setError(true);
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <Button variant="row" size="sm" onClick={changeStatus} loading={pending}>
        {status === "ACTIVE" ? "Retire" : "Reactivate"}
      </Button>
      {error ? <span className="text-[11px] text-danger">Couldn&rsquo;t update. Try again.</span> : null}
    </div>
  );
}
