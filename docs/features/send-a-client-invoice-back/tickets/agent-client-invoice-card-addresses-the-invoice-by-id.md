---
id: agent-client-invoice-card-addresses-the-invoice-by-id
title: Extract the Agent's Client Invoice card and point its controls at the by-id routes
status: in-progress
depends_on: [agent-reaches-a-client-invoice-by-its-id]
labels: [frontend, enabler]
stories: []
---

## Context

Prefactoring, enabling `agent-opens-a-client-invoice-on-its-own-page` (which renders the same card on a new page) and `agent-sees-sent-back-invoices-on-the-client-invoices-page`. Spec `## Solution`, "Frontend: Agent", "Prefactor". The card inside `AgentClientInvoicesView` becomes `AgentClientInvoiceCard(invoice)`, carrying `EditClientInvoiceLineControl`. `SendClientInvoiceControl`, `AttachCarrierInvoiceFileControl` and `EditClientInvoiceLineControl` take an invoice id instead of a Contract id and call the by-id routes; file downloads use the by-id route; Download PDF stays on the current-month route. Adds the BFF pass-through proxies for the by-id read, files, attach, `lines` and `send` using `backendFetch`, and the by-id types in `lib/api/types.ts`. The current-month page keeps looking and behaving as today. Modules: `components/agent`, `app/api`, `lib/api`.

The three controls' calls change URL, so the e2e waits and the line-control unit test that name the old URLs change with them; the Agent's send confirmation copy is changed in `agent-opens-a-client-invoice-on-its-own-page`, not here.

## Acceptance criteria

- [ ] The Agent's current-month Client Invoices page renders exactly as before, and sending, attaching a file, editing and resetting a line, and downloading a file or the PDF all still work from it.
- [ ] Those actions reach the backend through the by-id routes and the invoice id, and a stale or unknown id shows the controls' existing failure messages.

## Tests

- **Component seam (spec Testing decisions 3, 4):** `edit-client-invoice-line-control.test.tsx` keeps every case and asserts the by-id URL `/api/client-invoices/{invoiceId}/lines`; `client-invoices-view.test.tsx` passes unmodified.
- **e2e (existing seam):** `client-invoice-generation.spec.ts`, `client-invoice-submission-and-visibility.spec.ts`, `client-dashboard.spec.ts` and `edit-client-invoice-lines.spec.ts` play the Agent's current-month flow through the new calls.

## Regression

- At risk: the Agent's Client Invoices page (send, attach, edit, reset, file download), the BFF proxy set, the visual golden of the Agent page (it must not move).
- Existing tests expected to change: `frontend/components/agent/edit-client-invoice-line-control.test.tsx` asserts the fetch to `/api/contracts/contract-1/client-invoice/lines` (lines 47 and 103) and passes a `contractId` prop; the control now takes an invoice id and calls `/api/client-invoices/{id}/lines`, so those two assertions and the prop change. `frontend/tests/e2e/client-invoice-generation.spec.ts` (waits for a response whose URL includes `/client-invoice/files`), `frontend/tests/e2e/client-invoice-submission-and-visibility.spec.ts` and `frontend/tests/e2e/client-dashboard.spec.ts` (wait for a response whose URL includes `/client-invoice/send`) change only that response-URL predicate, to the by-id `/files` and `/send` paths, since the Agent's actions no longer go through the current-month URL. No assertion on what the user sees changes. Every other case of these files and of `edit-client-invoice-lines.spec.ts` and `client-invoices-view.test.tsx` passes unmodified.

## Observability

N/A — a behaviour-preserving refactor and pass-through proxies; failures are logged by the proxy as for every other.
