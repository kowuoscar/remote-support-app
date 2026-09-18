import type { RequestListItem, RequestTypeValue } from "@/lib/api/types";

type SummaryRenderer = (request: RequestListItem) => string | null;

/**
 * One "details summary" renderer per type, for the Requests lists (reboot-and-topup-details
 * ticket AC: "The Tester's and the Agent's Requests lists summarise the details on each row").
 * A later ticket adding a type's details adds one entry here alongside its `registry.tsx` entry;
 * a type with none yet (or a legacy Request that predates this ticket, so its fields are all
 * absent) renders nothing extra — the row still shows its type and, where given, its description,
 * exactly as before this ticket.
 */
const REQUEST_DETAILS_SUMMARY: Partial<Record<RequestTypeValue, SummaryRenderer>> = {
  REBOOT: (request) =>
    request.targetSmartphoneModel ? `Smartphone: ${request.targetSmartphoneModel}` : null,
  TOPUP: (request) => {
    if (!request.targetSimCardNumber) return null;
    return request.topupOptionName
      ? `SIM Card: ${request.targetSimCardNumber} · ${request.topupOptionName}`
      : `SIM Card: ${request.targetSimCardNumber}`;
  },
};

/** The one-line details summary for `request`'s row, or `null` when its type has none. */
export function requestDetailsSummary(request: RequestListItem): string | null {
  return REQUEST_DETAILS_SUMMARY[request.type]?.(request) ?? null;
}
