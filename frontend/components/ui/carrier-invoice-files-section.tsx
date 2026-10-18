import type { ReactNode } from "react";
import {
  IconAlertTriangle,
  IconDownload,
  IconPaperclip,
} from "@/components/icons";
import { formatDate } from "@/lib/format";

/**
 * The "Carrier invoice files" section of a Client Invoice, shared by the Agent's card, the
 * Manager's invoice detail and the Tester's Invoices page. While file storage is unavailable
 * (`filesAvailable` false, carrier-invoice-object-storage spec) the names stay listed, each
 * Download turns into non-interactive text, and one notice says what is paused. A file retention
 * deleted (`deletedAt` set, carrier-invoice-retention spec) keeps its row and name and shows plain
 * "Deleted on {date}" where Download was, whether or not storage is up; the notice shows only when
 * something is really paused (the attach control, or a file that is not deleted) and names
 * downloading unless every listed file is deleted. The Attach control itself stays with the caller (it
 * sits in the card header); `canAttach` only tells this section whether the notice should mention
 * attaching.
 */
export function CarrierInvoiceFilesSection({
  files,
  downloadHref,
  emptyText,
  filesAvailable,
  canAttach = false,
  labelledDownloads = false,
  renderRemove,
}: {
  files: { id: string; filename: string; deletedAt: string | null }[];
  downloadHref: (fileId: string) => string;
  emptyText: string;
  filesAvailable: boolean;
  canAttach?: boolean;
  /** Give each Download the accessible name `Download {filename}` (the Manager's view does). */
  labelledDownloads?: boolean;
  /**
   * The Agent's Remove for one file, beside its Download (an editable draft only). When given and
   * there is a file to remove, the paused notice names removing too: it says what the section offers.
   */
  renderRemove?: (file: { id: string; filename: string; deletedAt: string | null }) => ReactNode;
}) {
  const paused = !filesAvailable;
  const hasLiveFile = files.some((file) => file.deletedAt === null);
  const showNotice = paused && (canAttach || hasLiveFile);
  // Downloading is left out only when there are files and every one of them is deleted.
  const namesDownloading = hasLiveFile || files.length === 0;
  const pausedActions = [
    ...(canAttach ? ["Attaching"] : []),
    ...(canAttach && renderRemove && files.length > 0 ? ["removing"] : []),
    ...(namesDownloading ? [canAttach ? "downloading" : "Downloading"] : []),
  ];

  return (
    <div>
      <h3 className="mb-2 flex items-center gap-1.5 text-label font-medium text-ink-secondary">
        <IconPaperclip className="h-4 w-4" />
        Carrier invoice files
      </h3>
      {showNotice ? (
        <p className="mb-2 flex items-center gap-1.5 text-label-sm text-warning">
          <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
          {`File storage is unavailable right now. ${pausedSentence(pausedActions)}`}
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
              className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1.5 rounded-lg border border-hairline bg-canvas px-3.5 py-2.5"
            >
              <div className="flex min-w-0 flex-1 basis-48 items-center gap-2">
                <IconPaperclip className="h-4 w-4 shrink-0 text-ink-mute" />
                <span className="text-label text-ink wrap-anywhere sm:truncate">
                  {file.filename}
                </span>
              </div>
              <div className="ml-auto flex flex-wrap items-center justify-end gap-x-3 gap-y-1.5">
                {file.deletedAt !== null ? (
                  <span className="shrink-0 text-label-sm text-ink-mute">
                    Deleted on {formatDate(file.deletedAt)}
                  </span>
                ) : paused ? (
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
                {renderRemove ? renderRemove(file) : null}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** "Attaching, removing and downloading are paused." from whichever of the three are really paused. */
function pausedSentence(actions: string[]): string {
  const list =
    actions.length > 1 ? `${actions.slice(0, -1).join(", ")} and ${actions[actions.length - 1]}` : actions[0];
  return `${list} ${actions.length > 1 ? "are" : "is"} paused.`;
}
