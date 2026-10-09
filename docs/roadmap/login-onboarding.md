---
id: login-onboarding
title: Hand a new login over, and change a login's email
status: proposed
journeys: [keep-a-login-working-over-time]
---

<!-- sdlc:template epic 1 -->

## Intent

Promoted from `login-lifecycle`'s `## Later` at its closure (2026-10-06),
rather than dropped. That epic made a login changeable over time: its own
password changed, a reset by a Manager, switched off and on. Three things
were deliberately left out:

- **Changing a login's email after creation.** The human left this out of
  scope when the project was initialised. Today a mistyped email can only be
  fixed by creating a new login, which an Agent can't have twice.
- **Forcing a password change on first sign-in.** A Manager hands over a
  generated password out of band (`manager-resets-a-password`). Nothing asks
  the person to replace it with one of their own.
- **Invitation emails, or any outbound email.** The product sends no email.
  Sending one means adopting a mail service. That is escalation case 2 (a
  hosted service), so it is the human's call before anything is specced.

`proposed`, not `planned`: whether any of this is worth building depends on
how often logins are created and handed over in practice. The human places
it, or drops it with the reason written here.

## Journeys

- **Keep a login working over time** → `exists` once `login-lifecycle` closes.
  This epic would extend it, not open a new journey.

## Features

- [ ] `change-a-logins-email` — a Manager corrects an Agent's or a Tester's sign-in email; the old email stops working and the new one signs in with the same password.
- [ ] `password-change-on-first-sign-in` — a login created or reset by a Manager must choose its own password the first time it signs in.

Invitation emails get no feature line until the human decides whether the
product should send email at all.

## Later

- Invitation and password-reset emails, once a mail service is chosen.
