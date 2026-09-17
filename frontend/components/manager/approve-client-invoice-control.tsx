"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconCheckCircle } from "@/components/icons";
import type { ClientInvoiceDetail } from "@/lib/api/types";

/**
 * Which Client Invoice to approve: by its own id (the Client Invoice detail page, any billing
 * month), or as a Contract's current-month invoice (the Contract page, until
 * invoice-summaries-on-contract-and-agent-pages moves that review to the detail page).
 */
type Target = { invoiceId: string; contractId?: never } | { contractId: string; invoiceId?: never };

function approveUrl(target: Target): string {
  return target.invoiceId !== undefined
    ? `/api/client-invoices/${target.invoiceId}/approve`
    : `/api/contracts/${target.contractId}/client-invoice/approve`;
}

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
  onApproved,
  ...target
}: Target & { onApproved?: (invoice: ClientInvoiceDetail) => void }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function approve() {
    setPending(true);
    setError(null);
    try {
      const response = await fetch(approveUrl(target as Target), { method: "POST" });
      if (!response.ok) {
        setError(
          response.status === 409
            ? "This invoice is no longer awaiting approval. Refresh to see its current status."
            : "Couldn't approve. Try again.",
        );
        setPending(false);
        return;
      }
      if (onApproved) {
        onApproved((await response.json()) as ClientInvoiceDetail);
      }
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button type="button" variant="primary" size="sm" loading={pending} onClick={approve}>
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
