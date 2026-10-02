---
id: client-dashboard-fleet-and-requests
title: Show the Tester's active Fleet and open Request counts from their Client's Contracts
status: done
depends_on: [client-dashboard-identity]
labels: [frontend]
stories: [5, 6, 7, 8, 15, 18, 19, 24]
---

## Context

Fifth slice of `spec.md` (`## Execution order`, split from item 4). Active Fleet and Open Requests are computed from `GET /api/contracts` and each Contract's `smartphones`, `sim-cards` and `requests`, scoped by the caller (spec `## Solution`, "Where each figure comes from" and "Frontend: the page", Regions fail alone). The page does not use `backendFetchList`, because it turns a failure into `[]`. `ClientDashboardStats` loses `"use client"` and `useSimulatedLoad` (the hook stays for `components/manager/dashboard-stats.tsx`), takes `number | null` props and gets the Agent grid's unavailable rendering (spec "Frontend: the stat grid"). Counting rules and the Open Requests meta are in `## Decisions taken`.

It removes the page's imports of `clientSmartphones`, `clientSimCards` and `clientRequests`; `clientContracts` and `clientInvoices` remain until `client-dashboard-latest-invoices`. It depends on `client-dashboard-identity` because both edit `app/client/(dashboard)/page.tsx`, the stub and the `client-*` goldens. Lines it writes use the `type-scale-tokens` classes. A Client with no Contract showing `0` is proved against a real backend in the next ticket's e2e.

## Acceptance criteria

- [ ] With `visual-tester-session`, Active Fleet equals the Smartphones `ACTIVE` or `IN_REPAIR` plus the SIM Cards `ACTIVE` across both of that Tester's Contracts, with a Retired unit not counted, and its meta reads "Smartphones + SIM Cards, all Contracts".
- [ ] Open Requests equals the Requests at `PENDING_APPROVAL`, `SUBMITTED` or `IN_PROGRESS` across both Contracts, a Pending Approval Request is counted, one in any other status is not, and its meta reads "Pending Approval, Submitted or In Progress".
- [ ] With `visual-tester-degraded-session`, Active Fleet shows "—" with "Couldn't load your Fleet" and Open Requests shows "—" with "Couldn't load your Requests", the header is still rendered, and neither card ever shows `0`.
- [ ] With `visual-tester-contracts-failing-session` (`GET /api/contracts` answers `500`), both stat cards show "—" with their "Couldn't load…" lines, the header is still rendered, and neither card shows `0`.
- [ ] The stat grid is present on first render, marked `data-testid="dashboard-ready"`, with no loading skeleton, and the two cards carry `data-testid` `active-fleet-stat` and `open-requests-stat`.
- [ ] A `null` count renders a muted `aria-hidden` "—" with an sr-only "Unavailable".
- [ ] The four `client-*` goldens are deleted and recaptured from stubbed Contracts, Fleet and Requests, unmasked and stable on re-run.

## Tests

- **Component seam (spec Testing decisions 2):** new `frontend/components/client/dashboard-stats.test.tsx`, in the shape of `components/agent/dashboard-stats.test.tsx`. Cases: `shows-active-fleet-count-and-meta`, `shows-open-requests-count-and-its-meta-naming-pending-approval-submitted-and-in-progress`, `null-active-fleet-renders-dash-sr-only-unavailable-and-couldnt-load-meta`, `null-open-requests-renders-dash-and-couldnt-load-meta`, `renders-the-grid-at-once-marked-dashboard-ready-with-no-skeleton`.
- **Visual suite, non-golden (Testing decisions 4):** new `frontend/tests/visual/client-dashboard-regions.spec.ts`, cases `healthy-tester-counts-active-fleet-and-open-requests` (exact expected numbers from the stub fixtures, mixed statuses including a Retired unit, an In Repair Smartphone and a Pending Approval Request) `degraded-tester-shows-unavailable-never-zero` and `contract-list-failure-shows-both-stats-unavailable-never-zero`.
- **Goldens:** `client-*`, deleted first, then recaptured.

## Regression

- At risk: the Manager dashboard's stat grid (still calls `useSimulatedLoad`), `StatCard`, the Agent dashboard (shares the unavailable pattern), the `/client/fleet` and `/client/requests` pages.
- Existing tests expected to change: `frontend/tests/visual/stub-backend.mjs` gains the Tester's Contracts, Smartphones, SIM Cards and Requests routes and `visual-tester-degraded-session` and `visual-tester-contracts-failing-session` (this ticket adds the token; `client-dashboard-latest-invoices` reuses it; existing tokens answer as before); the four `client-*` goldens are replaced. `frontend/components/manager/dashboard-stats.test.tsx` and `frontend/components/agent/dashboard-stats.test.tsx` guard the siblings, unmodified. `frontend/tests/visual/surfaces.spec.ts` is not modified.

## Observability

A failed Contract, Smartphones, SIM Cards or Requests load is logged server-side with a label naming the endpoint and the response status.
