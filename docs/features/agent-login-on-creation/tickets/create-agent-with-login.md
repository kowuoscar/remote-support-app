---
id: create-agent-with-login
title: Manager creates an Agent together with its login
status: in-progress
depends_on: []
labels: [backend, frontend, manager, auth]
---

## Context

Every Manager-created Agent is currently unable to sign in. Implements spec.md
`## Solution` ("One request, one transaction", "One login per Agent", "Agent
read model", and the create-Agent dialog and sign-in row of "Frontend") and
user stories 1-5 and 10-13. Constraints are in spec.md `## Constraints`.

## Acceptance criteria

- [ ] The Manager's create-Agent dialog requires an email and a temporary password, alongside name, country and salary
- [ ] On success, the new Agent can sign in immediately with those credentials and lands on the Agent Console, seeing only their own Contracts
- [ ] Creating an Agent with an email already in use is rejected with a clear "email already in use" message, and no Agent (nor its standing salary) is created
- [ ] Creating an Agent without an email or password is rejected, and no Agent is created
- [ ] Each Agent in the Agent list response carries its login email, or null when it has none
- [ ] The Agent's detail view shows the email the Agent signs in with
- [ ] No Agent can ever be linked to more than one login
- [ ] Only a Manager can create an Agent; Agent and Tester callers are rejected

## Tests

Backend HTTP API seam (prior art: Agent API test, Tester API test, auth login test):
- Create Agent → 201 with `loginUsername`; signing in with those credentials succeeds and resolves to that Agent
- Missing username → 400; missing password → 400; neither case adds an Agent to the list
- Duplicate username → 409; the Agent list is unchanged
- Agent caller → 403; Tester caller → 403

Frontend browser seam (prior art: manager-entity-setup and login e2e specs):
- Manager creates an Agent with email and temporary password through the dialog, sees the email on the Agent's detail view, signs out, signs in as that Agent and reaches the Agent Console

## Regression

- Agent creation still records the initial `SALARY` standing amount effective the creation month — protected by the existing Agent API and standing-amount tests, updated to send credentials.
- Existing tests and e2e flows that create Agents through the API or dialog must supply credentials; the full backend and e2e suites must pass.
- The seeded demo Agent keeps signing in — protected by the existing login tests.
- Manager surfaces' visual goldens stay green unless `DESIGN.md` changes in the same commit.

## Observability

- Audit log entry for the created Agent login (actor, tenant, Agent id, User id), in addition to the existing Agent creation entry.
- Username conflicts on Agent creation logged at the same level as Tester's; the password is never logged.
