import { useMemo, useState } from "react";
import type { ContractOption } from "@/components/ui/contract-switcher";
import type { RequestListItem, RequestStatusValue } from "@/lib/api/types";

/**
 * Scopes a Requests list to the selected Contract and status filter, and tracks that selection —
 * shared by AgentRequestsView and ClientRequestsView, which otherwise carried identical copies.
 * Newest-first, matching each view's own prior ordering.
 */
export function useFilteredRequests(requests: RequestListItem[], contracts: ContractOption[]) {
  const [contractId, setContractId] = useState(contracts[0]?.id ?? "");
  const [status, setStatus] = useState<RequestStatusValue | "All">("All");

  const filtered = useMemo(() => {
    return requests
      .filter((r) => r.contractId === contractId)
      .filter((r) => status === "All" || r.status === status)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [requests, contractId, status]);

  return { contractId, setContractId, status, setStatus, filtered };
}
