---
id: store-client-invoice-lines
title: Store a Client Invoice's own lines and backfill every sent and approved invoice
status: ready-for-agent
depends_on: []
labels: [enabler, backend]
stories: []
---

## Context

First slice of `spec.md` (`## Execution order`). An enabler: it unblocks `serve-client-invoices-from-stored-lines`, which makes the read and the send use the lines, and through it every later ticket. Nothing observable changes for a user here.

Adds the `ClientInvoiceLine` entity and `client_invoice_lines` table, `ClientInvoice.linesStored`, the line repository, and one additive Flyway migration (V56) with its backfill, all as stated in spec `## Solution`, "The model" and "Migration". Modules: `domain`, `repository`. Nothing is deleted; `snapshot_base_amount` and `client_invoice_fee_snapshots` keep their data and stay unused-but-present (spec Non-goals).

## Acceptance criteria

- [ ] Migrating a database holding a `SENT` and an `APPROVED` Client Invoice (each with a snapshot base amount and Fee snapshot rows) gives each exactly one `BASE_AMOUNT` line whose `amount` and `computedAmount` equal the old base amount, and one `FEE` line per snapshot row whose `amount` and `computedAmount` equal that Fee's amount.
- [ ] After that migration both invoices have `lines_stored` true and a `DRAFT` invoice has no line rows and `lines_stored` false.
- [ ] The table refuses a negative `amount`, a line with the wrong reference for its kind (a `FEE` with no Fee, a `POSTPAID_SIM` with a Fee), and a second line for the same SIM or the same Fee on one invoice.
- [ ] Migration deletes and rewrites no existing row: the snapshot columns and Fee snapshot rows read as before, and no `agent_invoices` row changes.
- [ ] The application starts against the migrated schema and every existing backend test passes unedited (no behaviour changes).

## Tests

- **Migration seam (spec Testing decisions 1):** new `ClientInvoiceLinesMigrationTest` under `backend/src/test/.../migration`, migrating to the version before V56, writing a `SENT`, an `APPROVED` and a `DRAFT` invoice, then migrating. Cases: `sent-and-approved-get-one-base-amount-line-equal-to-the-old-base-amount`, `each-fee-snapshot-row-becomes-a-fee-line-at-that-fees-amount`, `sent-and-approved-are-marked-lines-stored-and-draft-is-not`, `draft-gets-no-lines`, `snapshot-data-and-agent-invoices-are-untouched`, `table-refuses-negative-amount-wrong-reference-and-duplicate-line`.
- Seam: the migration seam only; no HTTP behaviour changes yet.

## Regression

- At risk: every Client Invoice read and the Flyway chain (the version must be the next free one, V56 on `main`; a later feature takes the one after), `DemoDataLoader` seeding against the new schema.
- Existing tests expected to change: none. `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest` and `AgentInvoiceApiTest` guard the unchanged behaviour, unmodified.

## Observability

N/A — a schema and entity addition with no runtime behaviour; the migration's outcome is proved by its test.
