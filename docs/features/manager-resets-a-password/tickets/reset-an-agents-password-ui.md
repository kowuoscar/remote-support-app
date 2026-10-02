---
id: reset-an-agents-password-ui
title: Reset an Agent's password from the Agent's page
status: done
depends_on: [reset-an-agents-password-api, creation-dialogs-reveal-generated-password]
labels: [frontend]
stories: [4, 6, 7, 10, 11, 12, 13, 14, 19, 25, 26]
---

## Context

The Manager-facing reset for an Agent (spec `## Solution` → Frontend → "The reset dialog", "Agent's
page", "BFF"; `## Design direction`). Adds: the new BFF proxy for `POST /api/agents/{agentId}/login/password`
(via `backendFetch`, forwarding `no-store`); the reset dialog on `DialogShell` in two steps (Confirm, then
the shared one-time reveal), one instance per view, failures through `DialogErrorAlert`; a **Reset password**
row action (`Button variant="row" size="sm"`) in `AgentSignInEmail`, shown only when the Agent has a Login;
on close, focus returns to the trigger and an always-mounted polite status says "Password reset for {email}."
without the password. Wording per the spec's `Naming` paragraph. Read `docs/agents/frontend.md` and `DESIGN.md`.
The dialog is built target-agnostic (name, email, endpoint) so the Tester ticket reuses it. The Agent half of
`manager-resets-a-password.spec.ts` is added here.

Modules touched: `frontend/components` → `manager`; `frontend/app` → `api`.

## Acceptance criteria

- An Agent with a Login shows **Reset password** beside the sign-in email; an Agent without one shows only
  **Create login**.
- The dialog titled "Reset password for {name}" says the current password stops working and waits;
  Cancel sends nothing and the old password still signs in.
- Confirming shows the reveal with the new password; the Agent signs in with it through the UI and the
  creation password is refused; Done returns focus to the trigger, the status reads "Password reset for
  {email}." and no element in the DOM contains the password; reopening shows the confirm step.
- `AGENT_HAS_NO_LOGIN` renders the stale-page message, `404` renders "no longer exists" with a link back
  to the list, anything else (including a lost connection) the generic "Couldn't reset…try again" message.
- The reset opens, confirms, copies and closes by keyboard with a visible focus ring; the Agent can then
  change the password from the viewer-chip menu and the revealed one is refused.

## Tests

Seam: Vitest + Testing Library with the BFF stubbed at `fetch`; the isolated e2e stack; the stub-backend
visual suite.

- component tests for the reset dialog: Cancel sends nothing; `409 AGENT_HAS_NO_LOGIN`, `404` and a network
  failure each render their message; success shows the reveal; close returns focus to the trigger and announces
  the status without the password; and for `AgentSignInEmail`: row action present with a Login, absent without;
- e2e named case: the reset POST response's `cache-control` header contains `no-store`, asserted with `page.waitForResponse`;
- e2e `manager-resets-a-password.spec.ts`, Agent half: create an Agent through the UI keeping the revealed
  password, reset it from the Agent's page keeping the new one, log out, sign in with the new one, confirm the
  creation password is refused; also change it to one of their own via the viewer-chip menu;
- visual: new goldens for the reset dialog's confirm step and reveal step (fixed stub password) on the Manager's
  Agent page, per theme and breakpoint, with the stub backend extended to serve the Agent page and the reset route.

## Regression

At risk: the Agent's page (`AgentSignInEmail`, `AgentStandingAmountsView`, `CreateAgentLoginDialog`) and the shared
dialog shell. Guarded by `create-agent-login-dialog.test.tsx`, `create-login-for-existing-agent.spec.ts`,
`create-agent-with-login.spec.ts`, `agent-standing-amounts-and-invoice-generation.spec.ts` (all unedited) and
`frontend/tests/visual/surfaces.spec.ts`. Existing file modified: `frontend/tests/visual/stub-backend.mjs`
(gains the Agent page data and the reset route; every existing route unchanged) and `frontend/tests/visual/surfaces.spec.ts`
(gains the new Agent-page dialog cases; every existing surface keeps its golden). The visual suite has no Manager Agent
page or Client page golden today, so no existing golden moves; any other golden moving is a finding.

## Observability

N/A — frontend only; the backend audit line is `reset-an-agents-password-api`'s. The password must never be logged,
placed in a URL or stored in the browser.
