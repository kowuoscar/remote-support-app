---
id: superadmin-first-one
type: question
status: open
blocks: [super-admin-signs-in]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

How does the **first SuperAdmin** come to exist?

- **(a) Seeded by the database setup.** It's created as `superadmin@example.com` with a documented password you change at first sign-in, just like `manager@example.com` is today.
- **(b) Created at server start-up** from an email and password set in the server's environment.
- **(c) A one-off command** run on the server.

**Recommendation:** (a), the database seed. It's how every seeded login exists today, it needs no new deployment step, and Change password already works for every role. Choose (b) if a documented password in production, until it's changed, is unacceptable to you.

Spec: `docs/features/super-admin-signs-in/spec.md`, Open questions 2.

## Blocks

`super-admin-signs-in` (and the rest of `tenant-administration` after it).

## Meanwhile

The loop works on whatever else is unblocked.

## Answer

