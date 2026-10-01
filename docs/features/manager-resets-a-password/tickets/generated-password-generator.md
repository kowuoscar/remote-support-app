---
id: generated-password-generator
title: Generate a password and assign it through the password write
status: ready-for-agent
depends_on: [one-password-write]
labels: [enabler, backend]
stories: []
---

## Context

Enables `new-logins-generate-password-api` and `reset-an-agents-password-api`. Adds the
**password generator** (in `security`, one `SecureRandom`) and the password write's second
operation, "give this Login a freshly generated password and return it in the clear", exactly
as the spec's `## Solution` → "The generated password" defines: three hyphen-joined groups of
four from `abcdefghijkmnpqrstuvwxyz23456789`; when the Login already has a hash, redraw on
`PasswordEncoder.matches` up to three draws, then `IllegalStateException`; a Login with no
hash skips the check. Nothing calls it yet; no route changes.

Modules touched: `security`.

## Acceptance criteria

- Every generated password matches `^[a-km-np-z2-9]{4}(-[a-km-np-z2-9]{4}){2}$` and is at
  least `PasswordPolicy.MIN_LENGTH` characters long.
- Given a Login whose hash matches the first candidate, the generating operation discards it,
  sets and returns the second candidate; the stored hash matches the returned password and not
  the old one.
- Three candidates in a row that match the current hash raise `IllegalStateException`.
- For a Login with no hash, the first candidate is used without a `matches` check.

## Tests

Seam: plain unit tests, which the spec's `## Testing decisions` explicitly allows here (no
Spring context adds anything; Backend rule 6). No mocks of `PasswordEncoder`.

- Generator: many draws (for example 10,000) all match the regex and are at least
  `MIN_LENGTH` long.
- Generating operation with a scripted generator and a real `BCryptPasswordEncoder`:
  first candidate equals the current password, so the second is set and returned; three
  matching candidates raise `IllegalStateException`; a `User` with no hash takes the first
  candidate.

## Regression

N/A — new classes plus a new operation on the password write; no existing call site uses
them and no existing test changes. `AuthLoginTest` and `ChangeOwnPasswordApiTest` still guard
the shared write.

## Observability

N/A — no event here. The generated value must never reach a log line; the unit tests do not
log it and the operation returns it only to its caller.
