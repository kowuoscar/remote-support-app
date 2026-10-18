---
id: approve-a-month-api
title: Approve the invoices left to review in an Agent's month in one call
status: ready-for-agent
depends_on: []
labels: [backend]
stories: [28, 29, 30, 32, 33, 35]
---

## Context

`spec.md` `### Prefactoring: debt paid on the way` (One approve step), `### Month approval`, `## Constraints` (All or nothing, One lock order), `## Testing decisions` items 1 to 3. No migration; new classes go in the layer packages (ADR 0006).

Demoable through the API alone: `POST /api/agents/{agentId}/client-invoice-months/{YYYY-MM}/approve` approves exactly the invoices the caller names, all or nothing. Includes: `ClientInvoiceService.approve` split so a private `markApproved(invoice, now, principal)` is shared by the single approve and `approveMonth` (behaviour unchanged); `approveMonth` locking only through the existing `findMonthForUpdate`; `ClientInvoiceMonthApprovalController` (tenant check, shared `ClientInvoiceErrors` handlers); `ClientInvoiceConflictException.Reason.NOTHING_TO_APPROVE`; the `clientInvoiceMonthApproved` audit line. `approveMonth` adds no constructor argument to `ClientInvoiceService` (`AuditLog` is static and `findMonthForUpdate` already exists).

Enables `approve-the-rest-of-the-month`. Runs in parallel with `review-by-agent-reads`.

Harness declarations for the result (the merger applies them): `ARCHITECTURE.md` entries for `ClientInvoiceMonthApprovalController` and its DTO records, and the ADR 0006 frozen-list entries for the new classes.

## Acceptance criteria

- [ ] `POST .../client-invoice-months/{month}/approve` with `{invoiceIds}` equal to the month's `SENT` invoices answers 200 `{billingMonth, approvedAt, invoiceIds}`: all are `APPROVED` with one shared `approvedAt`, an Approved or sent-back invoice of the month is untouched, a past month approves, and the Agent's `GET /api/client-invoices/{id}` reads `APPROVED` as after a single approval. The single per-invoice approve behaves exactly as before.
- [ ] Refusals write nothing: ids short by one or with an extra is 409 `MONTH_CHANGED`; no invoice `SENT` is 409 `NOTHING_TO_APPROVE` (body `{code, message}`); a missing body, `[]` or `2026-13` is 400; an Agent or a Tester is 403; another tenant's or an unknown Agent is 404.
- [ ] Two parallel month approvals of one Agent's month give one 200 and one 409 with each invoice approved once; a month approval racing a send-back of one of its invoices ends either with the send-back winning and `MONTH_CHANGED` with nothing approved, or the approval winning and the send-back answering 409, never some approved and others not. A month approval logs one status line per invoice and then one line naming the Agent, the month and the count.

## Tests

Seams per spec `## Testing decisions` items 1 to 3 (HTTP seam with real Postgres through `IntegrationTest`, a fresh Agent per case with `ClientInvoiceMonthTestSupport.sendTheMonth`; Mockito where cheaper).

- **`ClientInvoiceMonthApproveApiTest` (new, `IntegrationTest`):** three sent and approved with their ids (200, one `approvedAt`); a sent-back one untouched; ids short by one and with an extra (`MONTH_CHANGED`, nothing approved); nothing SENT (`NOTHING_TO_APPROVE`); a past month; missing body, `[]`, `2026-13` (400); Agent and Tester 403; other tenant's Agent 404; after approval a send-back is 409 and the Agent's GET reads `APPROVED`; the audit line.
- **`ClientInvoiceMonthApproveConcurrentTest` (new, outside the shared transaction, prior art `ClientInvoiceMonthSendConcurrentTest`):** two month approvals (one 200, one `NOTHING_TO_APPROVE`); a month approval racing a send-back of one invoice (all-or-nothing outcomes above).
- **`ClientInvoiceMonthApproveServiceTest` (new file, JUnit 5 + Mockito, only if cheaper than a database case):** the order of month approval's checks (nothing to approve before `MONTH_CHANGED`) and that a refusal calls `markApproved` for no invoice.

## Regression

At risk: the single `POST /api/client-invoices/{id}/approve` (now through `markApproved`), the Agent's month send and its lock order, and `ClientInvoiceService`'s constructor. `approveMonth` adds no constructor argument, so `ClientInvoiceMonthServiceTest.java` (constructs the service by hand at :71) and `ClientInvoiceServiceAttachTest.java` (at :50) are the guard and pass unmodified, as do `ClientInvoiceApiTest`'s approve cases, `ClientInvoiceMonthSendApiTest` and `ClientInvoiceMonthSendConcurrentTest`.

The only existing test-source file that may change is `backend/src/test/java/com/remotesupport/backend/support/ClientInvoiceMonthTestSupport.java`, which may gain an approve helper (additive; the existing `sendTheMonth` helper and every caller keep behaving as before).

## Observability

One status line per approved invoice (as a single approval logs) and one `clientInvoiceMonthApproved` audit line naming the Agent, the month and the count, after the per-invoice lines; a refusal logs no approval.
