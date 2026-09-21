---
id: tenant-administration
title: Create and manage Tenants in the product
status: proposed
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

## Journeys

- **Administer the Tenants themselves** → `exists`: a SuperAdmin creates a
  Tenant and its first Manager in the product, and that Manager signs in.

The journey does not exist in `docs/journeys.md` yet and should be added as
`wanted` only if the human places this epic.

## Features

## Reworked

## Later
