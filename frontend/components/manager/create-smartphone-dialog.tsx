"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle, IconPlus } from "@/components/icons";

/**
 * Manager adds a Smartphone to a Contract's Fleet (fleet-management ticket AC: "Manager can add
 * a Smartphone to a Contract's Fleet"). Lives on the Contract detail view, mirroring
 * CreateTesterDialog's "scoped to one owning record" pattern. Status starts Active — the server
 * sets it, this dialog never asks for it.
 */
export function CreateSmartphoneDialog({ contractId }: { contractId: string }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [model, setModel] = useState("");
  const [serial, setSerial] = useState("");
  const [assignedTo, setAssignedTo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setModel("");
    setSerial("");
    setAssignedTo("");
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
        body: JSON.stringify({ model, serial, assignedTo: assignedTo || undefined }),
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
            Serial
            <Input
              required
              value={serial}
              onChange={(event) => setSerial(event.target.value)}
              disabled={submitting}
              invalid={Boolean(error)}
              placeholder="SN-12345"
            />
          </label>

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Assigned to (optional)
            <Input
              value={assignedTo}
              onChange={(event) => setAssignedTo(event.target.value)}
              disabled={submitting}
              placeholder="Front desk"
            />
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
