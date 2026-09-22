---
id: password-minimum-length
title: Hold every password-writing path to the same 8-character minimum
status: done
depends_on: [change-own-password-endpoint]
labels: [backend]
stories: [12, 13]
---

## Context

`spec.md` Solution: "The password rule" — one Bean Validation constraint (at
least 8 characters, no complexity classes) on every request DTO that
*writes* a password: the change-password request `change-own-password-endpoint`
adds, `AgentCreateRequest`, and `TesterCreateRequest`. `## Constraints` and
`## Decisions taken` are explicit that `LoginRequest` is not touched and
keeps `@NotBlank` alone — a shape rule at sign-in would lock out every
existing password shorter than 8 characters — and that the constraint is a
Bean Validation annotation on the DTO, never a service-layer re-check
(Backend rule 1). On the two creation paths the violation returns the
*existing* ordinary Bean Validation `400` those endpoints already produce for
a blank password (no new `code`); only the change-password path, which must
tell a rule violation from a wrong current password, carries one. This
ticket depends on `change-own-password-endpoint` because it adds the
constraint (and its `code`) to the DTO that ticket creates; the two creation
DTOs it also touches already exist today.

## Acceptance criteria

- [ ] `POST /api/me/password` with a new password shorter than 8 characters returns `400` with its own distinct `code` (not the wrong-current-password `code`), and the current password still signs in.
- [ ] `POST /api/me/password` with an exactly-8-character new password succeeds with `204` — the accepting side of the same boundary.
- [ ] Creating an Agent (`POST /api/agents`) with a 7-character temporary password is refused `400` in the same ordinary Bean Validation shape it already returns for a blank password, and an 8-character one is accepted.
- [ ] Creating a Tester with a 7-character temporary password is refused `400` the same way, and an 8-character one is accepted.
- [ ] `AuthLoginTest` is run and shown passing, unmodified — the minimum binds writes only, never sign-in.

## Tests

Seam: the existing HTTP API seam (`IntegrationTest` + MockMvc), same as
`change-own-password-endpoint`. Spec `## Testing decisions`: "A table-driven
`@ParameterizedTest` covers the boundary (7 refused, 8 accepted) rather than
copy-pasted per-path tests, per Backend rule 12." That table-driven test
lives in **one new test class** parameterized over the three write paths (the
change-password endpoint, Agent creation, Tester creation) plus the two
boundary lengths — not as added cases in any existing class — so this ticket
touches no pre-existing test file.

- Case: each of the three write paths × {7 chars refused, 8 chars accepted}.
- Case: the change-password path's 7-char refusal carries its own `code`, distinct from the wrong-current-password `code` `change-own-password-endpoint` established.
- Case: the two creation paths' 7-char refusal is the same shape as their existing blank-password `400` (no new `code` introduced there).
- Case: `AuthLoginTest`'s full existing suite, run as part of the same `mvn verify`, passes with an empty diff to that file.

## Regression

- `AuthLoginTest` must keep passing, and the spec requires it be shown untouched — cited here because a length rule leaking into sign-in is exactly the regression this ticket must not cause.
- `AgentApiTest`'s existing blank-password case (`creatingAnAgentWithoutAPasswordIsRejectedAndCreatesNoAgent`) and every other existing test in `AgentApiTest`/`TesterApiTest` keep passing unmodified — this ticket adds no method to either file; its new coverage lives entirely in a new test class (see `## Tests`).
- No existing test file is modified by this ticket, so there is nothing for `sdlc-test-guard` to flag.

## Observability

N/A — this ticket adds a validation constraint, not a new code path with its
own audit trail; the two creation paths' existing audit lines and the change
endpoint's `PASSWORD_CHANGED` line (from `change-own-password-endpoint`) are
unaffected.
