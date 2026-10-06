---
id: superadmin-where-the-login-lives
type: question
status: open
blocks: [super-admin-signs-in]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

Every login in the app belongs to exactly one Tenant. Where does a **SuperAdmin's** login live?

- **A hidden "Operator" Tenant** that holds only SuperAdmins and is never shown in the Tenant list.
- **A login with no Tenant at all.**

**Recommendation:** The hidden Operator Tenant. Sign-in and every query keep working unchanged, and a mistake would show an empty Tenant rather than someone else's data. A login with no Tenant changes the database rule, the sign-in token and every query, and it's costly to undo.

Spec: `docs/features/super-admin-signs-in/spec.md`, Open questions 1.

## Blocks

`super-admin-signs-in` (and the rest of `tenant-administration` after it).

## Meanwhile

The loop works on whatever else is unblocked.

## Answer

