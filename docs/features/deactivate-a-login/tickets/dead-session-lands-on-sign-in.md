---
id: dead-session-lands-on-sign-in
title: Send a dead Agent or Tester session to sign-in
status: done
depends_on: [deactivated-login-refused]
labels: [frontend]
stories: [14]
---

## Context

Fifth slice of `spec.md` (`## Execution order`; `## Solution`, "Frontend: Leaving a dead session"). `frontend/lib/api/guard.ts` gains `requireAgent` and `requireTester` in the shape of `requireManager`: call `/api/me`, and on a non-OK answer or the wrong role `redirect("/login")`. Each backend-driven page under `app/agent` (`(dashboard)`, `client-invoices`, `requests`, `my-invoice`, `carriers`, `fleet`, `stock`) calls `requireAgent`, and each under `app/client` (`(dashboard)`, `fleet`, `invoices`, `requests`) calls `requireTester`, per page and not in the layouts, as `requireManager` does, so backend-less visual pages keep rendering. Modules: `frontend/app/agent`, `frontend/app/client`, `frontend/lib/api`.

## Acceptance criteria

- [ ] An Agent or Tester whose `/api/me` answers `401` (deactivated, deleted or expired) and who loads an Agent or Client page lands on the sign-in page instead of empty lists; a caller of the wrong role does too.
- [ ] An Agent and a Tester with a live session see every Agent and Client page exactly as before, and the visual goldens do not move.

## Tests

- **Vitest component tests (spec `## Testing decisions`):** new `frontend/lib/api/guard.test.tsx` (vitest only picks up `*.test.tsx`; nearest prior art is `frontend/lib/api/backend.test.tsx`) with cases `non-ok-me-redirects-to-login`, `wrong-role-redirects-to-login`, `right-role-returns`, for `requireAgent` and `requireTester`.
- No new e2e here; the end-to-end proof is in `deactivate-an-agents-login-ui`.

## Regression

- At risk: every Agent and Client page now makes one `/api/me` call before rendering; the visual suite renders these pages against `frontend/tests/visual/stub-backend.mjs`, whose `/api/me` answers for the visual sessions, and the e2e specs under `frontend/tests/e2e` that sign in as an Agent or Tester.
- Protected by the visual surfaces in `frontend/tests/visual/surfaces.spec.ts` (including the unlinked, failing-identity and degraded sessions) and the Agent and Tester e2e specs, all unedited. No existing test is expected to change; a golden moving is a finding.

## Observability

N/A — redirect only; the backend refusal is logged by `deactivated-login-refused`.
