---
id: client-invoice-generation
title: Agent builds a Contract's draft Client Invoice for the month
status: done
depends_on: [fee-logging-and-provisioning]
labels: [backend, frontend, invoicing]
---

## Context

Implements the Client Invoice's draft state and its base-amount/Fee-line computation from the spec's Solution and user stories 22-23.

## Acceptance criteria

- [ ] Agent can open a Contract's Client Invoice for the current month, created in status draft on first access
- [ ] The base amount equals the sum of the monthly fee of every Postpaid SIM active in the Contract's Fleet at the time of viewing
- [ ] Every Fee logged against the Contract this month appears as its own line
- [ ] Agent can attach one or more carrier invoice files to the draft Client Invoice
- [ ] The draft view is scannable at a glance: base amount, each Fee line, and attached files are all visible together

## Tests

Backend HTTP API seam: base-amount calculation with a mix of Postpaid/Prepaid SIMs; Fee lines reflect the month's logged Fees; file attachment; only the Contract's Agent can build/view its draft.

## Regression

N/A — new capability.

## Observability

N/A — draft assembly has no side effects beyond the invoice record itself, already covered by standard entity-write logging.
