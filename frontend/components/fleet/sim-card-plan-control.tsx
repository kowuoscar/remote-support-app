"use client";

import { useState, type FormEvent, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { PostpaidPlanPicker } from "@/components/fleet/postpaid-plan-picker";
import { SimCardPlan } from "@/components/fleet/sim-card-plan";
import type { CatalogCarrierItem, SimCardListItem } from "@/lib/api/types";

const PLAN_UNUSABLE = "That plan can’t be chosen any more. Pick another.";
const PLAN_MESSAGES: Record<string, string> = {
  PLAN_ARCHIVED: PLAN_UNUSABLE,
  PLAN_NOT_ON_CARRIER: PLAN_UNUSABLE,
  CARRIER_NOT_USABLE: PLAN_UNUSABLE,
};
const GENERIC_ERROR = "Couldn’t update. Try again.";

/**
 * Corrects a Postpaid SIM Card's plan from the Fleet page (correct-a-fleet-unit spec): the Manager
 * and the Client's Testers. The edit-toggle shape of `SimCardNumberControl` around the
 * `PostpaidPlanPicker`, which shows the chosen plan's monthly fee before Save — the plan sets the
 * fee, so a misclick would move money. Only the SIM's own active Carrier's active plans are
 * offered; the table renders this control only for such a SIM. Enter on the select saves.
 */
export function SimCardPlanControl({
  contractId,
  simCardId,
  sim,
  carrier,
  currency,
  carriersHref,
}: {
  contractId: string;
  simCardId: string;
  sim: Pick<SimCardListItem, "postpaidPlanId" | "postpaidPlanName" | "postpaidPlanArchived">;
  carrier: CatalogCarrierItem;
  currency: string;
  carriersHref: string;
}) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [planId, setPlanId] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEditing() {
    const held = carrier.postpaidPlans.find((plan) => plan.id === sim.postpaidPlanId && plan.archivedAt === null);
    setPlanId(held ? held.id : "");
    setError(null);
    setEditing(true);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!planId) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/contracts/${contractId}/sim-cards/${simCardId}/postpaid-plan`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ postpaidPlanId: planId }),
      });
      if (!response.ok) {
        const body = (await response.json().catch(() => null)) as { code?: string } | null;
        setError((body?.code && PLAN_MESSAGES[body.code]) || GENERIC_ERROR);
        setPending(false);
        return;
      }
      setPending(false);
      setEditing(false);
      router.refresh();
    } catch {
      setError(GENERIC_ERROR);
      setPending(false);
    }
  }

  function saveOnEnter(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === "Enter" && event.target instanceof HTMLSelectElement) {
      event.preventDefault();
      event.currentTarget.requestSubmit();
    }
  }

  if (!editing) {
    return (
      <div className="flex items-center gap-2">
        <SimCardPlan sim={sim} />
        <Button variant="row" size="sm" aria-label="Edit plan" onClick={startEditing}>
          Edit
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={save} onKeyDown={saveOnEnter} className="flex min-w-48 flex-col items-start gap-1.5">
      <PostpaidPlanPicker
        carrier={carrier}
        currency={currency}
        carriersHref={carriersHref}
        value={planId}
        onChange={setPlanId}
        disabled={pending}
        autoFocus
        size="sm"
      />
      <div className="flex items-center gap-1.5">
        <Button type="submit" variant="row" size="sm" loading={pending} disabled={!planId}>
          Save
        </Button>
        <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={() => setEditing(false)}>
          Cancel
        </Button>
      </div>
      {error ? (
        <span role="alert" className="max-w-56 text-label-sm text-danger">
          {error}
        </span>
      ) : null}
    </form>
  );
}
