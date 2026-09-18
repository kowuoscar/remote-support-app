"use client";

import { forwardRef, useId, useImperativeHandle, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { readErrorCode } from "@/lib/api/errors";
import type { CarrierItem, Country } from "@/lib/api/types";

export type CarrierNameDialogHandle = {
  openCreate: () => void;
  openRename: (carrier: CarrierItem) => void;
};

function errorMessage(code: string | null, status: number, name: string, countryName: string): string {
  if (code === "CARRIER_NAME_TAKEN") {
    return `${countryName} already has an active carrier named “${name.trim()}”. Choose another name.`;
  }
  if (status === 400) return "Give the carrier a name.";
  if (status === 409) return "This carrier was archived meanwhile, so it can't be renamed. Reload the page.";
  if (status === 403) return `You can only change the ${countryName} catalog if it's your country.`;
  return "Couldn't save the carrier. Try again.";
}

/**
 * Adds a Carrier to one Country's catalog, or renames one (agent-maintains-carriers ticket). One
 * dialog serves both, since both only ever ask for a name; the Country is fixed by the page, never
 * chosen here. A duplicate active name comes back as a 409 with `CARRIER_NAME_TAKEN` and is shown
 * inline on the field.
 */
export const CarrierNameDialog = forwardRef<
  CarrierNameDialogHandle,
  { country: Country; countryName: string }
>(function CarrierNameDialog({ country, countryName }, ref) {
  const shellRef = useRef<DialogShellHandle>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const titleId = useId();
  const router = useRouter();
  const [renaming, setRenaming] = useState<CarrierItem | null>(null);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useImperativeHandle(ref, () => ({
    openCreate: () => {
      setRenaming(null);
      setName("");
      setError(null);
      shellRef.current?.open();
    },
    openRename: (carrier) => {
      setRenaming(carrier);
      setName(carrier.name);
      setError(null);
      shellRef.current?.open();
    },
  }));

  function close() {
    shellRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (name.trim() === "") {
      setError("Give the carrier a name.");
      inputRef.current?.focus();
      return;
    }
    setError(null);
    setSubmitting(true);

    try {
      const response = renaming
        ? await fetch(`/api/carriers/${renaming.id}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name }),
          })
        : await fetch("/api/carriers", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ country, name }),
          });

      if (!response.ok) {
        const code = response.status === 409 ? await readErrorCode(response) : null;
        setError(errorMessage(code, response.status, name, countryName));
        setSubmitting(false);
        inputRef.current?.focus();
        return;
      }

      setSubmitting(false);
      close();
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setSubmitting(false);
    }
  }

  const title = renaming ? `Rename ${renaming.name}` : "Add a carrier";
  const action = renaming ? "Save name" : "Add carrier";

  return (
    <DialogShell ref={shellRef} submitting={submitting} titleId={titleId}>
      <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit} noValidate>
        <div>
          <h2 id={titleId} className="text-base font-semibold text-ink">
            {title}
          </h2>
          <p className="text-[13px] text-ink-mute">
            {renaming
              ? "The new name shows everywhere this carrier is already used."
              : `Adds it to the ${countryName} catalog, shared with every agent there.`}
          </p>
        </div>

        {error ? <DialogErrorAlert message={error} /> : null}

        <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
          Carrier name
          <Input
            ref={inputRef}
            name="carrierName"
            autoFocus
            required
            maxLength={255}
            autoComplete="off"
            spellCheck={false}
            value={name}
            onChange={(event) => setName(event.target.value)}
            disabled={submitting}
            invalid={Boolean(error)}
            placeholder="e.g. Verizon…"
          />
        </label>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={submitting}>
            {action}
          </Button>
        </div>
      </form>
    </DialogShell>
  );
});
