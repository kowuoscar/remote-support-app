"use client";

import { useId, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle } from "@/components/icons";
import type { AgentInvoiceDetail } from "@/lib/api/types";

function errorMessage(status: number): string {
  switch (status) {
    case 400:
      return "Enter an amount that isn't negative.";
    case 409:
      return "This invoice is no longer awaiting approval, so its lines can't change. Refresh to see its current status.";
    default:
      return "Couldn't save. Try again.";
  }
}

function OverrideField({
  invoiceId,
  field,
  label,
  current,
  currency,
  onOverridden,
}: {
  invoiceId: string;
  field: "salary" | "rolloutAdvanceNewAdvance";
  label: string;
  current: number;
  currency: string;
  onOverridden?: (invoice: AgentInvoiceDetail) => void;
}) {
  const router = useRouter();
  const inputId = useId();
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
      const response = await fetch(`/api/agent-invoices/${invoiceId}/override`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ [field]: Number(amount) }),
      });

      if (!response.ok) {
        setError(errorMessage(response.status));
        setSubmitting(false);
        return;
      }
      if (onOverridden) {
        onOverridden((await response.json()) as AgentInvoiceDetail);
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
      <label htmlFor={inputId} className="text-[13px] font-medium text-ink-secondary">
        {label} ({currency})
      </label>
      <div className="flex items-center gap-2">
        <Input
          id={inputId}
          name={field}
          autoComplete="off"
          inputMode="decimal"
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
      {error ? (
        <p role="alert" className="flex items-center gap-1.5 text-[12px] text-danger">
          <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
          {error}
        </p>
      ) : null}
      <p role="status" className="text-[12px] text-success empty:hidden">
        {confirmed ? "Applied to this invoice only." : null}
      </p>
    </form>
  );
}

/**
 * A Manager's per-invoice override of a sent Agent Invoice's Salary and/or Rollout Advance
 * new-advance line, at approval time (spec.md user story 10; agent-invoice-submission-and-
 * approval ticket AC: "Manager can override the Salary or Rollout Advance value on that one
 * invoice ... without changing the Agent's standing amount used by future invoices"). Each field
 * submits independently ({@code field} distinguishes which of the two the request overrides), the
 * same shape AgentStandingAmountsView established for the two standing-amount fields — except
 * this edits *this invoice's own snapshot only*, never `AgentStandingAmount`, which is why the
 * copy below says so explicitly. The invoice is addressed by its own id, so any billing month
 * works.
 *
 * <p>Only Salary and the Rollout Advance <b>new advance</b> line are exposed — never the
 * repayment line, which simply settles an amount already fixed on the Agent's prior invoice (see
 * CONTEXT.md's "Agent Invoice" entry / ADR 0003 for the full reasoning), mirroring the backend's
 * {@code AgentInvoiceOverrideRequest}. `onOverridden` receives the updated invoice so a detail view
 * can show the new lines and total in place.
 */
export function AgentInvoiceOverrideControl({
  salary,
  rolloutAdvanceNewAdvance,
  currency,
  invoiceId,
  onOverridden,
}: {
  invoiceId: string;
  salary: number;
  rolloutAdvanceNewAdvance: number;
  currency: string;
  onOverridden?: (invoice: AgentInvoiceDetail) => void;
}) {
  return (
    <div className="flex flex-col gap-3">
      <p className="text-[12px] text-ink-mute">
        Applies only to this invoice — never changes the Agent&rsquo;s standing salary or Rollout Advance.
      </p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <OverrideField
          invoiceId={invoiceId}
          field="salary"
          label="Override Salary"
          current={salary}
          currency={currency}
          onOverridden={onOverridden}
        />
        <OverrideField
          invoiceId={invoiceId}
          field="rolloutAdvanceNewAdvance"
          label="Override Rollout Advance (new)"
          current={rolloutAdvanceNewAdvance}
          currency={currency}
          onOverridden={onOverridden}
        />
      </div>
    </div>
  );
}
