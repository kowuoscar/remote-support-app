---
id: client-invoices-sent-by-the-month
title: The Agent sends a month's Client Invoices together; the Manager reviews them by Agent
status: in-progress
journeys: [bill-the-client-for-the-month]
---

<!-- sdlc:template epic 1 -->

## Intent

Raised by the human on 2026-10-18, right after accepting the
`carrier-invoices-per-sim` epic. In their words:

> Invoices should be sent in bulk. The way I am seeing this is for a given
> month, let's say October, the agent can only send all to its manager where
> all the carriers invoices are attached. When all its client invoices are
> ready, only then can he send it to the manager.
>
> The manager UI is impacted because the manager would need to review
> invoices for an agent on a given month in bulk. He can still send one or n
> back after its review, but on the ui, it would be great if he can pick an
> agent > then see a list of all the invoices he needs to review.

What the code does today (explorer, 2026-10-18):

- A Client Invoice is DRAFT, SENT or APPROVED. The Agent sends each one on
  its own (`ClientInvoiceService.send`): only on a current-month draft or a
  sent-back one, and only when every Postpaid SIM line has its Carrier
  Invoice File (`CARRIER_INVOICES_MISSING`). Send stores the lines (ADR 0001,
  0004). The Manager approves or sends back each SENT invoice on its own.
- One Client Invoice per Contract per month; a Contract has exactly one
  Agent. Drafts are created lazily, for the current month only, when a page
  or a write touches them; nothing materialises "an Agent's month".
- The Manager's review is one flat queue of SENT Client Invoices and waiting
  Agent Invoices, filtered by type only (`ReviewQueueController`,
  `review-queue-view.tsx`); no grouping by Agent.
- A Client's Tester sees an invoice from SENT on; a send-back hides it again.
- The Agent Invoice does not depend on Client Invoices being sent.

## Journeys

- **Bill the Client for the month** → stays `exists`. The Agent sends the
  whole month at once when every invoice is ready; the Manager reviews it by
  Agent.

## Features

Ordered by dependency; cut and sized at spec time.

- [ ] `agent-sends-the-month` — on the Agent's Client Invoices page a month bar ("October 2026 · 4 of 6 ready · Send all 6 to Manager") replaces each invoice's own Send; it is enabled only when every one of the Agent's Client Invoices for the month is ready (every Postpaid SIM line has its carrier invoice); the side list marks each invoice ready or the files it is missing; sending creates any missing draft so the month is complete and sends them all; a sent-back invoice, or the invoice of a Contract added after the month was sent, is resent on its own.
- [ ] `manager-reviews-by-agent` — the Manager's Invoices page gains an Agents side list (count waiting for review); picking an Agent shows that Agent's month of Client Invoices (Client, total, status) with "Approve the N left to review"; each row opens today's detail page, which gains "Next invoice →"; Approve and Send back stay per invoice.

## Settled with the human (2026-10-18)

- **A sent-back invoice is resent on its own (a)**: the rest of the month
  stays where it is and the Manager approves the others meanwhile.
- **UI A1 and M1** (the human: "Let's go with recommended option"): a month
  bar above today's Agent page, per-invoice Send removed; an Agents side list
  on the Manager's Invoices page with the Agent's month as a list.
- **Specs approved in advance**: "implements this in full without stop". The
  orchestrator settles the remaining choices and records them; the human's
  walkthrough step waits at delivery.

## Settled by the orchestrator (2026-10-18), told to the human

- Every Contract the Agent holds that month gets an invoice; an invoice with
  nothing to bill is part of the batch and is sent at $0, as today.
- The Agent sends during the month itself, as today; `month-closes-on-the-fifth`
  (awaiting approval) must account for this feature.
- A Contract added after the month was sent: its invoice is sent on its own.
- "Approve the N left to review" is included.
- The Agent's own pay invoice is unchanged.
