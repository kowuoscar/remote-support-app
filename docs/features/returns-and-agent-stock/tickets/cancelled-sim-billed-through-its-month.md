---
id: cancelled-sim-billed-through-its-month
title: A cancelled Postpaid SIM is billed through the month of its cancellation date
status: ready-for-agent
depends_on: [manager-decides-return-disposition]
labels: [backend, invoicing]
---

## Context

Implements `spec.md` Solution (Billing a cancelled Postpaid SIM) and user stories 19 and 20.

## Acceptance criteria

- [ ] A Contract's base amount for a billing month counts every Active Postpaid SIM, plus every Postpaid SIM of the Contract cancelled with an effective date on or after that month's first day
- [ ] A cancelled Postpaid SIM is absent from the base amount of every month after its cancellation month
- [ ] No part-month amount exists
- [ ] The Client Invoice and the Agent Invoice's Local Support Fees move together, through the shared Contract amount computation
- [ ] A sent or approved Client Invoice, and a sent, approved or paid Agent Invoice, keeps its total
- [ ] The draft Client Invoice view lists a cancelled SIM Card it still bills, marked with its cancellation date

## Tests

- **API seam:** a SIM Card cancelled mid-month, on the first day, in a past month and with a future date, each checked on the current-month draft Client Invoice and Agent Invoice; a Prepaid SIM never counts; frozen invoices unchanged.
- **E2E:** extend the Return e2e: the draft Client Invoice still shows the cancelled SIM Card's fee for the current month.

## Regression

Every invoice total. `ClientInvoiceApiTest`, `AgentInvoiceApiTest`, `AgentInvoiceByIdApiTest` and the invoice e2e specs must pass unchanged in their assertions (ADR 0001, 0002, 0003).

## Observability

N/A — no new runtime behaviour beyond a changed computation; existing invoice audit events suffice.
