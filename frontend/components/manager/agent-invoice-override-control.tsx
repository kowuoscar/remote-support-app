"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle } from "@/components/icons";

function OverrideField({
  agentId,
  field,
  label,
  current,
  currency,
}: {
  agentId: string;
  field: "salary" | "rolloutAdvanceNewAdvance";
  label: string;
  current: number;
  currency: string;
}) {
  const router = useRouter();
  const [amount, setAmount] = useState(String(current));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setConfirmed(false);
    setSubmitting(true);

    try {
      const response = await fetch(`/api/agents/${agentId}/invoice/override`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ [field]: Number(amount) }),
      });

      if (!response.ok) {
        setError(response.status === 400 ? "Enter an amount that isn't negative." : "Couldn't save. Try again.");
        setSubmitting(false);
        return;
      }

      setConfirmed(true);
      setSubmitting(false);
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="flex flex-col gap-2 rounded-lg border border-hairline bg-canvas-soft p-3.5"
    >
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        {label} ({currency})
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
            Override
          </Button>
        </div>
      </label>
      {error ? (
        <p role="alert" className="flex items-center gap-1.5 text-[12px] text-danger">
          <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
          {error}
        </p>
      ) : null}
      {confirmed ? <p className="text-[12px] text-success">Applied to this invoice only.</p> : null}
    </form>
  );
}

/**
 * A Manager's per-invoice override of a sent Agent Invoice's Salary and/or Rollout Advance
 * new-advance line, at approval time (spec.md user story 10; agent-invoice-submission-and-
 * approval ticket AC: "Manager can override the Salary or Rollout Advance value on that one
 * invoice ... without changing the Agent's standing amount used by future invoices"). Each field
 * submits independently ({@code field} distinguishes which of the two the request overrides), the
 * same shape AgentStandingAmountsView already established for the two standing-amount fields one
 * section up on the same page — except this edits *this invoice's own snapshot only*, never
 * `AgentStandingAmount`, which is why the copy below says so explicitly rather than assuming that
 * distinction is obvious next to a standing-amounts form.
 *
 * <p>Only Salary and the Rollout Advance <b>new advance</b> line are exposed — never the
 * repayment line, which simply settles an amount already fixed on the Agent's prior invoice (see
 * CONTEXT.md's "Agent Invoice" entry / ADR 0003 for the full reasoning), mirroring the backend's
 * {@code AgentInvoiceOverrideRequest}.
 */
export function AgentInvoiceOverrideControl({
  agentId,
  salary,
  rolloutAdvanceNewAdvance,
  currency,
}: {
  agentId: string;
  salary: number;
  rolloutAdvanceNewAdvance: number;
  currency: string;
}) {
  return (
    <div className="flex flex-col gap-3">
      <p className="text-[12px] text-ink-mute">
        Applies only to this invoice — never changes the Agent&rsquo;s standing salary or Rollout Advance.
      </p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <OverrideField agentId={agentId} field="salary" label="Override Salary" current={salary} currency={currency} />
        <OverrideField
          agentId={agentId}
          field="rolloutAdvanceNewAdvance"
          label="Override Rollout Advance (new)"
          current={rolloutAdvanceNewAdvance}
          currency={currency}
        />
      </div>
    </div>
  );
}
