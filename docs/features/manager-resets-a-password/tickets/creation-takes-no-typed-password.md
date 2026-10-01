---
id: creation-takes-no-typed-password
title: Remove the typed password from the three creation routes
status: ready-for-agent
depends_on: [creation-dialogs-reveal-generated-password]
labels: [backend]
stories: [9, 14, 28, 29, 30]
---

## Context

The **contract** step after `new-logins-generate-password-api` (expand) and
`creation-dialogs-reveal-generated-password` (the only sender of a password stops). Spec
`## Solution` → "Login creation switches to a generated password": `password` and its
annotations leave `AgentCreateRequest`, `AgentLoginCreateRequest` and `TesterCreateRequest`; a body
that still sends it is ignored like any unknown property. The creation records always carry
`password`. `AgentLoginService.create` and `TesterLoginService.create` lose their password
parameter and always generate. `PasswordPolicy`'s Javadoc stops listing the three DTOs.
`DemoDataLoader`, which builds Priya Shah through `AgentController.create` and the five demo Testers
through `TesterController.create`, sets `PRIYA_PASSWORD`, `TESTER_PASSWORD` and
`HARBOR_TESTER_PASSWORD` through the password write's raw-value operation right after each call.
All backend tests listed in the spec's `## Testing decisions` move to the returned password.

Modules touched: `dto`, `web`, `demo`.

## Acceptance criteria

- A creation body that still sends `"password": "Passw0rd!23"` is accepted on all three routes; that
  value is `401` at sign-in and the returned `password` signs in.
- A body with no password creates the Login (no `400` for a missing or blank password).
- The password-bearing `201` always carries `password` and `Cache-Control: no-store`; the audit tests
  assert the returned password is absent from the log.
- After the demo data loads, Priya Shah, Dana Whitfield and Noah Kim sign in with
  `PRIYA_PASSWORD`, `TESTER_PASSWORD` and `HARBOR_TESTER_PASSWORD`; the Flyway-seeded Logins still sign in.
- `PasswordMinimumLengthApiTest` no longer has creation rows; its `CHANGE_PASSWORD` rows pass.

## Tests

Seam: HTTP API (`IntegrationTest` + MockMvc), per the spec's `## Testing decisions`; the demo story
is built on the Testcontainers database.

- one test per route sending a typed password: sign-in with it is `401`, with the returned one a token;
- "a body with no password creates the Login and the returned password signs in" replaces
  `AgentApiTest.creatingAnAgentWithoutAPasswordIsRejectedAndCreatesNoAgent` and the password half of
  `AgentLoginApiTest.aMissingUsernameOrPasswordIsRejected`;
- `AgentApiTest` and `AgentLoginApiTest` audit tests assert the **returned** password is absent;
  `TesterApiTest`'s audit test gains the same assertion;
- `DemoDataLoaderApiTest` signs in as `DemoDataLoader.PRIYA_USERNAME`, `DANA_USERNAME`, `NOAH_USERNAME`
  with the documented passwords;
- `AuthLoginTest` still signs in the three seeded Logins.

## Regression

At risk: every backend test that creates an Agent or Tester. Existing test files this ticket
modifies, and why (each moves to the returned password, mechanically):
`IntegrationTest` (`createAgent` stops sending a password; `createTesterAndLogin` loses its
`password` parameter and reads `$.password`) and its 21 callers `SimSwapRequestDetailsApiTest`,
`ReplaceRequestsApiTest`, `FeeApiTest`, `PostpaidSimPlanApiTest`, `SimCardInstalledInApiTest`,
`ReturnRequestsApiTest`, `SimCardApiTest`, `SimCardCarrierApiTest`, `ReviewQueueApiTest`,
`ClientInvoiceByIdApiTest`, `ClientInvoiceApiTest`, `StockApiTest`, `RebootAndTopupDetailsApiTest`,
`ManagerApprovesRequestsApiTest`, `StockFulfilmentApiTest`, `AgentInvoiceApiTest`,
`SmartphoneApiTest`, `ContractApiTest`, `ProvisionRequestDetailsApiTest`, `ChangeOwnPasswordApiTest`,
`RequestApiTest`; tests that build creation bodies themselves — `AgentApiTest` (its `PASSWORD` constant
and `ana.lima@agents.example` sign-in), `AgentLoginApiTest` (`loginBody`, `sofia.marin@agents.example`),
`TesterApiTest`, `TesterUsernameConflictApiTest`, `CrossTenantUsernameAgentCreationApiTest`,
`AgentCreationAtomicityTest`, `TopupFeeFromOptionApiTest` (`priya.raman@aurora.example`),
`PasswordMinimumLengthApiTest` (`freshAgentLogin`, creation rows removed, stale Javadoc counts fixed),
`ChangeOwnPasswordApiTest` (`freshAgentLogin`, `freshTesterLogin`), and `DemoDataLoaderApiTest` (gains
the sign-in cases; every pre-existing method keeps passing unmodified). `OtherTenantFixture` is not
edited. Guards that stay unedited: `AuthLoginTest`, `AuthLoginObservabilityTest`.

## Observability

Audit lines are unchanged and still carry no password (`AuditLog.agentLoginCreated`,
`AuditLog.created`); the audit tests above assert the returned password never appears in the log.
