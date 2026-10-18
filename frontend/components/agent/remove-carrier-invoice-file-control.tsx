"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { refreshPage } from "@/lib/selection-address";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle } from "@/components/icons";

/**
 * The Agent's Remove on one Carrier Invoice File of a draft open to them (carrier-invoice-per-sim).
 * A quiet text trigger, then an inline second step ("Remove this file?", Cancel, a danger-toned
 * Remove) rather than a dialog: it is one file and one sentence. Focus follows Send's flow:
 * Remove takes it when the confirm opens, the trigger gets it back on Cancel or Escape, and a
 * pending Remove stays focusable (aria-disabled + aria-busy, repeat activation ignored). On
 * success the page refreshes: the file leaves the list and the line loses its check mark.
 * `disabled` (file storage is unavailable) disables the trigger like Attach.
 */
export function RemoveCarrierInvoiceFileControl({
  invoiceId,
  fileId,
  filename,
  disabled = false,
}: {
  invoiceId: string;
  fileId: string;
  filename: string;
  disabled?: boolean;
}) {
  const router = useRouter();
  const trigger = useRef<HTMLButtonElement>(null);
  const confirmButton = useRef<HTMLButtonElement>(null);
  const refocusTrigger = useRef(false);
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (confirming) {
      confirmButton.current?.focus();
    } else if (refocusTrigger.current) {
      refocusTrigger.current = false;
      trigger.current?.focus();
    }
  }, [confirming]);

  function close() {
    refocusTrigger.current = true;
    setConfirming(false);
    setError(null);
  }

  async function remove() {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/client-invoices/${invoiceId}/files/${fileId}`, { method: "DELETE" });
      if (!response.ok) {
        setError("Couldn't remove the file. Try again.");
        setPending(false);
        return;
      }
      // Stays pending until the refreshed page replaces this row.
      refreshPage(router);
    } catch {
      setError("Couldn't remove the file. Try again.");
      setPending(false);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape" && !pending) close();
  }

  const errorLine = error ? (
    <p role="alert" className="flex items-center gap-1.5 text-label-sm text-danger">
      <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
      {error}
    </p>
  ) : null;

  if (confirming) {
    return (
      <div className="flex flex-col items-end gap-1" onKeyDown={handleKeyDown}>
        <div className="flex flex-wrap items-center justify-end gap-1.5">
          <span className="text-label-sm text-ink-mute">Remove this file?</span>
          <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={close}>
            Cancel
          </Button>
          <Button
            ref={confirmButton}
            type="button"
            variant="danger"
            size="sm"
            aria-disabled={pending || undefined}
            aria-busy={pending || undefined}
            className={pending ? "cursor-not-allowed opacity-60" : undefined}
            onClick={remove}
          >
            Remove
          </Button>
        </div>
        {errorLine}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        ref={trigger}
        type="button"
        aria-label={`Remove ${filename}`}
        disabled={disabled}
        onClick={() => setConfirming(true)}
        className="text-label-sm font-medium text-ink-mute hover:text-ink hover:underline disabled:cursor-not-allowed disabled:no-underline disabled:opacity-50 disabled:hover:text-ink-mute"
      >
        Remove
      </button>
      {errorLine}
    </div>
  );
}
