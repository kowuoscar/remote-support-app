"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconCheckCircle } from "@/components/icons";
import { DISPOSITION_LABEL, type DispositionValue, type ReturnedUnitItem, type RequestListItem } from "@/lib/api/types";

const CONFLICT_MESSAGE = "This Request is no longer Pending Approval. Refresh to see its current status.";

/**
 * Two choices per unit kind (spec.md Disposition table; agent-stock ticket AC: "The Manager can
 * choose Kept in Stock for a company-owned Smartphone or a SIM Card when approving a Return").
 */
function dispositionOptionsFor(unit: ReturnedUnitItem): DispositionValue[] {
  return unit.smartphoneId ? ["POSTED_TO_COMPANY", "KEPT_IN_STOCK"] : ["CANCELLED", "KEPT_IN_STOCK"];
}

function unitLabel(unit: ReturnedUnitItem): string {
  return unit.smartphoneModel ?? unit.simCardNumber ?? "Unit";
}

/**
 * agent-stock ticket AC: "on a Postpaid SIM the picker notes that the carrier keeps charging with
 * no Client to bill" (spec.md user story 17) — shown next to a Postpaid SIM Card unit's picker
 * regardless of which Disposition is currently selected, so the Manager reads it before choosing.
 */
function isPostpaidSimCard(unit: ReturnedUnitItem): boolean {
  return !unit.smartphoneId && unit.simCardFlavor === "POSTPAID";
}

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
 */
export function PendingRequestDecisionControls({ request }: { request: RequestListItem }) {
  const requestId = request.id;
  const unitsNeedingDisposition = (request.returnedUnits ?? []).filter((unit) => !unit.disposition);

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
      if (unitsNeedingDisposition.length > 0 && event) {
        const formData = new FormData(event.currentTarget);
        init.headers = { "Content-Type": "application/json" };
        init.body = JSON.stringify({
          dispositions: unitsNeedingDisposition.map((unit) => ({
            returnedUnitId: unit.id,
            disposition: String(formData.get(`disposition-${unit.id}`)),
          })),
        });
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

  if (unitsNeedingDisposition.length > 0) {
    return (
      <form onSubmit={approve} className="flex flex-col items-end gap-1.5">
        <div className="flex flex-col items-end gap-1">
          {unitsNeedingDisposition.map((unit) => (
            <div key={unit.id} className="flex flex-col items-end gap-0.5">
              <label className="flex items-center gap-1.5 text-[11px] font-medium text-ink-secondary">
                {unitLabel(unit)}
                <select
                  name={`disposition-${unit.id}`}
                  required
                  disabled={pending}
                  defaultValue={dispositionOptionsFor(unit)[0]}
                  className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
                >
                  {dispositionOptionsFor(unit).map((disposition) => (
                    <option key={disposition} value={disposition}>
                      {DISPOSITION_LABEL[disposition]}
                    </option>
                  ))}
                </select>
              </label>
              {isPostpaidSimCard(unit) ? (
                <span className="max-w-[220px] text-right text-[11px] text-warning">
                  Kept in Stock: the carrier keeps charging with no Client to bill.
                </span>
              ) : null}
            </div>
          ))}
        </div>
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
