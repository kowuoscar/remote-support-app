"use client";

import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconCheckCircle } from "@/components/icons";
import { usePostAction } from "@/lib/use-post-action";
import type { ClientInvoiceDetail } from "@/lib/api/types";

/**
 * Manager approves a sent Client Invoice (client-invoice-submission-and-visibility ticket AC:
 * "Manager can approve it, moving it to status approved"). A single confirm click is enough here
 * (unlike SendClientInvoiceControl's two-step confirm): approving happens only after the Manager
 * has already reviewed the invoice and its carrier files on this same page, so the reviewing
 * itself is the deliberate step — the click is the natural conclusion of that review, not a
 * surprise action reachable from a list. `onApproved` receives the approved invoice so a detail
 * view can show its final state in place.
 */
export function ApproveClientInvoiceControl({
  invoiceId,
  onApproved,
}: Readonly<{
  invoiceId: string;
  onApproved?: (invoice: ClientInvoiceDetail) => void;
}>) {
  const { pending, error, run } = usePostAction<ClientInvoiceDetail>(
    `/api/client-invoices/${invoiceId}/approve`,
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
