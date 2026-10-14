---
id: delete-frontend-demo-fixtures
title: No demo data, footer or simulated loading remains in the frontend
status: ready-for-agent
depends_on: [delete-demo-status-maps, manager-dashboard-money]
labels: [frontend]
stories: [24, 25]
---

## Context

With every Manager figure real, nothing needs `frontend/lib/demo/`: spec `## Solution`, "Prefactoring and removal". Depends on `delete-demo-status-maps` (frees `lib/demo/types.ts`) and `manager-dashboard-money` (the last importer of `lib/demo/manager.ts`). Deletes `frontend/lib/demo/` (`manager.ts`, `contracts.ts`, `types.ts`), `DemoNote`, `SurfacePage`'s `demoData` prop, `useSimulatedLoad`, and `StatCardSkeleton` if nothing else renders it.

## Acceptance criteria

- `frontend/lib/demo/` does not exist and nothing in `frontend/` references `@/lib/demo`, `DemoNote`, `demoData`, `useSimulatedLoad` or an unused `StatCardSkeleton`.
- No page shows a "Demo data — synthetic" footer; `/manager` shows none.
- No `text-[12px]` / `text-[13px]` in the Manager page, `ManagerDashboardStats` or `ChangeNameDialog`.
- The full visual suite is green with no golden changed by this ticket (recapture only if one genuinely moves, deleted first).
- `docs/agents/sdlc.json`'s `verify` passes.

## Tests

Seam: Playwright visual/e2e suite. `demo-footer.spec.ts` is rewritten as one assertion that `/manager` shows no "Demo data" footer (or folded into `surfaces.spec.ts`). Typecheck and lint prove no remaining import.

## Regression

Existing test expected to change: `tests/visual/demo-footer.spec.ts`, rewritten from asserting the footer to asserting its absence, because the footer is removed by design. At risk: any surface passing `demoData` and any component using `StatCardSkeleton`; protected by `tsc`, lint and the unchanged goldens.

## Observability

N/A — deletion of fixtures and UI chrome, no runtime behaviour.
