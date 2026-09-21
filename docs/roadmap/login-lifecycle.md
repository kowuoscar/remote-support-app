---
id: login-lifecycle
title: Keep a login working over time
status: planned
journeys: [keep-a-login-working-over-time]
---

<!-- sdlc:template epic 1 -->

## Intent

Today a login is created once, with a password, and nothing about it can ever
change: no password can be changed or reset by anyone, and a login cannot be
turned off. An Agent who forgets their password, or one who leaves the
company, has no path at all. `agent-login-on-creation` deferred the whole of
this deliberately (`docs/features/agent-login-on-creation/spec.md:39-44`).

It runs second because it touches the same authentication code as
`tenant-scoped-sign-in`, which will already be in hand.

## Journeys

- **Keep a login working over time** → `exists`.

The proof that closes this epic: on `main`, a user changes their own password
and signs in with the new one; a Manager resets a locked-out Agent's password
and that Agent signs in; a Manager resets a Tester's password likewise; a
deactivated login is refused at sign-in while its Agent record and invoice
history remain intact and readable.

## Features

## Reworked

## Later

- Changing a login's email address after creation — the one item of the
  original deferral the human left out of scope at init.
- Invitation emails or any outbound email; forcing a password change on first
  sign-in (`docs/features/agent-login-on-creation/spec.md:40,43`).

