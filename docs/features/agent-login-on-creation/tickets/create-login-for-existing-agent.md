---
id: create-login-for-existing-agent
title: Manager gives a login to an Agent that has none
status: ready-for-agent
depends_on: [create-agent-with-login]
labels: [backend, frontend, manager, auth]
---

## Context

Agents created before `create-agent-with-login` have no login and would stay
stranded. Implements spec.md `## Solution` ("Login for an existing Agent" and
the "No login" state of "Frontend") and user stories 6-9, 12-13. Reuses the
shared login creation and the one-login-per-Agent index delivered by
`create-agent-with-login`.

## Acceptance criteria

- [ ] An Agent without a login shows a "No login" state on its detail view, with a "Create login" action
- [ ] Creating a login there (email + temporary password) lets the Agent sign in immediately, and the detail view then shows that email
- [ ] The "Create login" action is not offered for an Agent that already has a login
- [ ] Creating a login for an Agent that already has one is rejected, even when called directly against the API
- [ ] An email already in use is rejected with a clear message, and the Agent stays without a login
- [ ] Creating a login for an Agent that doesn't exist in the caller's tenant is reported as not found
- [ ] Only a Manager can create an Agent's login; Agent and Tester callers are rejected

## Tests

Backend HTTP API seam (the login-less Agent fixture is inserted directly in test setup, per spec.md `## Testing decisions`):
- Login-less Agent → 201 with `loginUsername`; signing in with the credentials succeeds and resolves to that Agent
- Agent that already has a login → 409
- Username already in use → 409; the Agent's `loginUsername` stays null
- Missing username or password → 400
- Unknown Agent id, or an Agent in another tenant → 404
- Agent caller → 403; Tester caller → 403

Frontend browser seam:
- For a login-less Agent, the Manager sees "No login", creates the login through the dialog, sees the email on the detail view, and that Agent can then sign in

## Regression

- Agent creation with a login (`create-agent-with-login`) — protected by that ticket's API and e2e tests.
- The Agent detail view's standing amounts and Agent Invoice sections still render — protected by the existing agent-standing-amounts and agent-invoice e2e specs.

## Observability

Audit log entry for the created Agent login (actor, tenant, Agent id, User id), the same event `create-agent-with-login` emits; the password is never logged.
