---
id: change-own-password-endpoint
title: A signed-in user changes their own password through a new /api/me endpoint
status: done
depends_on: []
labels: [backend]
stories: [1, 2, 3, 6, 7, 8, 9, 10, 17, 18, 22]
---

## Context

`spec.md` Solution: "The endpoint is the first write acting on the caller"
(`POST /api/me/password`, its own controller beside `MeController`, no
`SecurityConfig` change — `/api/me/**` already falls through to
`.anyRequest().authenticated()`), "Request and response contract" (`204` on
success, every refusal `400` with `{code, message}`, never `401`/`403`),
"Verifying the current password" (`PasswordEncoder.matches` directly, never
`AuthenticationManager`), "The service, and what it deliberately does not do"
(a new service beside `AgentLoginService`/`TesterLoginService`, neither of
which is touched), "Schema" (no Flyway migration — `users.password_hash`
already exists) and "Observability" (one `PASSWORD_CHANGED` audit line,
subject and actor both the caller, no failed-attempt logging). `## Decisions
taken` settles: new classes go in the existing `web` package; a new password
identical to the current one is its own refusal with its own code; the
confirm-new field is a frontend-only concern and plays no part here. This
ticket does **not** add the 8-character minimum — that is
`password-minimum-length`, which depends on this ticket because it adds a
constraint to the request DTO this ticket creates.

## Acceptance criteria

- [ ] For a Manager Login (linked to neither an Agent nor a Tester), an Agent Login, and a Tester Login — each created by the test, never a seeded credential — `POST /api/me/password` with the correct current password and a new one returns `204` with an empty body.
- [ ] After each of those changes, signing in with the new password returns a token, and signing in with the old password is refused `401`.
- [ ] `POST /api/me/password` with a wrong current password returns `400` (never `401`, never `403`) carrying a distinct `code`, and a subsequent sign-in with the unchanged old password still succeeds.
- [ ] `POST /api/me/password` with a new password identical to the current one returns `400` with its own distinct `code`, and the old password still signs in.
- [ ] `POST /api/me/password` with no JWT is refused, and a valid JWT of each of the three roles is not refused on role grounds.
- [ ] A successful change writes exactly one audit line, `action=PASSWORD_CHANGED entity=User`, naming the subject user, the actor and the tenant, with no password, hash or password length anywhere in the log output; a wrong-current-password attempt writes no such line.
- [ ] `git diff` touches no Flyway migration, no `SecurityConfig` matcher, and neither `AgentLoginService.create` nor `TesterLoginService.create`.

## Tests

Seam: the existing HTTP API seam, `IntegrationTest` + MockMvc against real
Postgres (spec `## Testing decisions` — "One seam, and it already exists").
Prior art named by the spec: `AuthLoginTest` (the sign-in assertions this
feature's proof reuses, including the 401-on-wrong-password shape) and
`AgentLoginApiTest` (the coded-`400`/`409` assertion shape and the fixture
style for making a Login to act on). No unit test of the new service — it
has one branch and a Spring context behind it (Backend rule 6).

- Case: each of the three roles (Manager, Agent, Tester), on a Login the test itself creates, changes its password and the change is proven through `IntegrationTest.loginAs` — new password in, old password refused — never by reading `password_hash`.
- Case: wrong current password — its own test method (not chained with a success case, per `docs/agents/implementer-notes.md`'s MockMvc-transaction gotcha) — asserts `400`, the expected `code`, and that `loginAs` with the old password still works.
- Case: new password identical to current — `400` with its own distinct `code`, old password still works.
- Case: no `Authorization` header — refused; a valid header of each role — not refused on role grounds.
- Case: grep the log output of a successful change for one `PASSWORD_CHANGED` line naming user/actor/tenant, and assert no line anywhere contains the password or a hash; a failed attempt produces no such line.

## Regression

- `AuthLoginTest` passes unmodified — `LoginRequest` keeps `@NotBlank` only, and this ticket adds no constraint that could change what it accepts.
- No existing test file is modified by this ticket: its coverage lives in a new test class exercising the new endpoint, so `sdlc-test-guard` has nothing pre-existing to flag here.
- No seeded credential (`manager@example.com`, `agent@example.com`, `tester@example.com`) has its password changed by any new test — each test creates its own Login (spec `## Constraints`), since the backend suite's rollback does not protect against a change made through a real transaction commit path shared with other tests in the same run.
- Neither `AgentLoginService.create` nor `TesterLoginService.create` is refactored to share code with the new service — the epic's `manager-resets-a-password` owns that prefactor, not this ticket.

## Observability

One audit line on success, in the existing `AuditLog` vocabulary:
`action=PASSWORD_CHANGED entity=User entityId=<userId> actorUserId=<same> tenantId=…`
(subject and actor are the same user here; both are written so
`manager-resets-a-password` can reuse the exact line shape later). No
password, hash, or password length ever appears in it, and a failed
verification is not audited.
