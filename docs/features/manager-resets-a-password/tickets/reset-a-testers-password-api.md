---
id: reset-a-testers-password-api
title: Let a Manager reset a Tester's password through the API
status: ready-for-agent
depends_on: [reset-an-agents-password-api]
labels: [backend]
stories: [2, 15, 17, 18, 20, 21, 24]
---

## Context

The Tester route of the reset (spec `## Solution` → "Reset endpoints"): `POST
/api/clients/{clientId}/testers/{testerId}/password`, on the controller, service and guard that
`reset-an-agents-password-api` adds. The service resolves Client by `findByIdAndTenantId`, Tester by
`findByIdAndClientId`, then `Tester.user`, asks the guard, generates, saves and writes the same
single audit line. A Tester always has a Login, so there is no `409`. `OtherTenantFixture` gains a
Tester in the other Tenant, written directly with a known password.

Modules touched: `web`.

## Acceptance criteria

- `POST /api/clients/{clientId}/testers/{testerId}/password` as the Manager answers `200` with a
  generated-format `password` that differs from the Tester's previous one, and `Cache-Control: no-store`.
- The new password signs the Tester in; the previous one is then `401`.
- An unknown Client or Tester id, and a Tester in another Tenant, answer `404`; the other Tenant's
  Tester still signs in with its original password.
- The seeded Agent's and Tester's tokens are refused `403`; a refused reset leaves the old password working.
- A reset writes one `PASSWORD_CHANGED` audit line (target Tester's user id, the Manager as actor, the
  Tenant) and no log line contains the password or a hash.
- After a reset the Testers list returns the same email and Primary Contact flag.

## Tests

Seam: HTTP API (`IntegrationTest` + MockMvc), new class `TesterPasswordResetApiTest` in
`backend/src/test/java/com/remotesupport/backend/web`. Each test creates its own Tester by posting to
`POST /api/clients/{clientId}/testers` with no `password` and reading `$.password`.
Request bodies are built as JSON maps, never the request records, so the tests survive the contract step
`creation-takes-no-typed-password`.

- reset → `200`, format regex, `no-store`, differs; `loginAs` new = token, old = `401`;
- unknown Tester id and unknown Client id → `404`;
- cross-Tenant via `OtherTenantFixture`'s new Tester → `404`, original password still signs in;
- `agentToken()` and `testerToken()` → `403`; old password still works after a refusal;
- log capture: one `PASSWORD_CHANGED` line, never the returned password or a hash;
- Testers list email and Primary Contact flag unchanged after a reset.

## Regression

At risk: the shared controller, service and guard (`AgentPasswordResetApiTest`, `AgentPasswordResetGuardTest`
from the Agent ticket stay green unedited) and the Tester creation and list routes (`TesterApiTest`,
`TesterUsernameConflictApiTest`, unedited). Existing file modified: `OtherTenantFixture` gains a Tester
builder; every existing method keeps passing unmodified.

## Observability

Same single `PASSWORD_CHANGED` audit line as the Agent reset, target the Tester's user id; no password or
hash; refusals write none. Proven by the log-capture test.
