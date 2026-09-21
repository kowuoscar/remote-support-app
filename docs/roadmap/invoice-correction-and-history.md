---
id: invoice-correction-and-history
title: Send an invoice back, and look back at finished ones
status: planned
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

## Reworked

## Later

- Whether a sent-back invoice should notify the Agent by any means other than
  it reappearing in their list.
