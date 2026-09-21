---
id: tenant-scoped-sign-in
title: Operate a second tenant safely
status: in-progress
journeys: [operate-a-second-tenant-safely, sign-in]
---

<!-- sdlc:template epic 1 -->

## Intent

A second customer company is expected soon. Sign-in looks a username up
across **all** tenants, although usernames are only unique **within** one —
so the day a second tenant exists, a username present in both authenticates
against whichever row is found first. `agent-login-on-creation` recorded this
as a known limitation, out of scope at the time, that "must be addressed
before a second tenant exists"
(`docs/features/agent-login-on-creation/spec.md:152`).

This runs first because it is a correctness defect, and every feature that
lands on top of it is more code to re-audit later.

Scope is the fix and the audit, not a new surface: tenants continue to be
created directly in the database, and the SuperAdmin role keeps having no
screens. Decided with the human at init.

## Journeys

- **Operate a second tenant safely** → `exists`: with two tenants seeded and
  a username deliberately present in both, each user signs in to their own
  tenant and sees only their own tenant's data.
- **Sign in** stays `exists`, with its known defect gone.

The proof that closes this epic: an end-to-end walkthrough on `main` with two
tenants seeded, playing the colliding-username case, plus an audit naming
every remaining single-tenant assumption found and what was done about each.

## Features

- [x] `second-tenant-test-seam` — the suite can seed a second Tenant and sign in against it, and a test pins today's wrong behaviour: a username present in two Tenants is refused **401 Unauthorized**, indistinguishable from a wrong password.
- [x] `globally-unique-usernames` — a username is unique across the whole deployment, rejected at creation if already taken in any Tenant, so sign-in resolves to exactly one user; plus the audit of the remaining single-tenant assumptions.

## Reworked

The epic was written expecting the fix to be a scoped repository query. The
code says otherwise, and it changed the cut.

`JwtService.issueToken` already puts `tenantId` in the token, and
`JwtAuthenticationFilter` gives every authenticated request its tenant from
that claim — controllers read `principal.tenantId()` and never look it up. So
the tenant boundary is sound *after* sign-in; the entire defect is the one
unscoped lookup on the way in, `UserRepository.findByUsername(String)` called
from `AppUserDetailsService.loadUserByUsername`.

That method receives only a username, so it cannot scope by a tenant it has
not been told. With a tenant selector at login ruled out, the human chose at
planning to make usernames **globally unique** instead: the lookup stays by
username and becomes unambiguous, no login surface changes, and the cost —
one person cannot hold logins in two Tenants — is a need the human has
already said does not exist. The schema keeps `uq_users_tenant_username`
(`V1__create_tenants_and_users.sql:18`) and gains a global unique index above
it.

Nothing can prove any of this today: `IntegrationTest` hardcodes the one
seeded Tenant `11111111-1111-1111-1111-111111111111`, as do `DemoDataLoader`
and the seed migrations, and no helper creates a second one. The test seam is
therefore the first feature rather than a detail of the fix — the human chose
this cut over folding the two together.

**Corrected by observation, 2026-09-21.** This epic and that feature line both
predicted the colliding username would authenticate "against whichever row is
found first". It does not. `pin-colliding-username-sign-in` ran it and
observed **401 Unauthorized with an empty body**: `findByUsername`'s derived
`Optional` query fails on two matching rows, and Spring Security turns that
into an ordinary authentication failure inside the filter chain — no exception
reaches the controller, so it is not a 5xx either.

That makes the defect a **lockout, not a cross-tenant leak**. No caller can
reach another Tenant's data through it; the affected user simply cannot sign
in, and cannot tell that from a mistyped password. The reason for scheduling
this epic first — that the fix gets more expensive as code lands on top of it
— is unaffected, but the severity is lower than the epic assumed, and
`globally-unique-usernames` should be written against the real symptom.

Carried into `globally-unique-usernames` from this exploration:
`UserRepository.findByAgentId` and `CallerIdentityResolver`'s
`findById(principal.userId())` are also unscoped. Both are safe today (a UUID
does not collide), but they belong in the audit rather than being rediscovered
later.

## Later

- A SuperAdmin surface for creating and managing tenants in-app.
- Choosing or being routed to a tenant at sign-in (subdomain or a field),
  needed only if one person is ever to belong to two tenants.
