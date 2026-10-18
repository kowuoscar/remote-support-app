---
id: send-the-month-api
title: Send an Agent's whole month of Client Invoices in one call
status: done
depends_on: []
labels: [backend]
stories: [4, 5, 6, 26, 27, 28, 29, 32]
---

## Context

`spec.md` `### Prefactoring: debt paid on the way`, `### Schema: one column`, `### The model`, `### The month send: ClientInvoiceMonthService`, `### HTTP contract`, `## Constraints`, `## Testing decisions` items 1 to 3. Flyway: V63 (list `backend/src/main/resources/db/migration` first; V62 is the last on `main`).

The first half of the backend, demoable through the API alone: `POST /api/me/client-invoice-months/{YYYY-MM}/send` creates any missing current-month draft of the Agent's Contracts, then sends every one in one transaction, all or nothing. Also in this ticket: the three prefactorings (`ClientInvoiceDrafts` extracted from `ClientInvoiceController`; `BillingMonth.current()` in the controller and `ClientInvoiceService.requireOpenToAgent`; `ClientInvoiceService.send` split so a private `markSent(invoice, now, principal)` is shared by the per-invoice send and the month send, behaviour unchanged), the `first_sent_at` column and backfill, `monthSentAt` and `sendableOnItsOwn` on `ClientInvoiceResponse`, the `clientInvoiceMonthSent` audit line, and the new test helper `sendTheMonth(mockMvc, agentToken)` in a NEW file `backend/src/test/java/com/remotesupport/backend/support/ClientInvoiceMonthTestSupport.java` (a new file, so `CarrierInvoiceTestSupport` stays untouched).

Not in this ticket (`first-send-only-through-the-month`): the `MONTH_NOT_SENT` gate on the per-invoice send, the demo loader, and moving existing tests to the helper. Until that ticket merges the per-invoice send still accepts a first send, so every existing test passes unedited.

Keep `ClientInvoiceService`'s constructor unchanged (the month queries use the repository it already holds) so `ClientInvoiceServiceAttachTest`, which builds it by hand, needs no edit.

Harness declarations for the result (the merger applies them): `ARCHITECTURE.md` entries for V63, `ClientInvoiceDrafts`, `ClientInvoiceMonthService`, `ClientInvoiceMonthController`, `ClientInvoiceMonthSendResponse` (and the request body record), the new repository methods, and the ADR 0006 frozen-list entries for the new `web`/`dto`/`repository` classes.

## Acceptance criteria

- [ ] As an Agent holding three Contracts (two with a Postpaid SIM whose line has its file, one with nothing, one of the three never viewed), `POST /api/me/client-invoice-months/{current YYYY-MM}/send` answers `200 {billingMonth, sentAt, invoiceIds}`: three invoices `SENT` with one shared `sentAt` and `firstSentAt`, lines stored, the empty one at total 0 (the never-viewed Contract's draft is created by the send). One audit line names the Agent, the month and the count 3.
- [ ] When any invoice has a Postpaid SIM line without its file, the answer is `409 CARRIER_INVOICES_MISSING` whose body carries `invoiceIds` of exactly the not-ready invoices; every invoice stays `DRAFT`, `linesStored` false, no line row, `first_sent_at` null. Every other coded body stays `{code, message}`.
- [ ] The other refusals write nothing: `invoiceIds` in the body differing from the month's invoices is `409 MONTH_CHANGED`; a second send is `409 MONTH_ALREADY_SENT`; last month or next month is `409 NOT_THE_CURRENT_MONTH`; an Agent with no Contract is `409 NOTHING_TO_SEND`; a malformed month (`2026-13`) is `400`.
- [ ] A Manager, a Tester, and an Agent login without an Agent get `403`; another Agent's invoices are never in the batch. An absent body means "whatever the month holds".
- [ ] Two month sends of one Agent in parallel: one `200`, one `409 MONTH_ALREADY_SENT`, one line row per line. A month send racing a file remove on one of its invoices ends either `CARRIER_INVOICES_MISSING` with no invoice sent, or sent with the remove answering `409`: never a sent invoice missing its file, never some invoices sent and others not.
- [ ] `GET /api/client-invoices/{id}` serves `monthSentAt` (the Agent's month `sentAt`, kept through a send-back and resend) and `sendableOnItsOwn` (`DRAFT`, open to its Agent, and sent back or `monthSentAt` set) to the Agent and the Manager, and null/false to a Tester.
- [ ] The migration adds `first_sent_at` and backfills a `SENT`, an `APPROVED`, a sent-back and a never-sent row from the previous version to `COALESCE(sent_at, approved_at, sent_back_at, created_at)`, the never-sent row staying null; the per-invoice sends and every existing endpoint behave exactly as before.

## Tests

Seams per spec `## Testing decisions` items 1 to 3 (HTTP seam with real Postgres and SeaweedFS through `IntegrationTest` and `ObjectStoreTestSupport`; Mockito for the order of checks).

- **`ClientInvoiceMonthSendApiTest` (new, `IntegrationTest`):** the three-Contract month (shared `sentAt`/`firstSentAt`, lines stored, empty one at 0); a never-viewed Contract's draft created and sent; one missing file gives `CARRIER_INVOICES_MISSING` with exactly the not-ready `invoiceIds` and nothing written (rows read back through the repositories); `MONTH_CHANGED`; second send `MONTH_ALREADY_SENT`; last and next month `NOT_THE_CURRENT_MONTH`; `2026-13` is 400; no Contract `NOTHING_TO_SEND`; Manager and Tester 403; another Agent's invoices not in the batch; absent body; the audit line; `monthSentAt`/`sendableOnItsOwn` in each state (draft, sent, sent back then resent through the per-invoice route with `monthSentAt` unchanged, Tester view). A fresh Agent per case that needs several Contracts (the seeded Agent holds none in the test database).
- **`ClientInvoiceMonthSendConcurrentTest` (new, outside the shared transaction, prior art `ClientInvoiceConcurrentSendTest` and `CarrierInvoiceFileAttachConcurrentSendTest`):** two month sends race; a month send races a file remove.
- **`ClientInvoiceMonthServiceTest` (new, JUnit 5 + Mockito):** the order of checks (current month, then nothing to send, then already sent, then `MONTH_CHANGED`, then carrier gate), and that a refusal calls `markSent` for no invoice.
- **`FirstSentAtMigrationTest` (new, prior art `ClientInvoiceLinesMigrationTest`):** a throwaway database migrated to V62, loaded with the four rows, migrated through V63.
- **`ClientInvoiceMonthTestSupport.sendTheMonth` (new helper):** exercised by the new API test.

## Regression

At risk: `ClientInvoiceController`'s draft get-or-create and its V11 unique-constraint fallback, `ClientInvoiceService.send` and `requireOpenToAgent` (moved to `BillingMonth.current()` and `markSent`), the `ClientInvoiceResponse` consumers, `SecurityConfig` matcher order (the new route sits before the general `/api/me` fall-through), and `ClientInvoiceErrors`' coded bodies.

No existing test file is expected to change: the per-invoice sends are not narrowed here, the two new response fields are additive, and the new tests, helper and migration test are new files. `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest`, `ClientInvoiceSendBackApiTest`, `ClientInvoiceConcurrentSendTest`, `ClientInvoiceServiceAttachTest` and `CarrierInvoiceFileServiceTest` are the ones that exercise the refactored code and must stay green unedited. If the refactor forces an edit of any existing test file, return a proposed split instead of editing it (the merger refuses unlisted changes).

## Observability

One audit line per month send (`AuditLog.clientInvoiceMonthSent`: Agent, month, count, actor, tenant) after each invoice's status-change line. Refusals use the existing coded-409 path and log nothing new. No store call and no log of file bytes or keys inside the month send's transaction.
