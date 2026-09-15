"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { IconAlertTriangle, IconWallet } from "@/components/icons";
import type { StandingAmountTypeValue } from "@/lib/api/types";

/** "October 2026" for the calendar month after the one in progress, in the viewer's own clock — informational copy only; the server is the actual authority on which month a change lands in. */
function nextMonthLabel(): string {
  const now = new Date();
  const next = new Date(now.getFullYear(), now.getMonth() + 1, 1);
  return next.toLocaleDateString("en-US", { month: "long", year: "numeric" });
}

function StandingAmountField({
  agentId,
  amountType,
  label,
  description,
  current,
  currency,
}: {
  agentId: string;
  amountType: StandingAmountTypeValue;
  label: string;
  description: string;
  current: number;
  currency: string;
}) {
  const router = useRouter();
  const [amount, setAmount] = useState(String(current));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmedMonth, setConfirmedMonth] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setConfirmedMonth(null);
    setSubmitting(true);

    try {
      const response = await fetch(`/api/agents/${agentId}/standing-amounts`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ amountType, amount: Number(amount) }),
      });

      if (!response.ok) {
        setError(response.status === 400 ? "Enter an amount that isn't negative." : "Couldn't save. Try again.");
        setSubmitting(false);
        return;
      }

      const body = (await response.json()) as { effectiveMonth: string };
      setConfirmedMonth(body.effectiveMonth);
      setSubmitting(false);
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 rounded-lg border border-hairline bg-canvas-soft p-4">
      <div>
        <h3 className="text-[13px] font-medium text-ink">{label}</h3>
        <p className="mt-0.5 text-[12px] text-ink-mute">{description}</p>
      </div>

      <div className="flex items-baseline justify-between">
        <span className="text-[12px] uppercase tracking-wide text-ink-mute">Currently in effect</span>
        <Money amount={current} currency={currency} className="text-base" />
      </div>

      {error ? (
        <div role="alert" className="flex items-start gap-1.5 rounded-lg bg-danger-bg px-2.5 py-2 text-[12px] text-danger">
          <IconAlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <span>{error}</span>
        </div>
      ) : null}
      {confirmedMonth ? (
        <p className="text-[12px] text-success">Scheduled — takes effect from {nextMonthLabel()}.</p>
      ) : null}

      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        New amount ({currency})
        <div className="flex items-center gap-2">
          <Input
            type="number"
            min="0"
            step="0.01"
            required
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            disabled={submitting}
            invalid={Boolean(error)}
          />
          <Button type="submit" variant="secondary" size="sm" loading={submitting}>
            Save
          </Button>
        </div>
      </label>
      <p className="text-[12px] text-ink-mute">Takes effect from {nextMonthLabel()} — never changes the invoice already in progress.</p>
    </form>
  );
}

/**
 * A Manager sets an Agent's standing monthly salary and standing Rollout Advance (spec.md user
 * stories 5-6; agent-standing-amounts-and-invoice-generation ticket). Each field submits
 * independently ({@code amountType} distinguishes which history the change appends to — see
 * {@code AgentStandingAmount}'s Javadoc) so changing one never risks resubmitting a stale value
 * for the other. Every change is next-month-effective only — there is no field for
 * {@code effectiveMonth} here at all, since the server always computes it.
 */
export function AgentStandingAmountsView({
  agentId,
  currency,
  salaryAmount,
  rolloutAdvanceAmount,
}: {
  agentId: string;
  currency: string;
  salaryAmount: number;
  rolloutAdvanceAmount: number;
}) {
  return (
    <Card className="flex flex-col gap-4 p-5">
      <div className="flex items-center gap-2 border-b border-hairline pb-3.5">
        <IconWallet className="h-4 w-4 text-ink-mute" />
        <h2 className="text-sm font-semibold text-ink">Standing amounts</h2>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <StandingAmountField
          agentId={agentId}
          amountType="SALARY"
          label="Standing monthly salary"
          description="Auto-populates the Salary line on every future Agent Invoice."
          current={salaryAmount}
          currency={currency}
        />
        <StandingAmountField
          agentId={agentId}
          amountType="ROLLOUT_ADVANCE"
          label="Standing Rollout Advance"
          description="Cash-flow support — repaid the following month and re-advanced each cycle."
          current={rolloutAdvanceAmount}
          currency={currency}
        />
      </div>
    </Card>
  );
}
