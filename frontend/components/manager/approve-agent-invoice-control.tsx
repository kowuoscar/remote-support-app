"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconCheckCircle } from "@/components/icons";

/**
 * Manager approves a sent Agent Invoice (agent-invoice-submission-and-approval ticket AC:
 * "Manager can approve a sent Agent Invoice, moving it to status approved"). Single confirm
 * click, same shape as ApproveClientInvoiceControl: approving happens only after the Manager has
 * already reviewed the invoice (and applied any override) on this same page, so the click is the
 * natural conclusion of that review, not a surprise action reachable from a list.
 */
export function ApproveAgentInvoiceControl({ agentId }: { agentId: string }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function approve() {
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/agents/${agentId}/invoice/approve`, { method: "POST" });
      if (!response.ok) {
        setError("Couldn't approve. Try again.");
        setPending(false);
        return;
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
