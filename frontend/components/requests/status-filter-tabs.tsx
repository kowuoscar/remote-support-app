import { REQUEST_STATUS_LABEL, type RequestStatusValue } from "@/lib/api/types";

export const REQUEST_STATUS_FILTERS: (RequestStatusValue | "All")[] = [
  "All",
  "PENDING_APPROVAL",
  "SUBMITTED",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED",
  "REJECTED",
];

/**
 * The status-filter tab strip shared by AgentRequestsView and ClientRequestsView — same markup
 * and classes each caller had inline, so rendering is unchanged.
 */
export function StatusFilterTabs({
  status,
  onChange,
}: {
  status: RequestStatusValue | "All";
  onChange: (status: RequestStatusValue | "All") => void;
}) {
  return (
    <div
      role="tablist"
      aria-label="Filter by status"
      className="inline-flex flex-wrap items-center gap-1 rounded-lg border border-hairline bg-canvas-soft p-1"
    >
      {REQUEST_STATUS_FILTERS.map((value) => (
        <button
          key={value}
          role="tab"
          type="button"
          aria-selected={status === value}
          onClick={() => onChange(value)}
          className={`rounded-md px-2.5 py-1.5 text-[13px] font-medium transition-colors ${
            status === value ? "bg-canvas text-ink shadow-sm" : "text-ink-mute hover:text-ink"
          }`}
        >
          {value === "All" ? "All" : REQUEST_STATUS_LABEL[value]}
        </button>
      ))}
    </div>
  );
}
