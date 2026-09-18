"use client";

import { forwardRef, useId, useImperativeHandle, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import type { CarrierItem } from "@/lib/api/types";

export type ArchiveCarrierDialogHandle = { open: (carrier: CarrierItem) => void };

/**
 * Confirms archiving a Carrier (agent-maintains-carriers ticket). Archiving is one-way — there is
 * no restore — which is what earns an interrupting confirmation here.
 */
export const ArchiveCarrierDialog = forwardRef<ArchiveCarrierDialogHandle>(
  function ArchiveCarrierDialog(_props, ref) {
    const shellRef = useRef<DialogShellHandle>(null);
    const titleId = useId();
    const router = useRouter();
    const [carrier, setCarrier] = useState<CarrierItem | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    useImperativeHandle(ref, () => ({
      open: (target) => {
        setCarrier(target);
        setError(null);
        shellRef.current?.open();
      },
    }));

    function close() {
      shellRef.current?.close();
    }

    async function archive() {
      if (!carrier) return;
      setError(null);
      setSubmitting(true);
      try {
        const response = await fetch(`/api/carriers/${carrier.id}/archive`, { method: "POST" });
        if (!response.ok && response.status !== 409) {
          setError("Couldn't archive the carrier. Try again.");
          setSubmitting(false);
          return;
        }
        // A 409 means it was already archived: the outcome the user asked for.
        setSubmitting(false);
        close();
        router.refresh();
      } catch {
        setError("Couldn't reach the server. Check your connection and try again.");
        setSubmitting(false);
      }
    }

    return (
      <DialogShell ref={shellRef} submitting={submitting} titleId={titleId}>
        <div className="flex flex-col gap-4 p-6">
          <div className="space-y-1">
            <h2 id={titleId} className="text-base font-semibold text-ink">
              Archive {carrier?.name}?
            </h2>
            <p className="text-[13px] leading-relaxed text-ink-mute">
              It leaves every picker, so nobody can choose it for a new SIM card. Anything already
              using it keeps showing its name. Archiving can&rsquo;t be undone; to bring it back,
              add the carrier again.
            </p>
          </div>

          {error ? <DialogErrorAlert message={error} /> : null}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Keep it
            </Button>
            <Button type="button" variant="danger" loading={submitting} onClick={archive}>
              Archive carrier
            </Button>
          </div>
        </div>
      </DialogShell>
    );
  },
);
