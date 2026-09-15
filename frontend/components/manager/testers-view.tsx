"use client";

import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconClients } from "@/components/icons";
import { CreateTesterDialog } from "@/components/manager/create-tester-dialog";
import type { TesterListItem } from "@/lib/api/types";

/**
 * Testers for one Client, on its detail view (manager-entity-setup ticket) — a Tester only
 * makes sense scoped to a Client, so this never lives at a top-level `/manager/testers` route.
 */
export function ManagerTestersView({
  clientId,
  testers,
}: {
  clientId: string;
  testers: TesterListItem[];
}) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-ink">Testers</h2>
        <CreateTesterDialog clientId={clientId} />
      </div>

      {testers.length === 0 ? (
        <EmptyState
          icon={<IconClients className="h-5 w-5" />}
          title="No testers yet"
          description="Add this client's first tester — they'll be able to sign in and submit Requests right away."
        />
      ) : (
        <TableScroll>
          <Table>
            <Thead>
              <Tr>
                <Th>Email</Th>
                <Th>Role</Th>
              </Tr>
            </Thead>
            <Tbody>
              {testers.map((tester) => (
                <Tr key={tester.id}>
                  <Td className="font-medium text-ink">{tester.username}</Td>
                  <Td>
                    {tester.isPrimaryContact ? (
                      <Badge tone="primary">Primary contact</Badge>
                    ) : (
                      <Badge tone="neutral">Tester</Badge>
                    )}
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
