"use client";

import { useRef } from "react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconClients } from "@/components/icons";
import { CreateTesterDialog } from "@/components/manager/create-tester-dialog";
import { Button } from "@/components/ui/button";
import { ResetPasswordDialog, type ResetPasswordDialogHandle } from "@/components/manager/reset-password-dialog";
import { LoginActivationDialog, type LoginActivationDialogHandle } from "@/components/manager/login-activation-dialog";
import { DeactivatedTag, LoginActivationButton } from "@/components/manager/login-activation-controls";
import { useAnnouncement } from "@/components/ui/use-announcement";
import type { TesterListItem } from "@/lib/api/types";

/**
 * Testers for one Client, on its detail view (manager-entity-setup ticket) — a Tester only
 * makes sense scoped to a Client, so this never lives at a top-level `/manager/testers` route.
 */
export function ManagerTestersView({ clientId, testers }: { clientId: string; testers: TesterListItem[] }) {
  const resetRef = useRef<ResetPasswordDialogHandle>(null);
  const deactivateRef = useRef<LoginActivationDialogHandle>(null);
  const reactivateRef = useRef<LoginActivationDialogHandle>(null);
  const [announcement, announce] = useAnnouncement();

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
                <Th className="text-right">
                  <span className="sr-only">Actions</span>
                </Th>
              </Tr>
            </Thead>
            <Tbody>
              {testers.map((tester) => {
                const deactivatedAt = tester.deactivatedAt ?? null;
                const deactivated = deactivatedAt !== null;
                return (
                  <Tr key={tester.id}>
                    <Td className="font-medium text-ink">
                      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <span className="break-all">{tester.username}</span>
                        {deactivatedAt ? <DeactivatedTag deactivatedAt={deactivatedAt} /> : null}
                      </div>
                    </Td>
                    <Td>
                      {tester.isPrimaryContact ? (
                        <Badge tone="primary">Primary contact</Badge>
                      ) : (
                        <Badge tone="neutral">Tester</Badge>
                      )}
                    </Td>
                    {/* Named "Actions" so the cell never repeats the email its button carries: the email cell stays the one cell that answers to the email. */}
                    <Td aria-label="Actions" className="text-right">
                      <div className="inline-flex flex-wrap justify-end gap-x-3 gap-y-1.5">
                        <Button
                          variant="row"
                          size="sm"
                          aria-label={`Reset password for ${tester.username}`}
                          onClick={() =>
                            resetRef.current?.open({
                              name: tester.username,
                              email: tester.username,
                              endpoint: `/api/clients/${clientId}/testers/${tester.id}/password`,
                              listLink: {
                                href: "/manager/clients",
                                label: "Back to the Clients list",
                              },
                            })
                          }
                        >
                          Reset password
                        </Button>
                        <LoginActivationButton
                          deactivated={deactivated}
                          person={tester.username}
                          onClick={() =>
                            (deactivated ? reactivateRef : deactivateRef).current?.open({
                              name: tester.username,
                              email: tester.username,
                              endpoint: `/api/clients/${clientId}/testers/${tester.id}/${deactivated ? "reactivate" : "deactivate"}`,
                              listLink: {
                                href: "/manager/clients",
                                label: "Back to the Clients list",
                              },
                            })
                          }
                        />
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        </TableScroll>
      )}
      <ResetPasswordDialog ref={resetRef} onReset={(email) => announce(`Password reset for ${email}.`)} />
      <LoginActivationDialog ref={deactivateRef} mode="deactivate" onChanged={(email) => announce(`Login deactivated for ${email}.`)} />
      <LoginActivationDialog ref={reactivateRef} mode="reactivate" onChanged={(email) => announce(`Login reactivated for ${email}.`)} />
      <output className="sr-only">{announcement}</output>
    </div>
  );
}
