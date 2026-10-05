---
id: agent-sees-sent-back-invoices-on-the-client-invoices-page
title: Show the Agent every Client Invoice sent back to them on the Client Invoices page
status: done
depends_on: [agent-lists-the-client-invoices-sent-back-to-them, agent-opens-a-client-invoice-on-its-own-page]
labels: [frontend]
stories: [9, 10, 11, 13, 14, 22, 23, 34]
---

## Context

Closing slice of `spec.md` (`## Execution order`), "Frontend: Agent", "Client Invoices page". Above the Contract switcher, a "Sent back to you" section (a Card holding a Table in `TableScroll`) lists the sent-back invoices: Contract, billing month, when sent back, the reason clamped to two lines, and an **Open** row action to `/agent/client-invoices/{invoiceId}`. It is not rendered at all when the list is empty. When the current month's invoice is itself sent back, its card shows the reason and the **Sent back** badge. Adds the BFF proxy for `GET /api/client-invoices/sent-back` and its type. Also plays the whole journey in one e2e spec, so it needs the Manager's control and the Agent's by-id page. Modules: `components/agent`, `app/agent`, `app/api`.

## Acceptance criteria

- [ ] With sent-back invoices, the page shows "Sent back to you" above the Contract switcher, one row each with Contract, month, date and the reason, and Open leading to the invoice's by-id page; a resent invoice leaves the section.
- [ ] The current month's card, when sent back, shows the reason and the **Sent back** badge.
- [ ] With nothing sent back the section is absent and the page looks exactly as before (no golden moves).
- [ ] End to end on a Contract the spec creates: the Agent sends; the Manager opens it from the Review Queue and sends it back with a reason and the queue no longer lists it; the Agent logs a Fee of the month, sees the invoice under "Sent back to you" with the reason, opens it by id, sees the amounts as sent plus the Fee as a new line, edits a Postpaid SIM line and resends; the Manager sees it back in the queue with the late Fee's line, the edited line marked "Edited · computed …" and the earlier reason, and approves it.
- [ ] The section and its Open action work by keyboard alone with a visible focus ring, and the table is usable at the mobile breakpoint within the viewport.

## Tests

- **Component seam (spec Testing decisions 4):** `client-invoices-view.test.tsx` gains `section-absent-when-list-is-empty`, `rows-show-contract-month-date-reason-and-open-link`, `current-month-card-shows-reason-and-badge-when-sent-back`.
- **e2e (spec Testing decisions 5):** new `frontend/tests/e2e/send-a-client-invoice-back.spec.ts`, prior art `client-invoice-submission-and-visibility.spec.ts` and `edit-client-invoice-lines.spec.ts`, case `manager-sends-back-agent-corrects-and-resends-manager-approves`. A past month is not played in e2e (spec Testing decisions 5).
- **Visual:** no new goldens; the Agent's Client Invoices golden, if captured, must not move.

## Regression

- At risk: the Agent's Client Invoices page and its Contract switcher, the Review Queue and Manager detail page the e2e drives, every golden.
- Existing tests expected to change: `frontend/components/agent/client-invoices-view.test.tsx` gains the cases above and every pre-existing case passes unmodified. `client-invoice-submission-and-visibility.spec.ts`, `edit-client-invoice-lines.spec.ts` and `manager-invoice-review-queue.spec.ts` are not modified here.

## Observability

N/A — a read-only section over an existing list endpoint; the BFF proxy logs failures as every other proxy does.
