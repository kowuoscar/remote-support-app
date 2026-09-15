# 2. Agent Invoice's Local Support Fees line reads Fleet/Fee data directly, never a Client Invoice

Status: accepted
Date: 2026-09-15
Ticket: agent-standing-amounts-and-invoice-generation

## Context

By the time this ticket starts, `client-invoice-generation` and
`client-invoice-submission-and-visibility` have already given every Contract a Client Invoice
with its own `draft -> sent -> approved` lifecycle, live-computed while `draft` and **frozen** into
a snapshot the moment it's `sent` (ADR 0001). It would have been natural for this ticket's Agent
Invoice — whose Local Support Fees line is, per spec.md, "that Contract's Client Invoice base
amount + that Contract's Fee total" — to simply read each Contract's `ClientInvoice` row for the
month and sum its `totalAmount`, reusing ADR 0001's snapshot for `sent`/`approved` invoices the
same way every other reader of a Client Invoice does.

That would have been wrong for this line specifically. The ticket's own framing states it plainly:
the Agent fronts every base charge and every Fee the moment it happens, in cash, before any
Client-facing review takes place. A Contract's Client Invoice status — `draft`, `sent`, or
`approved` — describes how far the Manager's *review of the paperwork* has gotten; it says nothing
about whether the Agent has already paid the money out. A Fee an Agent logs against a Contract
*after* that Contract's Client Invoice was already sent is real, and the Agent already fronted it —
but ADR 0001 deliberately excludes it from that Client Invoice's frozen total, forever. If Local
Support Fees read the Client Invoice's own total (live-or-frozen depending on status), that Fee
would silently vanish from what the Agent is reimbursed for the month they actually paid it, only
reappearing (if ever) whenever the *next* Client Invoice happens to be assembled for that Contract
— an unrelated, Manager-driven event with no fixed timing.

## Decision

Extract the base-amount/Fee-total math client-invoice-generation already built
(`ClientInvoiceController#computeBaseAmount` and its live Fee-line query) into a shared
`ContractAmountService`, and have `AgentInvoiceController`'s Local Support Fees line call it
**directly against `sim_cards`/`fees`, for every one of the Agent's Contracts, regardless of that
Contract's own Client Invoice status** — never by reading a `ClientInvoice` row, live or frozen, at
all. `ClientInvoiceController` is refactored to call the same shared service, so the two features
share one implementation of "a Contract's base amount + Fee total for a month" rather than
maintaining the math twice.

## Alternatives considered

- **Read each Contract's `ClientInvoiceResponse.totalAmount` (live while draft, frozen from sent
  onward).** Rejected for the reason above: it ties an Agent's real reimbursement to an unrelated
  document's review status, and can silently drop Fees the Agent already paid out of pocket.
- **Read the live total always, but only for Contracts whose Client Invoice is still `draft`; fall
  back to the frozen snapshot once `sent`/`approved`.** A hybrid that still has the same problem
  for any Contract past `draft` — the exact case that matters, since a real month's Client Invoice
  is `sent` well before the Agent Invoice is likely to be reviewed.
- **Require a Client Invoice to already exist for every Contract before an Agent Invoice can be
  built.** Rejected as an unnecessary coupling and a worse user experience: nothing about assembling
  an Agent Invoice should depend on whether an Agent has gotten around to opening each Contract's
  separate Client Invoice screen first. `ContractAmountService` computes straight from
  `sim_cards`/`fees`, so no Client Invoice row needs to exist at all.

## Consequences

- Client Invoice and Agent Invoice numbers can legitimately diverge for the same Contract/month —
  by design. A Client Invoice's `sent`/`approved` total is a frozen, audit-stable figure (ADR
  0001); an Agent Invoice's Local Support Fees line is always the live truth of what's owed. This
  is the whole point, not a bug to reconcile.
- If a future ticket wants "flag Contracts where the Agent Invoice and the Client Invoice disagree"
  as an operational aid, it's a straightforward additive diff (compare `ContractAmountService`'s
  live total against the Client Invoice's own reported total) — not a rework of this decision.
- This is hard to reverse once real Agent Invoices exist: the next ticket
  (`agent-invoice-submission-and-approval`) will snapshot Agent Invoice numbers at send, the same
  way Client Invoice does — that snapshot will freeze *this* live computation. Switching Local
  Support Fees to read from Client Invoices afterwards would mean historical Agent Invoices and any
  newly-built one compute the same line two different ways.
