"use client";

import { useRef } from "react";
import { Button } from "@/components/ui/button";
import { IconPaperclip } from "@/components/icons";
import { AttachCarrierInvoiceDialog } from "@/components/agent/attach-carrier-invoice-dialog";
import type { DialogShellHandle } from "@/components/manager/dialog-shell";
import type { ClientInvoiceBaseSimLine } from "@/lib/api/types";

/**
 * The Agent's "Attach carrier invoice" on a Client Invoice draft: a button that opens the form
 * (carrier-invoice-per-sim). One file per Postpaid SIM line, so `missingLines` are the lines still
 * without theirs; with none left, or while file storage is unavailable, the button is disabled.
 */
export function AttachCarrierInvoiceFileControl({
  invoiceId,
  missingLines,
  disabled = false,
}: {
  invoiceId: string;
  missingLines: ClientInvoiceBaseSimLine[];
  /** File storage is unavailable: the button is disabled. */
  disabled?: boolean;
}) {
  const shellRef = useRef<DialogShellHandle>(null);

  return (
    <>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        disabled={disabled || missingLines.length === 0}
        onClick={() => shellRef.current?.open()}
      >
        <IconPaperclip className="h-4 w-4" />
        Attach carrier invoice
      </Button>
      <AttachCarrierInvoiceDialog invoiceId={invoiceId} lines={missingLines} shellRef={shellRef} />
    </>
  );
}
