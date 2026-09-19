"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  REQUEST_COMPLETION_BODY_BUILDERS,
  REQUEST_COMPLETION_COMPONENTS,
  REQUEST_COMPLETION_NEEDS_OWN_FORM,
} from "@/components/agent/completion/registry";
import {
  REQUEST_STATUS_LABEL,
  canCancelRequest,
  nextRequestStatus,
  requestTypeCanCarryFee,
  type CatalogCarrierItem,
  type RequestListItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";

/**
 * Agent quick action to progress or cancel a Request (agent-request-fulfillment ticket AC:
 * "Agent can move a Request from Submitted to In Progress, and from In Progress to Completed" /
 * "Agent can cancel a Request ... with a reason"). Mirrors fleet-status-controls.tsx's inline,
 * no-modal row actions — craft-floor.md: no dialog for a task that needs neither interruption nor
 * protected focus. Cancelling needs a short reason, so it expands a small inline form in place
 * rather than escalating to a `<dialog>` or a crude `prompt()`.
 *
 * <p>fee-logging-and-provisioning ticket: completing a Request whose type can carry a Fee (Topup,
 * Provision Smartphone, Provision SIM, Other — {@link requestTypeCanCarryFee}) expands the same
 * inline form to also collect the Fee amount. Reboot and SIM Swap complete exactly as before — a
 * single click, no form — since neither can ever carry a Fee.
 *
 * <p>provision-request-details ticket: the type-specific completion fields (a Provision
 * Smartphone/SIM Request's own new-style, narrow input, or the legacy full form for a Request that
 * predates this ticket) come from `REQUEST_COMPLETION_COMPONENTS` — one piece per type, registered
 * the same way `REQUEST_DETAILS_COMPONENTS` registers a submission-time details section — so this
 * shell only ever grows a registry entry, never a body of per-type conditionals. This takes the
 * whole {@link RequestListItem} (not a scattering of its own fields) so a piece can read whatever
 * of that Request's own fields it needs without this shell knowing about them.
 */
export function RequestStatusControl({
  request,
  currency,
  activeSmartphones = [],
  activeSimCards = [],
  carriers = [],
  carriersHref = "/agent/carriers",
}: {
  request: RequestListItem;
  currency: string;
  activeSmartphones?: SmartphoneListItem[];
  activeSimCards?: SimCardListItem[];
  carriers?: CatalogCarrierItem[];
  carriersHref?: string;
}) {
  const { contractId, id: requestId, status, type, topupOptionId, topupOptionPrice } = request;
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");
  const [completing, setCompleting] = useState(false);

  const next = nextRequestStatus(status);
  const canCancel = canCancelRequest(status);
  const canCarryFee = requestTypeCanCarryFee(type);
  // A type that can never carry a Fee (e.g. RETURN) may still need its own completing form — a
  // cancelled SIM Card's effective date, so far (manager-decides-return-disposition ticket).
  const completingNeedsOwnForm = Boolean(REQUEST_COMPLETION_NEEDS_OWN_FORM[type]?.(request));
  const completingNeedsForm = next === "COMPLETED" && (canCarryFee || completingNeedsOwnForm);
  const CompletionComponent = REQUEST_COMPLETION_COMPONENTS[type];

  function resetLocalState() {
    // Reset immediately rather than relying on router.refresh() to remount this component: the
    // parent Server Component re-renders with fresh props, but this Client Component instance
    // (same key) stays mounted, so a stale `pending`/`completing` would otherwise stay stuck
    // forever and permanently disable the next action.
    setCancelling(false);
    setReason("");
    setCompleting(false);
    setPending(false);
  }

  async function submitStatus(body: Record<string, unknown>): Promise<{ ok: boolean; completionNote?: string }> {
    const response = await fetch(`/api/contracts/${contractId}/requests/${requestId}/status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) return { ok: false };
    const data = await response.json().catch(() => null);
    return { ok: true, completionNote: data?.completionNote ?? undefined };
  }

  async function logFee(amount: number, description: string): Promise<boolean> {
    const response = await fetch(`/api/contracts/${contractId}/fees`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        requestId,
        feeType: type,
        amount,
        description: description || undefined,
        // The Fee stays linked to the same Option the Request itself named at submission
        // (reboot-and-topup-details ticket AC) — the amount above is whatever the Agent
        // submitted, adjusted or not from the pre-filled suggestion below.
        topupOptionId: type === "TOPUP" ? topupOptionId : undefined,
      }),
    });
    return response.ok;
  }

  async function advance() {
    if (!next) return;
    if (completingNeedsForm) {
      setCompleting(true);
      setError(null);
      setNote(null);
      return;
    }
    setPending(true);
    setError(null);
    setNote(null);
    try {
      const { ok } = await submitStatus({ status: next });
      if (!ok) {
        setError("Couldn't update. Try again.");
        setPending(false);
        return;
      }
      resetLocalState();
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  async function confirmComplete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    setNote(null);

    const formData = new FormData(event.currentTarget);
    const amount = Number(formData.get("amount"));
    const description = String(formData.get("description") ?? "").trim();

    // Payload-building lives in the same per-type registry as rendering (code review finding: this
    // used to be an `if/else if` cascade duplicating `REQUEST_COMPLETION_COMPONENTS`'s own list of
    // types) — a type with no builder (Reboot, Topup, SIM Swap, Other, Replace Smartphone) sends
    // nothing beyond the base body.
    const buildBody = REQUEST_COMPLETION_BODY_BUILDERS[type];
    const statusBody: Record<string, unknown> = {
      status: "COMPLETED",
      ...(buildBody ? buildBody(request, formData) : {}),
    };

    try {
      const { ok, completionNote } = await submitStatus(statusBody);
      if (!ok) {
        setError("Couldn't complete the request. Try again.");
        setPending(false);
        return;
      }

      // A type that can never carry a Fee (e.g. RETURN, opened here only for its own completion
      // fields) never logs one — the shell's own amount/description inputs aren't even rendered
      // for it below.
      const feeOk = canCarryFee ? await logFee(amount, description) : true;
      if (!feeOk) {
        setError("Request completed, but logging the fee failed — log it separately from Fleet/Requests.");
        resetLocalState();
        router.refresh();
        return;
      }

      resetLocalState();
      if (completionNote) setNote(completionNote);
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  async function confirmCancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!reason.trim()) return;
    setPending(true);
    setError(null);
    try {
      const { ok } = await submitStatus({ status: "CANCELLED", cancellationReason: reason.trim() });
      if (!ok) {
        setError("Couldn't cancel. Try again.");
        setPending(false);
        return;
      }
      resetLocalState();
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
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

  if (completing) {
    return (
      <form onSubmit={confirmComplete} className="flex w-64 flex-col items-start gap-2">
        {canCarryFee ? (
          <>
            <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
              Fee amount ({currency})
              <Input
                autoFocus
                type="number"
                name="amount"
                min="0.01"
                step="0.01"
                required
                disabled={pending}
                // reboot-and-topup-details ticket AC: pre-filled from the Request's own Topup
                // Option, still editable — a plain `defaultValue`, not a controlled field.
                defaultValue={type === "TOPUP" && topupOptionPrice != null ? topupOptionPrice : undefined}
                className="h-7 text-[12px]"
              />
            </label>
            <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
              Description (optional)
              <Input name="description" disabled={pending} className="h-7 text-[12px]" />
            </label>
          </>
        ) : null}

        {CompletionComponent ? (
          <CompletionComponent
            request={request}
            carriers={carriers}
            carriersHref={carriersHref}
            currency={currency}
            activeSmartphones={activeSmartphones}
            activeSimCards={activeSimCards}
            disabled={pending}
          />
        ) : null}

        <div className="flex items-center gap-1.5 pt-0.5">
          <Button
            type="submit"
            variant="row"
            size="sm"
            loading={pending}
            disabled={
              type === "PROVISION_SIM" &&
              !request.requestedFlavor &&
              !carriers.some((carrier) => carrier.archivedAt === null)
            }
          >
            Mark Completed
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={pending}
            onClick={() => {
              setCompleting(false);
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
      {error ? <span className="text-[11px] text-danger">{error}</span> : null}
      {note ? <span className="text-[11px] text-warning">{note}</span> : null}
    </div>
  );
}
