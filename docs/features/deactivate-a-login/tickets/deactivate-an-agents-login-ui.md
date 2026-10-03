---
id: deactivate-an-agents-login-ui
title: Let a Manager deactivate and reactivate an Agent's Login from the Agent's page
status: in-progress
depends_on: [deactivate-an-agents-login-api, deactivated-message-at-sign-in, dead-session-lands-on-sign-in]
labels: [frontend]
stories: [1, 3, 5, 6, 7, 13, 14, 18, 24]
---

## Context

Sixth slice of `spec.md` (`## Execution order`; `## Solution`, "Frontend", and `## Design direction`). Adds the two BFF pass-through proxies on `backendFetch` (`frontend/app/api/agents/[agentId]/login/deactivate/route.ts` and `.../reactivate/route.ts`, same shape as the reset proxy), the two confirm dialogs built on `DialogShell` (one instance per view, opened for the chosen target; **Deactivate login for {name}** and **Reactivate login for {name}**, wording from the spec; `DialogErrorAlert` for `AGENT_HAS_NO_LOGIN`, `404` and the generic "Couldn't change this login. Try again."), and `AgentSignInEmail` changes: active Login shows the email, **Reset password**, **Deactivate login**; deactivated shows the email, a neutral **Deactivated** tag with "Deactivated since 2 Oct 2026" as accessible text and tooltip, **Reset password**, **Reactivate login**; no Login shows **Create login** alone. On `200` the dialog closes, `router.refresh()` runs, focus returns to the trigger's position and the polite status region announces "Login deactivated for {email}." / "Login reactivated for {email}.". The Agent response type gains an optional `loginDeactivatedAt?: string | null` in `frontend/lib/api/types.ts` (optional, so existing fixture factories still typecheck) and the Agent page passes it down. The dialogs are written so `deactivate-a-testers-login-ui` reuses them. The stub backend gains one Agent with a deactivated Login. The Agent half of the new e2e spec lands here. Modules: `frontend/app/api`, `frontend/components/manager`, `frontend/lib/api`, plus `frontend/app/manager`.

If the implementer finds no shipped neutral tag, it adds one short `DESIGN.md` entry in the same commit (spec `## Design direction`).

## Acceptance criteria

- [ ] On an Agent with an active Login the Manager sees **Reset password** and **Deactivate login** beside the email; on one without a Login only **Create login**.
- [ ] **Deactivate login** opens a dialog titled with the Agent's name whose body names the email, says they are signed out at once, and that record, requests and invoices stay; **Cancel** (or Escape) sends nothing and the Agent still signs in.
- [ ] Confirming closes the dialog, returns focus to where the action was, announces "Login deactivated for {email}.", and the email now carries the **Deactivated** tag with its date, with **Reactivate login** offered and no **Create login**.
- [ ] **Reactivate login** (after confirming) removes the tag, announces "Login reactivated for {email}.", and the Agent signs in with the same password.
- [ ] A `409 AGENT_HAS_NO_LOGIN` answer shows the stale-page message and a `404` the no-longer-exists message, never the generic one; any other failure shows "Couldn't change this login. Try again."
- [ ] In a second browser context signed in as that Agent, the next navigation after deactivation lands on the sign-in page; signing in again with the right password shows the deactivated message, with a wrong one "Incorrect email or password."; after reactivation the same password reaches the Agent console.
- [ ] The whole flow works by keyboard alone with a visible focus ring at each stop.

## Tests

- **Vitest component tests (spec `## Testing decisions`):** in the shape of the reset dialog's tests. New `deactivate-login-dialog.test.tsx` and `reactivate-login-dialog.test.tsx`: `cancel-sends-nothing`, `200-closes-refreshes-and-announces`, `agent-has-no-login-shows-stale-page-message`, `404-shows-no-longer-exists-message`, `other-failure-shows-generic-message`, `focus-returns-to-trigger`. Add cases to `agent-sign-in-email.test.tsx`: `deactivated-login-shows-tag-reset-and-reactivate-and-no-create`, `active-login-shows-reset-and-deactivate`, `login-less-agent-shows-create-login-only`.
- **e2e:** new `frontend/tests/e2e/deactivate-a-login.spec.ts` (Agent half; uses `helpers.ts` `login`/`logout`; two browser contexts; the Manager creates its own Agent, never a seeded Login): `manager-deactivates-agent-second-context-lands-on-sign-in-then-reactivate-signs-in`.
- **Visual:** new goldens for the deactivate confirm step and the reactivate confirm step on the Agent's page, per theme and breakpoint, in a new spec file beside `reset-password-dialog.spec.ts`, against the stub backend's deactivated Agent.

## Regression

- At risk: the Agent's detail page and the reset and create-login flows in `AgentSignInEmail`, which keeps one status region.
- Existing tests expected to change: `frontend/components/manager/agent-sign-in-email.test.tsx` gains the three cases above; every pre-existing case keeps passing unmodified. `frontend/tests/visual/stub-backend.mjs` gains one deactivated Agent and leaves existing fixtures untouched (it is a fixture, not a test, but named here as touched). The new Agent joins the shared `AGENTS` list, which also feeds `/manager/stock`'s auto-width Agent filter `<select>`; its name must be no wider than "Jordan Ellis" so the `manager-stock-*` goldens cannot move. Existing goldens expected to move, recaptured here: `reset-password-dialog-confirm-*` and `reset-password-dialog-reveal-*` (the Agent page behind the dialog gains the new action), and any golden of the Manager's Agent page; a golden moving on any other surface is a finding.
- `frontend/tests/e2e/manager-resets-a-password.spec.ts` and `create-login-for-existing-agent.spec.ts` are not modified.

## Observability

N/A — presentation only; the audit lines are written by `deactivate-an-agents-login-api`, and the confirmation is announced in the page's status region.
