---
id: creation-dialogs-reveal-generated-password
title: Show the generated password once after creating an Agent, a Login or a Tester
status: ready-for-agent
depends_on: [new-logins-generate-password-api, one-time-password-reveal, dialog-shell-mounts-when-open]
labels: [frontend]
stories: [9, 10, 11, 12, 14, 28]
---

## Context

The frontend half of "generated, not typed" for creation (spec `## Solution` → Frontend →
"The creation dialogs", "BFF", and "The one-time reveal"). `CreateAgentDialog`,
`CreateAgentLoginDialog` and `CreateTesterDialog` drop their password field, their "only spaces"
check and the 400 message about a temporary password; the hint becomes "A password is generated
when you create the login — you'll see it once."; each shows the reveal on `201`. The three BFF
creation proxies forward `Cache-Control: no-store` on `POST`. `LoginCredentialFields` loses its
password props and keeps its name. `CreateAgentLoginDialog` calls `onCreated` when the reveal is
closed, not when the response arrives; dialogs that `router.refresh()` do so on entering the
reveal. e2e learns passwords from the reveal (spec `## Testing decisions`, last two bullets).

The backend still honours a typed password, but nothing here sends one. `agent-dashboard.spec.ts`
also fills "Temporary password" and signs in with `"Passw0rd!23"`; it moves with the others.

Modules touched: `frontend/components` → `manager`; `frontend/app` → `api`.

## Acceptance criteria

- **Add agent**, **Create login** and **Add tester** show no password field and the new hint; each
  `201` replaces the form with the reveal naming the email, the **Generated password** field
  holding what the backend returned, and the person signs in with that value.
- On **Create login**, the Agent's page swaps **Create login** for the sign-in email only after
  Done (or Escape / backdrop), never while the reveal is open; Add agent and Add tester refresh the
  list behind while the reveal stays.
- After the dialog closes, no element in the DOM contains the password.
- The three BFF proxies' `POST` responses carry `Cache-Control: no-store`.
- Every existing e2e flow that creates an Agent or Tester through the UI signs in with the revealed
  password, and the full e2e suite is green.

## Tests

Seam: Vitest + Testing Library with the BFF stubbed at `fetch`, then the isolated e2e stack.

- `create-tester-dialog.test.tsx` (existing): drop "Temporary password" typing; "closes and refreshes
  on success" becomes "shows the reveal and refreshes".
- New `create-agent-dialog.test.tsx` and `create-agent-login-dialog.test.tsx`: no password field;
  `201` shows the reveal with the stubbed password; `CreateAgentLoginDialog` calls `onCreated` only
  on close; USERNAME_TAKEN and generic failures still render.
- BFF route handlers: `no-store` forwarded on `POST` for `/api/agents`,
  `/api/agents/[agentId]/login`, `/api/clients/[clientId]/testers`.
- e2e `helpers.ts`: `addTester(page, clientId, email)` loses `password`, reads the **Generated
  password** field, clicks Done and returns it; `createContractWithTester` returns it with the two
  ids; `addTesterAndSubmitRequestAsAgent` uses it; `createUnrelatedContract` stops filling a password.
- e2e specs switched to the revealed value: `fee-logging-and-provisioning`,
  `tester-request-submission`, `cancelled-sim-billed-through-its-month`,
  `manager-decides-return-disposition`, `client-invoice-submission-and-visibility`, `agent-stock`,
  `fleet-management` (including its inline Add tester), `sim-swap-moves`, `agent-request-fulfillment`,
  `manager-approves-requests`, `return-client-owned-smartphones`, `change-password`,
  `agent-dashboard`, `manager-entity-setup`, `create-agent-with-login`,
  `create-login-for-existing-agent`; `topup-fee-from-option` and `client-invoice-generation` change
  only through the helpers. "Only spaces" password tests are deleted; direct API bodies drop `password`.

## Regression

At risk: every journey that creates a Login through the UI, and the dialogs' existing error
handling. Existing tests this ticket modifies, and why: `create-tester-dialog.test.tsx` (password
field gone, success now shows the reveal); `frontend/tests/e2e/helpers.ts` and the e2e specs listed
above (the typed password no longer exists; they read the reveal instead). `SEEDED_USERS` is
unchanged. Guards that stay unedited and green: `change-password-dialog.test.tsx`,
`frontend/tests/visual/change-password-dialog.spec.ts`. No golden moves (the creation dialogs have
none).

## Observability

N/A — no backend change; the password must not appear in a URL, storage or console output, which the
reveal's own tests already assert.
