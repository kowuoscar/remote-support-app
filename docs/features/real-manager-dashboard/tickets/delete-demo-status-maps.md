---
id: delete-demo-status-maps
title: Delete the seven unused demo-keyed status maps from lib/status
status: done
depends_on: []
labels: [enabler, frontend]
stories: []
---

## Context

Enabler for `delete-frontend-demo-fixtures`, which cannot delete `frontend/lib/demo/` while `frontend/lib/status.ts` still imports `frontend/lib/demo/types.ts`. See the spec's `## Solution`, "Prefactoring and removal", and `docs/tech-debt.md` (the `frontend/lib/status.ts` entry). The seven maps (`requestStatusTone`, `smartphoneStatusTone`, `simStatusTone`, `clientInvoiceStatusTone`, `clientInvoiceStatusLabel`, `agentInvoiceStatusTone`, `agentInvoiceStatusLabel`) are used by nothing; their `...ByValue` replacements keyed by the real enums serve every page. Delete them, do not move them. The implementer declares the `frontend/lib/status.ts` entry in `docs/tech-debt.md` paid in its result; the merger edits it.

## Acceptance criteria

- `frontend/lib/status.ts` no longer imports from `frontend/lib/demo/types.ts` or anywhere under `@/lib/demo`.
- The seven named maps no longer exist, and no comment in `frontend/` names them (comments are reworded to point at the `...ByValue` maps).
- Every `...ByValue` map and every page that renders a status looks exactly as before: no visual golden changes.

## Tests

No new cases: pure deletion of unused code. Seam: the existing typecheck, lint, vitest and visual suites prove nothing referenced the maps (a missed reference fails `tsc`).

## Regression

At risk: any page still reading one of the seven maps (grep says none). Protected by `tsc --noEmit`, the vitest suite and the unchanged visual goldens. No existing test is expected to change.

## Observability

N/A — deletion of dead code, no runtime behaviour.
