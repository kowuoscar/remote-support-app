---
id: attach-carrier-invoice-per-sim
title: Attach one Carrier Invoice File per Postpaid SIM line through a form
status: done
depends_on: []
labels: [backend, frontend]
stories: [1, 2, 8, 9, 10, 16, 26, 27]
---

## Context

`spec.md` `### Prefactoring`, `### Schema: V61`, `### Attaching: the contract`, `### Reading` (`CarrierInvoiceFileResponse.simCardId` only), `### Frontend` (types, attach form) and `## Design direction` (the dialog). The foundation of the feature: `carrier-invoice-computed-name` and `carrier-invoice-check-mark` depend on it for `sim_card_id`; `send-requires-every-carrier-invoice` and `remove-carrier-invoice-file` follow through the check mark.

Prefactoring first: one advice renders `ClientInvoiceConflictException` as `{code, message}` for both Client Invoice controllers (spec Prefactoring), because the already-attached refusal needs it on the current-month route too. Flyway number: V61 unless the orchestrator reserved another (spec `### Schema`).

Attach now requires the multipart part `simCardId`; the multi-file picker is gone. No check mark and no `carrierInvoiceFileId` yet (the next ticket), no send gate, no Remove, no retention (spec Non-goals). The frontend helper `missingCarrierInvoiceLines(invoice)` is introduced here: it returns the invoice's Postpaid SIM lines whose SIM has no entry in `files[].simCardId`. The dialog and Attach button use it; `send-requires-every-carrier-invoice` reads it for the Send note. The stored `filename` is a plain placeholder in this ticket: the uploaded file's own name, as today. `carrier-invoice-computed-name` replaces it with the computed name (V61 keeps `filename VARCHAR(255)`, so no schema change is left for it). Frontend DoD in `docs/agents/frontend.md` applies; no `DESIGN.md` change and no golden moves in this ticket (the card looks the same).

Tests that attach today move to the new contract in this ticket (they must send `simCardId`); sending tests are `send-requires-every-carrier-invoice`'s.

## Acceptance criteria

- [ ] As the Agent, "Attach carrier invoice" on an editable draft with Postpaid SIM lines opens a form with a required "Postpaid SIM" field (options `{number} · {Carrier}`, the bare number for a SIM with no Carrier, only lines without their file, preselected when one is left) and a required single-file "File" field; Attach posts `file` and `simCardId`, the dialog closes and the page refreshes.
- [ ] The attach API answers 400 without `simCardId` (before any upload, nothing in the bucket) and 400 "Not a Postpaid SIM line of this Client Invoice" for a Prepaid SIM, another Contract's SIM or a SIM not billing this month; a second attach for the same SIM answers 409 `{code: CARRIER_INVOICE_ALREADY_ATTACHED, message}` on both the by-id and the current-month routes, leaving one row and one object.
- [ ] The dialog stays open on failure and shows "This SIM already has its carrier invoice. Remove it first to replace it." for that 409, "File storage is unavailable right now. Try again later." for 503, "Couldn't attach the file. Try again." for any other failure.
- [ ] A Contract with 3 Postpaid SIMs and 1 Prepaid SIM offers exactly 3 lines in the form and none for the Prepaid SIM.
- [ ] "Attach carrier invoice" is disabled when every Postpaid SIM line has its file or `filesAvailable` is false, and absent when the invoice has no Postpaid SIM line. A file whose SIM has no line on the invoice stays listed in the Agent's and Tester's lists and is not offered in the form.
- [ ] The database holds at most one file per (Client Invoice, SIM Card), and a row written now always carries its SIM.

## Tests

Seams per spec `## Testing decisions`: items 1 (HTTP, real Postgres and SeaweedFS), 5 (component), 6 (e2e).

- **`ClientInvoiceByIdApiTest` and `ClientInvoiceApiTest` (HTTP seam):** attach with `simCardId` gives 201 with `simCardId` in the body; without `simCardId` gives 400 and no object under the prefix; Prepaid SIM, other-Contract SIM and non-billing SIM each give 400; a second attach for the same SIM gives coded 409 with one row and one object, on both routes with a `{code, message}` body; a file whose SIM has no line is listed; the file is listed and downloads under its uploaded name (the placeholder). Prior art: the existing attach/list/download cases.
- **Unique-constraint race to coded 409:** a Mockito case on `ClientInvoiceService.recordAttachedFile` where the repository save throws a constraint violation for `uq_carrier_invoice_files_invoice_sim` and the service raises `ClientInvoiceConflictException` `CARRIER_INVOICE_ALREADY_ATTACHED`, plus a racing-attach case (two simultaneous attaches for one SIM: one 201, one coded 409, one row, one object) in `CarrierInvoiceFileAttachConcurrentSendTest`'s sibling concurrency class or in that class.
- **Frontend component tests:** a new test beside `AttachCarrierInvoiceDialog`: lists only missing lines labelled `{number} · {Carrier}`, preselects the last one, posts `file` and `simCardId`, shows each error text with the dialog still open. `client-invoices-view.test.tsx`: Attach disabled when nothing is missing, disabled while `filesAvailable` is false, absent with no Postpaid lines. Unit cases for `missingCarrierInvoiceLines`.
- **E2E:** `helpers.ts` gains `attachCarrierInvoice(page, simNumber)` through the form; `client-invoice-generation.spec.ts` replaces its hidden-input attach with the form and attaches a file per Postpaid SIM. Prior art: `client-invoice-generation.spec.ts:26-88`.
- **Visual:** the four `agent-client-invoices-*` goldens must not move; one that does is a defect.

## Regression

At risk: the attach, list and download flow of Carrier Invoice Files; the coded 409 body of `send-a-client-invoice-back` on the by-id route; the current-month route's conflict body; the Tester's Invoices page; all Client Invoice goldens (unchanged).

Existing tests expected to change, each with the reason:

- `backend/src/test/java/com/remotesupport/backend/support/IntegrationTest.java`: adds the helper `addUniquePostpaidSim` (additive; the new attach tests need a Postpaid SIM with a unique number); nothing existing changes. Accepted by the orchestrator at merge.

- `ClientInvoiceApiTest` and `ClientInvoiceByIdApiTest`: the pre-existing attach cases must send `simCardId` now that it is required; their assertions on status, list and download move to the computed name. New cases are added; every other pre-existing method keeps passing unmodified.
- `ClientInvoiceSendBackApiTest` (`postFileById` ~line 560 and `sentInvoiceWithFile` ~line 618 post to `/files`): they must send the `simCardId` of a real Postpaid SIM line of the invoice; every other pre-existing method keeps passing unmodified.
- `ObjectStoreUnusableStartupCases` (`attachAnswers503`, shared by the four `ObjectStore*StartupTest` classes): its attach gets a Postpaid SIM line and its `simCardId`, so the availability gate (503) is still what is tested; every other pre-existing method keeps passing unmodified. No other backend, frontend or e2e test posts to a `/files` route without `simCardId` (grep: `ClientInvoiceByIdApiTest`, `ClientInvoiceApiTest`, `CarrierInvoiceFileAttachConcurrentSendTest`, `client-invoice-generation.spec.ts` are named here).
- `CarrierInvoiceFileAttachConcurrentSendTest` and `CarrierInvoiceFileServiceTest` (the test files that call `attach` / `recordAttachedFile`): the call signature gains `simCardId`; the racing-attach case is added; every pre-existing method keeps passing apart from that argument.
- `frontend/components/agent/client-invoices-view.test.tsx`, `frontend/components/manager/client-invoice-detail-view.test.tsx` and `frontend/components/manager/contracts-view.test.tsx` (and other fixtures building a Client Invoice): fixtures gain `simCardId` on files; the old multi-file picker cases are replaced by the dialog cases. Every other pre-existing case keeps passing unmodified.
- `carrier-invoice-files-section.test.tsx`: fixtures gain `simCardId` only.
- `client-invoice-generation.spec.ts`: hidden-input attach replaced by the form (spec Testing 6); its other steps keep passing.
- `tests/visual/stub-backend.mjs`: files gain `simCardId`; no golden is recaptured.

## Observability

N/A: no new operation beyond the existing attach; the attach audit log (`AuditLog.created`) is unchanged and the existing WARN/ERROR paths of the attach undo stay as they are.
