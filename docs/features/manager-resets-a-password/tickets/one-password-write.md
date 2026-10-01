---
id: one-password-write
title: Route every password write through one component
status: in-progress
depends_on: []
labels: [enabler, backend]
stories: [30]
---

## Context

Prefactoring, behaviour-preserving. Enables `generated-password-generator` (which
adds the generating operation to this component), and through it every ticket that
generates a password. See the spec's `## Solution` → "Prefactoring: one password
write, re-judged".

Add one small component, **the password write**, in the `security` package, with a
single operation: set this Login's password to this raw value (encode with the one
`PasswordEncoder` bean, set `User.passwordHash`). `AgentLoginService.create`,
`TesterLoginService.create` and `ChangePasswordService.changeOwnPassword` call it
instead of encoding themselves. Each keeps its username pre-check, flush, conflict
mapping, `PasswordEncoder.matches` verification and `PASSWORD_UNCHANGED` rule. The
8-character minimum stays on `ChangePasswordRequest`; the write does not re-validate.

Modules touched: `security`, `web`.

## Acceptance criteria

- Creating an Agent with a Login, giving an existing Agent a Login, adding a Tester
  and changing one's own password all behave exactly as before: same statuses, same
  bodies, same sign-in outcomes.
- In production sources, `passwordEncoder.encode` (or `.encode(` on a `PasswordEncoder`)
  appears in exactly one class, the password write.
- The password write's raw-value operation, given a `User` and a raw password, leaves a
  hash that `PasswordEncoder.matches` accepts for that raw password.

## Tests

Seam: the HTTP API seam (`IntegrationTest` + MockMvc, real Postgres), per the spec's
`## Testing decisions`.

- The existing suites pass **unedited** at this step: `AgentLoginApiTest`,
  `TesterApiTest`, `TesterUsernameConflictApiTest`, `ChangeOwnPasswordApiTest`,
  `PasswordMinimumLengthApiTest`, `AuthLoginTest`. They are the proof.
- One new unit test of the raw-value operation with a real `BCryptPasswordEncoder`
  (Backend rule 6: no Spring context needed): the set hash matches the raw value and
  does not match a different one.
- Walkthrough step 13's grep for `encode` in one class is run by the implementer.

## Regression

At risk: every password write path (Agent creation, give-an-Agent-a-Login, Tester
creation, self-service change). Guarded by `AgentLoginApiTest`, `TesterApiTest`,
`TesterUsernameConflictApiTest`, `ChangeOwnPasswordApiTest`,
`PasswordMinimumLengthApiTest` and `AuthLoginTest`, which this ticket must not edit.
No existing test is expected to change.

## Observability

N/A — behaviour-preserving refactor; no new event, and no log line may gain a password
or hash (spec `## Constraints`).
