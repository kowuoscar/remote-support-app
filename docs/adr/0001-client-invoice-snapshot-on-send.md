# 1. Snapshot a Client Invoice's numbers at send, instead of always computing live

Status: accepted
Date: 2026-09-15
Ticket: client-invoice-submission-and-visibility

## Context

`client-invoice-generation` deliberately computes a Client Invoice's base amount and Fee lines
*live*, on every read: sum of currently-Active Postpaid SIMs' fees, plus every Fee logged against
the Contract for that `billingMonth`. That was the right call while the invoice is still `draft`
— the Agent is actively assembling it, so "always current" is exactly the behaviour wanted.

This ticket adds `sent` and `approved`. Once an invoice is `sent`, a Manager reviews it and a
Client's Testers see it as "what we're being billed for" (spec.md user story 34). If the numbers
stayed live past that point, an Agent logging one more Fee against the same Contract/month —
entirely legitimate, ordinary usage — would silently change the total on an invoice a Manager has
already reviewed, or has already approved, or that a Client has already seen and possibly acted
on. Nobody involved would be notified; the two figures would simply disagree the next time either
of them looked.

## Decision

Freeze the numbers at the moment of the `draft -> sent` transition:

- `ClientInvoice` gets a `snapshotBaseAmount` column, populated once, at send.
- A new `client_invoice_fee_snapshots` join table pins which `Fee` rows counted — not a copy of
  their amount/description/etc. A `Fee` has no update or delete endpoint anywhere in this
  codebase, so its own columns can never drift once logged; the only thing that can change after
  sending is the *set* of Fees for that Contract/month (a new one logged later), and pinning
  membership is exactly what stops a later Fee from joining a total that's already been shown as
  final.
- Every read of a `sent`/`approved` invoice serves the snapshot. A `draft` invoice is unaffected —
  still computed live, exactly as `client-invoice-generation` built it.

## Alternatives considered

- **Keep computing live for `sent`/`approved` too.** Simplest, but wrong: it lets the Manager
  approve one set of numbers and the Client see a different one later, on what both sides treat as
  a final statement. Rejected — this is a financial record, not a dashboard.
- **Recompute at approval time too (a second snapshot).** Would let an Agent's late Fee sneak into
  the number the Manager approves, if logged between send and approval. Rejected: the ticket's AC
  is that a *sent* invoice is what the Client sees and what the Manager reviews — the figure has to
  be fixed at send, not approval, for those two to agree on what they're looking at.
- **Store a full serialized snapshot (JSON blob) instead of a join table.** Equally valid per the
  ticket's own framing. A relational join table was chosen because Fee rows are already immutable,
  so there's nothing to serialize beyond "which ones" — a plain foreign-key table says that
  directly and stays queryable/joinable like every other table in this schema, rather than opaque
  JSON only the application layer can interpret.

## Consequences

- A sent/approved invoice's total is permanently fixed at the value the Agent actually saw when
  they clicked send — the same number the Manager reviews and the Client is shown, indefinitely.
- A Fee logged after sending is still fully real and queryable everywhere else (Fee list, the
  Agent's own Contract-wide totals, a future Agent Invoice) — it simply never joins this specific
  Client Invoice's total. There is currently no UI signal surfacing "a Fee was logged against this
  Contract/month after the invoice was sent"; if that turns out to matter operationally, it's a
  small additive follow-up (diff live vs. snapshot), not a rework of this decision.
- This is hard to reverse once real `sent` invoices exist in production: switching back to live
  computation would change already-communicated numbers out from under a Manager/Client who saw
  something different.
