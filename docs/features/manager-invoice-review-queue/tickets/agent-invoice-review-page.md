---
id: agent-invoice-review-page
title: Manager reviews, overrides, approves and marks paid an Agent Invoice from the Review Queue
status: ready-for-agent
depends_on: [client-invoice-review-page]
labels: [backend, frontend, invoicing, payroll]
---

## Context

Second Review Queue slice: Agent Invoices join the queue and get their by-id routes and detail page. Implements spec.md Solution (Review Queue membership for Agent Invoices, invoice by id — Agent Invoice, Agent Invoice detail page) and user stories 4, 14-21, 23.

## Acceptance criteria

- [ ] The Review Queue also lists every Agent Invoice in `sent` or `approved`, any billing month, ordered with Client Invoices by how long each has waited (`sentAt` for `sent`, `approvedAt` for `approved`)
- [ ] The All / Client Invoices / Agent Invoices filter narrows the queue
- [ ] The Agent Invoice detail page shows Local Support Fees, Salary, both Rollout Advance lines, total, status and timestamps, for any billing month
- [ ] While `sent`, the Manager can override Salary and/or the new-advance line and approve; while `approved`, mark paid; each updates the page in place
- [ ] Override, approve and mark paid work on a past billing month's Agent Invoice
- [ ] A paid Agent Invoice no longer appears in the Review Queue; an approved one stays until paid
- [ ] A draft or paid Agent Invoice renders read-only; a failed action shows an inline error and changes nothing
- [ ] Agent and Tester get 403 on every Agent Invoice by-id route; unknown or other-tenant id is 404; no by-id route creates an invoice

## Tests

- **API seam:** queue membership (sent and approved in, draft and paid out); mixed-type ordering including an approved Agent Invoice ordered by `approvedAt`; by-id read/override/approve/paid for current and past month; override outside `sent` → 409, paid before approved → 409; override changes only that invoice's snapshot, not the standing amount; 403/404 as above.
- **Component seam:** type filter; Agent Invoice detail view shows override + Approve only while `sent`, Mark paid only while `approved`, none when draft/paid; final state after success.
- **E2E:** Agent sends their invoice → Manager opens it from the Review Queue → overrides Salary → approves → it's still in the queue → marks paid → final state → gone from the queue.

## Regression

The Agent-page review (override/approve/paid) and the Agent's own send flow keep working — `AgentInvoiceApiTest` and `agent-invoice-submission-and-approval.spec.ts` must still pass unchanged. Standing amounts' next-month-effective rule is unaffected by a by-id override (ADR 0003).

## Observability

By-id override, approve and paid log the same audit events as today's routes (Agent Invoice id, actor, tenant, old/new status or old/new value).
