---
id: tenant-administration
title: Create and manage Tenants in the product
status: in-progress
journeys: [administer-the-tenants-themselves]
---

<!-- sdlc:template epic 1 -->

## Intent

Promoted from `tenant-scoped-sign-in`'s `## Later` at its closure, rather than
dropped.

A Tenant is created today by inserting rows directly into the database. That
was the right scope for the epic that just closed — its job was correctness,
and the human explicitly chose the sign-in fix over building a SuperAdmin
surface. But the deployment is now genuinely safe for a second Tenant, and the
only way to add one is a hand-written `INSERT`.

`PRODUCT.md` records that the SuperAdmin role exists in the auth model with no
screens of its own. This epic is what would give it some: creating a Tenant,
seeding its first Manager, and seeing what Tenants exist.

`proposed`, not `planned`: whether this is worth building depends on how often
a Tenant is actually created, which only the human knows. If the answer is
"twice a year, by me, with psql open anyway", the right decision is to drop
this and say so.


Added by the human on 2026-09-30: **resetting a Manager's password is the
SuperAdmin's job**, and it belongs here. `manager-resets-a-password` lets a
Manager reset only Agents and Testers, so until this epic lands, a locked-out
Manager still needs the database.

## Journeys

- **Administer the Tenants themselves** → `exists`: a SuperAdmin creates a
  Tenant and its first Manager in the product, and that Manager signs in.

The journey does not exist in `docs/journeys.md` yet and should be added as
`wanted` only if the human places this epic.

## Features

- [ ] `super-admin-signs-in` — a SuperAdmin signs in to a console of their own and sees the Tenants that exist, each with its Manager count; how the first SuperAdmin comes to exist, and what Tenant (if any) a SuperAdmin belongs to, are settled in its spec.
- [ ] `create-a-tenant-with-its-first-manager` — a SuperAdmin creates a Tenant by name together with its first Manager login, whose generated password is shown once; that Manager signs in to an empty Tenant and can start working.
- [ ] `super-admin-resets-a-managers-password` — a SuperAdmin resets a locked-out Manager's password (generated, shown once), reusing the password write the Agent and Tester resets use.
- [ ] `super-admin-deactivates-a-managers-login` — a SuperAdmin switches a Manager's login off and on, with the refusals `deactivate-a-login` built.

The first feature closes the journey's front door; the second makes it
playable end to end ("a SuperAdmin creates a Tenant and its first Manager in
the product, and that Manager signs in"). The last two finish the Manager
login lifecycle that `manager-resets-a-password` and `deactivate-a-login`
left to this epic.

## Reworked

Explored on 2026-10-06 (`main`):

- **`SUPER_ADMIN` exists only as an enum value.** It is in `Role` and V1's
  check constraint, but:
  - no SuperAdmin user is seeded;
  - `SecurityConfig` has no matcher for it;
  - the frontend sends the role to `/login` (`frontend/lib/auth/role.ts:4,14-16`);
  - no test signs one in.

  The whole role is new work, which is why the console comes first.
- **Every login belongs to exactly one Tenant.** `users.tenant_id` is NOT NULL
  (V1:12-13), the JWT requires a `tenantId` claim (`JwtService.java:24-47`),
  and every query is scoped by it. So a SuperAdmin either lives in a
  dedicated "system" Tenant or breaks that invariant. That is hard to undo,
  and `super-admin-signs-in`'s spec settles it (an escalation if it is a
  *what*).
- **The first SuperAdmin must come from somewhere.** There is no screen to
  create one, so a migration seed or a start-up property would have to. That
  is a deployment decision for the same spec.
- **A minimal Tenant is just a name plus a first Manager.** `Tenant` holds
  only id, name and created_at. Agents carry their own country and currency,
  and the Carrier catalog is optional (`OtherTenantFixture` builds a working
  second Tenant from exactly this). Creating one needs no other seed data.
- **Usernames are globally unique** (V55), so a new Manager's email is
  checked against every Tenant.
- **The Manager-login guards already name this epic.**
  `LoginAdministrationGuard` refuses a Manager target "by design (a
  SuperAdmin concern)". Resetting and deactivating a Manager reuse
  `PasswordWrite`, `PasswordGenerator`, `AdministeredLoginLookup`'s pattern
  and the deactivation doors, with a SuperAdmin guard of their own.

## Later` at its closure, rather than
dropped.

A Tenant is created today by inserting rows directly into the database. That
was the right scope for the epic that just closed — its job was correctness,
and the human explicitly chose the sign-in fix over building a SuperAdmin
surface. But the deployment is now genuinely safe for a second Tenant, and the
only way to add one is a hand-written `INSERT`.

`PRODUCT.md` records that the SuperAdmin role exists in the auth model with no
screens of its own. This epic is what would give it some: creating a Tenant,
seeding its first Manager, and seeing what Tenants exist.

`proposed`, not `planned`: whether this is worth building depends on how often
a Tenant is actually created, which only the human knows. If the answer is
"twice a year, by me, with psql open anyway", the right decision is to drop
this and say so.


Added by the human on 2026-09-30: **resetting a Manager's password is the
SuperAdmin's job**, and it belongs here. `manager-resets-a-password` lets a
Manager reset only Agents and Testers, so until this epic lands, a locked-out
Manager still needs the database.

## Journeys

- **Administer the Tenants themselves** → `exists`: a SuperAdmin creates a
  Tenant and its first Manager in the product, and that Manager signs in.

The journey does not exist in `docs/journeys.md` yet and should be added as
`wanted` only if the human places this epic.

## Features

## Reworked

## Later
