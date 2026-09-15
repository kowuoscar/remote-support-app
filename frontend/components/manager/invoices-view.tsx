"use client";

import { useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Money } from "@/components/ui/money";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { EmptyState } from "@/components/ui/empty-state";
import { IconInbox } from "@/components/icons";
import { formatDate } from "@/lib/format";

export interface Approval {
  kind: "Client Invoice" | "Agent Invoice";
  id: string;
  subject: string;
  month: string;
  amount: number;
  currency: string;
  submittedAt: string;
}

type Filter = "all" | "Client Invoice" | "Agent Invoice";

export function ManagerInvoicesView({ approvals }: { approvals: Approval[] }) {
  const [filter, setFilter] = useState<Filter>("all");
  const [active, setActive] = useState<Approval | null>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);

  const filtered = useMemo(
    () =>
      filter === "all" ? approvals : approvals.filter((a) => a.kind === filter),
    [approvals, filter],
  );

  function openReview(approval: Approval) {
    setActive(approval);
    dialogRef.current?.showModal();
  }

  function closeReview() {
    dialogRef.current?.close();
    setActive(null);
  }

  return (
    <div className="flex flex-col gap-4">
      <div
        role="tablist"
        aria-label="Filter pending approvals"
        className="inline-flex w-fit items-center gap-1 rounded-lg border border-hairline bg-canvas-soft p-1"
      >
        {(["all", "Client Invoice", "Agent Invoice"] as const).map((value) => (
          <button
            key={value}
            role="tab"
            type="button"
            aria-selected={filter === value}
            onClick={() => setFilter(value)}
            className={`rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors ${
              filter === value
                ? "bg-canvas text-ink shadow-sm"
                : "text-ink-mute hover:text-ink"
            }`}
          >
            {value === "all" ? "All" : `${value}s`}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          icon={<IconInbox className="h-5 w-5" />}
          title="Nothing pending here"
          description="Every invoice in this category has already been reviewed. New submissions will appear oldest first."
        />
      ) : (
        <TableScroll>
          <Table>
            <Thead>
              <Tr>
                <Th>Submitted</Th>
                <Th>Type</Th>
                <Th>Subject</Th>
                <Th>Month</Th>
                <Th className="text-right">Amount</Th>
                <Th className="text-right">Action</Th>
              </Tr>
            </Thead>
            <Tbody>
              {filtered.map((approval) => (
                <Tr key={approval.id}>
                  <Td className="whitespace-nowrap text-ink-mute">
                    {formatDate(approval.submittedAt)}
                  </Td>
                  <Td>
                    <Badge tone={approval.kind === "Client Invoice" ? "info" : "primary"}>
                      {approval.kind}
                    </Badge>
                  </Td>
                  <Td className="font-medium text-ink">{approval.subject}</Td>
                  <Td className="whitespace-nowrap text-ink-mute">{approval.month}</Td>
                  <Td className="text-right">
                    <Money amount={approval.amount} currency={approval.currency} />
                  </Td>
                  <Td className="text-right">
                    <Button variant="primary" size="sm" onClick={() => openReview(approval)}>
                      Review
                    </Button>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      )}

      <dialog
        ref={dialogRef}
        onCancel={closeReview}
        onClick={(event) => {
          if (event.target === dialogRef.current) closeReview();
        }}
        className="m-auto w-[min(480px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        {active ? (
          <div className="flex flex-col gap-5 p-6">
            <div className="flex items-start justify-between gap-3">
              <div>
                <Badge tone={active.kind === "Client Invoice" ? "info" : "primary"}>
                  {active.kind}
                </Badge>
                <h2 className="mt-2 text-base font-semibold text-ink">{active.subject}</h2>
                <p className="text-[13px] text-ink-mute">{active.month}</p>
              </div>
              <span className="tnum text-xl font-semibold text-primary">
                <Money amount={active.amount} currency={active.currency} />
              </span>
            </div>
            <dl className="grid grid-cols-2 gap-y-2 text-[13px]">
              <dt className="text-ink-mute">Submitted</dt>
              <dd className="text-right text-ink">{formatDate(active.submittedAt)}</dd>
              <dt className="text-ink-mute">Status</dt>
              <dd className="text-right text-ink">Awaiting Manager approval</dd>
            </dl>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={closeReview}>
                Close
              </Button>
              <Button variant="primary" onClick={closeReview}>
                Approve
              </Button>
            </div>
          </div>
        ) : null}
      </dialog>
    </div>
  );
}
