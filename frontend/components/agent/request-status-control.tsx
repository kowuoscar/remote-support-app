"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  REQUEST_STATUS_LABEL,
  canCancelRequest,
  nextRequestStatus,
  type RequestStatusValue,
} from "@/lib/api/types";

/**
 * Agent quick action to progress or cancel a Request (agent-request-fulfillment ticket AC:
 * "Agent can move a Request from Submitted to In Progress, and from In Progress to Completed" /
 * "Agent can cancel a Request ... with a reason"). Mirrors fleet-status-controls.tsx's inline,
 * no-modal row actions — craft-floor.md: no dialog for a task that needs neither interruption nor
 * protected focus. Cancelling needs a short reason, so it expands a small inline form in place
 * rather than escalating to a `<dialog>` or a crude `prompt()`.
 */
export function RequestStatusControl({
  contractId,
  requestId,
  status,
}: {
  contractId: string;
  requestId: string;
  status: RequestStatusValue;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");

  const next = nextRequestStatus(status);
  const canCancel = canCancelRequest(status);

  async function submitStatus(body: { status: RequestStatusValue; cancellationReason?: string }) {
    setPending(true);
    setError(false);
    try {
      const response = await fetch(`/api/contracts/${contractId}/requests/${requestId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        setError(true);
        setPending(false);
        return;
      }
      // Reset immediately rather than relying on router.refresh() to remount this component:
      // the parent Server Component re-renders with fresh props, but this Client Component
      // instance (same key) stays mounted, so local `pending` would otherwise stay stuck `true`
      // forever and permanently disable the next action (e.g. In Progress -> Completed).
      setCancelling(false);
      setReason("");
      setPending(false);
      router.refresh();
    } catch {
      setError(true);
      setPending(false);
    }
  }

  async function advance() {
    if (!next) return;
    await submitStatus({ status: next });
  }

  async function confirmCancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!reason.trim()) return;
    await submitStatus({ status: "CANCELLED", cancellationReason: reason.trim() });
  }

  if (!next && !canCancel) {
    return <span className="text-[12px] text-ink-mute">No further changes</span>;
  }

  if (cancelling) {
    return (
      <form onSubmit={confirmCancel} className="flex flex-col items-start gap-1.5">
        <Input
          autoFocus
          required
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          disabled={pending}
          placeholder="Reason for cancelling"
          aria-label="Cancellation reason"
          className="h-7 w-48 text-[12px]"
        />
        <div className="flex items-center gap-1.5">
          <Button type="submit" variant="danger" size="sm" loading={pending}>
            Confirm cancel
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={pending}
            onClick={() => {
              setCancelling(false);
              setReason("");
              setError(false);
            }}
          >
            Back
          </Button>
        </div>
        {error ? (
          <span className="text-[11px] text-danger">Couldn&rsquo;t cancel. Try again.</span>
        ) : null}
      </form>
    );
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <div className="flex items-center gap-1.5">
        {next ? (
          <Button variant="row" size="sm" onClick={advance} loading={pending}>
            Mark {REQUEST_STATUS_LABEL[next]}
          </Button>
        ) : null}
        {canCancel ? (
          <Button variant="ghost" size="sm" disabled={pending} onClick={() => setCancelling(true)}>
            Cancel
          </Button>
        ) : null}
      </div>
      {error ? <span className="text-[11px] text-danger">Couldn&rsquo;t update. Try again.</span> : null}
    </div>
  );
}
