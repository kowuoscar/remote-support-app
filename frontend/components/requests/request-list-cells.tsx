import { Badge } from "@/components/ui/badge";
import { Td } from "@/components/ui/table";
import { formatRelativeAge } from "@/lib/format";
import { requestStatusToneByValue } from "@/lib/status";
import { requestDetailsSummary } from "@/components/requests/details/summary";
import { REQUEST_STATUS_LABEL, REQUEST_TYPE_LABEL, type RequestListItem } from "@/lib/api/types";

/**
 * The "Type" cell shared by AgentRequestsView and ClientRequestsView — the type's label, its
 * per-type details summary line, and the Request's own free-text description, if any. Extracted
 * with no change to markup or classes.
 */
export function RequestTypeCell({ request }: { request: RequestListItem }) {
  const summary = requestDetailsSummary(request);
  return (
    <Td className="font-medium text-ink">
      {REQUEST_TYPE_LABEL[request.type]}
      {summary ? (
        <span className="mt-1 block max-w-[220px] font-normal text-[12px] text-ink-secondary">{summary}</span>
      ) : null}
      {request.description ? (
        <span className="mt-1 block max-w-[220px] font-normal text-[12px] text-ink-mute">
          {request.description}
        </span>
      ) : null}
    </Td>
  );
}

/**
 * The "Status" cell shared by AgentRequestsView and ClientRequestsView: the status Badge, plus a
 * Rejected Request's own reason (both views) and — Agent only, `showCancellationReason` — a
 * Cancelled Request's own reason. Extracted with no change to markup, classes or which role sees
 * which reason.
 */
export function RequestStatusCell({
  request,
  showCancellationReason = false,
}: {
  request: RequestListItem;
  showCancellationReason?: boolean;
}) {
  return (
    <Td>
      <Badge tone={requestStatusToneByValue[request.status]}>{REQUEST_STATUS_LABEL[request.status]}</Badge>
      {showCancellationReason && request.status === "CANCELLED" && request.cancellationReason ? (
        <span className="mt-1 block max-w-[220px] text-[12px] text-ink-mute">{request.cancellationReason}</span>
      ) : null}
      {request.status === "REJECTED" && request.rejectionReason ? (
        <span className="mt-1 block max-w-[220px] text-[12px] text-ink-mute">{request.rejectionReason}</span>
      ) : null}
    </Td>
  );
}

/** The "Created" cell shared by AgentRequestsView and ClientRequestsView. */
export function RequestCreatedCell({ request }: { request: RequestListItem }) {
  return <Td className="whitespace-nowrap text-ink-mute">{formatRelativeAge(request.createdAt)}</Td>;
}
