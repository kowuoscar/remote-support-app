---
feature: agent-login-on-creation
status: approved
date: 2026-09-17
---

# Agent login on creation

## Problem

A Company Manager can create an Agent (name, country, standing salary), but
nothing in the product ever gives that Agent a login. The only Agent who can
sign in today is the seeded demo one, linked by a migration. Every Agent a
Manager actually creates is therefore locked out of the Agent Console: they
cannot see their Contracts' Fleets, fulfil Requests, log Fees or assemble
either invoice — the whole Agent side of the monthly cycle is unreachable
for real Agents.

Testers do not have this problem: a Manager creates a Tester and their login
in one step, and the Tester can sign in immediately.

## Goals / Non-goals

**Goals**

- Creating an Agent always creates that Agent's login in the same step, from
  an email and a temporary password the Manager enters; the Agent can sign in
  with them immediately.
- Creating the Agent and its login is all-or-nothing: if the login cannot be
  created (e.g. the email is already in use), no Agent is created either.
- An Agent created before this feature (and so without a login) can be given
  one by the Manager from the Agent's detail view.
- An Agent has at most one login, ever.
- The Manager can see, on the Agent's detail view, which email the Agent signs
  in with — or that it has no login yet.

**Non-goals**

- Changing or resetting a password (by the Agent or the Manager).
- Forcing a password change on first sign-in.
- Changing an Agent's login email after creation.
- Deactivating or deleting a login.
- Invitation emails or any outbound email.
- Showing the login email on the Agents list.
- Fixing the cross-tenant username lookup at sign-in (see Constraints).
- Any change to how Testers' logins are created.

## User stories

1. As a Company Manager, I want to enter an Agent's email and temporary password while creating the Agent, so that the Agent can sign in without any further setup.
2. As a Company Manager, I want the Agent creation to fail as a whole when the email is already in use, so that I never end up with an Agent that cannot sign in.
3. As a Company Manager, I want a clear error telling me the email is already in use, so that I can pick another one and retry.
4. As a Company Manager, I want the Agent creation form to refuse a missing email or password, so that I cannot create a login-less Agent by accident.
5. As a Company Manager, I want to see on an Agent's detail view the email that Agent signs in with, so that I can tell the Agent which credentials to use.
6. As a Company Manager, I want an Agent without a login to be clearly marked as such on its detail view, so that I notice Agents who still cannot sign in.
7. As a Company Manager, I want to create a login for an existing Agent that has none, from that Agent's detail view, so that Agents created before this feature are not stranded.
8. As a Company Manager, I want the "create login" action to be unavailable once an Agent has a login, so that an Agent never ends up with two.
9. As a Company Manager, I want creating a second login for an Agent to be rejected even if attempted directly against the API, so that the one-login rule cannot be bypassed.
10. As a Local Support Agent, I want to sign in with the email and temporary password my Manager gave me, so that I can reach the Agent Console.
11. As a Local Support Agent who just signed in for the first time, I want to see only my own Contracts, so that my scope is correct from day one.
12. As a Company Manager, I want only Managers to be able to create an Agent's login, so that Agents and Testers cannot mint logins.
13. As an operator, I want every Agent login creation recorded in the audit log, so that I can trace who gave which Agent access.

## Solution

**One request, one transaction.** Agent creation keeps its existing endpoint
and gains two required fields, `username` (an email) and `password`. The
Manager-facing contract mirrors Tester creation exactly. Creating the Agent,
its initial `SALARY` standing amount, and its `AGENT`-role User linked to it
happens in a single database transaction: a username conflict rolls back the
Agent and its standing amount too, and the API answers 409. Tester creation
today is *not* transactional (it saves the User, then the Tester); this
feature does not change that, but must not copy the gap.

Rejected: an optional login section on the create form (leaves the stranded
state reachable, which is the problem); a system-generated password shown once
(new UX with no precedent in the product); invitation emails (no email
infrastructure exists).

**Login for an existing Agent.** A new Manager-only operation creates the
login for an Agent that has none: `POST /api/agents/{agentId}/login` with
`{ username, password }`.
- 201 with the updated Agent on success.
- 404 when the Agent does not exist in the caller's tenant.
- 409 when the Agent already has a login, or the username is already in use.
- 400 on a missing username or password.

The login creation logic (build the User, encode the password, link it to the
Agent, map a username conflict to 409) is one shared piece used by both
operations, so the two paths cannot diverge.

**One login per Agent, enforced by the schema.** The existing nullable link
from User to Agent stays where it is. A new migration adds a partial unique
index on that link, so at most one User can reference a given Agent. The
service layer checks first to return a clean 409; the index is defence in
depth, the same pattern as the one-primary-contact-per-Client rule.

**Agent read model.** The Agent response gains a nullable `loginUsername`:
the linked User's username, or `null` when the Agent has no login. The
existing list endpoint returns it for each Agent; the detail view already
reads the Agent from that list, so no new single-Agent endpoint is added.

```
AgentCreateRequest { name, country, salaryAmount, username, password }  // all required
AgentLoginCreateRequest { username, password }                          // both required
AgentResponse { id, name, country, currency, salaryAmount, contractCount,
                loginUsername: string | null }
```

**Frontend.**
- The create-Agent dialog gains "Email" and "Temporary password" fields, with
  the same labels, input types, placeholders and helper copy as the create-Tester
  dialog, and its 409 message says the email is already in use.
- The Agent detail view shows a sign-in row: the login email when present;
  otherwise a "No login" state with a "Create login" action opening a dialog
  with the same two fields.
- A new BFF proxy route forwards the create-login call, following the existing
  proxy pattern.

**Seed data.** The seeded demo Agent already has a login; no data migration is
needed.

## Design direction

No new visual world: this feature extends existing Manager surfaces and
conforms to the committed `DESIGN.md`. No new design-system ticket — the MVP's
`design-system` ticket already anchors every frontend ticket.

- Create-Agent dialog — Operate.
- Agent detail view, sign-in row and create-login dialog — Operate.

Any component needed (e.g. an empty "No login" state) reuses existing
`DESIGN.md` patterns; if one doesn't exist, `DESIGN.md` changes in the same
commit.

## Constraints

- Username is an email-format string, required, unique per tenant (existing
  `users` unique constraint); password is required and non-blank, with no
  further strength rule — identical to Tester login creation.
- Passwords are stored only as a hash, through the application's existing
  password encoder. The plaintext is never logged, echoed in a response, or
  stored.
- Agent + standing amount + login creation is atomic: a failure at any point
  leaves no Agent, standing amount or User behind.
- At most one User per Agent, enforced by a database unique index and by a
  409 from the service layer.
- Only `MANAGER` may create an Agent or an Agent's login; `AGENT` and `TESTER`
  receive 403.
- Login creation for an Agent outside the caller's tenant returns 404, never
  409 or 403, so as not to reveal it.
- Known limitation, out of scope: usernames are unique per tenant, but sign-in
  looks users up by username across all tenants. Harmless while a single
  tenant is operated; must be addressed before a second tenant exists.

## Testing decisions

Tests assert external behaviour only: HTTP status and response bodies, and
what a user can do in the browser — never repository calls or entity state.

1. **Backend HTTP API seam** — the existing `@SpringBootTest` + Testcontainers
   integration tests. Prior art: the Agent API test and the Tester API test
   (username conflict → 409, non-Manager → 403), and the auth login test for
   proving a created login can actually sign in. Cases:
   - Creating an Agent returns its `loginUsername`; those credentials then
     sign in successfully and resolve to that Agent.
   - Missing username or password → 400, and no Agent appears in the list.
   - Duplicate username → 409, and no Agent appears in the list.
   - Agent or Tester caller → 403 on both operations.
   - Create-login on an Agent without a login → 201, `loginUsername` set,
     sign-in works; on an Agent that already has one → 409; on an unknown
     Agent → 404.
     Since the API can no longer create a login-less Agent, the fixture for
     that case inserts the Agent directly into the database — in this
     backend test and in the create-login e2e spec's fixture, the only two
     places setup bypasses the API.
   - Atomicity is proven outside the shared, rolled-back test transaction
     (which would hide a partial commit): a duplicate-username create leaves
     no Agent, standing amount or User behind. A leftover standing amount or
     User isn't observable through the API, so this one test reads the
     database directly, and deletes what it committed.
2. **Frontend browser seam** — the existing Playwright e2e suite against the
   full stack. Prior art: the manager-entity-setup e2e spec and the login e2e
   spec. Case: a Manager creates an Agent with email and temporary password
   through the real dialog, sees the email on the Agent's detail view, signs
   out, signs in as that Agent and lands on the Agent Console.
3. **Visual regression** — existing goldens are updated only if a surface
   they cover visibly changes, and then alongside `DESIGN.md` per the frontend
   DoD.

## Open questions

None

## Execution order

1. `create-agent-with-login` — Manager creates an Agent and its login in one atomic step; the Agent can sign in immediately
2. `create-login-for-existing-agent` — Manager gives a login to an Agent that has none, from its detail view
