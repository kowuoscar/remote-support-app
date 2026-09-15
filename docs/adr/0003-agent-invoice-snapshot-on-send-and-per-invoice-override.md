# 3. Snapshot all four Agent Invoice line items at send; let a Manager override two of them on that one invoice only

Status: accepted
Date: 2026-09-15
Ticket: agent-invoice-submission-and-approval

## Context

ADR 0001 established snapshot-on-send for Client Invoice: freeze the numbers the moment an Agent
sends them, so a later Fee can never silently move a total a Manager or Client has already seen as
final. ADR 0002 explicitly anticipated this ticket needing the same treatment for Agent Invoice,
whose Local Support Fees line is computed live, straight against `sim_cards`/`fees`, independent of
any Client Invoice.

Agent Invoice's send/approve/paid lifecycle needs the identical freeze, with two differences from
Client Invoice: there are **four** line items instead of one "base amount" (Local Support Fees,
Salary, Rollout Advance repayment, Rollout Advance new advance), and the ticket also requires a
Manager to be able to override the Salary or Rollout Advance figure on one specific invoice, at
approval time, without touching the Agent's standing rate used by every other invoice.

## Decision

**Snapshot all four lines together, at `DRAFT -> SENT`.** `AgentInvoice` gets four `snapshot*`
columns (Local Support Fees, Salary, Rollout Advance repayment, Rollout Advance new advance),
populated once by `AgentInvoiceController#send` in the same request as the status change. Every
read from `SENT` onward serves these columns; a `DRAFT` invoice is unaffected, still computed live
exactly as the prior ticket built it. Freezing all four together (rather than, say, only Local
Support Fees) means a standing-amount change landing mid-cycle — which normally can't happen
because standing-amount updates are always next-month-effective, but the freeze must not silently
depend on that separate rule holding — also can never move a sent invoice's Salary/Rollout Advance
lines.

**The Manager's override edits the snapshot directly, not a separate override table.** A new
`POST /api/agents/{agentId}/invoice/override` endpoint, callable only while the invoice is `SENT`,
overwrites `snapshotSalary` and/or `snapshotRolloutAdvanceNewAdvance` in place and recalculates the
total on the next read. `StandingAmountService`/`AgentStandingAmount` are never touched by this
endpoint at all — the override is purely a one-invoice edit, so every other invoice (past or
future) resolves exactly as it would have. No separate "override" columns exist alongside the
snapshot columns; overwriting the snapshot value *is* the override, and the audit log (old/new
value) is the durable record of what changed and by how much.

**Only the Rollout Advance *new advance* line is overridable, never the *repayment* line.** The
repayment line settles an amount already fixed and communicated on the Agent's prior invoice — by
the time this invoice exists, there is no decision left to make about it. The new advance is the
forward-looking figure for next month's cash-flow support, which is exactly what the Manager is
reviewing and deciding on right now, alongside Salary. `AgentInvoiceOverrideRequest` exposes only
these two fields for this reason.

## Alternatives considered

- **Only snapshot Local Support Fees, leave Salary/Rollout Advance live until approval.** Rejected:
  it would make the freeze's correctness depend on the next-month-effective rule always holding,
  rather than being true unconditionally — and it complicates `buildResponse` with a
  three-way live/frozen/overridden split instead of one boolean.
- **A separate `agent_invoice_overrides` table, keeping the original snapshot immutable.** Would
  let a future reader distinguish "what the Agent actually sent" from "what the Manager approved"
  after the fact. Rejected for this ticket as unnecessary complexity: the ticket AC only requires
  the current total to reflect the override, and the audit log already records the old value: the
  same trade-off ADR 0001 made keeping `ClientInvoiceFeeSnapshot` a thin membership table instead
  of a richer audit structure. If a future ticket needs "was this invoice ever overridden, and from
  what", it's an additive column/table, not a rework of this decision.
- **Let the Manager override at any status (including `APPROVED`).** Rejected: once `APPROVED`, an
  Agent Invoice is the locked final review record, matching how Client Invoice treats `APPROVED` as
  terminal (ADR 0001). Restricting override to `SENT` keeps "what the Manager approved" and "what
  is currently on the invoice" the same thing from the moment of approval onward.
- **Make the repayment line overridable too.** Rejected per the reasoning above — it was already
  a decided number as of the prior invoice, not a live decision this Manager review is making.

## Consequences

- A `sent`/`approved`/`paid` Agent Invoice's numbers are permanently fixed at what the Agent
  actually saw when they sent it, except for whichever of Salary/new-advance a Manager explicitly
  overrides — and even then, only on that one invoice.
- `AgentStandingAmount` — and therefore every other Agent Invoice, past or future — is provably
  unaffected by an override; this is exercised directly in the HTTP-seam tests (no new history row
  written, `GET /standing-amounts` unchanged).
- This is hard to reverse once real sent Agent Invoices exist in production, for the same reason
  ADR 0001 is: switching back to live computation would change already-communicated numbers out
  from under a Manager who saw something different.
