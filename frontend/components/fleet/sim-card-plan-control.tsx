"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { PostpaidPlanPicker } from "@/components/fleet/postpaid-plan-picker";
import { SimCardPlan } from "@/components/fleet/sim-card-plan";
import { CorrectionError } from "@/components/fleet/correction-error";
import { useInlineEdit } from "@/components/fleet/use-inline-edit";
import type { CatalogCarrierItem, SimCardListItem } from "@/lib/api/types";

const PLAN_UNUSABLE = "That plan can’t be chosen any more. Pick another.";
const PLAN_MESSAGES: Record<string, string> = {
  PLAN_ARCHIVED: PLAN_UNUSABLE,
  PLAN_NOT_ON_CARRIER: PLAN_UNUSABLE,
  CARRIER_NOT_USABLE: PLAN_UNUSABLE,
};

/**
 * Corrects a Postpaid SIM Card's plan from the Fleet page (correct-a-fleet-unit spec): the Manager
 * and the Contract's Agent. The edit-toggle shape of `SimCardNumberControl` around the
 * `PostpaidPlanPicker`, which shows the chosen plan's monthly fee before Save — the plan sets the
 * fee, so a misclick would move money. Only the SIM's own active Carrier's active plans are
 * offered; the table renders this control only for such a SIM. Enter on the select saves.
 */
export function SimCardPlanControl({
  contractId,
  simCardId,
  number,
  sim,
  carrier,
  currency,
  carriersHref,
}: {
  contractId: string;
  simCardId: string;
  /** The SIM's number; names which unit the Edit button is for. */
  number: string;
  sim: Pick<SimCardListItem, "postpaidPlanId" | "postpaidPlanName" | "postpaidPlanArchived">;
  carrier: CatalogCarrierItem;
  currency: string;
  carriersHref: string;
}) {
  const [planId, setPlanId] = useState("");
  const { focusTarget, ...edit } = useInlineEdit({
    url: `/api/contracts/${contractId}/sim-cards/${simCardId}/postpaid-plan`,
    messages: PLAN_MESSAGES,
    onStart: () => {
      const held = carrier.postpaidPlans.find((plan) => plan.id === sim.postpaidPlanId && plan.archivedAt === null);
      setPlanId(held ? held.id : "");
    },
  });

  if (!edit.editing) {
    return (
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className="min-w-0 max-w-full">
          <SimCardPlan sim={sim} />
        </span>
        <Button
          ref={focusTarget}
          variant="ghost"
          size="sm"
          aria-label={`Edit plan for ${number}`}
          onClick={edit.start}
        >
          Edit
        </Button>
      </div>
    );
  }

  return (
    <form
      onSubmit={(event) => {
        if (!planId) {
          event.preventDefault();
          return;
        }
        void edit.submit(event, { postpaidPlanId: planId });
      }}
      onKeyDown={edit.onKeyDown}
      className="flex w-full flex-col items-start gap-1.5"
    >
      <PostpaidPlanPicker
        carrier={carrier}
        currency={currency}
        carriersHref={carriersHref}
        value={planId}
        onChange={setPlanId}
        disabled={edit.pending}
        autoFocus
        size="sm"
      />
      <div className="flex items-center gap-1.5">
        <Button type="submit" variant="row" size="sm" loading={edit.pending} disabled={!planId}>
          Save
        </Button>
        <Button type="button" variant="ghost" size="sm" disabled={edit.pending} onClick={edit.cancel}>
          Cancel
        </Button>
      </div>
      <CorrectionError id={edit.errorId} message={edit.error} />
    </form>
  );
}
