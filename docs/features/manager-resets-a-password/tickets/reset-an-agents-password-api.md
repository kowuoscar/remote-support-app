---
id: reset-an-agents-password-api
title: Let a Manager reset an Agent's password through the API
status: in-progress
depends_on: [new-logins-generate-password-api]
labels: [backend]
stories: [1, 3, 8, 15, 16, 18, 20, 21, 22, 23, 24, 25]
---

## Context

The backend half of the Agent reset (spec `## Solution` → "The permission rule", "Reset
endpoints", "The reset service", "Observability"). Adds: the guard class in `security` (modelled on
`FleetAccessGuard`: caller is a Manager, target Login is in the caller's Tenant, role is `AGENT` or
`TESTER`); the reset service with its Agent operation (resolve within the Tenant through
`findByIdAndTenantId` and `UserRepository.findByAgentId`, ask the guard, call the generating
operation, save, one `AuditLog.passwordChanged` line); one new controller with
`POST /api/agents/{agentId}/login/password` (no body; `200 { "password" }`, `no-store`) and its own
`@ExceptionHandler` for `409 AGENT_HAS_NO_LOGIN`. `OtherTenantFixture` gains an Agent with a Login in
the other Tenant, written directly with a known password. No `SecurityConfig` matcher, no migration.
The Tester route is `reset-a-testers-password-api`, on the same controller and service.

Modules touched: `security`, `web`, `dto` (the `{ password }` response record, `toString` redacted).

## Acceptance criteria

- `POST /api/agents/{agentId}/login/password` as the Manager answers `200` with a `password` in the
  generated format that differs from the Agent's previous one, and `Cache-Control: no-store`.
- The new password signs the Agent in; the previous one is then `401`.
- An Agent with no Login answers `409` `AGENT_HAS_NO_LOGIN`; an unknown Agent id, and an Agent in
  another Tenant, answer `404` and the other Tenant's Agent still signs in with its original password.
- The seeded Agent's token and the seeded Tester's token are refused `403`, and a refused reset leaves
  the old password working.
- A reset writes one `PASSWORD_CHANGED` audit line with `entityId` the Agent's user id, `actorUserId`
  the Manager's and the Tenant id; no password or hash is in any log line; a refused reset writes none.
- The guard refuses a Manager-role target, so no route built on it can reset a Manager's Login.
- After a reset the Agents list returns the same `loginUsername` and the Agent's standing amounts are unchanged.

## Tests

Seam: HTTP API (`IntegrationTest` + MockMvc), new class `AgentPasswordResetApiTest` in
`backend/src/test/java/com/remotesupport/backend/web`. Each test creates its own Agent by posting to
`POST /api/agents` with no `password` and reading `$.password`, and never touches a seeded Login.
Request bodies are built as JSON maps, never the request records, so the tests survive the contract step
`creation-takes-no-typed-password`.

- reset → `200`, format regex, `no-store`, differs from creation password; `loginAs` new = token,
  `loginAs` old = `401`;
- no-Login Agent → `409` `AGENT_HAS_NO_LOGIN` (a login-less Agent built the way `AgentLoginApiTest`'s fixture builds one, since the API
  can no longer create one);
- unknown id → `404`;
- cross-Tenant via `OtherTenantFixture`'s new Agent → `404` and its original password still signs in;
- `agentToken()` and `testerToken()` → `403`; old password still works after a refusal;
- log capture: one `PASSWORD_CHANGED` line with the ids, never the returned password or a hash; refusals log none;
- Agents list `loginUsername` and standing amounts unchanged after a reset;
- guard: a narrow Spring-context test (`AgentPasswordResetGuardTest`) calls the guard with a Manager `User` and
  with a cross-Tenant `User`, and each is refused with `AccessDeniedException`.

## Regression

At risk: Manager-only matching under `/api/agents/**` (`SecurityConfig`, unchanged), the Agent `login`
sub-resource beside `POST /api/agents/{agentId}/login`, and the audit line shared with self-service
change. Guarded by `AgentLoginApiTest`, `ChangeOwnPasswordApiTest` and `AuthLoginTest`, all unedited.
Existing file modified: `OtherTenantFixture` gains an Agent-with-Login builder; every existing method
keeps passing unmodified (it is used by the tests that already call
`sentClientInvoiceInAnotherTenant`, `managerLoginInAnotherTenant` and the rest).

## Observability

One audit line per successful reset, reusing `AuditLog.passwordChanged`:
`action=PASSWORD_CHANGED entity=User entityId=<target userId> actorUserId=<Manager's userId> tenantId=…`.
No password, hash or length. Refusals write none. Proven by the log-capture test above.
