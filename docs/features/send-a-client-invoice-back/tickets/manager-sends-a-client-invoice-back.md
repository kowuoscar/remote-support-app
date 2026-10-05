---
id: manager-sends-a-client-invoice-back
title: Let a Manager send a sent Client Invoice back to draft with a reason
status: in-progress
depends_on: []
labels: [backend]
stories: [1, 2, 4, 5, 6, 7, 8, 9, 11, 12, 16, 26, 27, 29, 30, 31, 32, 34, 36]
---

## Context

First slice of `spec.md` (`## Execution order`), the backend half, resting on the merged `edit-client-invoice-lines`. Adds `POST /api/client-invoices/{invoiceId}/send-back` (spec `## Solution`, "Backend: send back" and "The lifecycle, amended"): migration `V57` (nullable `sent_back_at`, `sent_back_reason varchar(1000)` on `client_invoices`), the `SENT -> DRAFT` edge in `ClientInvoiceStatus.canTransitionTo`, `ClientInvoiceService.sendBack(invoice, reason, principal)` under the Client Invoice row lock that `send` and `editLine` already take, `approve` made to take the same lock, `sentBackAt` and `sentBackReason` on `ClientInvoiceResponse` (null for a Tester), and the audit line through the existing `AuditLog.statusChanged`. A send-back writes no `client_invoice_lines` row and never clears `linesStored`.

Also records ADR 0005 and adds the dated "Amended by ADR 0005" note to ADR 0001 (spec `## Solution`, "ADR"; ADR 0004 is the stored-lines model). Modules: `domain`, `dto`, `web`.

The Agent resends through the existing current-month `POST /api/contracts/{contractId}/client-invoice/send`, which suffices for current-month invoices; the by-id Agent routes and past-month resends belong to `agent-reaches-a-client-invoice-by-its-id`. A past-month sent invoice is built by a fixture in the way `DemoDataLoader.writePastClientInvoice` does.

## Acceptance criteria

- [ ] A Manager's `POST .../send-back` with a reason on a `SENT` invoice (current month, past month, or a backfilled one with a single `BASE_AMOUNT` line) returns `200` with `status` `DRAFT`, `sentBackAt`, `sentBackReason`, no `sentAt`, and every line, `amount` and `computedAmount` exactly as sent; a Postpaid SIM added afterwards adds no line and moves no amount.
- [ ] The invoice leaves the Review Queue after the send-back; after the Agent's current-month resend it is listed again, `waitingSince` equal to the new `sentAt`, with the resent total, and a later send-back of the same invoice succeeds again.
- [ ] A draft or an approved invoice gets `409`; a blank or 1001-character reason gets `400`; approving a sent-back draft gets `409`; an Agent or a Tester gets `403`; an unknown or other-Tenant invoice gets `404`.
- [ ] A Tester's current-month read of a sent-back invoice gets `403`; after the resend it gets `200` with the resent numbers and null `sentBackReason` and `sentBackAt`, and the PDF prints no reason; a Manager's by-id read of the resent invoice still shows `sentBackAt` and `sentBackReason`.
- [ ] A send-back and an approval of the same invoice, racing, leave exactly one winner and the other `409`, and the final invoice is either `APPROVED` with no `sentBackAt`, or `DRAFT` with `sentBackAt` and no `approvedAt`, its lines as sent in both cases.
- [ ] Each send-back writes one `STATUS_CHANGE` audit line for `ClientInvoice` from `SENT` to `DRAFT` naming the Manager and Tenant, and the reason text appears in no log line.
- [ ] ADR 0005 exists, with the note on ADR 0001 in the shape ADR 0003's 2026-09-17 note set, stating that nothing is recomputed or cleared, that a late Fee joins as a new line, that a send-back and a resend move no pay, and that `approved` stays terminal.

## Tests

- **HTTP API seam (spec Testing decisions 1):** new `ClientInvoiceSendBackApiTest`. Cases: `send-back-current-month-invoice-returns-draft-with-lines-amounts-and-computed-as-sent`, `new-postpaid-sim-after-send-back-adds-no-line`, `send-back-past-month-fixture-invoice-returns-lines-as-sent`, `send-back-backfilled-invoice-returns-single-base-line-and-fee-lines`, `review-queue-excludes-after-send-back-and-includes-after-resend-ordered-by-new-sent-at-with-resent-total`, `send-back-twice-with-resend-between-succeeds`, `send-back-of-draft-or-approved-is-409`, `blank-and-1001-character-reason-are-400`, `approve-after-send-back-is-409`, `agent-and-tester-get-403`, `other-tenant-invoice-is-404`, `tester-read-of-sent-back-is-403-and-after-resend-has-null-reason`, `manager-reads-resent-invoice-with-sent-back-reason-and-pdf-carries-none`, `audit-line-is-status-change-sent-to-draft-without-reason-text`.
- **Race (spec Testing decisions 2):** new `ClientInvoiceSendBackRaceTest`, committing, cleaning up its own rows, prior art `ClientInvoiceLineEditRaceTest` and `ClientInvoiceConcurrentSendTest`. Case: `send-back-versus-approve-leaves-one-consistent-final-state`.

## Regression

- At risk: approve (now locks), the current-month send and the freeze, the Review Queue and Dashboard Pending approvals card, the Tester's view and the PDF, `toResponse`, the stored-lines draft served by `ClientInvoiceService.lines()`, the Agent's pay following `AgentInvoiceService.followClientInvoiceEdit`.
- Existing tests expected to change: none. `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest` (its approve cases now run under the lock and keep passing), `ClientInvoiceStoredLinesApiTest` (its sent-back fixture cases stay as they are), `ClientInvoiceLineEditApiTest`, `ClientInvoiceLineEditRaceTest`, `ClientInvoiceConcurrentSendTest`, `ReviewQueueApiTest` and `LocalSupportFeesFollowClientInvoiceApiTest` pass unmodified. If adding the response fields forces an exact-body assertion in an existing test to change, that test is declared in the result, not edited silently.

## Observability

`AuditLog.statusChanged("ClientInvoice", id, "SENT", "DRAFT", actor, tenant)`, reused as is; the reason's text is never logged, following `requestRejected`'s rule. The tests and walkthrough step 8 grep it.
