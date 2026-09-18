"use client";

import { useMemo, useState } from "react";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconInbox } from "@/components/icons";
import { formatRelativeAge } from "@/lib/format";
import { requestStatusToneByValue } from "@/lib/status";
import { RequestStatusControl } from "@/components/agent/request-status-control";
import { LogRequestDialog } from "@/components/agent/log-request-dialog";
import { LogFeeDialog } from "@/components/agent/log-fee-dialog";
import { requestDetailsSummary } from "@/components/requests/details/summary";
import {
  REQUEST_STATUS_LABEL,
  REQUEST_TYPE_LABEL,
  type CatalogCarrierItem,
  type ContractTesterListItem,
  type RequestListItem,
  type RequestStatusValue,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

const AGENT_CARRIERS_HREF = "/agent/carriers";

const statusFilters: (RequestStatusValue | "All")[] = [
  "All",
  "SUBMITTED",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED",
];

/**
 * agent-request-fulfillment ticket AC: "Agent can move a Request from Submitted to In Progress,
 * and from In Progress to Completed" / "can cancel a Request ... with a reason" / "can log a
 * Request directly ... for one of their own Contracts". Status changes are inline row actions
 * (RequestStatusControl, mirroring fleet-status-controls.tsx); logging a new Request is scoped to
 * whichever Contract is selected in the switcher (LogRequestDialog), the same "Contract chosen,
 * then act within it" shape as the Fleet "Add smartphone" dialogs.
 */
export function AgentRequestsView({
  requests,
  contracts,
  testersByContract,
  smartphonesByContract = {},
  simCardsByContract = {},
  carriers = [],
}: {
  requests: RequestListItem[];
  contracts: ContractOption[];
  testersByContract: Record<string, ContractTesterListItem[]>;
  smartphonesByContract?: Record<string, SmartphoneListItem[]>;
  simCardsByContract?: Record<string, SimCardListItem[]>;
  /** The Agent's Country's Carrier catalog; every Contract of one Agent shares its Country. */
  carriers?: CatalogCarrierItem[];
}) {
  const [contractId, setContractId] = useState(contracts[0]?.id ?? "");
  const [status, setStatus] = useState<RequestStatusValue | "All">("All");
  const currency = contracts.find((c) => c.id === contractId)?.currency ?? "";
  const activeSmartphones = (smartphonesByContract[contractId] ?? []).filter((p) => p.status === "ACTIVE");
  const activeSimCards = (simCardsByContract[contractId] ?? []).filter((s) => s.status === "ACTIVE");

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
        description="Once a manager creates a contract for you, incoming Requests show up here."
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
        {contractId ? (
          <div className="flex items-center gap-2">
            <LogRequestDialog
              contractId={contractId}
              testers={testersByContract[contractId] ?? []}
              smartphones={activeSmartphones}
              simCards={activeSimCards}
              carriers={carriers}
              currency={currency}
              carriersHref={AGENT_CARRIERS_HREF}
            />
            <LogFeeDialog
              contractId={contractId}
              currency={currency}
              testers={testersByContract[contractId] ?? []}
              simCards={activeSimCards}
              carriers={carriers}
              carriersHref={AGENT_CARRIERS_HREF}
            />
          </div>
        ) : null}
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          icon={<IconInbox className="h-5 w-5" />}
          title="No Requests match this filter"
          description="Switch Contract or status above, or wait for a Tester to raise a new Request — it will queue here immediately."
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
                <Th>Actions</Th>
              </Tr>
            </Thead>
            <Tbody>
              {filtered.map((request) => (
                <Tr key={request.id}>
                  <Td className="font-medium text-ink">
                    {REQUEST_TYPE_LABEL[request.type]}
                    {requestDetailsSummary(request) ? (
                      <span className="mt-1 block max-w-[220px] font-normal text-[12px] text-ink-secondary">
                        {requestDetailsSummary(request)}
                      </span>
                    ) : null}
                    {request.description ? (
                      <span className="mt-1 block max-w-[220px] font-normal text-[12px] text-ink-mute">
                        {request.description}
                      </span>
                    ) : null}
                  </Td>
                  <Td className="text-ink-secondary">
                    {request.raisedByUsername}
                    {request.agentAuthored ? (
                      <span className="block text-[12px] text-ink-mute">
                        Logged by {request.loggedByUsername}
                      </span>
                    ) : null}
                  </Td>
                  <Td>
                    <Badge tone={requestStatusToneByValue[request.status]}>
                      {REQUEST_STATUS_LABEL[request.status]}
                    </Badge>
                    {request.status === "CANCELLED" && request.cancellationReason ? (
                      <span className="mt-1 block max-w-[220px] text-[12px] text-ink-mute">
                        {request.cancellationReason}
                      </span>
                    ) : null}
                  </Td>
                  <Td className="whitespace-nowrap text-ink-mute">
                    {formatRelativeAge(request.createdAt)}
                  </Td>
                  <Td>
                    <RequestStatusControl
                      contractId={request.contractId}
                      requestId={request.id}
                      status={request.status}
                      type={request.type}
                      currency={currency}
                      activeSmartphones={activeSmartphones}
                      activeSimCards={activeSimCards}
                      carriers={carriers}
                      carriersHref={AGENT_CARRIERS_HREF}
                      topupOptionId={request.topupOptionId}
                      topupOptionPrice={request.topupOptionPrice}
                    />
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
