---
id: first-send-only-through-the-month
title: Refuse a first Client Invoice send outside the month send and move the demo and tests to it
status: ready-for-agent
depends_on: [send-the-month-api]
labels: [backend]
stories: [25, 30, 31, 33, 34]
---

## Context

`spec.md` `### The per-invoice send, narrowed`, `### Demo data`, `## Testing decisions` items 2 (the late-Contract race), 4 and 6 (the e2e sentence on Jordan), and `## Decisions taken` ("Backend tests: one `sendTheMonth` helper…"). Depends on `send-the-month-api` for the month send, `firstSentAt`, `monthSentAt` and the helper `ClientInvoiceMonthTestSupport.sendTheMonth`.

Backend rule: in `ClientInvoiceService.send`, under the row lock, after the status check and `requireOpenToAgent`, before the carrier gate, a never-sent invoice (`firstSentAt` null) whose Agent's month is not sent is `409 MONTH_NOT_SENT`. Both per-invoice routes call this method. A sent-back resend and a Contract's first send once its month was sent proceed as today.

`DemoDataLoader` attaches one generated PDF per Postpaid SIM line of both of Jordan's current-month drafts (US-1, US-2), outside any transaction through `CarrierInvoiceFileService.attach`, then sends Jordan's month through `ClientInvoiceMonthService.send`; Priya's UK-1 draft stays unsent; with the store down (probed first as today) it logs one WARN and leaves both drafts unsent.

Finding that shapes the e2e part: the isolated e2e backend runs without the `demo` profile (`scripts/run-backend-for-e2e-isolated.sh` sets no profile), so the spec's premise "Jordan's month is sent by the demo at startup" is false for the e2e database. Every existing e2e spec sends a Jordan Contract's invoice one at a time, which the new rule refuses while Jordan's month is unsent. So `frontend/tests/e2e/global-setup.ts` makes Jordan's month sent once, before any spec: as the Manager it creates a Client with a Contract for the seeded Agent that has nothing to bill, then as Jordan calls the month send, tolerating `MONTH_ALREADY_SENT` (a database reused from an earlier run) and failing the run loudly on any other refusal (this part must not be swallowed by the warm-up's best-effort catch). Every other spec's new Contract is then a late one, sent on its own through the unchanged card Send.

Harness declarations: none beyond the ticket's own (no new class).

## Acceptance criteria

- [ ] `POST /api/client-invoices/{id}/send` and `POST /api/contracts/{contractId}/client-invoice/send` on a never-sent current-month draft while the Agent's month is not sent answer `409 {code: MONTH_NOT_SENT, message}` (body is exactly those two fields), also when the draft's carrier invoices are missing (the month rule is checked first).
- [ ] After the month send, a sent-back invoice resends alone (`200`, the other invoices untouched, `monthSentAt` unchanged), and a Contract created afterwards sends alone (`200`); its send with a file missing is `409 CARRIER_INVOICES_MISSING`, not `MONTH_NOT_SENT`. An Agent whose current month already holds an invoice with `firstSentAt` set (sent before this feature) reads as sent, and a remaining draft sends alone.
- [ ] The Tester of a Client sees each of its invoices from `SENT` on, and the Manager's Review Queue lists the month's invoices as separate rows, each approvable and sendable-back on its own, as before.
- [ ] A demo start sends Jordan's US-1 and US-2 current-month invoices (`SENT`, one `firstSentAt`, one file per Postpaid SIM line) and leaves Priya's UK-1 invoice `DRAFT`; with the store down the demo still starts, logs one WARN, and leaves Jordan's two drafts unsent with no write attempted.
- [ ] Every existing backend test that sends a first Client Invoice now does so through `sendTheMonth`, asserting what it asserted before (a test needing one of several Contracts sent and another not uses a second Agent; resends after a send-back keep the per-invoice route); the whole backend suite is green.
- [ ] The per-invoice race on a late Contract's draft (the month sent first) ends one `200`, one `409`.
- [ ] With the e2e global setup run, the whole existing e2e suite passes unedited on the unchanged frontend: Jordan's month is sent before the first spec, and a second run on the same database does not fail.

## Tests

Seams per spec `## Testing decisions` items 1, 2, 4 (HTTP seam, real Postgres and SeaweedFS) and 6.

- **`PerInvoiceSendNarrowedApiTest` (new, `IntegrationTest`; a new file, so the previous ticket's test file is not touched):** `MONTH_NOT_SENT` on both routes with body `{code, message}`; `MONTH_NOT_SENT` before `CARRIER_INVOICES_MISSING`; sent-back resend alone after the month send; a Contract created afterwards sends alone; its missing-file send is `CARRIER_INVOICES_MISSING`; a pre-feature month (an invoice `SENT` with `firstSentAt` set directly) reads sent and a remaining draft sends alone; a Tester sees a sent invoice; the Review Queue lists a month's invoices as separate rows.
- **`DemoDataLoaderApiTest` (existing, edited) and `DemoDataLoaderStoreDownTest` (existing, edited):** see Regression.
- **E2E:** `global-setup.ts` covered by the suite itself; no new spec.

## Regression

At risk: every flow that sends a first Client Invoice (the send gate, send-back, Agent Invoice, tenant totals, the Review Queue, the demo), and the e2e suite's dependence on Jordan's month.

Existing tests expected to change, each with the reason (the merger refuses any unlisted change; none of these changes what it asserts):

- `backend/src/test/java/com/remotesupport/backend/web/AgentInvoiceApiTest.java`: sends contract2/contract3 per invoice; moves to `sendTheMonth`, a second Agent where one Contract must stay unsent.
- `.../web/CarrierInvoiceFileAttachConcurrentSendTest.java`: its two send races start from a first send; the first send moves to `sendTheMonth`, the race stays per-invoice on a late Contract.
- `.../web/CarrierInvoiceRetentionTest.java`: its private `send` helper moves to `sendTheMonth`.
- `.../web/ClientInvoiceApiTest.java`: its send cases (sending, freezing, already sent, wrong role, other Agent's Contract) move to `sendTheMonth`.
- `.../web/ClientInvoiceByIdApiTest.java`: `send` and `sendById` first sends move to `sendTheMonth`; the by-id carrier-gate cases (`sendByIdWithAMissingLine…`, the store-unavailable ones) run on a late Contract's draft (second Agent whose month is sent) so they still reach the carrier gate. Direct fixtures (`new ClientInvoice()` + `setSentAt`, at :906) set `firstSentAt` alongside `sentAt`.
- `.../web/ClientInvoiceConcurrentSendTest.java`: keeps its per-invoice race, now on a late Contract's draft (the month sent first).
- `.../web/ClientInvoiceLineEditApiTest.java`: its `send()` helper moves to `sendTheMonth`. Direct fixtures (`new ClientInvoice()` + `setSentAt`, at :400, :530) set `firstSentAt` alongside `sentAt`.
- `.../web/ClientInvoiceLineEditRaceTest.java`: its send moves to `sendTheMonth`.
- `.../web/ClientInvoiceSendBackApiTest.java`: its `send()` first sends move to `sendTheMonth`; resends after a send-back keep the per-invoice route. Direct fixtures (`new ClientInvoice()` + `setSentAt`, at :287, :1076) set `firstSentAt` alongside `sentAt`.
- `.../web/ClientInvoiceSendBackRaceTest.java`: its setup send moves to `sendTheMonth`.
- `.../web/ClientInvoiceStoredLinesApiTest.java`: `send()` and `sendExpecting` move to `sendTheMonth` (a refusal case that expected 409 on a first send keeps its expectation via the month send). Direct fixtures (`new ClientInvoice()` + `setSentAt`, at :143) set `firstSentAt` alongside `sentAt`.
- `.../web/LocalSupportFeesFollowClientInvoiceApiTest.java`: `sendClientInvoice` moves to `sendTheMonth`. Direct fixtures (`new ClientInvoice()` + `setSentAt`, at :356) set `firstSentAt` alongside `sentAt`.
- `.../web/MeClientApiTest.java`: `sendCurrent` moves to `sendTheMonth`.
- `.../web/PostpaidSimPlanApiTest.java`: its send moves to `sendTheMonth`.
- `.../web/ReviewQueueApiTest.java`: `sendClientInvoice` moves to `sendTheMonth`. Direct fixtures (`new ClientInvoice()` + `setSentAt`, at :85, :107) set `firstSentAt` alongside `sentAt`.
- `.../web/SimCardCorrectionApiTest.java`: its sends move to `sendTheMonth`; the resend after send-back keeps the per-invoice route.
- `.../web/TenantTotalsApiTest.java`: `sendClientInvoice` moves to `sendTheMonth`. Direct fixtures (`new ClientInvoice()` + `setSentAt`, at :140) set `firstSentAt` alongside `sentAt`.
- `.../demo/DemoDataLoaderApiTest.java`: asserts Jordan's two current-month invoices `SENT` with one file per Postpaid SIM line and one `firstSentAt`, and Priya's `DRAFT`.
- `.../demo/DemoDataLoaderStoreDownTest.java`: asserts Jordan's two current-month drafts remain `DRAFT` with the store down, and still one WARN, no write.
- `frontend/tests/e2e/global-setup.ts`: gains the month seeding described in Context. Its existing warm-up stays as it is.
- `ClientInvoiceServiceAttachTest` and `CarrierInvoiceFileServiceTest` are expected to stay unchanged (the service constructor is kept). `backend/src/test/java/com/remotesupport/backend/support/OtherTenantFixture.java` (line ~105) builds a SENT invoice without `firstSentAt`; it only proves the other tenant's data stays hidden, so it is expected to stay unchanged, and if it must change it may set `firstSentAt` alongside `sentAt` and nothing else. No other test file is changed; in particular `CarrierInvoiceTestSupport`, `IntegrationTest` and every other e2e spec stay unedited, and the Agent Invoice sends in `AgentInvoiceByIdApiTest` and `LocalSupportFeesRaceTest` (no Client Invoice send) are untouched.

## Observability

A refused first send logs nothing new (the existing coded-409 path); the demo loader keeps its one WARN when the store is down and its INFO lines for the month it sends. Nothing else.
