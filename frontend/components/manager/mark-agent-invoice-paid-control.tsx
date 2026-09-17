"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconCoins } from "@/components/icons";
import { agentInvoiceActionUrl, type AgentInvoiceTarget } from "@/components/manager/agent-invoice-target";
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
  onPaid,
  ...target
}: AgentInvoiceTarget & { onPaid?: (invoice: AgentInvoiceDetail) => void }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function markPaid() {
    setPending(true);
    setError(null);
    try {
      const response = await fetch(agentInvoiceActionUrl(target as AgentInvoiceTarget, "paid"), { method: "POST" });
      if (!response.ok) {
        setError(
          response.status === 409
            ? "This invoice is no longer awaiting payment. Refresh to see its current status."
            : "Couldn't mark as paid. Try again.",
        );
        setPending(false);
        return;
      }
      if (onPaid) {
        onPaid((await response.json()) as AgentInvoiceDetail);
      }
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button type="button" variant="primary" size="sm" loading={pending} onClick={markPaid}>
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
