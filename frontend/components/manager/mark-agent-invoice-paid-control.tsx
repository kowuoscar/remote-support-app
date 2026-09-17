"use client";

import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconCoins } from "@/components/icons";
import { usePostAction } from "@/lib/use-post-action";
import type { AgentInvoiceDetail } from "@/lib/api/types";

/**
 * Manager marks an approved Agent Invoice as paid (agent-invoice-submission-and-approval ticket
 * AC: "Manager can mark an approved Agent Invoice as paid, moving it to status paid; no payment
 * is executed by the app"). Purely a status flag the Manager sets once payment has happened
 * outside the app (spec.md Non-goals: no payment-processor integration) — same single-confirm
 * shape as ApproveAgentInvoiceControl. `onPaid` receives the paid invoice so a detail view can
 * show its final state in place.
 */
export function MarkAgentInvoicePaidControl({
  invoiceId,
  onPaid,
}: Readonly<{
  invoiceId: string;
  onPaid?: (invoice: AgentInvoiceDetail) => void;
}>) {
  const { pending, error, run } = usePostAction<AgentInvoiceDetail>(
    `/api/agent-invoices/${invoiceId}/paid`,
    "This invoice is no longer awaiting payment. Refresh to see its current status.",
    "Couldn't mark as paid. Try again.",
  );

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button type="button" variant="primary" size="sm" loading={pending} onClick={() => run(onPaid)}>
        <IconCoins className="h-4 w-4" />
        Mark paid
      </Button>
      {error ? (
        <p role="alert" className="flex items-center gap-1.5 text-[12px] text-danger">
          <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
          {error}
        </p>
      ) : null}
    </div>
  );
}
