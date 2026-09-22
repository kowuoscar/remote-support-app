---
id: login-lifecycle
title: Keep a login working over time
status: in-progress
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

- [ ] `self-service-password-change` — a signed-in user of any role changes their own password, proving their current one, and signs in again with the new one.
- [ ] `manager-resets-a-password` — a Manager sets a new password for an Agent or a Tester in their own Tenant who cannot sign in, and hands it over out of band.
- [ ] `deactivate-a-login` — a login can be switched off and back on; a deactivated one is refused at sign-in while its user row, and everything hanging off it, stays intact.

## Reworked

The exploration changed the order and put a prefactor inside feature two.

**There is no notion of an enabled or disabled login anywhere.** `User`
carries id, tenant, username, password hash, role, a nullable agent link and
`created_at` — nothing else (`V1__create_tenants_and_users.sql:11-22`). And
`AppUserPrincipal` overrides none of `isEnabled()`,
`isAccountNonLocked()`, `isCredentialsNonExpired()` or
`isAccountNonExpired()`, so Spring Security's permissive defaults are in
force. Deactivation is therefore a new concept end to end — a column, the
`UserDetails` wiring, and a refusal at sign-in — which is why it is the last
feature rather than the first: the other two need none of it.

**Self-service change goes first because it needs nothing new.** It acts on
the caller's own user row, reached from the authenticated principal, so it
crosses neither of the two problems below.

**The two login paths are not one.** An Agent's login is written by
`AgentLoginService` (`.../web/AgentLoginService.java:62`), while a Tester's
password is hashed inline in the controller
(`.../web/TesterController.java:74`). A Manager resetting either would mean
writing the same thing twice, so `manager-resets-a-password` prefactors the
password write into one place first.

**There are no guard classes for Agent or Tester administration.** Access is
role-based in `SecurityConfig.java:189`, where `/api/agents/**` and
`/api/clients/**` simply require `ROLE_MANAGER`. The existing guard classes
govern fleets, contracts and the carrier catalog, not people. A reset needs
one, and `manager-resets-a-password` creates it.

**Deactivation cannot be deletion**, and the spec for it must say so: four
tables hold foreign keys into `users` — `testers.user_id`,
`requests.raised_by_user_id`, `requests.decided_by_user_id` and
`agent_standing_amounts.set_by_user_id` — and V16's
`uq_users_one_login_per_agent` partial index means a switched-off Agent login
still occupies its Agent's one slot.

**Found while delivering `self-service-password-change`, 2026-09-22 — read this
before specifying `deactivate-a-login`.** `ChangePasswordService` verifies the
caller's current password with `PasswordEncoder.matches` directly rather than
through `AuthenticationManager`. That is exactly equivalent to sign-in **today**,
and only because `AppUserPrincipal` leaves every `UserDetails` account-status
flag at its default `true`, so `DaoAuthenticationProvider`'s extra checks are
no-ops. The moment `deactivate-a-login` adds an enabled flag and wires
`isEnabled()`, this call site diverges from sign-in **silently**: a deactivated
Login would be refused at sign-in but could still change its own password. No
test will catch that, because nothing today can express a disabled account.
`deactivate-a-login` owns closing it.

Left for `deactivate-a-login`'s spec to settle, since it is a *what* the user
will notice rather than something the cut decides: whether deactivation takes
effect immediately for a session already holding a valid JWT, or only when
that token expires. `CallerIdentityResolver` re-resolves the caller on every
request but checks no account state, so either answer is reachable.

## Later

- Changing a login's email address after creation — the one item of the
  original deferral the human left out of scope at init.
- Invitation emails or any outbound email; forcing a password change on first
  sign-in (`docs/features/agent-login-on-creation/spec.md:40,43`).

