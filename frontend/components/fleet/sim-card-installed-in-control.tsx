"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { SmartphoneStatusValue } from "@/lib/api/types";

export interface InstallableSmartphone {
  id: string;
  model: string;
  serial: string | null;
  status: SmartphoneStatusValue;
}

/**
 * Sets, moves or clears a SIM Card's Installed-in Smartphone from the Fleet page
 * (sim-installed-in-smartphone ticket AC: "the Agent (own Contract) or the Manager can set or
 * clear a SIM Card's Smartphone from the Fleet page"). A `<select>` of the Contract's Active
 * Smartphones plus "Not installed", not a dialog — craft-floor.md: no modal for a task that needs
 * neither interruption nor protected focus. Mirrors fleet-status-controls.tsx's immediate-apply
 * shape rather than smartphone-serial-control.tsx's edit-toggle one, since every option is already
 * known up front (no free-text to type).
 */
export function SimCardInstalledInControl({
  contractId,
  simCardId,
  installedInSmartphoneId,
  smartphones,
}: {
  contractId: string;
  simCardId: string;
  installedInSmartphoneId?: string;
  smartphones: InstallableSmartphone[];
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(false);

  const options = smartphones.filter(
    (phone) => phone.status === "ACTIVE" || phone.id === installedInSmartphoneId,
  );

  async function change(smartphoneId: string) {
    setPending(true);
    setError(false);
    try {
      const response = await fetch(
        `/api/contracts/${contractId}/sim-cards/${simCardId}/installed-in`,
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ smartphoneId: smartphoneId || null }),
        },
      );
      if (!response.ok) {
        setError(true);
        setPending(false);
        return;
      }
      // Reset immediately rather than relying on router.refresh() to remount this component: the
      // parent Server Component re-renders with fresh props, but this Client Component instance
      // (same key) stays mounted, so a stale `pending` would otherwise stay stuck forever.
      setPending(false);
      router.refresh();
    } catch {
      setError(true);
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <select
        aria-label="Installed in"
        value={installedInSmartphoneId ?? ""}
        disabled={pending}
        onChange={(event) => change(event.target.value)}
        className="h-7 rounded-lg border border-hairline-strong bg-canvas px-2 text-[12px] text-ink-secondary focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70"
      >
        <option value="">Not installed</option>
        {options.map((phone) => (
          <option key={phone.id} value={phone.id}>
            {phone.model}
            {phone.serial ? ` — ${phone.serial}` : ""}
          </option>
        ))}
      </select>
      {error ? <span className="text-[11px] text-danger">Couldn&rsquo;t update. Try again.</span> : null}
    </div>
  );
}
