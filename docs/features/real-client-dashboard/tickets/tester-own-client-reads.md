---
id: tester-own-client-reads
title: Give a Tester reads of their own Client and its latest sent or approved Client Invoice per Contract
status: done
depends_on: []
labels: [backend]
stories: [3, 9, 11, 18, 20, 21]
---

## Context

First backend slice of `spec.md` (`## Execution order`, item 3); the frontend tickets read these two routes. The contract, DTO shape, selection rule, frozen total (ADR 0001) and "no write, ever" are in spec `## Solution`, "Backend: the caller's own Client". A new `MeClientController` sits beside `MeAgentController`; selection lives in `ClientInvoiceService`; `ClientInvoiceRepository` gains one derived query; `ClientInvoiceSummaryResponse` is a new DTO. No `SecurityConfig` change and no migration (spec `## Constraints`). The spec's assumption that a Client's Contracts can be listed within a Tenant is verified here; add a derived query on the Contract repository if none exists.

## Acceptance criteria

- [ ] `GET /api/me/client` as a Tester returns `200 { clientId, name }` for the Tester's own Client, including a Client that holds no Contract.
- [ ] `GET /api/me/client` and `GET /api/me/client/latest-client-invoices` return `404` for a Manager, an Agent and the unlinked `tester@example.com`, and `401` with no token.
- [ ] The latest-invoices read returns one entry per Contract of the caller's Client that has a `SENT` or `APPROVED` invoice, carrying exactly `contractId`, `invoiceId`, `billingMonth`, `status`, `currency`, `totalAmount`, with the latest `billingMonth` chosen; the current month is returned when it is `SENT`.
- [ ] When the current month is `DRAFT` or absent, the entry is the latest older `APPROVED` or `SENT` month; a Contract with only a draft is absent; `status` is never `DRAFT`.
- [ ] It never returns a Contract of another Client, including one of `OtherTenantFixture`.
- [ ] `totalAmount` equals the total of that invoice's own `GET /api/contracts/{id}/client-invoice`, and a Fee logged after sending does not move it.
- [ ] Calling either read creates no `client_invoices` row.

## Tests

Seam: the HTTP API with `IntegrationTest` and MockMvc against real Postgres (spec Testing decisions 1). New class `backend/src/test/java/com/remotesupport/backend/web/MeClientApiTest.java`, no repository mocks. Cases: `tester-gets-own-client-name`, `tester-with-no-contract-still-gets-client-name`, `manager-agent-and-unlinked-tester-get-404-on-both-routes`, `no-token-gets-401-on-both-routes`, `latest-returns-current-month-when-sent`, `latest-falls-back-to-older-approved-when-current-is-draft`, `latest-falls-back-when-current-month-absent`, `contract-with-only-a-draft-is-absent`, `never-returns-another-clients-contract`, `never-returns-other-tenant-contract` (`OtherTenantFixture`), `total-amount-equals-the-invoices-own-total`, `fee-logged-after-sending-does-not-move-total-amount`, `calling-the-read-creates-no-client-invoice-row`. Older months are built the way `AgentInvoiceByIdApiTest` does, by moving `billingMonth` through the repository.

## Regression

- At risk: `ClientInvoiceService`'s existing get-or-create and `toResponse` paths (the new method must reuse the total computation, not change it), `GET /api/me/agent`, and `/api/me/**` falling through to `.anyRequest().authenticated()`.
- Guarding tests: `ClientInvoiceApiTest`, `AgentOwnRecordApiTest`, `AgentInvoiceByIdApiTest` and the rest of the `web` package, all unmodified. Existing tests expected to change: none; the new cases live in the new `MeClientApiTest` class.

## Observability

N/A — two read-only routes; an unlinked caller's `404` and the existing request logging are the whole signal.
