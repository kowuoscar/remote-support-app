"use client";

import { useMemo, useState } from "react";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { SubmitRequestDialog } from "@/components/client/submit-request-dialog";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconInbox } from "@/components/icons";
import { formatRelativeAge } from "@/lib/format";
import { requestStatusToneByValue } from "@/lib/status";
import {
  REQUEST_STATUS_LABEL,
  REQUEST_TYPE_LABEL,
  type RequestListItem,
  type RequestStatusValue,
} from "@/lib/api/types";

const statusFilters: (RequestStatusValue | "All")[] = [
  "All",
  "SUBMITTED",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED",
];

export function ClientRequestsView({
  requests,
  contracts,
}: {
  requests: RequestListItem[];
  contracts: ContractOption[];
}) {
  const [contractId, setContractId] = useState(contracts[0]?.id ?? "");
  const [status, setStatus] = useState<RequestStatusValue | "All">("All");

  const filtered = useMemo(() => {
    return requests
      .filter((r) => r.contractId === contractId)
      .filter((r) => status === "All" || r.status === status)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [requests, contractId, status]);

  if (contracts.length === 0) {
    return (
      <EmptyState
        icon={<IconInbox className="h-5 w-5" />}
        title="No contracts yet"
        description="Once your company has a contract in place, you can submit Requests against it."
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <ContractSwitcher contracts={contracts} value={contractId} onChange={setContractId} />
          <div
            role="tablist"
            aria-label="Filter by status"
            className="inline-flex flex-wrap items-center gap-1 rounded-lg border border-hairline bg-canvas-soft p-1"
          >
            {statusFilters.map((value) => (
              <button
                key={value}
                role="tab"
                type="button"
                aria-selected={status === value}
                onClick={() => setStatus(value)}
                className={`rounded-md px-2.5 py-1.5 text-[13px] font-medium transition-colors ${
                  status === value ? "bg-canvas text-ink shadow-sm" : "text-ink-mute hover:text-ink"
                }`}
              >
                {value === "All" ? "All" : REQUEST_STATUS_LABEL[value]}
              </button>
            ))}
          </div>
        </div>
        <SubmitRequestDialog contracts={contracts} />
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          icon={<IconInbox className="h-5 w-5" />}
          title="No Requests match this filter"
          description="Everyone at your company's Requests show up here, not just your own. Submit one to get started."
        />
      ) : (
        <TableScroll>
          <Table>
            <Thead>
              <Tr>
                <Th>Type</Th>
                <Th>Raised by</Th>
                <Th>Status</Th>
                <Th>Created</Th>
              </Tr>
            </Thead>
            <Tbody>
              {filtered.map((request) => (
                <Tr key={request.id}>
                  <Td className="font-medium text-ink">
                    {REQUEST_TYPE_LABEL[request.type]}
                    {request.description ? (
                      <span className="mt-1 block max-w-[220px] font-normal text-[12px] text-ink-mute">
                        {request.description}
                      </span>
                    ) : null}
                  </Td>
                  <Td className="text-ink-secondary">{request.raisedByUsername}</Td>
                  <Td>
                    <Badge tone={requestStatusToneByValue[request.status]}>
                      {REQUEST_STATUS_LABEL[request.status]}
                    </Badge>
                  </Td>
                  <Td className="whitespace-nowrap text-ink-mute">
                    {formatRelativeAge(request.createdAt)}
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      )}
    </div>
  );
}
