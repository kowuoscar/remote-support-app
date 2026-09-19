"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/**
 * Sets or changes a Smartphone's serial from the Fleet page (smartphone-owner-and-optional-serial
 * ticket AC: "the Agent (own Contract) or the Manager can set or change the serial later from the
 * Fleet page"). Mirrors fleet-status-controls.tsx's inline, no-modal row-action shape — craft-
 * floor.md: no dialog for a task that needs neither interruption nor protected focus.
 */
export function SmartphoneSerialControl({
  contractId,
  smartphoneId,
  serial,
}: {
  contractId: string;
  smartphoneId: string;
  serial: string | null;
}) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(serial ?? "");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(false);

  function startEditing() {
    setValue(serial ?? "");
    setError(false);
    setEditing(true);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(false);
    try {
      const response = await fetch(`/api/contracts/${contractId}/smartphones/${smartphoneId}/serial`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ serial: value.trim() || undefined }),
      });
      if (!response.ok) {
        setError(true);
        setPending(false);
        return;
      }
      // Reset immediately rather than relying on router.refresh() to remount this component: the
      // parent Server Component re-renders with fresh props, but this Client Component instance
      // (same key) stays mounted, so a stale `pending` would otherwise stay stuck forever.
      setPending(false);
      setEditing(false);
      router.refresh();
    } catch {
      setError(true);
      setPending(false);
    }
  }

  if (!editing) {
    return (
      <div className="flex items-center gap-2">
        <span className="tnum text-ink-secondary">{serial ?? "—"}</span>
        <Button variant="row" size="sm" onClick={startEditing}>
          {serial ? "Edit" : "Set serial"}
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={save} className="flex flex-col items-start gap-1">
      <div className="flex items-center gap-1.5">
        <Input
          autoFocus
          value={value}
          onChange={(event) => setValue(event.target.value)}
          disabled={pending}
          invalid={error}
          placeholder="SN-12345"
          aria-label="Smartphone serial"
          className="h-7 w-28 text-[12px]"
        />
        <Button type="submit" variant="row" size="sm" loading={pending}>
          Save
        </Button>
        <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={() => setEditing(false)}>
          Cancel
        </Button>
      </div>
      {error ? <span className="text-[11px] text-danger">Couldn&rsquo;t update. Try again.</span> : null}
    </form>
  );
}
