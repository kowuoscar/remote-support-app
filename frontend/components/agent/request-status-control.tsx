"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CarrierPicker } from "@/components/fleet/carrier-picker";
import { PostpaidPlanPicker } from "@/components/fleet/postpaid-plan-picker";
import {
  REQUEST_STATUS_LABEL,
  canCancelRequest,
  nextRequestStatus,
  requestTypeCanCarryFee,
  type CatalogCarrierItem,
  type RequestStatusValue,
  type RequestTypeValue,
  type SimCardFlavorValue,
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
 * Provision Smartphone, Provision SIM, Repair — {@link requestTypeCanCarryFee}) expands the same
 * inline form to also collect the Fee amount, and for a Provision type, the new Fleet unit's
 * details (reusing the Manager's Add Smartphone/SIM Card field shape) plus an optional "retiring
 * which unit" picker. Reboot and SIM Swap complete exactly as before — a single click, no form —
 * since neither can ever carry a Fee.
 */
export function RequestStatusControl({
  contractId,
  requestId,
  status,
  type,
  currency,
  activeSmartphones = [],
  activeSimCards = [],
  carriers = [],
  carriersHref = "/agent/carriers",
}: {
  contractId: string;
  requestId: string;
  status: RequestStatusValue;
  type: RequestTypeValue;
  currency: string;
  activeSmartphones?: SmartphoneListItem[];
  activeSimCards?: SimCardListItem[];
  carriers?: CatalogCarrierItem[];
  carriersHref?: string;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");
  const [completing, setCompleting] = useState(false);
  const [flavor, setFlavor] = useState<SimCardFlavorValue>("POSTPAID");
  // The Carrier and Plan are controlled, not just FormData fields: the Plan picker lists the
  // chosen Carrier's Plans and shows the monthly fee the chosen one sets (postpaid-sim-plan).
  const [carrierId, setCarrierId] = useState("");
  const [postpaidPlanId, setPostpaidPlanId] = useState("");

  const next = nextRequestStatus(status);
  const canCancel = canCancelRequest(status);
  const completingNeedsFeeForm = next === "COMPLETED" && requestTypeCanCarryFee(type);

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

  async function submitStatus(body: {
    status: RequestStatusValue;
    cancellationReason?: string;
    newSmartphone?: { model: string; serial: string; assignedTo?: string };
    newSimCard?: { number: string; carrierId: string; flavor: SimCardFlavorValue; postpaidPlanId?: string };
    replacesSmartphoneId?: string;
    replacesSimCardId?: string;
  }): Promise<boolean> {
    const response = await fetch(`/api/contracts/${contractId}/requests/${requestId}/status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return response.ok;
  }

  async function logFee(amount: number, description: string): Promise<boolean> {
    const response = await fetch(`/api/contracts/${contractId}/fees`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ requestId, feeType: type, amount, description: description || undefined }),
    });
    return response.ok;
  }

  async function advance() {
    if (!next) return;
    if (completingNeedsFeeForm) {
      setCompleting(true);
      setError(null);
      return;
    }
    setPending(true);
    setError(null);
    try {
      const ok = await submitStatus({ status: next });
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

    const formData = new FormData(event.currentTarget);
    const amount = Number(formData.get("amount"));
    const description = String(formData.get("description") ?? "").trim();

    const statusBody: Parameters<typeof submitStatus>[0] = { status: "COMPLETED" };
    if (type === "PROVISION_SMARTPHONE") {
      statusBody.newSmartphone = {
        model: String(formData.get("model")),
        serial: String(formData.get("serial")),
        assignedTo: String(formData.get("assignedTo") ?? "") || undefined,
      };
      const replaces = String(formData.get("replacesSmartphoneId") ?? "");
      if (replaces) statusBody.replacesSmartphoneId = replaces;
    } else if (type === "PROVISION_SIM") {
      statusBody.newSimCard = {
        number: String(formData.get("number")),
        carrierId,
        flavor,
        postpaidPlanId: flavor === "POSTPAID" ? postpaidPlanId : undefined,
      };
      const replaces = String(formData.get("replacesSimCardId") ?? "");
      if (replaces) statusBody.replacesSimCardId = replaces;
    }

    try {
      const statusOk = await submitStatus(statusBody);
      if (!statusOk) {
        setError("Couldn't complete the request. Try again.");
        setPending(false);
        return;
      }

      const feeOk = await logFee(amount, description);
      if (!feeOk) {
        setError("Request completed, but logging the fee failed — log it separately from Fleet/Requests.");
        resetLocalState();
        router.refresh();
        return;
      }

      resetLocalState();
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
      const ok = await submitStatus({ status: "CANCELLED", cancellationReason: reason.trim() });
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
            className="h-7 text-[12px]"
          />
        </label>
        <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
          Description (optional)
          <Input name="description" disabled={pending} className="h-7 text-[12px]" />
        </label>

        {type === "PROVISION_SMARTPHONE" ? (
          <>
            <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
              New smartphone model
              <Input name="model" required disabled={pending} className="h-7 text-[12px]" />
            </label>
            <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
              New smartphone serial
              <Input name="serial" required disabled={pending} className="h-7 text-[12px]" />
            </label>
            <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
              Assigned to (optional)
              <Input name="assignedTo" disabled={pending} className="h-7 text-[12px]" />
            </label>
            {activeSmartphones.length > 0 ? (
              <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
                Retiring which smartphone? (optional)
                <select
                  name="replacesSmartphoneId"
                  disabled={pending}
                  defaultValue=""
                  className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
                >
                  <option value="">None — first-time provisioning</option>
                  {activeSmartphones.map((phone) => (
                    <option key={phone.id} value={phone.id}>
                      {phone.model} — {phone.serial}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
          </>
        ) : null}

        {type === "PROVISION_SIM" ? (
          <>
            <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
              New SIM number
              <Input name="number" required disabled={pending} className="h-7 text-[12px]" />
            </label>
            <CarrierPicker
              name="carrierId"
              carriers={carriers}
              carriersHref={carriersHref}
              value={carrierId}
              onChange={(id) => {
                setCarrierId(id);
                setPostpaidPlanId("");
              }}
              disabled={pending}
              size="sm"
            />
            <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
              Flavor
              <select
                required
                value={flavor}
                onChange={(event) => setFlavor(event.target.value as SimCardFlavorValue)}
                disabled={pending}
                className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
              >
                <option value="POSTPAID">Postpaid</option>
                <option value="PREPAID">Prepaid</option>
              </select>
            </label>
            {flavor === "POSTPAID" ? (
              <PostpaidPlanPicker
                name="postpaidPlanId"
                carrier={carriers.find((carrier) => carrier.id === carrierId)}
                currency={currency}
                carriersHref={carriersHref}
                value={postpaidPlanId}
                onChange={setPostpaidPlanId}
                disabled={pending}
                size="sm"
              />
            ) : null}
            {activeSimCards.length > 0 ? (
              <label className="flex w-full flex-col gap-1 text-[11px] font-medium text-ink-secondary">
                Retiring which SIM? (optional)
                <select
                  name="replacesSimCardId"
                  disabled={pending}
                  defaultValue=""
                  className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
                >
                  <option value="">None — first-time provisioning</option>
                  {activeSimCards.map((sim) => (
                    <option key={sim.id} value={sim.id}>
                      {sim.number}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
          </>
        ) : null}

        <div className="flex items-center gap-1.5 pt-0.5">
          <Button
            type="submit"
            variant="row"
            size="sm"
            loading={pending}
            disabled={type === "PROVISION_SIM" && !carriers.some((carrier) => carrier.archivedAt === null)}
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
    </div>
  );
}
