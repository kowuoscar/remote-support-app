---
id: serve-client-invoices-from-stored-lines
title: Freeze a Client Invoice's lines at send and serve every read from them
status: done
depends_on: [store-client-invoice-lines]
labels: [enabler, backend]
stories: [1, 10, 20, 23, 25]
---

## Context

Second slice of `spec.md` (`## Execution order`). Prefactoring and read switch; it enables `agent-edits-a-client-invoice-line`, which edits through the resolution and the send built here. Modules: `web`, `repository`, `demo`.

- **One send, in the service** (spec `## Solution`, "Prefactoring"): sending moves from `ClientInvoiceController` into `ClientInvoiceService` as one `@Transactional` operation on an already-found invoice, re-reading it under a new row-locking finder on `ClientInvoiceRepository`. It stores a row for every line the invoice shows that has none, sets `linesStored`, and stops writing `snapshotBaseAmount` and Fee snapshot rows. On a draft with stored lines it leaves every existing row alone.
- **One line resolution** (spec "The model"): `linesStored` false is the computation; `linesStored` true and `SENT`/`APPROVED` is the stored rows; `linesStored` true and `DRAFT` is the stored rows plus a pre-filled line per Fee of the month with no row, and none for a Postpaid SIM. `toResponse` follows it; the response shape is unchanged. `findQueueRows` sums the stored lines. `DemoDataLoader` seeds lines for its past invoices.
- The new ADR 0004 (lines part only; the pay part comes with `local-support-fees-follow-billed-client-invoice-lines`), the dated note on ADR 0001, and a `docs/tech-debt.md` entry for the unused snapshot structures.

Since nothing is editable yet, every invoice reads as it does today; the story-25 draft-with-stored-lines shape is reached by a fixture that sets a sent invoice back to `DRAFT`. Story 1 (a draft opening pre-filled with one line per SIM and Fee) is kept by the unedited draft reading as today.

## Acceptance criteria

- [ ] A never-sent draft with no edits reads exactly as today: one line per billing Postpaid SIM and per Fee of the month, the same base amount, Fees total and total.
- [ ] After send, the Agent's and the Manager's reads serve the same Fee lines, base amount and totals as before the send, and a Fee logged after the send does not appear on the invoice.
- [ ] A Client Invoice sent or approved before this change reads the same lines, amounts and totals, and its PDF and Review Queue row are unchanged.
- [ ] The Review Queue row's base amount, Fees total and total equal the sums of the invoice's stored lines.
- [ ] A sent invoice set back to `DRAFT` by a fixture serves every line it was sent with exactly as sent, plus a Fee of its month logged since in `feeLines` at its logged amount, included in the totals, and no line for a Postpaid SIM added since; a legacy invoice with a `BASE_AMOUNT` line keeps that line untouched and gains the late Fee.
- [ ] Sending that sent-back draft again succeeds with no constraint violation, stores the late-Fee line, leaves every other stored row as it was, and a Fee logged after that second send does not appear.
- [ ] Two concurrent sends of one draft leave it `SENT` once with one set of stored lines and the other gets `409`; sending a `SENT` or `APPROVED` invoice gets `409`.

## Tests

- **HTTP API seam (spec Testing decisions 2):** new `ClientInvoiceStoredLinesApiTest` (the fixture-based story-25 read and resend parts of `ClientInvoiceLineEditApiTest` split here so this ticket is provable alone; the edit parts arrive in the next ticket). Cases: `unedited-draft-reads-as-the-computation`, `send-freezes-lines-and-a-later-fee-does-not-appear`, `sent-back-draft-serves-sent-lines-plus-late-fee-as-prefilled-and-no-new-sim-line`, `sent-back-legacy-invoice-keeps-base-amount-line-and-gains-late-fee`, `resend-stores-the-late-fee-line-and-leaves-stored-rows-alone`, `fee-logged-after-resend-does-not-appear`, `review-queue-row-sums-the-stored-lines`, `send-of-a-sent-or-approved-invoice-gets-409`, `legacy-invoice-reads-same-lines-totals-pdf-and-queue-row` (a backfilled pre-change invoice, one `BASE_AMOUNT` line plus Fee lines, read through the Agent, Manager by-id, PDF and Review Queue routes).
- **Race (spec item 4, committing test cleaning up its own rows):** `concurrent-sends-store-lines-once-and-one-gets-409`.
- **Proven by existing suites passing unedited (spec item 5):** `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest`, `AgentInvoiceApiTest`, `AgentInvoiceByIdApiTest`, `client-invoice-generation.spec.ts`, `client-invoice-submission-and-visibility.spec.ts`, `agent-invoice-submission-and-approval.spec.ts`, `manager-invoice-review-queue.spec.ts`.

## Regression

- At risk: the draft read, the send and its status and `sentAt` writes, the Manager's by-id read and approve, the Review Queue, the Dashboard's Pending approvals card, the PDF, the demo data, and the Agent Invoice (it still reads the Fleet and Fees; unchanged here).
- Existing tests expected to change: none. This ticket's response shape is unchanged, so `ClientInvoiceApiTest` and the other suites above pass unmodified.

## Observability

N/A — no new failure path or business event; the send keeps its existing logging and audit, now inside one transaction.
