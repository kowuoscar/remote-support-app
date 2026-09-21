---
id: pin-colliding-username-sign-in
title: Pin today's sign-in behaviour for a username that exists in two Tenants
status: in-progress
depends_on: [second-tenant-login-fixture]
labels: [backend, testing]
stories: [7, 8]
---

## Context

`spec.md`'s Solution ("The pinning test, precisely") and Decisions taken. This
records the epic's defect — a username unique only within a Tenant is looked
up across all of them — on `main`, before `globally-unique-usernames` removes
it. It depends on `second-tenant-login-fixture` because it calls
`managerLoginInAnotherTenant`, added there, to create the collision.

The epic's feature line predicts the colliding username "authenticates
against whichever row is found first". The code makes that doubtful:
`UserRepository.findByUsername` is a Spring Data derived query returning
`Optional<User>`, and a derived `Optional` query matching two rows raises an
incorrect-result-size failure rather than picking one. **Do not assume either
outcome.** The implementer runs the test once, observes the real response,
and pins that — recording in the ticket's result which of the two it was, so
the epic's wording can be corrected if needed.

## Acceptance criteria

- [ ] A test, in its own test class — separate from the class holding `second-tenant-login-fixture`'s two surviving tests, so `globally-unique-usernames` deletes a file rather than editing a surviving one — creates a username that exists in both the seeded Tenant (the seeded Manager's own username) and a second Tenant, via `managerLoginInAnotherTenant(MANAGER_USERNAME, <a different password>)`, then calls `POST /api/auth/login` with that username and the second Tenant's password.
- [ ] The test asserts the concrete response actually observed by running it, and only that response — satisfied by either: (a) the response is 200 and the token's Tenant id (via the helper from `second-tenant-login-fixture`) is not the second Tenant's id, or (b) the response is a non-2xx failure. Whichever it is, the assertion pins exactly that outcome and no other. In the same test run, a control sign-in through `managerLoginInAnotherTenant` with a username that collides with nothing signs in 200, so a non-2xx result for the colliding username can only be attributed to the collision itself, not to a mistyped password or a broken fixture call.
- [ ] The test's name states the property, not a mechanism: a colliding username does not sign the caller in to their own (the second) Tenant — never "picks the first row" or similar.
- [ ] The test asserts nothing beyond the defect itself: no assertion of which row wins, no assertion of an error message's text, nothing about `findByUsername`'s signature or the exception type thrown.
- [ ] A comment or Javadoc on the test states both the epic's prediction ("whichever is found first") and what was actually observed when this ticket was implemented.
- [ ] A comment or Javadoc on the test names `globally-unique-usernames` as the feature that deletes and replaces it, and states its replacement's assertion: creating a login whose username is already taken in another Tenant is refused.
- [ ] `mvn -f backend/pom.xml verify` (`JAVA_HOME` on JDK 21) stays green; the diff remains entirely under `backend/src/test`, no `src/main`, no migration, V55 still unused.

## Tests

- **Seam:** the existing HTTP API seam — `IntegrationTest` subclass, `POST /api/auth/login` through MockMvc against real Postgres, same as `AuthLoginTest` and `second-tenant-login-fixture`'s tests.
- Cases:
  - Observe-and-pin: the seeded Manager's username, created a second time in a second Tenant with a different password via `managerLoginInAnotherTenant`, signed in with that second Tenant's password — the single case this ticket exists for. There is no "happy path" variant to add; a clean (non-colliding) sign-in is already `second-tenant-login-fixture`'s case.
- This is a bug-demonstrating ticket, not a bug-fix one: the "reproduces the bug and fails before the fix" rule from `docs/agents/ticket-critic.md` R5 does not apply the usual way round — there is no fix in this feature, so the test is written to pass on `main` by pinning whatever `main` does, not to fail.

## Regression

- No existing test is changed by this ticket. `AuthLoginTest`'s assertions about the seeded Manager's own username signing in successfully in the seeded Tenant are unaffected: each test method runs in its own rolled-back transaction, so the second Tenant's colliding row this ticket creates never persists into another test.
- `uq_users_tenant_username` (V1 migration) is the constraint that permits this collision (it is scoped to a Tenant, not global) — nothing here changes it, and no existing test asserts against it.
- This test is itself the thing at risk from the *next* feature, by design: `globally-unique-usernames` is expected to delete and replace it (its own Javadoc says so), not adapt it — flagged here so a future ticket-writer or merger does not mistake its deletion for an unreviewed regression.

## Observability

N/A — test-only change; nothing here runs in a deployed environment for anything to observe.
