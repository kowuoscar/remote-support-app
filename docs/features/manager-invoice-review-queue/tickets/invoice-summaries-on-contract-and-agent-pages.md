---
id: invoice-summaries-on-contract-and-agent-pages
title: Replace invoice review on the Contract and Agent pages with a summary linking to the detail page
status: in-progress
depends_on: [client-invoice-review-page, agent-invoice-review-page]
labels: [frontend, invoicing]
---

## Context

Migrate step: review actions move to one place, the invoice detail page. Implements spec.md Solution (Contract and Agent pages) and user stories 26-28.

## Acceptance criteria

- [ ] The Contract page shows its current month's Client Invoice as a compact summary (month, status, total) with an "Open invoice" link to its detail page, and no approve action
- [ ] The Agent page shows its current month's Agent Invoice as the same kind of summary with an "Open invoice" link, and no override/approve/mark-paid action; standing amounts stay on the page
- [ ] The link works whatever the invoice's status, including draft
- [ ] The Manager frontend no longer calls any current-month Manager action route (Client Invoice approve; Agent Invoice override/approve/paid)
- [ ] `client-invoice-submission-and-visibility.spec.ts` and `agent-invoice-submission-and-approval.spec.ts` perform the Manager's review through the Review Queue / summary link and the detail page, not on the Contract or Agent page

## Tests

- **Component seam:** summary renders month, status badge, total and the correct link for each status.
- **E2E:** the two migrated specs above, plus: from the Contract page and from the Agent page, "Open invoice" lands on the matching detail page.

## Regression

Agent standing amounts on the Agent page (`agent-standing-amounts-and-invoice-generation.spec.ts`) and the Contract Fleet section (`fleet-management.spec.ts`) keep working; the Tester's invoice view (`client-invoice-submission-and-visibility.spec.ts`, Tester part) is unchanged.

## Observability

N/A — presentation change only; no new server behaviour.
