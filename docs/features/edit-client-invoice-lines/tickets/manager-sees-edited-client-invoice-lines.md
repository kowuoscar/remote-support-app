---
id: manager-sees-edited-client-invoice-lines
title: Show the Manager per-SIM lines and edited markers on a sent Client Invoice
status: in-progress
depends_on: [agent-edits-lines-on-the-client-invoice-page]
labels: [frontend]
stories: [16, 17, 18, 24]
---

## Context

Sixth slice of `spec.md` (`## Execution order`), "Frontend: Manager". `ClientInvoiceDetailView` shows the per-SIM base lines when the invoice has them (not for a legacy `BASE_AMOUNT` invoice) and the "Edited · computed {amount}" line under each edited line, as muted secondary text with `Money` and `.tnum`. The Review Queue, the Pending approvals card and the Agent Invoice detail page need no change; they render the backend's totals. Modules: `components/manager`.

It depends on `agent-edits-lines-on-the-client-invoice-page` because it extends the e2e spec that ticket creates; that ticket's chain already carries the backend edit ticket whose response fields it reads. Follows `docs/agents/frontend.md`; no golden may move.

## Acceptance criteria

- [ ] A sent invoice's detail page shows its base amount broken down per Postpaid SIM, each with its billed amount.
- [ ] An edited line shows "Edited · computed {amount}" and an unedited line shows no marker; a legacy invoice with one base-amount line shows no per-SIM breakdown.
- [ ] The Review Queue row and the Dashboard's Pending approvals card show the same edited total as the detail page.
- [ ] The detail page's lines and markers are readable by keyboard and within the viewport at the mobile breakpoint.
- [ ] End to end: after the Agent's edits and send, the Manager sees the edited SIM line with "computed $25.00" and a total of $76.40.

## Tests

- **Component seam (spec item 6):** `frontend/components/manager/client-invoice-detail-view.test.tsx`. Cases: `shows-per-sim-base-lines`, `edited-line-shows-computed-amount-marker`, `unedited-line-and-legacy-base-amount-show-no-marker`.
- **e2e (spec item 7):** extend `frontend/tests/e2e/edit-client-invoice-lines.spec.ts` with the Manager's closing steps, including the queue total.

## Regression

- At risk: the Manager's Client Invoice detail page and approve action, the Review Queue and Dashboard Pending approvals card.
- Existing tests expected to change: `frontend/components/manager/client-invoice-detail-view.test.tsx` gains the cases above, every pre-existing case passing unmodified; `frontend/tests/e2e/edit-client-invoice-lines.spec.ts` is extended (it is created by the previous ticket). `manager-invoice-review-queue.spec.ts` guards the queue, unmodified.

## Observability

N/A — a read-only rendering of fields the backend already serves.
