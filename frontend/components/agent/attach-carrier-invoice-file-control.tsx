"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconPaperclip } from "@/components/icons";

/**
 * Agent attaches one or more carrier invoice files to a Contract's current-month draft
 * (client-invoice-generation ticket AC: "Agent can attach one or more carrier invoice files").
 * A visually-hidden native file input behind a styled button — the standard accessible pattern
 * for a custom-looking file picker — rather than a dialog, since there's nothing else to fill in:
 * picking a file is the whole interaction, unlike LogFeeDialog/LogRequestDialog's multi-field
 * forms. `multiple` lets one interaction pick several files at once; each is still POSTed as its
 * own upload (the backend accepts one file per call), sequentially, so a failure partway through
 * leaves the files that already succeeded attached rather than losing all of them.
 */
export function AttachCarrierInvoiceFileControl({ contractId }: { contractId: string }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const router = useRouter();
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleFiles(fileList: FileList | null) {
    if (!fileList || fileList.length === 0) return;
    setError(null);
    setUploading(true);

    try {
      for (const file of Array.from(fileList)) {
        const formData = new FormData();
        formData.append("file", file);
        const response = await fetch(`/api/contracts/${contractId}/client-invoice/files`, {
          method: "POST",
          body: formData,
        });
        if (!response.ok) {
          setError("Couldn't attach one or more files. Try again.");
          setUploading(false);
          return;
        }
      }
      setUploading(false);
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setUploading(false);
    } finally {
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button
        type="button"
        variant="secondary"
        size="sm"
        loading={uploading}
        onClick={() => inputRef.current?.click()}
      >
        <IconPaperclip className="h-4 w-4" />
        Attach carrier invoice
      </Button>
      <input
        ref={inputRef}
        type="file"
        multiple
        className="sr-only"
        aria-label="Attach carrier invoice file"
        onChange={(event) => handleFiles(event.target.files)}
      />
      {error ? (
        <p role="alert" className="flex items-center gap-1.5 text-[12px] text-danger">
          <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
          {error}
        </p>
      ) : null}
    </div>
  );
}
