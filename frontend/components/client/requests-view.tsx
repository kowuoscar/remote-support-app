"use client";

import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { SubmitRequestDialog } from "@/components/client/submit-request-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconInbox } from "@/components/icons";
import { RequestCreatedCell, RequestStatusCell, RequestTypeCell } from "@/components/requests/request-list-cells";
import { StatusFilterTabs } from "@/components/requests/status-filter-tabs";
import { useFilteredRequests } from "@/components/requests/use-filtered-requests";
import {
  type CatalogCarrierItem,
  type RequestListItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

export function ClientRequestsView({
  requests,
  contracts,
  smartphonesByContract = {},
  simCardsByContract = {},
  carriersByContract = {},
}: {
  requests: RequestListItem[];
  contracts: ContractOption[];
  smartphonesByContract?: Record<string, SmartphoneListItem[]>;
  simCardsByContract?: Record<string, SimCardListItem[]>;
  carriersByContract?: Record<string, CatalogCarrierItem[]>;
}) {
  const { contractId, setContractId, status, setStatus, filtered } = useFilteredRequests(requests, contracts);

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
          <StatusFilterTabs status={status} onChange={setStatus} />
        </div>
        <SubmitRequestDialog
          contracts={contracts}
          smartphonesByContract={smartphonesByContract}
          simCardsByContract={simCardsByContract}
          carriersByContract={carriersByContract}
        />
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
                  <RequestTypeCell request={request} />
                  <Td className="text-ink-secondary">{request.raisedByUsername}</Td>
                  <RequestStatusCell request={request} />
                  <RequestCreatedCell request={request} />
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      )}
    </div>
  );
}
