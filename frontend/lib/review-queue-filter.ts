import type { ReviewQueueKind } from "@/lib/api/types";

/** The Review Queue's type filter. Shared by the server page (reading `?type=`) and the client view. */
export type ReviewQueueFilter = "ALL" | ReviewQueueKind;

/** The `?type=` value each filter is reflected as, so a filtered queue can be linked and reloaded. */
const FILTER_PARAM: Record<ReviewQueueKind, string> = {
  CLIENT_INVOICE: "client",
  AGENT_INVOICE: "agent",
};

export function reviewQueueFilterFromParam(type: string | string[] | undefined): ReviewQueueFilter {
  if (type === FILTER_PARAM.CLIENT_INVOICE) return "CLIENT_INVOICE";
  if (type === FILTER_PARAM.AGENT_INVOICE) return "AGENT_INVOICE";
  return "ALL";
}

/** The query string for a filter: empty for "All", `?type=…` otherwise. */
export function reviewQueueFilterSearch(filter: ReviewQueueFilter): string {
  return filter === "ALL" ? "" : `?type=${FILTER_PARAM[filter]}`;
}
