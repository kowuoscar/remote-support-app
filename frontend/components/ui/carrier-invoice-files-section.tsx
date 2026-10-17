import type { ReactNode } from "react";
import {
  IconAlertTriangle,
  IconDownload,
  IconPaperclip,
} from "@/components/icons";

/**
 * The "Carrier invoice files" section of a Client Invoice, shared by the Agent's card, the
 * Manager's invoice detail and the Tester's Invoices page. While file storage is unavailable
 * (`filesAvailable` false, carrier-invoice-object-storage spec) the names stay listed, each
 * Download turns into non-interactive text, and one notice says what is paused. The Attach
 * control itself stays with the caller (it sits in the card header); `canAttach` only tells this
 * section whether the notice should mention attaching.
 */
export function CarrierInvoiceFilesSection({
  files,
  downloadHref,
  emptyText,
  filesAvailable,
  canAttach = false,
  labelledDownloads = false,
  renderAction,
}: {
  files: { id: string; filename: string }[];
  downloadHref: (fileId: string) => string;
  emptyText: string;
  filesAvailable: boolean;
  canAttach?: boolean;
  /** Give each Download the accessible name `Download {filename}` (the Manager's view does). */
  labelledDownloads?: boolean;
  /**
   * A per-file action beside Download (the Agent's Remove on an editable draft). When given, the
   * paused notice names removing as well: it says exactly what the section offers.
   */
  renderAction?: (file: { id: string; filename: string }) => ReactNode;
}) {
  const paused = !filesAvailable;
  const showNotice = paused && (canAttach || files.length > 0);

  return (
    <div>
      <h3 className="mb-2 flex items-center gap-1.5 text-label font-medium text-ink-secondary">
        <IconPaperclip className="h-4 w-4" />
        Carrier invoice files
      </h3>
      {showNotice ? (
        <p className="mb-2 flex items-center gap-1.5 text-label-sm text-warning">
          <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
          {canAttach
            ? renderAction
              ? "File storage is unavailable right now. Attaching, removing and downloading are paused."
              : "File storage is unavailable right now. Attaching and downloading are paused."
            : "File storage is unavailable right now. Downloading is paused."}
        </p>
      ) : null}
      {files.length === 0 ? (
        <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-label text-ink-mute">
          {emptyText}
        </p>
      ) : (
        <ul className="flex flex-col gap-1.5">
          {files.map((file) => (
            <li
              key={file.id}
              className="flex items-center justify-between gap-3 rounded-lg border border-hairline bg-canvas px-3.5 py-2.5"
            >
              <div className="flex min-w-0 items-center gap-2">
                <IconPaperclip className="h-4 w-4 shrink-0 text-ink-mute" />
                <span className="truncate text-label text-ink">
                  {file.filename}
                </span>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                {paused ? (
                  <span
                    aria-disabled="true"
                    aria-label={
                      labelledDownloads
                        ? `Download ${file.filename}`
                        : undefined
                    }
                    className="inline-flex shrink-0 cursor-not-allowed items-center gap-1 text-label-sm font-medium text-ink-mute"
                  >
                    <IconDownload className="h-3.5 w-3.5" />
                    Download
                  </span>
                ) : (
                  <a
                    href={downloadHref(file.id)}
                    aria-label={
                      labelledDownloads
                        ? `Download ${file.filename}`
                        : undefined
                    }
                    className={`inline-flex shrink-0 items-center gap-1 text-label-sm font-medium text-primary hover:underline${labelledDownloads ? " underline-offset-2" : ""}`}
                  >
                    <IconDownload className="h-3.5 w-3.5" />
                    Download
                  </a>
                )}
                {renderAction ? renderAction(file) : null}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
