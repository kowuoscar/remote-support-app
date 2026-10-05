---
id: agent-lists-the-client-invoices-sent-back-to-them
title: Let the Agent list every Client Invoice sent back to them
status: in-progress
depends_on: [manager-sends-a-client-invoice-back]
labels: [backend]
stories: [13, 14]
---

## Context

Second slice of `spec.md` (`## Execution order`), the list endpoint of "Backend: the Agent reaches an invoice by its id", "The sent-back list". `GET /api/client-invoices/sent-back`, Agent only, returning the caller's own Contracts' invoices that are `DRAFT` with a `sentBackAt`, oldest send-back first, each as `{ id, contractId, clientName, country, billingMonth, currency, sentBackAt, sentBackReason }` and no amounts. The matcher is placed so `sent-back` is not read as an invoice id. Modules: `web`, `dto`, `repository`.

## Acceptance criteria

- [ ] The Agent gets exactly their own Contracts' sent-back invoices, from any Contract and any billing month, each with the listed fields and no amounts, oldest send-back first; another Agent's sent-back invoices are absent.
- [ ] An invoice leaves the list once resent, and an invoice sent back again re-enters it with the new reason and time.
- [ ] A Manager or a Tester gets `403`; another Tenant's invoices never appear.

## Tests

- **HTTP API seam (spec Testing decisions 1):** `ClientInvoiceSendBackApiTest` gains `agent-lists-own-sent-back-invoices-oldest-first-with-fields-and-no-amounts`, `other-agents-invoices-and-other-tenants-are-absent`, `resent-invoice-leaves-the-list-and-second-send-back-reenters-it`, `manager-and-tester-get-403-on-the-list`.

## Regression

- At risk: the `/api/client-invoices/**` matchers (this route precedes the broader one), the by-id `GET /api/client-invoices/{id}` mapping that `sent-back` must not shadow or be shadowed by.
- Existing tests expected to change: `ClientInvoiceSendBackApiTest` (created by the blocker) gains the cases above and every pre-existing method keeps passing unmodified. `ClientInvoiceByIdApiTest` is not modified.

## Observability

N/A — a read-only list; no state changes.
