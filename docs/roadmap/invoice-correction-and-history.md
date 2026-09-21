---
id: invoice-correction-and-history
title: Send an invoice back, and look back at finished ones
status: in-progress
journeys: [send-an-invoice-back-for-correction, look-back-at-finished-invoices]
---

<!-- sdlc:template epic 1 -->

## Intent

The monthly cycle only moves one way. A Manager reviewing a sent invoice can
approve it or leave it sitting — there is no way to hand it back to the Agent
saying what is wrong, so a wrong invoice gets approved or silently stalls.
And once an invoice leaves the Review Queue it is reachable only by its id:
there is no view of what was billed last month.
`manager-invoice-review-queue` deferred both
(`docs/features/manager-invoice-review-queue/spec.md:29,31`).

Settled with the human at init: a sent-back invoice **returns to draft and
its numbers go live again**, so the Agent fixes the underlying Fees and
resends, freezing a fresh snapshot. This is consistent with why a draft
computes live at all, and it is the one point where this epic must answer to
ADR 0001 and ADR 0003 — both lifecycles are currently strictly forward
(`DRAFT → SENT → APPROVED`, and `→ PAID` for Agent Invoices), with the
snapshot frozen at send precisely so a later Fee cannot silently move a
number a Manager has already reviewed. Re-opening a snapshot is therefore a
deliberate amendment to those ADRs, and the spec must say so.

History covers **all final invoices of both types** — approved Client
Invoices, approved and paid Agent Invoices — filterable by month, Contract
and Agent.

## Journeys

- **Send an invoice back for correction** → `exists`.
- **Look back at finished invoices** → `exists`.

The proof that closes this epic: on `main`, a Manager sends a Client Invoice
back with a reason; the Agent sees the reason, sees the invoice live again as
a draft, corrects a Fee, resends it with a fresh snapshot, and the Manager
approves it. The same for an Agent Invoice. Then the Manager finds both in
the history view, filtered by month.

## Features

- [ ] `send-a-client-invoice-back` — a Manager returns a sent Client Invoice to the Agent with a reason; it leaves the Review Queue, its numbers go live again, and a resend freezes a fresh snapshot.
- [ ] `send-an-agent-invoice-back` — the same for an Agent Invoice, whose snapshot is four scalar columns rather than a membership table.
- [ ] `invoice-history` — a Manager browses final invoices of both types, filtered by month, Contract and Agent.

## Reworked

The exploration made this epic smaller than its intent implies, and it moved
the risk to a place the intent did not mention.

**Un-freezing is almost free on the read path.** Both
`ClientInvoiceService.toResponse` (`:142`) and
`AgentInvoiceService.toResponse` (`:171`) decide what to serve with the same
expression — `frozen = status != DRAFT`. So an invoice returned to `DRAFT`
already serves live numbers again, through `ContractAmountService`, with no
change to how a read chooses. The amendment to ADR 0001 and ADR 0003 is
therefore narrower than feared: the ADRs' reason for freezing (a Manager must
never see a reviewed number move) still holds for everything in `SENT` and
beyond, and only the backward edge is new.

**The risk is on the write path, and it differs by type — which is why these
are two features, not one.** A Client Invoice's snapshot is
`snapshotBaseAmount` (`ClientInvoice.java:88`) *plus* rows in
`ClientInvoiceFeeSnapshot`, a thin membership table pinning which Fees
counted. Going back to draft must clear those rows, or the resend's
`ClientInvoiceController.snapshot()` (`:256-270`) adds a second set over the
first. An Agent Invoice's snapshot is four scalar columns
(`AgentInvoice.java:83-100`) that `AgentInvoiceService.snapshot()`
(`:209-218`) simply overwrites — no membership rows, no accumulation.

**The backward edge has a small, enumerable set of gates.** `canTransitionTo`
is called in exactly two places for Client Invoices
(`ClientInvoiceController.send:131`, `ClientInvoiceService.approve:72`) and
three for Agent Invoices (`AgentInvoiceService` `send:54`, `approve:121`,
`markPaid:146`). Those, plus each type's `canTransitionTo`, are the whole
enforcement surface.

**Two things need nothing built.** The Review Queue selects purely by status
(`ClientInvoiceRepository.findQueueRows`, `AgentInvoiceRepository.findQueueRows`),
so a sent-back invoice leaves it with no queue change at all. And
`AuditLog.statusChanged` (`AuditLog.java:49-59`) is already generic over
entity type and old/new status, so a send-back needs no new audit method.

**The reason field has prior art:** `Request` carries `cancellationReason`
(`:76`) and `rejectionReason` (`:85`) as two distinct columns rather than one
shared one. A sent-back reason should follow that shape rather than reuse
either.

**History is the genuinely new build.** No endpoint lists invoices by month,
Contract or Agent; the repositories offer only single fetches by
`(contract, billingMonth)` and `(agent, billingMonth)`, and
`frontend/app/manager/invoices/page.tsx` renders the Review Queue and nothing
else. It goes last because both send-back features add the statuses it will
have to display.

Left to each send-back feature's spec, as a *what* the cut does not decide:
an Agent Invoice sits in the Review Queue while `SENT` **and** while
`APPROVED` (it leaves only at `PAID`), so whether a Manager may send back an
already-approved Agent Invoice — not merely a sent one — is a real question
with no answer in the epic.

## Later

- Whether a sent-back invoice should notify the Agent by any means other than
  it reappearing in their list.
