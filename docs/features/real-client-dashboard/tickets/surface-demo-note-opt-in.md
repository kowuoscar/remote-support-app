---
id: surface-demo-note-opt-in
title: Show the "Demo data" footer only on the Manager dashboard
status: in-progress
depends_on: []
labels: [enabler, frontend]
stories: [23]
---

## Context

Prefactoring for `client-dashboard-identity`, which recaptures the `client-*` goldens and should do so once, against a footer-less shell. It pays `docs/tech-debt.md` F12 (spec `## Solution`, Prefactoring; `## Decisions taken`, the opt-in `demoData` prop). `SurfacePage` gains a boolean `demoData`, false by default, and renders `DemoNote` only when it is set. `app/manager/page.tsx`, the one surface still showing demo figures, is the only page that sets it. `real-manager-dashboard` later removes the prop and `DemoNote` together. The orchestrator removes the tech-debt entry at delivery; the implementer does not edit `docs/tech-debt.md`.

## Acceptance criteria

- [ ] `/manager` still shows the "Demo data" footer.
- [ ] `/agent`, `/manager/carriers` and every other page that renders through `SurfacePage` without the prop show no "Demo data" footer.
- [ ] The 28 full-page goldens of the non-Manager surfaces (`agent-*`, `agent-carriers-*`, `agent-stock-*`, `client-*`, `manager-carriers-*`, `manager-requests-*`, `manager-stock-*`, each in desktop and mobile, light and dark) are deleted and recaptured, and each one's diff against `main` shows only the footer gone.
- [ ] The `manager-*` dashboard goldens, the `manager-menu-open-*` goldens and the `change-password-dialog-open-*` goldens are byte-for-byte unchanged, because they render `/manager`.

## Tests

- **Visual suite (spec Testing decisions 4), non-golden:** new file `frontend/tests/visual/demo-footer.spec.ts`, cases `manager-dashboard-shows-the-demo-footer` (`/manager`), `agent-dashboard-shows-no-demo-footer` (`/agent`, `visual-agent-session`) and `manager-carriers-shows-no-demo-footer` (`/manager/carriers`, `visual-manager-session`). It does not visit `/client`, which the next tickets move onto a Tester session; the recaptured `client-*` goldens evidence that page.
- **Goldens:** the 28 above, deleted first, then recaptured, then re-run to prove stable.

## Regression

- At risk: every page rendered through `SurfacePage` (`frontend/app/agent`, `frontend/app/manager`, `frontend/app/client`), and the `/manager` goldens.
- Existing tests expected to change: the 28 goldens in `frontend/tests/visual/__screenshots__/` named above are replaced (footer only). Guarding them unchanged: `manager-{desktop,mobile}-{light,dark}.png`, `manager-menu-open-*` (`viewer-menu.spec.ts`) and `change-password-dialog-open-*` (`change-password-dialog.spec.ts`), all of which render `/manager`. No `.test.tsx` file changes. `frontend/components/ui/demo-note.tsx` is untouched.

## Observability

N/A — a presentational prop, nothing runs that could fail silently.
