---
id: deactivated-login-refused
title: Refuse a deactivated Login at sign-in, on every token request and on password change
status: ready-for-agent
depends_on: []
labels: [backend]
stories: [10, 11, 12, 13, 15, 26, 27]
---

## Context

Second slice of `spec.md` (`## Execution order`; `## Solution`, "What deactivated means, at three doors"). Adds Flyway `V58__add_users_deactivated_at.sql` (`ALTER TABLE users ADD COLUMN deactivated_at timestamptz NULL`, additive, no data rewrite; V56/V57 are reserved by other features), `User.deactivatedAt` with `deactivate(now)`, `reactivate()`, `isDeactivated()`, and the three doors:

1. `AppUserPrincipal.isEnabled()` returns `!user.isDeactivated()`; the `DaoAuthenticationProvider` is configured so the enabled check runs after the password check (empty pre-check, post-check throwing `DisabledException`), so a wrong password stays `BadCredentialsException` for active and deactivated Logins alike. `AuthController` catches `DisabledException` and answers `401 { "code": "LOGIN_DEACTIVATED" }` with a `warn` line.
2. `JwtAuthenticationFilter` asks a new Login-state check (primary-key read, no cache) whether the token's `userId` is still an active Login; deactivated or missing leaves the context unauthenticated so the existing rules answer `401`.
3. `ChangePasswordService` refuses a deactivated caller before verifying anything, with the exception path its controller already maps to `401`; its `matches` check stays.

There is no way to deactivate a Login over HTTP yet; tests deactivate through the `User` entity in the test's transaction (IntegrationTest rolls back per method, MockMvc shares it). Modules: `domain`, `security`, `web` (plus the migration). This is the slice that makes the epic's "no test will catch that" false.

## Acceptance criteria

- [ ] Signing in with the right password on a deactivated Login answers `401` with body `code: LOGIN_DEACTIVATED`; the same Login with a wrong password answers `401` with no body, identical to an active Login with a wrong password.
- [ ] A token issued before deactivation is refused with `401` on `GET /api/me`, on an Agent or Tester endpoint and on `POST /api/me/password`; a token for a Login whose row no longer exists is refused with `401` too.
- [ ] Calling `ChangePasswordService` with a deactivated caller is refused and leaves the stored password unchanged (a later reactivated sign-in with the old password succeeds).
- [ ] Once the Login is reactivated (entity `reactivate()`), the same password signs in and a fresh token works.
- [ ] Every active Login behaves as before: seeded Manager, Agent and Tester sign in, the demo-profile Logins still sign in with their documented passwords, and a self-service password change still works.
- [ ] Every existing Login stays active after the V58 migration: the seeded Manager, Agent and Tester still sign in.
- [ ] A refused sign-in for a deactivated Login writes one `login refused deactivated userId=… tenantId=…` warn line with no password; a request refused at door 2 writes no line.

## Tests

- **HTTP API seam (spec `## Testing decisions`):** new `DeactivatedLoginRefusedApiTest` (extends `IntegrationTest`; creates its own Agent and Tester through the API, never a seeded or demo Login, and deactivates through the `User` entity). Cases: `right-password-on-deactivated-login-is-401-login-deactivated`, `wrong-password-on-deactivated-login-is-401-with-no-body-same-as-active`, `kept-token-is-401-on-me-agent-endpoint-and-change-password`, `token-of-a-deleted-user-row-is-401`, `reactivated-login-signs-in-with-same-password-and-fresh-token-works`, `refused-sign-in-logs-one-warn-line-without-password-and-door-two-logs-none`, for an Agent and a Tester Login.
- **Narrow Spring-context test:** new `ChangePasswordServiceDeactivatedTest` calling `ChangePasswordService` with a deactivated `User`: `deactivated-caller-is-refused-before-verification`, `password-unchanged-after-refusal-and-reactivated-signin-still-works`.

## Regression

- At risk: sign-in for every role, every authenticated request (the filter now reads one row per request), self-service password change, the demo profile.
- Protected by `AuthLoginTest`, `AuthLoginObservabilityTest`, `ChangeOwnPasswordApiTest`, `ProtectedEndpointTest`, `SecondTenantSignInApiTest` and `DemoDataLoaderApiTest`, which pass unedited. No existing test is expected to change.

## Observability

Sign-in refusal writes `login refused deactivated userId=… tenantId=…` at `warn`, never the password (spec `## Solution`, "Observability"). Door 2 writes nothing by design. The test above reads the captured log.
