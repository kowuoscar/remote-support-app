"use client";

import { Badge } from "@/components/ui/badge";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { EmptyState } from "@/components/ui/empty-state";
import { IconInbox } from "@/components/icons";
import { formatDate, formatWaitingTime } from "@/lib/format";
import { requestStatusToneByValue } from "@/lib/status";
import { requestDetailsSummary } from "@/components/requests/details/summary";
import { PendingRequestDecisionControls } from "@/components/manager/pending-request-decision-controls";
import { REQUEST_TYPE_LABEL, type PendingRequestItem } from "@/lib/api/types";

/**
 * The Manager's Pending Requests page (CONTEXT.md "Pending Requests"; request-types-and-flow
 * spec, Manager approval; manager-approves-requests ticket AC: "lists every Pending Approval
 * Request, longest-waiting first, with type, Client, Tester, Agent, requested details ... and
 * age, and approve and reject controls"). `items` arrives already sorted by the backend
 * (`PendingRequestsController`); this only renders it, the same "server orders, client only
 * renders" shape `ReviewQueueView` established. `now` comes from the server page so "how long it
 * has waited" renders the same on server and client.
 */
export function PendingRequestsView({ items, now }: { items: PendingRequestItem[]; now: string }) {
  if (items.length === 0) {
    return (
      <EmptyState
        icon={<IconInbox className="h-5 w-5" />}
        title="Nothing is waiting on you"
        description="A Provision or Replace Request lands here the moment a Tester submits one, or an Agent logs one, longest waiting first."
      />
    );
  }

  return (
    <TableScroll>
      <Table>
        <Thead>
          <Tr>
            <Th>Type</Th>
            <Th>Client</Th>
            <Th>Tester</Th>
            <Th>Agent</Th>
            <Th>Details</Th>
            <Th>Waiting</Th>
            {/* relative: keeps the sr-only label inside the table's scroll container on narrow screens */}
            <Th className="relative text-right">
              <span className="sr-only">Decision</span>
            </Th>
          </Tr>
        </Thead>
        <Tbody>
          {items.map((item) => (
            <Tr key={item.request.id}>
              <Td className="font-medium text-ink">
                {REQUEST_TYPE_LABEL[item.request.type]}
                <span className="mt-1 block">
                  <Badge tone={requestStatusToneByValue[item.request.status]}>Pending Approval</Badge>
                </span>
              </Td>
              <Td className="whitespace-nowrap text-ink-secondary">{item.clientName}</Td>
              <Td className="whitespace-nowrap text-ink-secondary">
                {item.request.raisedByUsername}
                {item.request.agentAuthored ? (
                  <span className="block text-[12px] text-ink-mute">Logged by {item.request.loggedByUsername}</span>
                ) : null}
              </Td>
              <Td className="whitespace-nowrap text-ink-secondary">{item.agentName}</Td>
              <Td className="max-w-[240px] text-ink-secondary">
                {requestDetailsSummary(item.request) ?? "—"}
                {item.request.description ? (
                  <span className="mt-1 block text-[12px] text-ink-mute">{item.request.description}</span>
                ) : null}
              </Td>
              <Td className="whitespace-nowrap">
                <time dateTime={item.waitingSince} title={`Submitted ${formatDate(item.waitingSince)}`}>
                  {formatWaitingTime(item.waitingSince, now)}
                </time>
              </Td>
              <Td className="text-right">
                <PendingRequestDecisionControls request={item.request} />
              </Td>
            </Tr>
          ))}
        </Tbody>
      </Table>
    </TableScroll>
  );
}
