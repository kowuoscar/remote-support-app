"use client";

import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconCheckCircle } from "@/components/icons";
import { usePostAction } from "@/lib/use-post-action";
import type { AgentInvoiceDetail } from "@/lib/api/types";

/**
 * Manager approves a sent Agent Invoice (agent-invoice-submission-and-approval ticket AC:
 * "Manager can approve a sent Agent Invoice, moving it to status approved"). Single confirm
 * click, same shape as ApproveClientInvoiceControl: approving happens only after the Manager has
 * already reviewed the invoice (and applied any override) on this same page, so the click is the
 * natural conclusion of that review, not a surprise action reachable from a list. `onApproved`
 * receives the approved invoice so a detail view can show its new state in place.
 */
export function ApproveAgentInvoiceControl({
  invoiceId,
  onApproved,
}: Readonly<{
  invoiceId: string;
  onApproved?: (invoice: AgentInvoiceDetail) => void;
}>) {
  const { pending, error, run } = usePostAction<AgentInvoiceDetail>(
    `/api/agent-invoices/${invoiceId}/approve`,
    "This invoice is no longer awaiting approval. Refresh to see its current status.",
    "Couldn't approve. Try again.",
  );

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button type="button" variant="primary" size="sm" loading={pending} onClick={() => run(onApproved)}>
        <IconCheckCircle className="h-4 w-4" />
        Approve
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
