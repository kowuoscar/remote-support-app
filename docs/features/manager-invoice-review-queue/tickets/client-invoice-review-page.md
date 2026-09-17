---
id: client-invoice-review-page
title: Manager reviews and approves a Client Invoice from the Review Queue
status: in-progress
depends_on: [component-test-harness]
labels: [backend, frontend, invoicing]
---

## Context

First tracer bullet of the Review Queue: the queue endpoint and page, the Client Invoice by-id routes, and its detail page. Implements spec.md Solution (Backend: Review Queue, invoice by id — Client Invoice; Frontend: Invoices page, Client Invoice detail page) and user stories 1-13, 22-24, 28-29.

## Acceptance criteria

- [ ] Manager's Invoices page lists every Client Invoice in `sent` across all Contracts and billing months, longest waiting first, from the backend — the sample data and the review modal are gone
- [ ] Each row shows type, subject (Client — Agent), billing month, total and how long it has waited, and links to that invoice's detail page
- [ ] The empty state shows when nothing is waiting
- [ ] The Client Invoice detail page shows the full invoice (base amount, Fee lines, total, status, timestamps), its Carrier Invoice Files for download, and its PDF, for any billing month
- [ ] Manager can approve a `sent` Client Invoice from the detail page, including one from a past billing month; the page updates in place to the final read-only state
- [ ] An approved invoice no longer appears in the Review Queue
- [ ] A Client Invoice in a status with no Manager action (draft, approved) renders read-only
- [ ] A failed approval (409 or other) shows an inline error and changes nothing
- [ ] The detail page links back to the Invoices page
- [ ] Agent and Tester cannot use the Review Queue or any Client Invoice by-id route (403); an unknown or other-tenant id is 404; no by-id route ever creates an invoice

## Tests

- **API seam:** queue membership (sent in, draft and approved out) and past-month inclusion; order by `sentAt` with id tiebreak; totals from snapshot; by-id read/files/PDF/approve for current and past month; approve from draft/approved → 409; 403 for Agent/Tester on every new route; 404 unknown/other tenant; by-id read on a nonexistent Contract month creates nothing.
- **Component seam:** queue view renders rows, empty state and correct detail links; Client Invoice detail view shows Approve only while `sent` and final state after success.
- **E2E:** Agent sends a Client Invoice → Manager opens it from the Review Queue → approves → sees final state → back on the queue it's gone.

## Regression

The Client Invoice approve on the Contract page, the Agent's build/send/attach flow, and the Tester's read-only view keep working — protected by the existing `ClientInvoiceApiTest` and `client-invoice-submission-and-visibility.spec.ts`, which must still pass unchanged. Snapshot-on-send (ADR 0001) is protected by asserting a Fee logged after send doesn't change the by-id total.

## Observability

The by-id approve logs the same status-transition audit event as today's approve (Client Invoice id, old/new status, actor, tenant). Review Queue reads log at debug with tenant and item count.
