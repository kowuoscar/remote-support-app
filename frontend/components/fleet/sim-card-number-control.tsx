"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const NUMBER_MESSAGES: Record<string, string> = {
  NUMBER_TOO_LONG: "A number can be at most 64 characters.",
};
const GENERIC_ERROR = "Couldn’t update. Try again.";

/**
 * Corrects a SIM Card's number from the Fleet page (correct-a-fleet-unit spec): the Manager and
 * the Client's Testers. Same inline edit-toggle shape as `SmartphoneSerialControl`, no dialog.
 * Trims before sending, refuses a blank value without a request, and keeps what was typed when
 * the backend refuses.
 */
export function SimCardNumberControl({
  contractId,
  simCardId,
  number,
}: {
  contractId: string;
  simCardId: string;
  number: string;
}) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(number);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEditing() {
    setValue(number);
    setError(null);
    setEditing(true);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) {
      setError("Enter a number.");
      return;
    }
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/contracts/${contractId}/sim-cards/${simCardId}/number`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ number: trimmed }),
      });
      if (!response.ok) {
        const body = (await response.json().catch(() => null)) as { code?: string } | null;
        setError((body?.code && NUMBER_MESSAGES[body.code]) || GENERIC_ERROR);
        setPending(false);
        return;
      }
      setPending(false);
      setEditing(false);
      router.refresh();
    } catch {
      setError(GENERIC_ERROR);
      setPending(false);
    }
  }

  if (!editing) {
    return (
      <div className="flex items-center gap-2">
        <span className="tnum font-medium text-ink">{number}</span>
        <Button variant="row" size="sm" onClick={startEditing}>
          Edit
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
          invalid={error !== null}
          aria-label="SIM Card number"
          className="h-7 w-40 text-label-sm"
        />
        <Button type="submit" variant="row" size="sm" loading={pending}>
          Save
        </Button>
        <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={() => setEditing(false)}>
          Cancel
        </Button>
      </div>
      {error ? <span className="text-label-sm text-danger">{error}</span> : null}
    </form>
  );
}
