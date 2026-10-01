"use client";

import { useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconClients } from "@/components/icons";
import { CreateTesterDialog } from "@/components/manager/create-tester-dialog";
import { Button } from "@/components/ui/button";
import { ResetPasswordDialog, type ResetPasswordDialogHandle } from "@/components/manager/reset-password-dialog";
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
  const resetRef = useRef<ResetPasswordDialogHandle>(null);
  const [resetEmail, setResetEmail] = useState<string | null>(null);

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
                <Th>
                  <span className="sr-only">Actions</span>
                </Th>
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
                  {/* Named "Actions" so the cell never repeats the email its button carries: the email cell stays the one cell that answers to the email. */}
                  <Td aria-label="Actions">
                    <Button
                      variant="row"
                      size="sm"
                      aria-label={`Reset password for ${tester.username}`}
                      onClick={() =>
                        resetRef.current?.open({
                          name: tester.username,
                          email: tester.username,
                          endpoint: `/api/clients/${clientId}/testers/${tester.id}/password`,
                          listLink: { href: "/manager/clients", label: "Back to the Clients list" },
                        })
                      }
                    >
                      Reset password
                    </Button>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      )}
      <ResetPasswordDialog ref={resetRef} onReset={setResetEmail} />
      <output className="sr-only">{resetEmail ? `Password reset for ${resetEmail}.` : ""}</output>
    </div>
  );
}
