"use client";

import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconInbox } from "@/components/icons";
import { RequestStatusControl } from "@/components/agent/request-status-control";
import { LogRequestDialog } from "@/components/agent/log-request-dialog";
import { LogFeeDialog } from "@/components/agent/log-fee-dialog";
import { RequestCreatedCell, RequestStatusCell, RequestTypeCell } from "@/components/requests/request-list-cells";
import { StatusFilterTabs } from "@/components/requests/status-filter-tabs";
import { useFilteredRequests } from "@/components/requests/use-filtered-requests";
import {
  type CatalogCarrierItem,
  type ContractTesterListItem,
  type RequestListItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

const AGENT_CARRIERS_HREF = "/agent/carriers";

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
  const { contractId, setContractId, status, setStatus, filtered } = useFilteredRequests(requests, contracts);
  const currency = contracts.find((c) => c.id === contractId)?.currency ?? "";
  const activeSmartphones = (smartphonesByContract[contractId] ?? []).filter((p) => p.status === "ACTIVE");
  const activeSimCards = (simCardsByContract[contractId] ?? []).filter((s) => s.status === "ACTIVE");

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
          <StatusFilterTabs status={status} onChange={setStatus} />
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
                  <RequestTypeCell request={request} />
                  <Td className="text-ink-secondary">
                    {request.raisedByUsername}
                    {request.agentAuthored ? (
                      <span className="block text-[12px] text-ink-mute">
                        Logged by {request.loggedByUsername}
                      </span>
                    ) : null}
                  </Td>
                  <RequestStatusCell request={request} showCancellationReason />
                  <RequestCreatedCell request={request} />
                  <Td>
                    <RequestStatusControl
                      request={request}
                      currency={currency}
                      activeSmartphones={activeSmartphones}
                      activeSimCards={activeSimCards}
                      carriers={carriers}
                      carriersHref={AGENT_CARRIERS_HREF}
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
