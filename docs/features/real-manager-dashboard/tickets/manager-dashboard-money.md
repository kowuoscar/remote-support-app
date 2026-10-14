---
id: manager-dashboard-money
title: Manager dashboard shows real billed and payout totals per currency
status: ready-for-agent
depends_on: [tenant-totals-read, manager-changes-own-name]
labels: [frontend]
stories: [6, 8, 10, 13, 14, 15, 16, 18, 23]
---

## Context

Makes "Billed this month" and "Payout this month" real from `GET /api/tenant-totals` (built by `tenant-totals-read`): spec `## Solution` ("Frontend: the page", "Frontend: the stat grid"), `## Design direction`. Depends on `manager-changes-own-name` because both edit the same page, stub and goldens and the e2e spec (the `manager-*` golden pattern includes `manager-menu-open-*`). Per-currency `Money` lines in the read's order; `[]` renders a plain `0` with no currency symbol; failure renders "—" with "Couldn't load this month's totals"; meta "Sent or approved · <Month YYYY>" / "Approved or paid · <Month YYYY>" with the month from the read's `billingMonth`, never the wall clock. Props per the spec's `ManagerDashboardStats` signature and the `data-testid`s.

## Acceptance criteria

- "Billed this month" shows, per currency, the total the read returns (one USD line equal to Solstice's US invoice total in the demo story) with meta "Sent or approved · <this month>".
- "Payout this month" reads a plain `0` (no symbol) while no Agent Invoice is approved, with meta "Approved or paid · <this month>"; after the Manager approves Jordan's, a reload shows that invoice's total in USD, unchanged once it is marked paid.
- With invoices in two currencies the card shows one line per currency (GBP then USD), never one summed figure; sending an invoice back removes its line and resending restores it.
- A failed `/api/tenant-totals` makes both money cards read "—" with "Couldn't load this month's totals" while every other card still shows.
- Against the stub, the golden shows "Billed" in GBP and USD and "Payout" in USD; the empty session shows both cards as a plain `0` with the chip reading the stub's username followed by "· Manager".

## Tests

Seams: vitest component tests on `ManagerDashboardStats`; Playwright e2e `tests/e2e/manager-dashboard.spec.ts` against the real backend; the stubbed visual suite (spec `## Testing decisions` 3, 4, 5). Cases: one currency, two currencies in given order, `[]` as plain `0`, `null` as "—" with its meta, month meta from `billingMonth`. E2E asserts differences, not absolutes: Agent sends a Client Invoice, "Billed" grows by its total; Manager approves, unchanged; Agent sends Agent Invoice, "Payout" unchanged; Manager approves, "Payout" grows by its total. Non-golden `surfaces.spec.ts` tests for the degraded session (money "—") and the empty session (plain `0`, no symbol). Goldens `manager-*` deleted then recaptured.

## Regression

Existing tests expected to change: `frontend/components/manager/dashboard-stats.test.tsx` — its shared `BASE_PROPS` changes `billedThisMonth`/`payoutThisMonth` from `number` to `CurrencyAmount[] | null`, because the props now carry per-currency lines (new money cases are added; the other cases keep their assertions). `tests/visual/surfaces.spec.ts` — the degraded-session test from `manager-dashboard-identity-and-counts` gains money assertions ("—" with "Couldn't load this month's totals"), because the totals read is now consumed; the empty-session test gains the plain `0` assertion. `tests/e2e/manager-dashboard.spec.ts` (created earlier) gains the money steps; its earlier cases keep passing unmodified. At risk: the identity, counts and name steps already shipped on this page, and the `manager-*` goldens, recaptured with only the money change in their diff.

## Observability

N/A — a failed read is logged by `backendFetchJsonOrNull`; no new signal.
