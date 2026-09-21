---
id: tenant-scoped-sign-in
title: Operate a second tenant safely
status: planned
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

## Reworked

## Later

- A SuperAdmin surface for creating and managing tenants in-app.
- Choosing or being routed to a tenant at sign-in (subdomain or a field),
  needed only if one person is ever to belong to two tenants.
