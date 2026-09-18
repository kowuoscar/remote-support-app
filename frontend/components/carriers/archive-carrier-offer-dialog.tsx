"use client";

import { forwardRef, useId, useImperativeHandle, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { OFFER_COPY } from "@/components/carriers/carrier-offer-copy";
import type { CarrierOfferList } from "@/lib/api/carrier-offers";
import type { CarrierItem, CarrierOfferItem } from "@/lib/api/types";

export type ArchiveCarrierOfferDialogHandle = {
  open: (list: CarrierOfferList, carrier: CarrierItem, entry: CarrierOfferItem) => void;
};

type Target = { list: CarrierOfferList; carrier: CarrierItem; entry: CarrierOfferItem };

/**
 * Confirms archiving a Topup Option or a Postpaid Plan (topup-options-and-postpaid-plans ticket).
 * Archiving is one-way, like a Carrier's, which is what earns an interrupting confirmation.
 */
export const ArchiveCarrierOfferDialog = forwardRef<ArchiveCarrierOfferDialogHandle>(
  function ArchiveCarrierOfferDialog(_props, ref) {
    const shellRef = useRef<DialogShellHandle>(null);
    const titleId = useId();
    const router = useRouter();
    const [target, setTarget] = useState<Target | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    useImperativeHandle(ref, () => ({
      open: (list, carrier, entry) => {
        setTarget({ list, carrier, entry });
        setError(null);
        shellRef.current?.open();
      },
    }));

    const copy = target ? OFFER_COPY[target.list] : OFFER_COPY["topup-options"];

    function close() {
      shellRef.current?.close();
    }

    async function archive() {
      if (!target) return;
      setError(null);
      setSubmitting(true);
      try {
        const response = await fetch(
          `/api/carriers/${target.carrier.id}/${target.list}/${target.entry.id}/archive`,
          { method: "POST" },
        );
        if (!response.ok && response.status !== 409) {
          setError(`Couldn't archive the ${copy.singular}. Try again.`);
          setSubmitting(false);
          return;
        }
        // A 409 means it (or its carrier) was archived meanwhile; the page refresh shows which.
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
              Archive {target?.entry.name}?
            </h2>
            <p className="text-[13px] leading-relaxed text-ink-mute">
              {target?.list === "postpaid-plans"
                ? "It leaves every picker, so no new SIM card can go on it. SIM cards already on it keep the plan and its monthly fee."
                : "It leaves every picker, so no new topup fee can be logged from it. Fees already logged keep showing it."}{" "}
              Archiving can&rsquo;t be undone; to bring it back, add it again.
            </p>
          </div>

          {error ? <DialogErrorAlert message={error} /> : null}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Keep it
            </Button>
            <Button type="button" variant="danger" loading={submitting} onClick={archive}>
              Archive {copy.singular}
            </Button>
          </div>
        </div>
      </DialogShell>
    );
  },
);
