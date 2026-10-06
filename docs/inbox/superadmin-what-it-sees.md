---
id: superadmin-what-it-sees
type: question
status: open
blocks: [super-admin-signs-in]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

What may a SuperAdmin see of a Tenant?

- **Only the list:** e.g. "Acme Ltd — 2 Managers — created 3 Oct 2026", and in later features its Manager logins.
- **Also what's inside it:** Acme's Agents, Clients and invoices.

**Recommendation:** Only the list, and later its Manager logins, never a Tenant's work. Nothing in the epic needs more, and widening access later is cheap while narrowing it after the fact is not.

Spec: `docs/features/super-admin-signs-in/spec.md`, Open questions 3.

## Blocks

`super-admin-signs-in` (and the rest of `tenant-administration` after it).

## Meanwhile

The loop works on whatever else is unblocked.

## Answer

