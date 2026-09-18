"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle, IconPlus } from "@/components/icons";
import { SMARTPHONE_OWNER_LABEL, type SmartphoneOwnerValue } from "@/lib/api/types";

const owners: SmartphoneOwnerValue[] = ["COMPANY", "CLIENT"];

/**
 * Manager adds a Smartphone to a Contract's Fleet (fleet-management ticket AC: "Manager can add
 * a Smartphone to a Contract's Fleet"). Lives on the Contract detail view, mirroring
 * CreateTesterDialog's "scoped to one owning record" pattern. Status starts Active — the server
 * sets it, this dialog never asks for it. Owner defaults to Company (spec.md Solution — Fleet
 * model; smartphone-owner-and-optional-serial ticket AC: "The Manager's add-Smartphone form asks
 * for the Owner, defaulting to company"). Serial is optional (same ticket AC: "A Smartphone can
 * be created without a serial").
 */
export function CreateSmartphoneDialog({ contractId }: { contractId: string }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [model, setModel] = useState("");
  const [serial, setSerial] = useState("");
  const [owner, setOwner] = useState<SmartphoneOwnerValue>("COMPANY");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setModel("");
    setSerial("");
    setOwner("COMPANY");
    setError(null);
    dialogRef.current?.showModal();
  }

  function close() {
    dialogRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ model, serial: serial || undefined, owner }),
      });

      if (!response.ok) {
        setError("Couldn't add the smartphone. Try again.");
        setSubmitting(false);
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

  return (
    <>
      <Button variant="primary" onClick={open}>
        <IconPlus className="h-4 w-4" />
        Add smartphone
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(420px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 className="text-base font-semibold text-ink">Add a smartphone</h2>
            <p className="text-[13px] text-ink-mute">Starts Active on this contract&rsquo;s fleet.</p>
          </div>

          {error ? (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2.5 text-[13px] text-danger"
            >
              <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          ) : null}

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Model
            <Input
              autoFocus
              required
              value={model}
              onChange={(event) => setModel(event.target.value)}
              disabled={submitting}
              invalid={Boolean(error)}
              placeholder="iPhone 14"
            />
          </label>

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Serial (optional)
            <Input
              value={serial}
              onChange={(event) => setSerial(event.target.value)}
              disabled={submitting}
              invalid={Boolean(error)}
              placeholder="SN-12345"
            />
          </label>

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Owner
            <select
              value={owner}
              onChange={(event) => setOwner(event.target.value as SmartphoneOwnerValue)}
              disabled={submitting}
              className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
            >
              {owners.map((value) => (
                <option key={value} value={value}>
                  {SMARTPHONE_OWNER_LABEL[value]}
                </option>
              ))}
            </select>
          </label>

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Add smartphone
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}
