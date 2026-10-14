---
id: manager-dashboard-identity-and-counts
title: Manager dashboard shows its real login and real Client, Agent and Contract counts
status: ready-for-agent
depends_on: []
labels: [frontend]
stories: [2, 3, 4, 5, 18, 19, 20, 26, 28]
---

## Context

First page slice of the spec's `## Solution`: "Frontend: the page", "Frontend: the stat grid" and "Visual suite". `frontend/app/manager/page.tsx` reads `/api/me`, the lists and the existing queues in parallel via `backendFetchJsonOrNull`; the chip is `<name> · Manager` when `/api/me` carries a `name`, else `<username> · Manager`, else plain "Manager" (the `name` field arrives with `manager-name-api`; until then the chip shows the username). Counts are list lengths with "Couldn't load your ..." unavailable states and the caption "In this tenant". `ManagerDashboardStats` becomes presentational (no `useSimulatedLoad`). The two money cards stay as they are in this ticket and are made real by `manager-dashboard-money`. The visual stub gains Manager fixtures and a degraded session; the `manager-*`, `manager-menu-open-*` and `change-password-dialog-open-*` goldens move onto `visual-manager-session` with masks dropped.

## Acceptance criteria

- Signed in as the unnamed Manager, the chip reads "manager@example.com · Manager"; the page contains no "Priya Ashford".
- The Clients, Agents and Contracts cards equal the lengths of `/api/clients`, `/api/agents` and `/api/contracts` (2, 3, 3 in the demo story), and the Clients caption reads "In this tenant".
- The stat grid appears at once, with no skeleton first (`dashboard-ready` present on load).
- With a failing `/api/agents`, the Agents card reads "—" with "Couldn't load your Agents" while every other card still shows; with a failing `/api/me` the chip reads plain "Manager" and the page still renders.
- With the empty stub session (`/api/me` carries no `name`), the chip reads `<username> · Manager`.
- Against the stub, `/manager`, the open viewer menu and the open change-password dialog render from `visual-manager-session` with no masks, and the goldens are recaptured.

## Tests

Seams: component tests (vitest) for `ManagerDashboardStats`; Playwright e2e `tests/e2e/manager-dashboard.spec.ts` (created here); the stubbed visual suite (spec `## Testing decisions` 3, 4 and 5). Cases: counts shown for numbers; each `null` count renders "—" with its "Couldn't load..." meta; "In this tenant" caption; grid renders at once as `dashboard-ready`; non-golden test in `surfaces.spec.ts` for `visual-manager-degraded-session` (chip "Manager", Agents "—", other cards present). E2E case: the three count cards equal the lengths of `/api/clients`, `/api/agents` and `/api/contracts` fetched through the API. Non-golden stub-session test in `surfaces.spec.ts`: with the empty session the chip reads `<username> · Manager`. Goldens `manager-*`, `manager-menu-open-*`, `change-password-dialog-open-*` deleted first, then recaptured; each diff shows only the intended change.

## Regression

Existing tests expected to change: `components/manager/dashboard-stats.test.tsx` loses its fake-timer/skeleton step and gains the cases above (the other cases keep passing); `tests/visual/surfaces.spec.ts` gains the degraded-session and empty-session tests and its existing `{ slug: "manager", path: "/manager" }` entry gains session `visual-manager-session` and loses its `backendDrivenRegions()` masks, because the dashboard now renders from stubbed backend data and masking would hide real changes; `viewer-menu.spec.ts` and `change-password-dialog.spec.ts` move from the placeholder cookie to `visual-manager-session` and drop masks, because the dashboard now renders from stubbed data. At risk: the Review Queue and Pending Requests cards, unchanged in behaviour, and the Agent and Tester surfaces' goldens, which must not move.

## Observability

N/A — a failed read is already logged by `backendFetchJsonOrNull`; no new signal.
