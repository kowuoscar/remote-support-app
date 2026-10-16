---
id: approve-super-admin-signs-in
type: approval
status: open
blocks: [super-admin-signs-in]
created: 2026-10-15
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the revised spec for a SuperAdmin signing in?
`docs/features/super-admin-signs-in/spec.md`.

Your amendment ("everything, every Tenant") was too big for one feature, so
the epic now has three steps:

1. **This feature:** the SuperAdmin `superadmin@example.com` signs in on the
   normal page and lands on their own console. It lists every Tenant with
   its Manager count and creation date. They can change their password and
   sign out.
2. `super-admin-enters-a-tenant`: open any Tenant as its Manager and do
   everything a Manager can, with a banner naming the Tenant and a way back.
3. `super-admin-opens-agent-and-client-pages`: inside a Tenant, open the
   Agent pages as any Agent and the Client pages as any Tester.

Every action is audit-logged under the SuperAdmin's own login. The design
for steps 2 and 3 is drafted and kept for their specs.

What changed from the version you amended: the SuperAdmin is no longer
fenced out of Tenants. Nothing is built that the next two steps would have
to undo. Your choices 1–4 stay as approved.

One consequence of "everything", recorded rather than asked: one person can
send an Agent's invoice as that Agent, then approve it as the Manager.

## Recommendation

Approve. It is the front door; entering Tenants follows straight after.

## Blocks

`super-admin-signs-in`, and the rest of `tenant-administration` after it.

## Meanwhile

The loop drafts the side lists and tabs spec.

## Answer
