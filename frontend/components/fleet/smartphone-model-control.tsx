"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const MODEL_MESSAGES: Record<string, string> = {
  MODEL_TOO_LONG: "A model can be at most 255 characters.",
};
const GENERIC_ERROR = "Couldn’t update. Try again.";

/**
 * Corrects a Smartphone's model from the Fleet page (correct-a-fleet-unit spec): the Manager and
 * the Client's Testers. Same inline edit-toggle shape as `SmartphoneSerialControl`, no dialog.
 * Trims before sending, refuses a blank value without a request, and keeps what was typed when
 * the backend refuses.
 */
export function SmartphoneModelControl({
  contractId,
  smartphoneId,
  model,
}: {
  contractId: string;
  smartphoneId: string;
  model: string;
}) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(model);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEditing() {
    setValue(model);
    setError(null);
    setEditing(true);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) {
      setError("Enter a model.");
      return;
    }
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/contracts/${contractId}/smartphones/${smartphoneId}/model`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ model: trimmed }),
      });
      if (!response.ok) {
        const body = (await response.json().catch(() => null)) as { code?: string } | null;
        setError((body?.code && MODEL_MESSAGES[body.code]) || GENERIC_ERROR);
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
        <span className="font-medium text-ink">{model}</span>
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
          aria-label="Smartphone model"
          className="h-7 w-36 text-label-sm"
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
