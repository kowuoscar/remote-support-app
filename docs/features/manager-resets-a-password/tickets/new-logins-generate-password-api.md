---
id: new-logins-generate-password-api
title: Generate and return a password when a Login is created without one
status: ready-for-agent
depends_on: [generated-password-generator]
labels: [backend]
stories: [9, 14, 28]
---

## Context

The **expand** step of a wide change, kept to the backend so the product stays green: the three
creation routes learn to generate, while the frontend that still sends a typed password keeps
working. The **contract** step is `creation-takes-no-typed-password`. See the spec's
`## Solution` → "Login creation switches to a generated password" for routes and response shape.

For `POST /api/agents`, `POST /api/agents/{agentId}/login` and
`POST /api/clients/{clientId}/testers`:

- `password` in the three request DTOs becomes optional (drop `@NotBlank`; keep the
  `@Size(min = PasswordPolicy.MIN_LENGTH …)` so a typed one is still held to the minimum).
- When it is absent, `AgentLoginService.create` / `TesterLoginService.create` call the
  generating operation and the controller returns it. When it is present, today's typed path is
  taken unchanged (transitional; removed by the contract ticket) and the response carries no
  `password`.
- Each route returns a dedicated creation record: the existing body flattened plus `password`
  (omitted when not generated), `toString` redacting it, sent with `Cache-Control: no-store`.
  List endpoints keep their existing records.

Modules touched: `dto`, `web`.

## Acceptance criteria

- On each of the three routes, a body with no `password` answers `201` with a `password`
  matching `^[a-km-np-z2-9]{4}(-[a-km-np-z2-9]{4}){2}$` and `Cache-Control: no-store`, and the new
  Login signs in with that value.
- The `201` bodies keep every field they return today at the same names (callers still read `id`).
- A body that still carries a typed `password` behaves as today: the typed value signs in and the
  response has no `password`.
- `GET /api/agents` and `GET /api/clients/{clientId}/testers` return no `password` for the
  Logins just created.
- The captured log for each creation holds its audit line and never the returned password;
  the creation records' `toString` never contains it.

## Tests

Seam: HTTP API (`IntegrationTest` + MockMvc), one new class
`GeneratedPasswordCreationApiTest` in `backend/src/test/java/com/remotesupport/backend/web`, using
`loginAs(username, <response password>)` as proof, plus a plain unit assertion on `toString`.

- per route: no-password body → `201`, format regex, `no-store`, `loginAs` with the returned value
  gives a token (Agent creation, give-login, Tester creation);
- per route: typed-password body → typed value signs in, no `password` in the response;
- list endpoints carry no `password`;
- log capture: audit line present, returned password absent (all three routes);
- a body with a typed password shorter than 8 characters is still `400`.

## Regression

At risk: the creation contract the frontend and every test helper use today. Guarded by
`AgentApiTest`, `AgentLoginApiTest`, `TesterApiTest`, `TesterUsernameConflictApiTest`,
`CrossTenantUsernameAgentCreationApiTest`, `AgentCreationAtomicityTest`,
`PasswordMinimumLengthApiTest` and `DemoDataLoaderApiTest`, plus `IntegrationTest`'s
`createAgent` / `createTesterAndLogin` helpers, which still send a typed password. All stay
**unedited and green**: that is the proof the transitional path works. No existing test is
expected to change.

## Observability

Creation's audit lines (`AuditLog.agentLoginCreated`, `AuditLog.created`) are unchanged and carry
no password; the log-capture test above proves it. No new field on any audit line.
