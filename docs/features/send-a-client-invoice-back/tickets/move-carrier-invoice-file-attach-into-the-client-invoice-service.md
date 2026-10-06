---
id: move-carrier-invoice-file-attach-into-the-client-invoice-service
title: Move Carrier Invoice File attach from the controller into the Client Invoice service
status: done
depends_on: []
labels: [backend, enabler]
stories: []
---

## Context

Prefactoring, enabling `agent-reaches-a-client-invoice-by-its-id`, which adds a second attach route (`POST /api/client-invoices/{id}/files`) that must run the same rule as the current-month `POST /api/contracts/{contractId}/client-invoice/files`. Spec `## Solution`, "Backend: the Agent reaches an invoice by its id", and `## Decisions taken`, "File attach moves into `ClientInvoiceService`, taking a found invoice".

The attach logic lives in `ClientInvoiceController` today. Move it into `ClientInvoiceService` as one operation taking an already-found `ClientInvoice` (as `send(ClientInvoice, principal)` and `editLine` already do), and make the current-month attach route call it. No behaviour, status code, size or type rule, or audit line changes. Modules: `web`.

## Acceptance criteria

- [ ] Attaching a Carrier Invoice File to the Agent's current-month draft returns exactly what it returned before (same status, body, stored file, same rejections for a missing, empty or oversized file) and the file lists and downloads as before.
- [ ] Attaching to a sent invoice, another Agent's Contract, or another Tenant's Contract is refused exactly as before.
- [ ] An oversized, empty or missing file is refused with the same status and body as before.

## Tests

- **HTTP API seam (spec Testing decisions 1, 3):** no new test. The prefactor is proven by the existing `ClientInvoiceApiTest` and `ClientInvoiceByIdApiTest` (which attaches files through the current-month route) passing unedited.

## Regression

- At risk: the current-month file attach, file listing and download, the send that freezes the files, the Manager's by-id file reads.
- Existing tests expected to change: none. `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest`, `ClientInvoiceLineEditApiTest` and `ClientInvoiceStoredLinesApiTest` pass unmodified.

## Observability

N/A — a behaviour-preserving move; no new log or audit line.
