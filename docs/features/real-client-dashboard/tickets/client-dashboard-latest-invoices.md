---
id: client-dashboard-latest-invoices
title: Show the Tester's latest sent or approved Client Invoice per Contract and delete the demo data
status: in-progress
depends_on: [client-dashboard-fleet-and-requests]
labels: [frontend]
stories: [3, 9, 10, 12, 13, 14, 15, 22, 24, 25]
---

## Context

Last slice of `spec.md` (`## Execution order`, item 5). The Latest Client Invoice card joins one row per Contract in `GET /api/contracts` to `GET /api/me/client/latest-client-invoices` by `contractId` (spec `## Solution`, table row "Latest Client Invoice rows", and "Frontend: the page", Empty states and The link). It finishes the page: `frontend/lib/demo/client.ts` is deleted, and `frontend/lib/demo/types.ts` stays because `lib/status.ts` still imports it. It depends on `client-dashboard-fleet-and-requests` because `page.tsx` imports `clientContracts` and `clientInvoices` from `@/lib/demo/client` until this ticket removes them, so the deletion cannot compile earlier; the read it calls comes from `tester-own-client-reads`, merged beneath it through `client-dashboard-identity`.

Lines it writes use the `type-scale-tokens` classes. The link reads "Open Invoices" (`## Decisions taken`); `/client/invoices` itself is unchanged. The e2e spec runs on an isolated stack (spec `## Constraints`).

## Acceptance criteria

- [ ] With `visual-tester-session`, each of the two Contracts has a row showing "Solstice Retail Group — <country label>", the formatted billing month, a status badge ("Awaiting approval" for `SENT`, "Approved" for `APPROVED`) and the total in the Contract's own currency (USD, GBP); no draft appears.
- [ ] A Contract with no entry in the read shows "No invoices yet" in its row, and a Client with no Contract shows "No Contracts yet" with "Your Contracts and their invoices will show up here once the Manager sets them up." in the card, with Active Fleet and Open Requests at `0`.
- [ ] The card's header link reads "Open Invoices", goes to `/client/invoices`, and the subtitle stays "Most recent month per Contract".
- [ ] When the latest-invoices read fails (`visual-tester-degraded-session`) or the Contract list fails (`visual-tester-contracts-failing-session`, added by `client-dashboard-fleet-and-requests`), the card shows "Couldn't load your invoices" with "Reload the page to try again." with the header still rendered, and, for the latest-invoices failure, both stat cards still render.
- [ ] A Client's header names the Client when it holds no Contract, and a new Contract's row reads "No invoices yet" until the Agent sends its invoice, then "Awaiting approval" with its total, then "Approved" once the Manager approves it.
- [ ] `frontend/lib/demo/client.ts` no longer exists and `frontend/lib/demo/types.ts` does; neither the dashboard page nor `ClientDashboardStats` imports `@/lib/demo`, and neither contains `text-[12px]` or `text-[13px]`.
- [ ] The four `client-*` goldens are deleted and recaptured with the invoice card from stubbed data, unmasked and stable on re-run.

## Tests

- **E2E against the real backend (spec Testing decisions 3):** new `frontend/tests/e2e/client-dashboard.spec.ts`, using `helpers.ts` (`createContractWithTester`, `addSmartphone`, `addSimCard`, `submitRequestAsTester`). Cases: `tester-dashboard-shows-client-name-fleet-and-open-requests-then-no-invoices-yet`, `agent-sends-invoice-and-row-reads-awaiting-approval-with-total`, `manager-approves-and-row-reads-approved`, `client-with-no-contract-shows-name-zero-counts-and-no-contracts-yet`.
- **Visual suite, non-golden (Testing decisions 4):** new `frontend/tests/visual/client-dashboard-invoices.spec.ts`, cases `healthy-tester-rows-show-month-status-and-currency-total`, `open-invoices-link-goes-to-client-invoices`, `degraded-tester-shows-invoices-unavailable-with-the-rest-of-the-page-intact` and `contract-list-failure-shows-invoice-card-unavailable`.
- **Goldens:** `client-*`, deleted first, then recaptured.

## Regression

- At risk: `/client/invoices` (it must keep showing the current-month invoice only), the Agent's Send Client Invoice flow and the Manager's approval flow that the e2e drives, `lib/status.ts` (still imports `lib/demo/types.ts`), the Manager dashboard (still imports `lib/demo/manager.ts`, `lib/demo/contracts.ts`).
- Existing tests expected to change: `frontend/tests/visual/stub-backend.mjs` gains `GET /api/me/client/latest-client-invoices` (one `SENT`, one `APPROVED`) and the degraded token's `500` for it; the four `client-*` goldens are replaced. Guarding: `client-invoice-submission-and-visibility.spec.ts` and `client-invoice-generation.spec.ts`, unmodified. The `docs/tech-debt.md` entries for `frontend/lib/demo/client.ts` and the `text-[12px]` known debt are the orchestrator's at delivery.

## Observability

A failed latest-invoices or Contract load is logged server-side with a label naming the endpoint and the response status.
