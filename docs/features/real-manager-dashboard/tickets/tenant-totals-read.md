---
id: tenant-totals-read
title: A Manager can read this month's billed and payout totals per currency
status: ready-for-agent
depends_on: []
labels: [backend]
stories: [6, 7, 8, 9, 11, 12, 13, 14, 17, 21, 22, 27]
---

## Context

New Manager-only `GET /api/tenant-totals`, defined in the spec's `## Solution` ("The money definitions", "Backend: the Tenant-wide aggregate") and `## Constraints`. Two grouped repository queries (Client Invoice lines; Agent Invoice frozen figures), a service, `TenantTotalsResponse` with `CurrencyAmount`, and a `SecurityConfig` matcher beside `/api/review-queue`'s. The Agent Invoice total becomes one shared expression adopted by the Review Queue query too, so `invoice-adjustment` later changes one place (story 27). No schema change. Frontend consumption is `manager-dashboard-money`.

## Acceptance criteria

- As a Manager, the response holds `billingMonth` (the current UTC billing month's first day), `billed` and `payout`, each a list of `{currency, amount}` with one entry per currency that has a counted invoice, ordered by currency code; nothing counted gives `[]`.
- `billed` counts this month's Client Invoices that are `SENT` or `APPROVED`, each at the total `GET /api/client-invoices/{id}` returns; a `DRAFT` (a sent-back one included) and last month's invoice do not count.
- `payout` counts this month's Agent Invoices that are `APPROVED` or `PAID`, each at its total including a Manager override; `DRAFT`, `SENT` and last month's do not count.
- A Fee logged after a Client Invoice was sent does not change `billed`; USD and GBP Contracts give two entries and are never summed together.
- Another Tenant's invoices (`OtherTenantFixture`) are never counted.
- An Agent or a Tester gets `403`; no token gets `401`.
- Calling the read creates no `client_invoices` or `agent_invoices` row, and the Review Queue's results are unchanged.

## Tests

Seam: the HTTP API seam, `IntegrationTest` with MockMvc against real Postgres (spec `## Testing decisions` 1). New `TenantTotalsApiTest`, prior art `ReviewQueueApiTest`, `MeClientApiTest`, `AgentInvoiceByIdApiTest`. Cases: empty Tenant (current month, two `[]`); SENT and APPROVED Client Invoice counted at the invoice endpoint's total; DRAFT, sent-back draft and last month's SENT not counted; Fee after send leaves `billed` unmoved; Agent Invoice APPROVED counted, stays counted when PAID, override included; DRAFT/SENT/last-month Agent Invoice not counted; USD plus GBP gives two ordered entries; `OtherTenantFixture` invoices ignored; Agent 403, Tester 403, no token 401; row counts unchanged after the read.

## Regression

At risk: the Review Queue's Agent Invoice totals, since its query adopts the shared expression. `ReviewQueueApiTest` must keep passing unmodified. No existing test is expected to change.

## Observability

N/A — a read-only aggregate; failures surface as ordinary HTTP errors already logged by the existing error handling.
