---
feature: super-admin-signs-in
epic: tenant-administration
status: draft
date: 2026-10-06
---

<!-- sdlc:template spec 1 -->

# A SuperAdmin signs in

## Problem

The deployment can now safely hold a second Tenant, but a Tenant only comes
into being through a hand-written `INSERT`, and nobody can see in the product
which Tenants exist. The people who look after Tenants have no place in the
product at all, and when something goes wrong inside a Tenant they cannot
look at it, or fix it, without the database.

The SuperAdmin role exists only as a word in the auth model. `Role` has
`SUPER_ADMIN` and V1's check constraint allows it, but:

- no SuperAdmin Login exists, and nothing can create one;
- the backend's security configuration has no rule for the role, so a
  SuperAdmin token would fall through to the Manager-only `403`s and, on the
  routes open to any signed-in role, to per-resource guards that were never
  written with a SuperAdmin in mind;
- the frontend has nowhere to send one: `landingPathForRole` falls back to
  `/login`, so a SuperAdmin who signed in would land back on the sign-in page;
- `PRODUCT.md` says the role "has no dedicated screens this iteration";
- no test signs one in.

There is also a structural constraint. **Every Login belongs to exactly one
Tenant**: `users.tenant_id` is NOT NULL, the token always carries a `tenantId`
claim, and every query is scoped by it. A SuperAdmin looks after all Tenants,
so its own Login needs a home that keeps that invariant. The human chose a
hidden **Operator Tenant** (2026-10-08).

The human then widened the role (2026-10-14): **the SuperAdmin gets access to
everything, in every Tenant.** They open and change every page of every
Tenant (Manager, Agent and Client pages alike), as if they were a Manager of
all of them, and every action is recorded in the audit log under the
SuperAdmin's own Login. Three facts in the code make this a real piece of
work and not a flag:

- every Tenant-scoped query reads the Tenant from the token, so a token can
  only ever see one Tenant;
- the Agent Console and the Client Portal do not show "an Agent's" or "a
  Client's" pages, they show **the caller's own**: `CallerIdentityResolver`
  finds the Agent from the caller's Login and the Client from the caller's
  Tester, and the Tester-authored Request path reads the Tester straight from
  the caller's Login;
- every guard decides by role (`MANAGER`, `AGENT`, `TESTER`) and refuses any
  other.

This is the first of the epic's features and its front door. Creating a
Tenant, resetting a Manager's password and deactivating a Manager's Login
all need a SuperAdmin who can sign in and a console to put them in.

## Journeys

Advances `docs/roadmap/tenant-administration.md`, the **first** of its
features.

- **Administer the Tenants themselves** (not yet in `docs/journeys.md`; the
  epic adds it as `wanted` when placed). After this feature it is
  **`partial`**: a SuperAdmin signs in, sees the Tenants that exist and works
  inside any of them, but cannot create one yet.
  `create-a-tenant-with-its-first-manager` makes it play end to end.
- **Sign in** (`exists`, stays `exists`): it gains a fourth role. A SuperAdmin
  signs in with an email and a password and lands on their own console. Every
  other role is unchanged, and that is proven. The journey's wording "Any of
  the three roles" becomes "any of the four roles" at delivery.
- **Keep a login working over time** (`exists`, stays `exists`): a SuperAdmin
  changes their own password the same way every other role does.
- **Operate a second tenant safely** (`exists`, stays `exists`, wording
  changes at delivery): no Manager, Agent or Tester of one Tenant reaches
  another. The SuperAdmin is the one deliberate exception: they enter any
  Tenant, one at a time, and everything they do there is recorded under their
  own Login.
- **Every Tenant journey** (`Set up the operating picture` through `Keep a
  login working over time`) is unchanged for its own roles. A SuperAdmin can
  now play any of them inside any Tenant, as that Tenant's Manager, one of
  its Agents or one of its Testers.

## Goals / Non-goals

Goals:

- A SuperAdmin Login exists in every deployment from the moment this feature
  is migrated, without anyone writing SQL by hand.
- A SuperAdmin signs in on the same sign-in page as everyone else and lands
  on the **SuperAdmin Console**, a console of their own.
- The console shows the Tenants that exist: each one's name, its Manager
  count and the date it was created.
- From the console, a SuperAdmin **enters any Tenant as its Manager**, and
  sees and changes everything a Manager of that Tenant can.
- Inside a Tenant, a SuperAdmin **opens the Agent Console as any of its
  Agents** and **the Client Portal as any of its Testers**, and sees and
  changes everything that Agent or Tester can.
- Everything a SuperAdmin does inside a Tenant is recorded in the audit log
  under the SuperAdmin's own Login, with the Tenant it happened in.
- On every page inside a Tenant, the SuperAdmin can see which Tenant they are
  in and in which role, and has one action back to the SuperAdmin Console.
- A SuperAdmin changes their own password and signs out from the console, as
  every other role does.
- A Manager, Agent or Tester cannot reach the SuperAdmin Console or its API,
  and cannot enter another Tenant.
- Every existing role signs in and works exactly as before.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No creating a Tenant.** That is `create-a-tenant-with-its-first-manager`.
  The console offers no **New Tenant** button, not even a disabled one.
- **No renaming, archiving or deleting a Tenant.** Nothing in this feature
  writes to `tenants` beyond the migration.
- **No Tenant detail page** (choice 1, approved 2026-10-14). The Tenant row
  carries one action, **Open as Manager**; it is not a link to a page about
  the Tenant. A list of a Tenant's Manager Logins arrives with the reset and
  deactivation features.
- **No resetting a Manager's password, no deactivating a Manager's Login.**
  Those are the epic's third and fourth features. A SuperAdmin inside a Tenant
  as its Manager meets the same refusal a Manager does when they try to
  administer a Manager's Login.
- **No creating, listing, resetting or deactivating SuperAdmin Logins in the
  product.** There is exactly one way a SuperAdmin comes to exist: the
  database seed (human, 2026-10-08, choice 2 approved 2026-10-14).
- **No pages that span Tenants.** No combined Review Queue, invoice list,
  Fleet or totals across Tenants. A SuperAdmin works in one Tenant at a time,
  on that Tenant's own pages; the Tenant list is the only cross-Tenant view.
- **No acting as a particular Manager.** A SuperAdmin enters a Tenant *as its
  Manager*, not as one named Manager's Login. Agents and Testers are opened
  individually because their pages only exist for one Agent or one Tester.
- **No powers beyond the role entered.** Inside a Tenant the SuperAdmin can do
  exactly what a Manager, that Agent or that Tester can do there, no more: the
  same matchers, the same guards, the same refusals.
- **No changing anyone's own password or name from inside a Tenant.** The
  account menu's **Change password** and **Change name** stay on the
  SuperAdmin Console, where they act on the SuperAdmin's own Login.
- **No notice to a Tenant's people that a SuperAdmin entered.** No banner,
  e-mail or list on the Manager's side. What the SuperAdmin did is in the
  audit log, and wherever a page already names who did something.
- **No in-product audit log viewer.** The audit log stays the structured
  `AUDIT` log it is today.
- **No dashboard, charts or search on the console.** One list.
- **No change to how Managers, Agents and Testers sign in, what their tokens
  carry, or what they can see.**

## User stories

Signing in and the console

1. As a SuperAdmin, I want a Login that exists from the moment the product is deployed, so that I can get in without anyone writing SQL.
2. As a SuperAdmin, I want to sign in on the same sign-in page as everyone else, with my email and password, so that there is one front door to the product.
3. As a SuperAdmin, I want to land on a console of my own after signing in, so that I start from the list of Tenants rather than inside one of them.
4. As a SuperAdmin, I want the console to say it is the SuperAdmin Console and who I am signed in as, so that I know which hat I am wearing.
5. As a SuperAdmin, I want to see every Tenant that exists, by name, so that I know who is operating in this deployment.
6. As a SuperAdmin, I want each Tenant shown with its number of Managers, so that I can spot a Tenant with nobody able to run it.
7. As a SuperAdmin, I want each Tenant shown with the date it was created, so that I can tell an established Tenant from a new one.
8. As a SuperAdmin, I want the Tenants in a stable, predictable order, so that I find the one I mean without hunting.
9. As a SuperAdmin, I want a Tenant with no Managers shown with a count of 0, not left out, so that the list is complete.
10. As a SuperAdmin, I want the list to say so plainly when there are no Tenants, so that an empty page is not mistaken for a failure.
11. As a SuperAdmin, I want the place my own Login lives not to appear among the customer Tenants, and not to be enterable, so that the list is only the companies operating here.
12. As a SuperAdmin, I want to change my own password from the console, so that the password I was first given does not stay in use.
13. As a SuperAdmin, I want to sign out from the console, so that I can leave a shared machine safely.

Entering a Tenant as its Manager

14. As a SuperAdmin, I want an **Open as Manager** action on each Tenant in the list, so that I can get into any Tenant from one place.
15. As a SuperAdmin, I want to land on that Tenant's Manager Console after opening it, so that I see exactly what its Manager sees.
16. As a SuperAdmin inside a Tenant as its Manager, I want to open every Manager page of that Tenant (dashboard, Clients, Agents, Contracts and their Fleets, Requests and Pending Requests, Invoices and the Review Queue, Carriers, Stock), so that nothing of the Tenant is out of my reach.
17. As a SuperAdmin inside a Tenant as its Manager, I want to do everything its Manager can (create Clients, Agents, Testers and Contracts, build a Fleet, correct a unit, approve or reject a Request, set a standing amount, override, approve, send back and mark paid an invoice, reset or deactivate an Agent's or a Tester's Login), so that I can fix a problem myself rather than ask someone to.
18. As a SuperAdmin inside a Tenant, I want to see only that Tenant's data, never another Tenant's mixed in, so that what I change is the Tenant I meant.
19. As a SuperAdmin inside a Tenant, I want every page to show which Tenant I am in and in which role, so that I never mistake one Tenant for another or a Tenant for my own console.
20. As a SuperAdmin inside a Tenant, I want one **Back to SuperAdmin Console** action on every page, so that I can leave the Tenant and pick another.
21. As a SuperAdmin inside a Tenant, I want opening the SuperAdmin Console's address to take me out of the Tenant and back to the console, so that the address bar works as I expect.
22. As a SuperAdmin inside a Tenant, I want to sign out from there and be fully signed out, not dropped back on my console, so that leaving a shared machine is one action.

Opening the Agent Console and the Client Portal

23. As a SuperAdmin inside a Tenant as its Manager, I want an **Open as this Agent** action on each Agent's page, so that I can see the Agent Console exactly as that Agent sees it.
24. As a SuperAdmin inside a Tenant as its Manager, I want an **Open as this Tester** action on each Tester of a Client's page, so that I can see the Client Portal exactly as that Tester sees it.
25. As a SuperAdmin in the Agent Console as an Agent, I want every page that Agent has (dashboard, Fleet, Requests, Client Invoices, my invoice, Stock, Carriers) showing that Agent's own Contracts and data, so that I see what they see.
26. As a SuperAdmin in the Agent Console as an Agent, I want to do everything that Agent can (log a Request or a Fee, progress or cancel a Request, keep the Fleet accurate and correct a unit, edit and send a Client Invoice, send the Agent Invoice, maintain the Carrier catalog), so that I can finish a piece of work the Agent cannot.
27. As a SuperAdmin in the Client Portal as a Tester, I want every page that Tester has (dashboard, Fleet, Requests, Invoices) showing that Tester's Client, so that I see what they see.
28. As a SuperAdmin in the Client Portal as a Tester, I want to submit a Request on that Tester's Client's Contracts, so that I can raise something on the Client's behalf.
29. As a SuperAdmin, I want to open an Agent who has no Login, or whose Login is deactivated, so that a missing or switched-off Login never hides an Agent's work from me.
30. As a SuperAdmin, I want the banner in the Agent Console and the Client Portal to name the Agent or Tester I am acting as, so that I know whose pages I am on.

Recording and attribution

31. As the company, I want every change a SuperAdmin makes inside a Tenant recorded in the audit log under the SuperAdmin's own Login and that Tenant, so that it can always be told apart from the work of the Tenant's own people.
32. As the company, I want a SuperAdmin entering a Tenant recorded in the audit log, with the Tenant and the role entered (and the Agent or Tester, when one), so that every visit is traceable even when nothing is changed.
33. As a Manager, Agent or Tester of a Tenant, I want a Request the SuperAdmin logged or decided shown with the name the open question below settles, so that the record says who acted.

The fence

34. As the company, I want only a SuperAdmin able to enter a Tenant, so that a Manager, Agent or Tester can never reach another Tenant's data.
35. As the company, I want a SuperAdmin able to enter a Tenant only from their own signed-in session, so that being inside one Tenant is not a way to jump to another without passing the console.
36. As the company, I want Managers, Agents and Testers unable to open the SuperAdmin Console or call its API, so that seeing every Tenant stays with the SuperAdmin.
37. As the company, I want a SuperAdmin's time inside a Tenant to stop working the moment the SuperAdmin's own Login is deactivated or removed, so that a SuperAdmin's access can be closed in one place.
38. As a visitor who is not signed in, I want to be sent to the sign-in page when I open the SuperAdmin Console, so that it behaves like every other console.
39. As a SuperAdmin whose session has expired, I want to land on the sign-in page at my next page load, whether on the console or inside a Tenant, rather than see an empty list, so that what happened is clear.

Nothing else moves

40. As a Manager, Agent or Tester, I want sign-in, my console and my own password change to behave exactly as before, so that the new role carries no release risk.
41. As a Manager, I want my Tenant's data unchanged by this feature, so that nothing I own moves.

Usability and setup

42. As a SuperAdmin working by keyboard, I want to reach the Tenant list, **Open as Manager**, the change-password action, sign-out and **Back to SuperAdmin Console** without a mouse, with a visible focus ring, so that the console is usable without one.
43. As a SuperAdmin on a phone, I want the Tenant list and the inside-a-Tenant banner readable at the mobile breakpoint, so that I can check on a Tenant away from a desk.
44. As a developer or someone trying the demo, I want the SuperAdmin's seeded email and password documented next to the other seeded Logins, so that I can sign in as one locally.

## Solution

The three questions this spec first raised were answered as recommended
(2026-10-08). The approval was answered on 2026-10-14 with an amendment:
choices 1 to 4 approved as written; "can't open any Manager, Agent or Client
page" and choice 5 replaced by full access to every Tenant. See
`## Decisions taken`.

### Where a SuperAdmin lives: the Operator Tenant

A SuperAdmin Login belongs to a dedicated **Operator Tenant**: a `tenants` row
that holds the deployment's SuperAdmin Logins and nothing else. No Client,
Agent, Contract, Manager or invoice ever belongs to it.

This keeps the invariant "every Login belongs to exactly one Tenant" exactly
as it is. `users.tenant_id` stays NOT NULL and every sign-in token keeps its
`tenantId` claim. A SuperAdmin's own token names the Operator Tenant, so even
if it reached a Tenant-scoped query, the query would find nothing.

The Operator Tenant is marked on its row, not by a magic id:

```sql
ALTER TABLE tenants ADD COLUMN operator boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX uq_tenants_one_operator ON tenants (operator) WHERE operator;
```

`Tenant` gains the read-only `operator` field. The Tenant list excludes it,
and it cannot be entered.

### How the first SuperAdmin exists: a migration seed

One migration, **the next free Flyway version at merge (V61 today; V60 is
now `add_users_name`)**, does three things:

1. adds the `operator` column and its partial unique index (above);
2. inserts the Operator Tenant, named `Operator`, with a fixed id in the same
   style as V2's Default Tenant;
3. inserts one Login, `superadmin@example.com`, role `SUPER_ADMIN`, in the
   Operator Tenant, with a documented bcrypt-hashed password, exactly as V2
   seeds `manager@example.com`.

The migration is additive. It rewrites no row and drops nothing. The seed
runs in every environment, as V2 and V3 do today. The documented password is
to be changed with **Change password** at first sign-in (story 12). The
README's seeded-Logins list gains the SuperAdmin's email and password. V55's
global username index already guarantees the email cannot clash; if a
deployment somehow holds `superadmin@example.com` already, the migration
fails loudly rather than seeding a second Login.

Nothing else creates a SuperAdmin. There is no endpoint, command or start-up
hook for it.

### Sign-in and the SuperAdmin's own token

Sign-in is unchanged. `AuthController` already authenticates any role and
issues a token with `userId`, `tenantId` (the Operator Tenant's id) and
`role: SUPER_ADMIN`, and already logs `login success … role=SUPER_ADMIN`. The
deactivation doors apply to a SuperAdmin unchanged. `/api/me` answers for a
SuperAdmin as for any role. Self-service password change already works for
any role; the test that excluded `SUPER_ADMIN` now covers it.

### Inside a Tenant: the acting session

A SuperAdmin works inside a Tenant through an **acting session**: a second,
short-lived token the backend issues to a SuperAdmin on request, which looks
to every existing route exactly like a Manager's, an Agent's or a Tester's
token of that Tenant, except that its user is the SuperAdmin.

```
acting token claims
  id (userId)   = the SuperAdmin's own Login        -> audit actor, Login-state check
  sub           = the SuperAdmin's username
  tenantId      = the Tenant entered                -> every query's scope
  role          = MANAGER | AGENT | TESTER          -> every matcher and guard
  acting        = true
  actingAgentId = the Agent's id     (role AGENT only)
  actingTesterId= the Tester's id    (role TESTER only)
```

Why this shape: every matcher in `SecurityConfig` and every guard already
decides by role and Tenant, so they serve an acting session **without being
edited**. A SuperAdmin inside a Tenant as its Manager *is* that Tenant's
Manager to every guard; as an Agent, the Agent's ownership checks pass for
that Agent's Contracts and refuse every other, exactly as for the Agent. And
because `userId` stays the SuperAdmin's, every `AuditLog` call, which already
takes `principal.userId()` as the actor and `principal.tenantId()` as the
Tenant, records the SuperAdmin and the Tenant with no change at any call site.

**Issuing.** `POST /api/super-admin/acting-sessions`, under the
`SUPER_ADMIN`-only prefix, so only a SuperAdmin's own token can ask (an
acting token has role `MANAGER`/`AGENT`/`TESTER` and is refused, story 35).

```
POST /api/super-admin/acting-sessions
{ "tenantId": "…", "role": "MANAGER" }
{ "tenantId": "…", "role": "AGENT",  "agentId": "…" }
{ "tenantId": "…", "role": "TESTER", "testerId": "…" }

200 { "token": "…", "role": "MANAGER", "tenantName": "Acme Ltd" }
400 — role missing or not one of the three; agentId/testerId missing for its role or given for another
401 — no or invalid token
403 — caller is not a SuperAdmin on their own token
404 — no such Tenant, the Operator Tenant, or no such Agent/Tester in that Tenant
```

The Agent or Tester is looked up within the named Tenant; an id from another
Tenant is `404`. The Agent's or Tester's own Login state is not consulted, so
an Agent with no Login or a deactivated one can be opened (story 29). The
acting token lives as long as a sign-in token.

**The principal.** `AuthenticatedPrincipal` gains one nullable component:

```java
record AuthenticatedPrincipal(UUID userId, String username, UUID tenantId, String role, ActingAs actingAs)
record ActingAs(UUID agentId, UUID testerId)   // null on every sign-in token; both ids null when acting as Manager
```

`JwtService` reads and writes the three acting claims; a sign-in token is
issued and parsed exactly as today, with `actingAs` null. The
`JwtAuthenticationFilter`'s per-request Login-state check reads the
SuperAdmin's own Login (the token's `userId`), so deactivating or removing it
ends every acting session at the next request (story 37).

**Who the caller is.** Prefactoring: `CallerIdentityResolver` becomes the one
place that says which Agent, Client and Tester a caller is.

- `resolveAgentId`: `actingAs.agentId` when acting, else the Login's Agent,
  as today.
- `resolveClientId`: the acting Tester's Client when acting, else the Login's
  Tester's Client, as today.
- new `resolveTesterId`: the acting Tester when acting, else the Login's
  Tester. `RequestController`'s Tester-authored path, which today reads the
  Tester straight from `testerRepository.findByUserId(principal.userId())`,
  moves to it. No other code reads a caller's Agent or Tester from the Login
  directly (checked: `MeAgentController`, `MeClientController`,
  `StockController`, `ClientInvoiceSentBackController` and every guard already
  go through the resolver).

**What the product records.** Rows that name a Login already take the
caller's `userId`: a Request's `raised_by_user_id` on an Agent-authored
Request and on a proactive Fee's linking Request, `decided_by_user_id`, a
Client Invoice line's `edited_by`, a standing amount's `set_by_user_id`. In
an acting session these name the SuperAdmin's Login; the plain foreign keys
to `users` allow it. The one exception today is a Tester-authored Request,
whose `raised_by_user_id` is set to the Tester's own Login; its treatment
follows the open question (recommended: the SuperAdmin's Login too, the
Request's Tester unchanged).

**Own-account routes refuse an acting session.** `POST /api/me/password` and
`PUT /api/me/name` answer `403` to an acting token: inside a Tenant they would
change the SuperAdmin's own Login while looking like the Manager's. One check
on `actingAs`, in the existing services.

**`/api/me`.** Gains one nullable field, `acting`:
`{ "tenantName": "Acme Ltd", "role": "AGENT", "agentName": "Maria Lopez" }`
(or `testerName`, or neither for Manager). Null on every sign-in token. The
existing fields keep their meaning: `role` is the role entered, `agentId` and
`clientId` resolve as above, `username` is the SuperAdmin's.

**Audit.** A new line, `audit action=SUPER_ADMIN_ACTING_STARTED entity=Tenant
entityId=<tenantId> role=<role> agentId=<id|null> testerId=<id|null>
actorUserId=<superAdminUserId> tenantId=<tenantId>`, on every acting session
issued (story 32). `RequestLoggingFilter` adds `acting=true` to the MDC of an
acting request, beside `tenantId` and `userId`. No other audit line changes
shape: every one already carries the SuperAdmin as `actorUserId` and the
entered Tenant as `tenantId` (story 31).

### Backend access: the fence

Two rules in the security configuration, placed before every existing
matcher so that they win:

1. **`/api/super-admin/**` is `SUPER_ADMIN` only.** Any other role, and every
   acting token, gets `403`.
2. **A SuperAdmin's own token gets `403` on every route except
   `/api/super-admin/**`, `/api/me` and `/api/me/password`** (and the public
   sign-in and health routes). The own token names the Operator Tenant, which
   holds nothing; the way into a Tenant is an acting session, never the own
   token. This is a single deny rule on the role, so the matchers that only
   ask for "any signed-in role" never meet a role their guards did not
   consider.

No existing matcher changes, so every Manager, Agent and Tester answer is
unchanged.

### The Tenant list API

```
GET /api/super-admin/tenants

200 [
  { "id": "…", "name": "Default Tenant", "createdAt": "2026-09-01T10:00:00Z", "managerCount": 1 },
  …
]
401 — no or invalid token
403 — caller is not a SuperAdmin on their own token
```

- Every Tenant except the Operator Tenant, ordered by name
  (case-insensitive), then by `createdAt` as a tiebreaker.
- `managerCount` is the number of `MANAGER`-role Logins in that Tenant,
  active or deactivated. A Tenant with none shows `0`.
- One read-only query (Tenants left-joined to a grouped count of Manager
  Logins), behind a small **Tenant directory** read service and a new
  controller. It and the acting-session issuer are the only code in the
  backend that reads across Tenants on purpose, and they say so in their
  names and Javadoc.
- No paging: Tenants are created by hand, a handful per year.
- No audit line: it is a read.

### Frontend

- **Route.** A new `/super-admin` surface, with one page: the Tenant list at
  `/super-admin`. `landingPathForRole("SUPER_ADMIN")` returns `/super-admin`.
  The session proxy's matcher gains `/super-admin/:path*`.
- **Guard.** `requireSuperAdmin`, in the same shape as `requireManager`: ask
  `/api/me`; on a non-OK answer or another role, `redirect("/login")`; on an
  acting session, redirect to the BFF's leave route, which returns to the
  console (story 21). `requireManager`, `requireAgent` and `requireTester` are
  unchanged: an acting session's `/api/me` answers with the role entered, so
  they let it through, and a SuperAdmin's own session is sent to `/login`.
- **Shell.** `ShellFrame` with a new `super-admin` surface and the role label
  **SuperAdmin Console**. Its navigation has one item, **Tenants**. The
  shared viewer menu gives **Change password** and **Sign out** as on every
  other console.
- **The Tenant list.** A page title **Tenants**, then a table: **Tenant**,
  **Managers** (right-aligned number), **Created** (date, in the format the
  other lists use), and a trailing action, **Open as Manager**. Rows are not
  links. With no Tenants: "No Tenants yet." A failure to load shows the page's
  existing error boundary.
- **Two cookies.** The SuperAdmin's own token moves to a second httpOnly
  cookie when they enter a Tenant, and the acting token takes the session
  cookie's place. Every existing page, BFF route and `backendFetch` call then
  works inside the Tenant unchanged, because they all read the session cookie.
  - **Enter** (BFF route): with the own-token cookie, call
    `POST /api/super-admin/acting-sessions`; on `200`, set the session cookie
    to the acting token, keep the own token in the second cookie, and send the
    browser to `/manager`, `/agent` or `/client`.
  - **Leave** (BFF route): put the own token back in the session cookie, drop
    the second cookie, and send the browser to `/super-admin`. With the own
    token gone or expired, send it to `/login`.
  - **Sign out** (the existing `DELETE` on the session route) clears both
    cookies (story 22).
  - Opening an Agent or a Tester from inside a Tenant is an Enter with the own
    token still in the second cookie; the acting token is replaced, the own
    token kept.
- **Banner.** When `/api/me` carries `acting`, every shell (Manager Console,
  Agent Console, Client Portal) shows one full-width bar above the page:
  **"SuperAdmin · Acme Ltd · as Manager"** (or **"as Agent Maria Lopez"**,
  **"as Tester Sam Lee"**), and a **Back to SuperAdmin Console** button that
  calls Leave. The viewer menu inside a Tenant shows the SuperAdmin's email
  and **Sign out**, and hides **Change password** and **Change name**.
  Without `acting`, nothing about any shell changes.
- **Entry points inside a Tenant.** Shown only when `/api/me` carries
  `acting` with role `MANAGER`:
  - the Manager's Agent page gains **Open as this Agent**;
  - each Tester row on the Manager's Client page gains **Open as this
    Tester**.
- **BFF.** One pass-through proxy on `backendFetch` for the Tenant list; the
  Enter and Leave routes above.
- **Naming.** "SuperAdmin", "SuperAdmin Console", "Tenant", "Managers",
  "Open as Manager", "Open as this Agent", "Open as this Tester", "Back to
  SuperAdmin Console". Never "admin", "organisation", "workspace", "account",
  "impersonate" or "view as".

### Documentation at delivery

- `PRODUCT.md` Users: the SuperAdmin gains a line of its own ("looks after the
  Tenants of the deployment; enters any Tenant as its Manager, one of its
  Agents or one of its Testers, and can do there whatever that role can, with
  every action recorded under their own Login"), replacing "has no dedicated
  screens this iteration". Product Principle 2 gains the SuperAdmin's scope:
  every Tenant, one at a time.
- `docs/journeys.md`: **Sign in** says four roles; **Operate a second tenant
  safely** names the SuperAdmin as the one deliberate exception; **Administer
  the Tenants themselves** is `partial`.
- `DESIGN.md` Navigation: the SuperAdmin Console's item list (Tenants), and
  the inside-a-Tenant banner.
- `ARCHITECTURE.md`: the fifth browser surface; the acting-session issuer.
- The `role.ts` comment that says SuperAdmin has no shell is removed.

## Design direction

One surface, **Operate**: a console shell and one table, all from shipped
components (`ShellFrame`, `NavRail`, the viewer menu, the table and
empty-state styles the Manager lists use), plus one bar shared by the three
existing shells. **The `design` slot is not needed.**

- The console's shell is the Manager Console's, with its own role label and
  one nav item. No new colour of its own: the role label tells the consoles
  apart.
- The table follows the Manager's Clients list: hairline rows, the number
  column tabular and right-aligned, the date muted, the **Open as Manager**
  action a quiet secondary button at the row's end.
- The empty state is the quiet text the other empty lists use, without an
  action.
- **The inside-a-Tenant banner** uses the existing warning notice tone, so it
  reads as "you are not where you usually are" on every page, in both themes.
  One line: the Tenant name in the strongest weight, the role after it, the
  **Back to SuperAdmin Console** button at the end. It sits above the page
  header and scrolls with the page. At the mobile breakpoint the button wraps
  under the text.
- The **Open as this Agent** / **Open as this Tester** actions are quiet
  secondary buttons in the Agent page's header card and at a Tester row's
  end, beside the existing reset and deactivation actions.

Visual goldens:

- **New goldens:** the Tenant list with three Tenants and with none; the
  Manager dashboard, an Agent Console page and a Client Portal page with the
  banner; the Agent page and the Client page with their **Open as** actions.
  Per theme and breakpoint, against stub-backend fixtures. The stub backend
  gains the list route and an acting `/api/me`.
- **No existing golden moves.** A golden moving on any surface without the
  banner is a finding.

## Constraints

- **Migration: the next free Flyway version at merge (V61 today)**, additive
  only: one column with a default, one partial unique index, two inserts.
  No row is rewritten.
- `users.tenant_id` stays NOT NULL. A sign-in token's claims are unchanged;
  only an acting token carries the acting claims.
- No existing security matcher is edited. The two new rules are placed
  before them. No guard's role logic is edited; guards change only where they
  read a caller's Agent or Tester, and that goes through
  `CallerIdentityResolver`.
- Only the SuperAdmin's own token can issue an acting session. An acting
  token can never issue another, or reach `/api/super-admin/**`.
- An acting session sees exactly one Tenant, the one it names. No query is
  widened to read across Tenants.
- The Tenant directory reads only `tenants` and `users`, and returns only id,
  name, creation date and Manager count. It never returns a username.
- The acting token, like the sign-in token, never reaches the browser: both
  live in httpOnly cookies set by the BFF.
- Backend tests run under `IntegrationTest` (singleton Testcontainers Postgres,
  rollback per method). Mocking a repository in an integration test is banned
  (Backend rule 5).
- **No test, integration or e2e, changes the seeded SuperAdmin's password.**
  A test that needs to change one creates its own SuperAdmin Login in the
  Operator Tenant through the repository, as `OtherTenantFixture` does for
  Tenants.
- **No e2e test writes into the seeded Default Tenant as the SuperAdmin
  beyond what the existing specs already create.** Writes go to data the spec
  creates for itself.
- Maven runs with `JAVA_HOME=/opt/homebrew/opt/openjdk@21`. Checkstyle stays
  clean.
- e2e and visual runs go against the isolated stack, per
  `docs/agents/implementer-notes.md`.
- New backend classes go in the existing `web` and `security` packages (the
  package layout is being decided separately; this feature does not
  anticipate it).

## Testing decisions

Tests assert external behaviour only: HTTP status, the response body, the
audit lines written, and where a browser lands. They never read the
`operator` column to prove the list excludes it, and never decode a token to
prove what it carries; they show what the token can and cannot do.

- **One seam, and it already exists:** the HTTP API seam, `IntegrationTest` +
  MockMvc against real Postgres. Prior art: `AuthLoginTest`,
  `SecondTenantSignInApiTest`, `ChangeOwnPasswordApiTest`, and
  `OtherTenantFixture` for a second Tenant with its own Manager, Agent and
  Tester. Audit lines are asserted with a `ListAppender` on the `AUDIT`
  logger, as `SmartphoneCorrectionApiTest` does. No new seam.
- **`IntegrationTest` gains `superAdminToken()`**, signing in the seeded
  SuperAdmin, and `actingToken(tenantId, role, agentOrTesterId)`, which calls
  `POST /api/super-admin/acting-sessions` with it.
- **Sign-in:** the seeded SuperAdmin signs in and the role is `SUPER_ADMIN`;
  `/api/me` answers with that role and `acting: null`; a wrong password is
  `401` with no body.
- **The list:** with `OtherTenantFixture`'s Tenant present, the list contains
  `Default Tenant` and that Tenant with their Manager counts, not the
  Operator Tenant, in name order. A Tenant with no Manager shows `0`; a second
  Manager raises a count by one.
- **Issuing an acting session:** `200` for Manager, for an Agent and for a
  Tester of the named Tenant; `404` for an unknown Tenant, the Operator
  Tenant, and an Agent or Tester of another Tenant; `400` for a role/id
  mismatch; `403` for the Manager, Agent and Tester tokens and for an acting
  token; `401` with none. An Agent with no Login and an Agent whose Login is
  deactivated can both be opened. Each `200` writes one
  `SUPER_ADMIN_ACTING_STARTED` line naming the SuperAdmin and the Tenant.
- **Inside a Tenant as Manager:** an acting Manager token for
  `OtherTenantFixture`'s Tenant lists that Tenant's Clients, Agents and
  Contracts and none of the Default Tenant's; a Default Tenant id is `404`, as
  for that Tenant's own Manager. One parameterised test runs a representative
  write from every Manager matcher group (create a Client, add a Smartphone,
  correct a unit, approve a Request, set a standing amount, approve a Client
  Invoice) and shows each succeeds and its audit line carries the
  SuperAdmin's `actorUserId` and that Tenant's `tenantId`. The same token
  administering a Manager's Login meets the existing refusal.
- **Inside a Tenant as an Agent:** an acting Agent token lists that Agent's
  Contracts only, reads `/api/me/agent` as that Agent, logs a Request and a
  Fee and sends a Client Invoice on its own Contract, and is `403` on another
  Agent's Contract, exactly as the Agent's own token is.
- **Inside a Tenant as a Tester:** an acting Tester token reads that Tester's
  Client, submits a Request on its Contract (the Request names that Tester),
  and is `403` on another Client's Contract and on a Correction, as the
  Tester's own token is.
- **Attribution:** a Request logged by an acting Agent and one decided by an
  acting Manager show the SuperAdmin's username in `loggedByUsername` and
  `decidedByUsername`; a Tester-authored one follows the open question's
  answer.
- **The fence, both ways:** Manager, Agent and Tester tokens get `403` on
  `/api/super-admin/**`. `superAdminToken()` gets `403` on a representative
  route from every Tenant matcher group (one parameterised test). An acting
  token gets `403` on `POST /api/me/password` and `PUT /api/me/name`.
  Deactivating the SuperAdmin's Login makes an acting token `401` on the next
  request.
- **Self-service password change:** `ChangeOwnPasswordApiTest`'s
  parameterised test gains `SUPER_ADMIN`, on a SuperAdmin Login the test
  creates in the Operator Tenant.
- **Every other role unchanged:** the whole suite passes unedited apart from
  new tests, the `ChangeOwnPasswordApiTest` case above, and the mechanical
  `AuthenticatedPrincipal` constructor change in test helpers.
- **The migration:** a migration test in the shape of
  `GlobalUsernameIndexMigrationTest` shows exactly one Operator Tenant, that a
  second `operator = true` row is refused, and that every pre-existing Tenant
  has `operator = false`.
- **Frontend component tests (Vitest + Testing Library):** the Tenant table
  renders name, count, date and **Open as Manager**, and the empty state;
  `landingPathForRole` returns `/super-admin`; `requireSuperAdmin` redirects
  on a non-OK `/api/me`, on every other role, and to Leave on an acting
  session; the banner renders each of its three wordings and is absent
  without `acting`; the **Open as** actions render only on an acting Manager
  session; the viewer menu hides **Change password** and **Change name** when
  acting. The Enter and Leave route handlers are tested in the shape of
  `app/api/session/route.test.tsx`.
- **One new e2e spec, `super-admin-signs-in.spec.ts`,** using `helpers.ts`'
  `login`/`logout`: sign in as the seeded SuperAdmin, land on the console, see
  `Default Tenant`; **Open as Manager**, land on `/manager` with the banner,
  create a Client; open an Agent's page, **Open as this Agent**, land on
  `/agent` with the banner naming that Agent; **Back to SuperAdmin Console**;
  open a Client, **Open as this Tester**, land on `/client`; sign out and
  open `/manager` to land on `/login`; sign in as the seeded Manager and
  open `/super-admin` to land on `/login`. It never changes the seeded
  password. A failure is judged against a controlled comparison on an idle
  machine, never waved off as a flake.

## Decisions taken

### Following the human's answers

- **A SuperAdmin's Login lives in a hidden Operator Tenant** that holds only
  SuperAdmins and never shows in the Tenant list (human, 2026-10-08, as
  recommended). Sign-in and every Tenant-scoped query keep working unchanged.
- **The first SuperAdmin is seeded by the database setup** as
  `superadmin@example.com` with a documented password, changed at first
  sign-in through **Change password** (human, 2026-10-08, as recommended).
  Until it is changed, the documented password opens the SuperAdmin Console,
  **and now every Tenant**, in a deployment; the human accepted the seed, and
  the walkthrough's human step makes the first change part of acceptance.
- **The SuperAdmin gets access to everything, in every Tenant** (human,
  2026-10-14, clarified "Everything, every Tenant"). They open and change
  every Manager, Agent and Client page of every Tenant. This replaces the
  2026-10-08 answer "never a Tenant's Agents, Clients or invoices", the
  former non-goals "no impersonation" and "nothing of a Tenant's work", and
  choice 5. It follows that a SuperAdmin can do an Agent's acts and a
  Manager's acts on the same work, for example send an Agent's Client Invoice
  as that Agent and then approve it as the Manager. The Manager approval step
  (Product Principle 3) is still passed through; it is no longer necessarily a
  second person. That is what "everything" asks for, and the audit log shows
  both acts under the SuperAdmin.
- **Every action inside a Tenant is recorded in the audit log under the
  SuperAdmin's own Login** (human, 2026-10-14). Delivered by keeping the
  SuperAdmin's `userId` in the acting token, so every existing audit line
  names them without a change at any call site.
- **Choices 1 to 4 approved as written** (human, 2026-10-14): no **New
  Tenant** button and no Tenant detail page; exactly one SuperAdmin; the
  Manager count includes deactivated Manager Logins; Tenants sorted by name,
  no paging.
- **Resetting a Manager's password is the SuperAdmin's job** (human,
  2026-09-30). It is not in this feature.
- **Passwords are generated by the product, never typed** by one person for
  another (human, 2026-10-01/02). Nothing here sets anyone's password, and
  the own-account routes refuse an acting session.
- **A Manager administers only Agents and Testers in their own Tenant**
  (human). A SuperAdmin inside a Tenant as its Manager is held to the same.

### Taken alone

- **A SuperAdmin works inside a Tenant through an acting token that carries
  the entered Tenant and role and the SuperAdmin's own `userId`.** Every
  matcher, guard, Tenant-scoped query and audit call then works unchanged.
  The alternatives were teaching every guard and every `hasRole("MANAGER")`
  matcher a fifth case and passing a Tenant on every request (dozens of
  edits, each a place to get the fence wrong), or signing in as the target
  Login (which loses the SuperAdmin from the audit log, against the human's
  answer).
- **The SuperAdmin enters "as Manager", not as one named Manager Login.**
  Manager pages do not depend on which Manager is looking, and acting as a
  particular Login would put that person's name on the SuperAdmin's acts.
- **Agents and Testers are opened individually, by Agent id and Tester id.**
  Their pages only exist for one Agent or one Tester, because
  `CallerIdentityResolver` answers "which Agent / which Client is this
  caller". Opening by Agent id (not the Agent's Login) reaches an Agent with
  no Login or a deactivated one; a Client is opened through one of its
  Testers because the Client Portal's Request submission needs a Tester.
- **`CallerIdentityResolver` becomes the only place that says which Agent,
  Client or Tester a caller is** (prefactoring). The one bypass,
  `RequestController`'s Tester-authored path, moves to it. Otherwise that path
  would be the one place an acting Tester session silently fails.
- **The entry points are the Tenant row's Open as Manager, then Open as this
  Agent / this Tester on the Manager's own Agent and Client pages.** Choice 1
  rules out a Tenant detail page, and the Manager pages already list every
  Agent and Tester with their ids.
- **One Tenant at a time; changing Tenant or role goes through the console**
  (or, for Agent and Tester, through the Manager pages). Every existing page
  is built for one Tenant, and one acting token at a time keeps "which Tenant
  am I changing" unambiguous.
- **The SuperAdmin's own token is still refused (`403`) on every Tenant
  route.** It names the Operator Tenant, which holds nothing, so letting it
  through would only show empty pages. Choice 5's "forbidden" no longer
  describes what the SuperAdmin meets in the product: they enter the Tenant
  and are never refused it.
- **The acting token is held in the existing session cookie and the own token
  in a second httpOnly cookie, swapped by two BFF routes.** Every existing
  page, BFF route and `backendFetch` reads the session cookie, so none changes.
- **The inside-a-Tenant banner is shown on every page, in the warning tone,
  with one Back action.** Story 19 needs it on every page; a warning tone
  separates "inside a customer's Tenant" from the SuperAdmin's own console at
  a glance. It is wording and styling, cheap to change.
- **Opening `/super-admin` while inside a Tenant leaves the Tenant** rather
  than sending the SuperAdmin to `/login`.
- **Sign out inside a Tenant signs the SuperAdmin out fully.** A shared
  machine should need one action, not two.
- **Change password and Change name are hidden inside a Tenant and their
  routes refuse an acting token.** Inside a Tenant they would act on the
  SuperAdmin's own Login while the screen says "Manager"; the console is
  where the SuperAdmin manages their own Login.
- **An acting session ends when the SuperAdmin's own Login is deactivated or
  removed**, through the existing per-request Login-state check on the
  token's `userId`. An acting token lives as long as a sign-in token; it is
  not tied to the own token's remaining time, and Leave with an expired own
  token lands on `/login`.
- **A new audit line, `SUPER_ADMIN_ACTING_STARTED`, and `acting=true` in the
  request MDC.** Every change is already attributed; entering a Tenant without
  changing anything would otherwise leave no trace (story 32).
- **The acting-session issuer answers `404` for an unknown Tenant, the
  Operator Tenant, and an Agent or Tester of another Tenant.** It is the
  Tenant-scoped not-found shape every by-id route uses; it reveals nothing to
  a SuperAdmin they could not already list.
- **The Operator Tenant is marked by a boolean column with a partial unique
  index**, not a fixed id or a name, and is **named `Operator`**.
- **The Tenant directory is a separate read service**, kept apart and named
  for reading across Tenants on purpose.
- **The shell reuses the Manager Console's look with its own label and one
  nav item.**
- **Testing: the existing HTTP seam, with `superAdminToken()` and
  `actingToken(…)` added to `IntegrationTest`, and audit lines asserted with
  a `ListAppender` as `SmartphoneCorrectionApiTest` does.** Prior art is
  `SecondTenantSignInApiTest`, `ChangeOwnPasswordApiTest` and
  `OtherTenantFixture`. The fence is proven by status codes, not by reading
  configuration or decoding tokens.
- **Tests never change the seeded SuperAdmin's password, and the e2e spec
  writes only to data it creates.** Seeded credentials underpin the token
  helpers and the e2e suite, and e2e state does not roll back.

## Open questions

1. **When a SuperAdmin logs, decides or edits something inside a Tenant, whose
   name does the product show on it, for example "Logged by" or "Decided by"
   on a Request: the SuperAdmin's email, or the Manager, Agent or Tester they
   were acting as?** Recommendation: **the SuperAdmin's email**, everywhere,
   including a Request submitted in the Client Portal as a Tester (it still
   names that Tester as the one it is for). It matches the audit log. A
   Tenant's people can tell their own colleagues' work from the SuperAdmin's.
   And "as Manager" has no single Manager to name. Most paths already work
   this way. Only the Tester-submitted Request needs a change. (case 1)

## Acceptance walkthrough

1. [agent] Run the migrations on a fresh database and show exactly one Operator Tenant, named `Operator`, and one `SUPER_ADMIN` Login, `superadmin@example.com`, belonging to it. Show every pre-existing Tenant has `operator = false`, and that inserting a second operator Tenant is refused. (stories: 1, 11)
2. [agent] `POST /api/auth/login` as `superadmin@example.com` with the documented password and show `200` with role `SUPER_ADMIN`; with a wrong password, `401` with no body. `GET /api/me` with the token shows role `SUPER_ADMIN` and `acting: null`. (stories: 1, 2)
3. [agent] With the SuperAdmin token, `GET /api/super-admin/tenants` and show `Default Tenant` with `managerCount: 1` and its creation date, and no `Operator`. Add `OtherTenantFixture`'s Tenant and a Tenant with no Manager; show all three in name order, the empty one with `0`. Add a second Manager to one and show its count rise by one. Show no username in the response. (stories: 5, 6, 7, 8, 9, 11)
4. [agent] `POST /api/super-admin/acting-sessions` as Manager of the second Tenant and show `200` with its name. With the acting token, list Clients, Agents and Contracts and show only the second Tenant's; request a Default Tenant Contract by id and show `404`. Ask for the Operator Tenant and an unknown Tenant and show `404`. Show one `SUPER_ADMIN_ACTING_STARTED` audit line naming the SuperAdmin and the Tenant. (stories: 11, 14, 15, 16, 18, 32)
5. [agent] With that acting Manager token, create a Client, add a Smartphone to a Contract, correct a SIM number, approve a Pending Approval Request, set an Agent's standing salary and approve a sent Client Invoice. Show each succeeds and its audit line carries the SuperAdmin's `actorUserId` and the second Tenant's `tenantId`. Show the approved Request's `decidedByUsername`. Try to reset a Manager's Login and show the same refusal a Manager meets. (stories: 17, 31, 33)
6. [agent] Open an acting session as one of the second Tenant's Agents. Show `/api/me/agent` is that Agent, the Contract list holds only that Agent's Contracts, and a Request and a Fee can be logged and a Client Invoice sent on one of them, each audit line naming the SuperAdmin. Show another Agent's Contract is `403`. Repeat the session for an Agent with no Login and for one with a deactivated Login, and show both open. (stories: 25, 26, 29, 31)
7. [agent] Open an acting session as one of the second Tenant's Testers. Show it reads that Tester's Client, submits a Request on its Contract (the Request names that Tester), and is `403` on another Client's Contract and on a Correction. Show the Request's `loggedByUsername` per the open question's answer. (stories: 27, 28, 31, 33)
8. [agent] Call `POST /api/super-admin/acting-sessions` and `GET /api/super-admin/tenants` with the Manager, Agent and Tester tokens and with an acting token and show `403` for each, and with no token `401`. Ask for an Agent of the Default Tenant under the second Tenant's id and show `404`. With the SuperAdmin's own token, call a representative route of every Tenant matcher group and show `403`. With an acting token, call `POST /api/me/password` and `PUT /api/me/name` and show `403`. (stories: 34, 35, 36)
9. [agent] Deactivate a test-created SuperAdmin Login holding an acting token and show the token is `401` on its next request. (stories: 37)
10. [agent] On a SuperAdmin Login created for the test, change its password through `POST /api/me/password`, show the new one signs in and the old one is refused. (stories: 12)
11. [agent] Show `git diff` adds exactly one migration, at the next free version, that only adds a column, an index and two rows, and edits no existing security matcher. Run `AuthLoginTest`, `SecondTenantSignInApiTest`, `ChangeOwnPasswordApiTest` and `mvn verify`, then the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green. Show no existing golden moved. (stories: 40, 41)
12. [agent] In a browser, sign in as the seeded SuperAdmin and show the landing page is `/super-admin`, the shell reads **SuperAdmin Console** with one nav item, **Tenants**, and the viewer menu shows the SuperAdmin's email. (stories: 2, 3, 4)
13. [agent] On the console, show the **Tenants** table with name, Manager count, creation date and **Open as Manager** for each Tenant, no `Operator` row, no row links and no **New Tenant** button. Against the stub backend with no Tenants, show "No Tenants yet." (stories: 5, 6, 7, 10, 11, 14)
14. [agent] Click **Open as Manager** on a Tenant and show `/manager` with the banner "SuperAdmin · <Tenant> · as Manager". Visit Clients, Agents, Contracts, Requests, Invoices, Carriers and Stock and show the banner on each and only that Tenant's data. Create a Client from the page. Show the viewer menu has **Sign out** and no **Change password** or **Change name**. (stories: 15, 16, 17, 18, 19)
15. [agent] From an Agent's page, click **Open as this Agent** and show `/agent` with the banner naming that Agent and that Agent's own Contracts. Visit Fleet, Requests, Client Invoices, my invoice, Stock and Carriers. Click **Back to SuperAdmin Console** and show `/super-admin`. Enter again, open a Client's page, click **Open as this Tester** and show `/client` with the banner naming that Tester and that Tester's Client. (stories: 20, 23, 24, 25, 27, 30)
16. [agent] Inside a Tenant, type `/super-admin` in the address bar and show the console. Enter again and sign out; show `/login`, then open `/manager` and `/super-admin` and show `/login` for both. (stories: 21, 22)
17. [agent] Sign in as the seeded Manager, open `/super-admin`, and show `/login`; repeat as the seeded Agent and Tester. Signed in as the SuperAdmin on the console, open `/manager`, `/agent` and `/client` directly and show `/login`. Signed out, open `/super-admin` and show `/login`. Show no **Open as** action on the Manager's own Agent and Client pages. (stories: 34, 36, 38)
18. [agent] With an expired or deleted session cookie, load `/super-admin` and, inside a Tenant, `/manager`, and show the sign-in page, not an empty page. (stories: 39)
19. [agent] Sign out from the console's viewer menu and show `/login`. (stories: 13)
20. [agent] By keyboard alone, sign in, reach the Tenants table, activate **Open as Manager**, reach **Back to SuperAdmin Console**, return, open the viewer menu, open **Change password**, cancel with Escape and sign out, showing a visible focus ring at each stop. At the mobile breakpoint, show the table's columns and the banner inside the viewport. (stories: 42, 43)
21. [agent] Show the README lists `superadmin@example.com` and its documented password beside the other seeded Logins, and that `PRODUCT.md` and `docs/journeys.md` describe the SuperAdmin, four roles and the SuperAdmin's reach into every Tenant. (stories: 44)
22. [human] On the running app, sign in as the SuperAdmin with the documented password, change it from the console, sign out and back in with the new one, and show the documented password is now refused. (stories: 1, 2, 12)
23. [human] As the person who will look after Tenants, enter a Tenant as its Manager, then as one of its Agents and one of its Testers. Confirm each page is the one that role sees, the banner always says where you are, and you could change what you needed to. Then read the audit log for that visit and confirm each change is under your own Login and that Tenant, as decided on 2026-10-14. (stories: 15, 16, 17, 19, 25, 26, 27, 28, 31, 32)
24. [human] Look at a Request you logged or decided inside the Tenant from that Tenant's own Manager's sign-in, and confirm the name shown is what you chose in the open question. (stories: 33)

## Execution order

Eight slices. Slices 1 to 4 deliver the console; 5 and 6 add entering a
Tenant as its Manager; 7 and 8 add the Agent Console and the Client Portal.
Each group is shippable on its own (see the split proposed in the result).

1. `super-admin-exists-and-signs-in` — the migration (operator column, index, Operator Tenant, seeded SuperAdmin), `Tenant.operator`, `superAdminToken()` in `IntegrationTest`, the two security rules with their parameterised test, `SUPER_ADMIN` in `ChangeOwnPasswordApiTest`, the migration test, and the README's seeded Login. Labels: `backend`. Depends on nothing. (stories: 1, 2, 12, 36, 40, 41, 44)
2. `tenant-list-api` — the Tenant directory read service, `GET /api/super-admin/tenants`, and its integration tests. Labels: `backend`. Depends on `super-admin-exists-and-signs-in`. (stories: 5, 6, 7, 8, 9, 11)
3. `super-admin-console-shell` — the `/super-admin` surface, `landingPathForRole`, the session proxy's matcher, `requireSuperAdmin` (without the acting branch), the shell with its label and nav item, the viewer menu, a placeholder page body, component tests, and the e2e spec's sign-in, landing and fence steps. Labels: `frontend`. Depends on `super-admin-exists-and-signs-in`. (stories: 3, 4, 13, 36, 38, 39, 42)
4. `tenant-list-ui` — the BFF proxy, the Tenants table and empty state (without the row action), the stub-backend fixture, the new goldens, the e2e spec's list step. Labels: `frontend`. Depends on `tenant-list-api` and `super-admin-console-shell`. (stories: 5, 6, 7, 9, 10, 11, 43)
5. `acting-session-as-manager-api` — `AuthenticatedPrincipal.actingAs`, the acting claims in `JwtService`, `POST /api/super-admin/acting-sessions` for role `MANAGER`, the `SUPER_ADMIN_ACTING_STARTED` audit line and MDC flag, `/api/me`'s `acting` field, the own-account refusals, `actingToken(…)` in `IntegrationTest`, and the Manager-side tests (scope, writes, audit, fence, deactivation). Labels: `backend`. Depends on `tenant-list-api`. (stories: 11, 14, 15, 16, 17, 18, 31, 32, 33, 34, 35, 37)
6. `enter-a-tenant-as-manager-ui` — the Enter and Leave BFF routes and the second cookie, sign-out clearing both, **Open as Manager** on the Tenant row, the banner on the Manager Console, the viewer menu's acting variant, `requireSuperAdmin`'s leave branch, goldens, and the e2e spec's enter, act, back and sign-out steps. Labels: `frontend`. Depends on `acting-session-as-manager-api` and `tenant-list-ui`. (stories: 14, 15, 16, 19, 20, 21, 22, 42, 43)
7. `acting-session-as-agent-or-tester-api` — `CallerIdentityResolver` reading `actingAs` and gaining `resolveTesterId` (with `RequestController`'s Tester-authored path moved to it), the issuer accepting `AGENT` and `TESTER` with their ids, attribution per the open question's answer, and the Agent- and Tester-side tests. Labels: `backend`. Depends on `acting-session-as-manager-api`. (stories: 25, 26, 27, 28, 29, 31, 33, 34)
8. `open-agent-console-and-client-portal-ui` — **Open as this Agent** on the Manager's Agent page and **Open as this Tester** on the Client page's Tester rows (acting Manager only), the banner on the Agent Console and the Client Portal with the Agent's or Tester's name, goldens, the e2e spec's Agent and Tester steps, and the delivery documentation (`PRODUCT.md`, `docs/journeys.md`, `DESIGN.md`, `ARCHITECTURE.md`). Labels: `frontend`. Depends on `acting-session-as-agent-or-tester-api` and `enter-a-tenant-as-manager-ui`. (stories: 23, 24, 25, 27, 30, 43)
