---
id: approve-super-admin-signs-in
type: approval
status: open
blocks: [super-admin-signs-in]
created: 2026-10-08
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for a SuperAdmin signing in?
`docs/features/super-admin-signs-in/spec.md`.

The first step of creating Tenants in the product. Every deployment gets a
SuperAdmin login, `superadmin@example.com`, from the database setup. They
sign in on the normal sign-in page and land on their own console. The console
shows one list of Tenants, for example "Acme Ltd · 2 Managers · created 3 Oct
2026". They can change their password and sign out. They can't open any
Manager, Agent or Client page, and nobody else can open theirs.

These are choices made without you; approving accepts them:
1. The console is read-only for now: there's no "New Tenant" button and no
   Tenant detail page. Creating a Tenant and managing its Managers are the
   next features of the epic.
2. There's exactly one SuperAdmin, and no screen to add another.
3. The Manager count includes deactivated Manager logins.
4. Tenants are sorted by name, with no paging, since there are only a
   handful.
5. A SuperAdmin who calls a Tenant's address gets "forbidden", not "not
   found".

## Recommendation

Approve. Your three answers from today are built in, and nothing here is
hard to undo except the Operator Tenant you chose.

## Blocks

`super-admin-signs-in`, and the rest of `tenant-administration` after it.

## Meanwhile

The loop moves on to the research for the package layout.

## Answer
