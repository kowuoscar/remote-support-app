"use client";

import { useState, type FormEvent, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { PickerSelect } from "@/components/fleet/picker-select";
import { SMARTPHONE_OWNER_LABEL, type SmartphoneOwnerValue } from "@/lib/api/types";

const OWNER_MESSAGES: Record<string, string> = {
  SMARTPHONE_ON_OPEN_RETURN: "This smartphone is on an open Return. Change its owner once the Return is done.",
};
const GENERIC_ERROR = "Couldn’t update. Try again.";

/**
 * Corrects a Smartphone's owner from the Fleet page (correct-a-fleet-unit spec): the Manager and
 * the Client's Testers. The edit-toggle shape of `SmartphoneModelControl` around a two-option
 * select; a misclick on an immediate-apply select would change a Return's path, so Save commits.
 * Enter on the select saves, as it does in a text field.
 */
export function SmartphoneOwnerControl({
  contractId,
  smartphoneId,
  owner,
}: {
  contractId: string;
  smartphoneId: string;
  owner: SmartphoneOwnerValue;
}) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState<SmartphoneOwnerValue>(owner);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEditing() {
    setValue(owner);
    setError(null);
    setEditing(true);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/contracts/${contractId}/smartphones/${smartphoneId}/owner`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ owner: value }),
      });
      if (!response.ok) {
        const body = (await response.json().catch(() => null)) as { code?: string } | null;
        setError((body?.code && OWNER_MESSAGES[body.code]) || GENERIC_ERROR);
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

  function saveOnEnter(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === "Enter" && event.target instanceof HTMLSelectElement) {
      event.preventDefault();
      event.currentTarget.requestSubmit();
    }
  }

  if (!editing) {
    return (
      <div className="flex items-center gap-2">
        <span>{SMARTPHONE_OWNER_LABEL[owner]}</span>
        <Button variant="row" size="sm" aria-label="Edit owner" onClick={startEditing}>
          Edit
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={save} onKeyDown={saveOnEnter} className="flex flex-col items-start gap-1">
      <div className="flex items-center gap-1.5">
        <PickerSelect
          id={`owner-${smartphoneId}`}
          ariaLabel="Owner"
          autoFocus
          value={value}
          onChange={(next) => setValue(next as SmartphoneOwnerValue)}
          disabled={pending}
          size="sm"
          placeholder="Choose an owner"
        >
          <option value="CLIENT">{SMARTPHONE_OWNER_LABEL.CLIENT}</option>
          <option value="COMPANY">{SMARTPHONE_OWNER_LABEL.COMPANY}</option>
        </PickerSelect>
        <Button type="submit" variant="row" size="sm" loading={pending}>
          Save
        </Button>
        <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={() => setEditing(false)}>
          Cancel
        </Button>
      </div>
      {error ? (
        <span role="alert" className="max-w-56 text-label-sm text-danger">
          {error}
        </span>
      ) : null}
    </form>
  );
}
