"use client";

import { useId, useRef, useState, type FormEvent, type RefObject } from "react";
import { useRouter } from "next/navigation";
import { refreshPage } from "@/lib/selection-address";
import { Button } from "@/components/ui/button";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { PickerSelect } from "@/components/fleet/picker-select";
import { readErrorCode } from "@/lib/api/errors";
import type { ClientInvoiceBaseSimLine } from "@/lib/api/types";

const ALREADY_ATTACHED = "This SIM already has its carrier invoice. Remove it first to replace it.";
const STORAGE_UNAVAILABLE = "File storage is unavailable right now. Try again later.";
const GENERIC = "Couldn't attach the file. Try again.";
const NO_FILE = "Choose the file to attach.";
const NETWORK = "Couldn't reach the server. Check your connection and try again.";

/** What a Postpaid SIM line is called in the picker: "{number} · {Carrier}", or the bare number without a Carrier. */
function optionLabel(line: Pick<ClientInvoiceBaseSimLine, "number" | "carrierName">): string {
  return line.carrierName ? `${line.number} · ${line.carrierName}` : line.number;
}

/**
 * The form behind "Attach carrier invoice": one of the invoice's Postpaid SIM lines that has no
 * file yet, and one file. It posts both to the invoice's files route and, on success, closes and
 * refreshes the page. A refusal stays in the dialog, with the text that tells the Agent what to
 * do. The `lines` are the ones still missing their file; the line is preselected when only one is
 * left.
 */
export function AttachCarrierInvoiceDialog({
  invoiceId,
  lines,
  shellRef,
}: {
  invoiceId: string;
  lines: ClientInvoiceBaseSimLine[];
  shellRef: RefObject<DialogShellHandle | null>;
}) {
  const titleId = useId();
  const simFieldId = useId();
  const fileFieldId = useId();
  const router = useRouter();
  const fileInput = useRef<HTMLInputElement>(null);
  const [simCardId, setSimCardId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // The form is mounted only while the dialog is open, and the choice is reset on every close.
  const onlyLine = lines.length === 1 ? lines[0].simCardId : "";
  const selected = simCardId || onlyLine;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const file = fileInput.current?.files?.[0];
    if (!file) {
      setError(NO_FILE);
      return;
    }
    setError(null);
    setSubmitting(true);

    const body = new FormData();
    body.append("file", file);
    body.append("simCardId", selected);
    try {
      const response = await fetch(`/api/client-invoices/${invoiceId}/files`, { method: "POST", body });
      if (!response.ok) {
        const code = response.status === 409 ? await readErrorCode(response) : null;
        setError(
          code === "CARRIER_INVOICE_ALREADY_ATTACHED"
            ? ALREADY_ATTACHED
            : response.status === 503
              ? STORAGE_UNAVAILABLE
              : GENERIC,
        );
        setSubmitting(false);
        return;
      }
      setSubmitting(false);
      shellRef.current?.close();
      refreshPage(router);
    } catch {
      setError(NETWORK);
      setSubmitting(false);
    }
  }

  return (
    <DialogShell
      ref={shellRef}
      submitting={submitting}
      widthClassName="w-[min(440px,90vw)]"
      titleId={titleId}
      onClosed={() => {
        setSimCardId("");
        setError(null);
      }}
    >
      <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
        <h2 id={titleId} className="text-base font-semibold text-ink">
          Attach carrier invoice
        </h2>

        {error ? <DialogErrorAlert message={error} /> : null}

        <label htmlFor={simFieldId} className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
          Postpaid SIM
          <PickerSelect
            id={simFieldId}
            value={selected}
            onChange={setSimCardId}
            disabled={submitting}
            size="md"
            placeholder="Choose a Postpaid SIM"
          >
            {lines.map((line) => (
              <option key={line.simCardId} value={line.simCardId}>
                {optionLabel(line)}
              </option>
            ))}
          </PickerSelect>
        </label>

        <label htmlFor={fileFieldId} className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
          File
          <input
            id={fileFieldId}
            ref={fileInput}
            type="file"
            name="file"
            aria-required="true"
            disabled={submitting}
            className="text-[13px] font-normal text-ink file:mr-3 file:rounded-lg file:border file:border-hairline-strong file:bg-canvas file:px-3 file:py-1.5 file:text-label file:font-medium file:text-ink"
          />
        </label>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={() => shellRef.current?.close()} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={submitting}>
            Attach
          </Button>
        </div>
      </form>
    </DialogShell>
  );
}
