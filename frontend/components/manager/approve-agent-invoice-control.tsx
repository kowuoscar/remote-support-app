"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconCheckCircle } from "@/components/icons";
import { agentInvoiceActionUrl, type AgentInvoiceTarget } from "@/components/manager/agent-invoice-target";
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
  onApproved,
  ...target
}: AgentInvoiceTarget & { onApproved?: (invoice: AgentInvoiceDetail) => void }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function approve() {
    setPending(true);
    setError(null);
    try {
      const response = await fetch(agentInvoiceActionUrl(target as AgentInvoiceTarget, "approve"), {
        method: "POST",
      });
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
        onApproved((await response.json()) as AgentInvoiceDetail);
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
