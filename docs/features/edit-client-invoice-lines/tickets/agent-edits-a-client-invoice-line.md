---
id: agent-edits-a-client-invoice-line
title: Let the Agent edit a draft Client Invoice line's amount, with edited markers and reset
status: in-progress
depends_on: [serve-client-invoices-from-stored-lines]
labels: [backend]
stories: [2, 3, 4, 5, 6, 7, 8, 9, 11, 13, 15, 17, 19, 21, 22]
---

## Context

Third slice of `spec.md` (`## Execution order`). Adds `PUT /api/contracts/{contractId}/client-invoice/lines` and `ClientInvoiceService.editLine` steps 1 to 6 (spec `## Solution`, "Backend: editing a line"), the edit request DTO, `AuditLog.clientInvoiceLineEdited` and the additive response fields (`amount`, `computedAmount`, `edited` on per-SIM lines and the new `ClientInvoiceFeeLineResponse`; `basePostpaidSims` now served on every status that has per-SIM lines, null for a `BASE_AMOUNT` invoice). Modules: `dto`, `web`, `logging`. `editLine` takes an already-found invoice and goes through `ClientInvoiceAccessGuard`.

Step 7 (handing the difference to a sent Agent Invoice) is deliberately not here; it arrives in `local-support-fees-follow-billed-client-invoice-lines`. Reset is saving the computed amount: it deletes the override row on a never-sent draft, and on a draft with stored lines saves the computed amount into the row and keeps it. A Tester's response carries null `computedAmount` and `edited`.

## Acceptance criteria

- [ ] Editing a Postpaid SIM line and a Fee line on the Agent's draft returns `200` with the whole invoice recomputed: each edited line has `edited` true and its `computedAmount`, and the base amount, Fees total and total follow the new amounts; the Fee read from the Fee list and from its Request still shows the Fee as logged.
- [ ] Saving the computed amount back makes the line `edited` false and, on a never-sent draft, the line follows the Fleet and Fees again; an edited line keeps the Agent's amount when a SIM is added or a Fee is logged afterwards, an untouched line follows the change, and the new SIM or Fee appears as a new pre-filled line.
- [ ] Zero is accepted; a missing, blank, negative, or more-than-two-decimal amount gets `400` with a message; a SIM not billing this month, or a Fee of another month or Contract, gets `404`.
- [ ] An edit on a `SENT` or `APPROVED` invoice gets `409`; a Manager, a Tester or another Agent gets `403`; another Tenant's Contract gets `404`.
- [ ] On a sent-back draft (fixture), a sent line is editable, an edit of the pre-filled late-Fee line inserts its row, a reset of that line reads `edited` false again and the row stays, and a second send stores the late-Fee line.
- [ ] The Manager's by-id read of a sent invoice serves per-SIM lines with `amount`, `computedAmount` and `edited` (null `basePostpaidSims` for a `BASE_AMOUNT` invoice); a Tester's read has `computedAmount` and `edited` null and the same billed amounts; the Review Queue row's total equals the edited total.
- [ ] An edit racing the invoice's send leaves the sent lines exactly as the send stored them, the edit having landed first or got `409`; each edit writes one `clientInvoiceLineEdited` audit line naming actor, Tenant, invoice, line and old and new amount.

## Tests

- **HTTP API seam (spec Testing decisions 2):** new `ClientInvoiceLineEditApiTest`. Cases: `edit-sim-line-and-fee-line-recomputes-totals-and-marks-edited`, `fee-list-and-request-unchanged-after-edit`, `new-sim-and-new-fee-appear-and-edited-line-keeps-amount-untouched-line-follows`, `reset-by-saving-computed-amount-clears-edited`, `zero-accepted`, `negative-blank-and-three-decimal-amounts-get-400`, `unbilled-sim-or-other-months-fee-gets-404`, `edit-after-send-gets-409`, `manager-tester-and-other-agent-get-403`, `other-tenant-gets-404`, `manager-by-id-read-of-sent-invoice-serves-per-sim-lines-with-amount-computed-and-edited-and-null-for-base-amount-invoice`, `tester-read-has-null-computed-and-edited`, `review-queue-total-equals-edited-total`, `sent-back-draft-edit-of-sent-line-and-late-fee-line-and-reset-keeps-row`, `resend-after-edits-stores-late-fee-line-without-constraint-violation`, `audit-line-names-actor-tenant-line-old-and-new-amount`.
- **Races (spec item 4, committing test cleaning up its own rows):** new `ClientInvoiceLineEditRaceTest`, case `edit-versus-client-invoice-send-leaves-sent-lines-as-stored`.

## Regression

- At risk: the draft read and send from the previous ticket, the Fee list and Request Fee views (`FeeResponse` must stay unchanged), the Manager's by-id read and the PDF (they now see billed amounts), the Review Queue.
- Existing tests expected to change: `backend/src/test/java/com/remotesupport/backend/web/ClientInvoiceApiTest.java` method `basePostpaidSimsIsAbsentOnceTheInvoiceIsSentSinceThereIsNoFrozenPerUnitBreakdown` asserts `basePostpaidSims` is absent after send; this ticket now serves per-SIM lines on a sent invoice, so it is changed to assert them (the spec's item 5 exception, named in the commit). Every other method of that class keeps passing unmodified. `ClientInvoiceByIdApiTest` and `ClientInvoiceStoredLinesApiTest` are not modified.

## Observability

Each edit writes `AuditLog.clientInvoiceLineEdited(invoiceId, kind, sourceId, oldAmount, newAmount, actor, tenant)`, in the shape of `agentInvoiceOverridden` (no free text). The race test and the walkthrough grep read it.
