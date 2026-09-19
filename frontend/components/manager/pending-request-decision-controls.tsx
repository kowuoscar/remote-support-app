"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconCheckCircle } from "@/components/icons";
import type { RequestListItem } from "@/lib/api/types";
import {
  REQUEST_APPROVAL_BODY_BUILDERS,
  REQUEST_APPROVAL_NEEDS_FORM,
  REQUEST_APPROVAL_PAYLOAD_COMPONENTS,
} from "./approval/registry";

const CONFLICT_MESSAGE = "This Request is no longer Pending Approval. Refresh to see its current status.";

/**
 * The Manager's approve/reject row actions on the Pending Requests page (request-types-and-flow
 * spec, Manager approval; manager-approves-requests ticket AC: "approve and reject controls").
 * Approve is a single confirm click (row-styled per DESIGN.md's Pill-Is-Primary Rule — many rows,
 * so never the pill primary); reject expands the same small inline reason form
 * `RequestStatusControl`'s own cancel action already established, rather than a `<dialog>` or
 * `prompt()`, since neither interruption nor protected focus is needed here (craft-floor.md). A
 * 409 (the Request stopped being Pending Approval under the Manager, e.g. another tab already
 * decided it) gets its own copy telling them to refresh, distinct from a generic failure.
 *
 * <p>manager-decides-return-disposition ticket: takes the whole {@link RequestListItem} (not just
 * its id — mirrors `RequestStatusControl`'s own prop-shape change) since approving a Return with a
 * company-owned unit needs a Disposition per such unit (spec.md Solution: "Approving a Return
 * requires a Disposition for every company-owned unit in the same action; without them the
 * approval is refused"). A unit that already has one (a Client-owned Smartphone, or any unit once
 * decided) shows nothing to choose here — the row's own Details cell already names it via
 * `requestDetailsSummary`. A Return with nothing left to choose approves exactly as any other type
 * always has: one click, no body.
 *
 * <p>Which type gets an extra approval form, what it renders, and how it turns into the approve
 * body is delegated to `./approval/registry` (review finding on this feature's finisher pass) —
 * this control itself no longer knows `RETURN`/Disposition exist, mirroring how
 * `components/agent/completion/`'s registries keep `RequestStatusControl` generic over type.
 */
export function PendingRequestDecisionControls({ request }: { request: RequestListItem }) {
  const requestId = request.id;
  const needsForm = REQUEST_APPROVAL_NEEDS_FORM[request.type]?.(request) ?? false;
  const ApprovalPayload = REQUEST_APPROVAL_PAYLOAD_COMPONENTS[request.type];
  const buildApprovalBody = REQUEST_APPROVAL_BODY_BUILDERS[request.type];

  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [decided, setDecided] = useState<"APPROVED" | "REJECTED" | null>(null);

  async function approve(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setPending(true);
    setError(null);
    try {
      const init: RequestInit = { method: "POST" };
      if (needsForm && event && buildApprovalBody) {
        const formData = new FormData(event.currentTarget);
        init.headers = { "Content-Type": "application/json" };
        init.body = JSON.stringify(buildApprovalBody(request, formData));
      }
      const response = await fetch(`/api/requests/${requestId}/approve`, init);
      if (!response.ok) {
        setError(response.status === 409 ? CONFLICT_MESSAGE : "Couldn't approve. Try again.");
        setPending(false);
        return;
      }
      setDecided("APPROVED");
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  async function confirmReject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!reason.trim()) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/requests/${requestId}/reject`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: reason.trim() }),
      });
      if (!response.ok) {
        setError(response.status === 409 ? CONFLICT_MESSAGE : "Couldn't reject. Try again.");
        setPending(false);
        return;
      }
      setDecided("REJECTED");
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  if (decided) {
    return (
      <span className="text-[12px] text-ink-mute">{decided === "APPROVED" ? "Approved" : "Rejected"}</span>
    );
  }

  if (rejecting) {
    return (
      <form onSubmit={confirmReject} className="flex flex-col items-end gap-1.5">
        <Input
          autoFocus
          required
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          disabled={pending}
          placeholder="Reason for rejecting"
          aria-label="Rejection reason"
          className="h-7 w-48 text-[12px]"
        />
        <div className="flex items-center gap-1.5">
          <Button type="submit" variant="danger" size="sm" loading={pending}>
            Confirm reject
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={pending}
            onClick={() => {
              setRejecting(false);
              setReason("");
              setError(null);
            }}
          >
            Back
          </Button>
        </div>
        {error ? <span className="text-[11px] text-danger">{error}</span> : null}
      </form>
    );
  }

  if (needsForm && ApprovalPayload) {
    return (
      <form onSubmit={approve} className="flex flex-col items-end gap-1.5">
        <ApprovalPayload request={request} disabled={pending} />
        <div className="flex items-center gap-1.5">
          <Button type="submit" variant="row" size="sm" loading={pending}>
            <IconCheckCircle className="h-3.5 w-3.5" />
            Approve
          </Button>
          <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={() => setRejecting(true)}>
            Reject
          </Button>
        </div>
        {error ? <span className="text-[11px] text-danger">{error}</span> : null}
      </form>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <div className="flex items-center gap-1.5">
        <Button type="button" variant="row" size="sm" loading={pending} onClick={() => approve()}>
          <IconCheckCircle className="h-3.5 w-3.5" />
          Approve
        </Button>
        <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={() => setRejecting(true)}>
          Reject
        </Button>
      </div>
      {error ? <span className="text-[11px] text-danger">{error}</span> : null}
    </div>
  );
}
