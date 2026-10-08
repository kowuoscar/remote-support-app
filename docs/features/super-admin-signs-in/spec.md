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
which Tenants exist. The people who would look after Tenants have no place in
the product at all.

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

This is the first of the epic's four features and its front door. Creating a
Tenant, resetting a Manager's password and deactivating a Manager's Login
all need a SuperAdmin who can sign in and a console to put them in.

## Journeys

Advances `docs/roadmap/tenant-administration.md`, the **first** of its four
features.

- **Administer the Tenants themselves** (not yet in `docs/journeys.md`; the
  epic adds it as `wanted` when placed). After this feature it is
  **`partial`**: a SuperAdmin signs in and sees the Tenants that exist, but
  cannot create one yet. `create-a-tenant-with-its-first-manager` makes it
  play end to end.
- **Sign in** (`exists`, stays `exists`): it gains a fourth role. A SuperAdmin
  signs in with an email and a password and lands on their own console. Every
  other role is unchanged, and that is proven. The journey's wording "Any of
  the three roles" becomes "any of the four roles" at delivery.
- **Keep a login working over time** (`exists`, stays `exists`): a SuperAdmin
  changes their own password the same way every other role does.
- **Operate a second tenant safely** (`exists`, stays `exists`): nothing a
  SuperAdmin can do reaches inside a Tenant, and no Tenant-scoped route
  answers a SuperAdmin.

## Goals / Non-goals

Goals:

- A SuperAdmin Login exists in every deployment from the moment this feature
  is migrated, without anyone writing SQL by hand.
- A SuperAdmin signs in on the same sign-in page as everyone else and lands
  on the **SuperAdmin Console**, a console of their own.
- The console shows the Tenants that exist: each one's name, its Manager
  count and the date it was created.
- A SuperAdmin changes their own password and signs out from the console, as
  every other role does.
- A SuperAdmin cannot reach any Manager, Agent or Tester page, nor any
  Tenant-scoped API route. A Manager, Agent or Tester cannot reach the
  SuperAdmin Console or its API.
- Every existing role signs in and works exactly as before.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No creating a Tenant.** That is `create-a-tenant-with-its-first-manager`.
  The console offers no **New Tenant** button, not even a disabled one.
- **No renaming, archiving or deleting a Tenant.** Nothing in this feature
  writes to `tenants` beyond the migration.
- **No list of a Tenant's Managers, no Tenant detail page.** The Tenant row is
  not a link. Manager Logins appear with the reset and deactivation features,
  which decide where they are shown.
- **No resetting a Manager's password, no deactivating a Manager's Login.**
  Those are the epic's third and fourth features.
- **No creating, listing, resetting or deactivating SuperAdmin Logins in the
  product.** There is exactly one way a SuperAdmin comes to exist: the
  database seed (human, 2026-10-08). A second SuperAdmin, if ever wanted, is a
  later decision.
- **No impersonation, "view as Manager" or switching into a Tenant.**
- **Nothing of a Tenant's work, not even as a count.** No Agent, Client,
  Tester, Contract or invoice lists or counts per Tenant, and no activity or
  billing figures. A SuperAdmin sees the Tenant list and, in later features,
  that Tenant's Manager Logins; never a Tenant's Agents, Clients or invoices
  (human, 2026-10-08).
- **No dashboard, charts or search.** One list.
- **No change to how Managers, Agents and Testers sign in, what their tokens
  carry, or what they can see.**
- **No new audit events for viewing.** Sign-in is already logged with role and
  Tenant.

## User stories

1. As a SuperAdmin, I want a Login that exists from the moment the product is deployed, so that I can get in without anyone writing SQL.
2. As a SuperAdmin, I want to sign in on the same sign-in page as everyone else, with my email and password, so that there is one front door to the product.
3. As a SuperAdmin, I want to land on a console of my own after signing in, so that I never see a Manager's, Agent's or Tester's screens.
4. As a SuperAdmin, I want the console to say it is the SuperAdmin Console and who I am signed in as, so that I know which hat I am wearing.
5. As a SuperAdmin, I want to see every Tenant that exists, by name, so that I know who is operating in this deployment.
6. As a SuperAdmin, I want each Tenant shown with its number of Managers, so that I can spot a Tenant with nobody able to run it.
7. As a SuperAdmin, I want each Tenant shown with the date it was created, so that I can tell an established Tenant from a new one.
8. As a SuperAdmin, I want the Tenants in a stable, predictable order, so that I find the one I mean without hunting.
9. As a SuperAdmin, I want a Tenant with no Managers shown with a count of 0, not left out, so that the list is complete.
10. As a SuperAdmin, I want the list to say so plainly when there are no Tenants, so that an empty page is not mistaken for a failure.
11. As a SuperAdmin, I want the place my own Login lives not to appear among the customer Tenants, so that the list is only the companies operating here.
12. As a SuperAdmin, I want to change my own password from the console, so that the password I was first given does not stay in use.
13. As a SuperAdmin, I want to sign out from the console, so that I can leave a shared machine safely.
14. As the company, I want a SuperAdmin unable to open any Manager, Agent or Tester page, so that the SuperAdmin role never sees inside a Tenant's work.
15. As the company, I want every Tenant-scoped API route to refuse a SuperAdmin's token, so that the boundary holds at the backend and not only in the browser.
16. As the company, I want Managers, Agents and Testers unable to open the SuperAdmin Console or call its API, so that seeing every Tenant stays with the SuperAdmin.
17. As a visitor who is not signed in, I want to be sent to the sign-in page when I open the SuperAdmin Console, so that it behaves like every other console.
18. As a SuperAdmin whose session has expired, I want to land on the sign-in page at my next page load, rather than see an empty list, so that what happened is clear.
19. As a Manager, Agent or Tester, I want sign-in, my console and my own password change to behave exactly as before, so that the new role carries no release risk.
20. As a Manager, I want my Tenant's data unchanged by this feature, so that nothing I own moves.
21. As a SuperAdmin working by keyboard, I want to reach the Tenant list, the change-password action and sign-out without a mouse, with a visible focus ring, so that the console is usable without one.
22. As a SuperAdmin on a phone, I want the Tenant list readable at the mobile breakpoint, so that I can check on Tenants away from a desk.
23. As a developer or someone trying the demo, I want the SuperAdmin's seeded email and password documented next to the other seeded Logins, so that I can sign in as one locally.

## Solution

The three questions this spec raised were answered as recommended
(2026-10-08); see `## Decisions taken`.

### Where a SuperAdmin lives: the Operator Tenant

A SuperAdmin Login belongs to a dedicated **Operator Tenant**: a `tenants` row
that holds the deployment's SuperAdmin Logins and nothing else. No Client,
Agent, Contract, Manager or invoice ever belongs to it.

This keeps the invariant "every Login belongs to exactly one Tenant" exactly
as it is. `users.tenant_id` stays NOT NULL, the token keeps its `tenantId`
claim, `AuthenticatedPrincipal` keeps a non-null `tenantId`, and every
Tenant-scoped query keeps working. Even if a SuperAdmin token reached a
Tenant-scoped query, the query would find only the Operator Tenant's data,
which is none.

The Operator Tenant is marked on its row, not by a magic id:

```sql
ALTER TABLE tenants ADD COLUMN operator boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX uq_tenants_one_operator ON tenants (operator) WHERE operator;
```

`Tenant` gains the read-only `operator` field. The Tenant list excludes it.

### How the first SuperAdmin exists: a migration seed

One migration, **the next free Flyway version at merge (V60 today)**, does
three things:

1. adds the `operator` column and its partial unique index (above);
2. inserts the Operator Tenant, named `Operator`, with a fixed id in the same
   style as V2's Default Tenant;
3. inserts one Login, `superadmin@example.com`, role `SUPER_ADMIN`, in the
   Operator Tenant, with a documented bcrypt-hashed password, exactly as V2
   seeds `manager@example.com`.

The migration is additive. It rewrites no row and drops nothing. The seed
runs in every environment, as V2 and V3 do today, so every deployment has a
SuperAdmin from the moment it migrates. The documented password is to be
changed with **Change password** at first sign-in (story 12). The README's
seeded-Logins list gains the SuperAdmin's email and password. `V55`'s global
username index already guarantees the email cannot clash with an existing
Login; if a deployment somehow holds `superadmin@example.com` already, the
migration fails loudly rather than seeding a second Login.

Nothing else creates a SuperAdmin. There is no endpoint, command or start-up
hook for it.

### Sign-in and the token

Sign-in is unchanged. `AuthController` already authenticates any role and
issues a token with `userId`, `tenantId` (the Operator Tenant's id) and
`role: SUPER_ADMIN`, and already logs `login success … role=SUPER_ADMIN`. The
deactivation doors (`isEnabled()`, the Login-state check, the self-service
refusal) apply to a SuperAdmin unchanged. `/api/me` answers for a SuperAdmin
as it does for any role. Self-service password change (`POST /api/me/password`)
already works for any role and needs no change; the test that excluded
`SUPER_ADMIN` now covers it.

### Backend access: a fence both ways

Two rules in the security configuration, placed before every existing
matcher so that they win:

1. **`/api/super-admin/**` is `SUPER_ADMIN` only.** Any other role gets `403`.
2. **A SuperAdmin gets `403` on every route except `/api/super-admin/**`,
   `/api/me` and `/api/me/password`** (and the public sign-in and health
   routes). This is a single deny rule on the role, not a change to each
   existing matcher. Without it, a SuperAdmin token would pass the matchers
   that only ask for "any signed-in role" (the Contract-scoped Fleet,
   Requests, Fees and Client Invoice routes) and depend on per-resource guards
   that never considered this role.

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
403 — caller is not a SuperAdmin
```

- Every Tenant except the Operator Tenant, ordered by name
  (case-insensitive), then by `createdAt` as a tiebreaker.
- `managerCount` is the number of `MANAGER`-role Logins in that Tenant,
  active or deactivated. A Tenant with none shows `0`.
- One read-only query (Tenants left-joined to a grouped count of Manager
  Logins), behind a small **Tenant directory** read service and a new
  controller. It is the one place in the backend that reads across Tenants on
  purpose, and it says so in its name and its Javadoc. It reads no table other
  than `tenants` and `users`.
- No paging: Tenants are created by hand, a handful per year.
- No audit line: it is a read.

### Frontend

- **Route.** A new `/super-admin` surface, with one page: the Tenant list at
  `/super-admin`. `landingPathForRole("SUPER_ADMIN")` returns `/super-admin`.
  The session proxy's matcher gains `/super-admin/:path*`, so a visitor with
  no session is sent to `/login` as on the other three surfaces.
- **Guard.** `requireSuperAdmin`, in the same shape as `requireManager`: ask
  `/api/me`, and on a non-OK answer or another role, `redirect("/login")`.
  The existing `requireManager`, `requireAgent` and `requireTester` already
  send a SuperAdmin to `/login`, because they compare roles exactly; no
  change is needed for story 14 beyond a test proving it.
- **Shell.** `ShellFrame` with a new `super-admin` surface and the role label
  **SuperAdmin Console**. Its navigation has one item, **Tenants**. The
  shared viewer menu gives **Change password** and **Sign out** as on every
  other console, unchanged.
- **The Tenant list.** A page title **Tenants**, then a table with three
  columns: **Tenant**, **Managers** (right-aligned number), **Created**
  (date, in the format the other lists use). Rows are not links. With no
  Tenants: a quiet empty state, "No Tenants yet." A failure to load shows
  the page's existing error boundary.
- **BFF.** One pass-through proxy on `backendFetch` for the list, in the same
  shape as the other read proxies.
- **Naming.** "SuperAdmin", "SuperAdmin Console", "Tenant", "Managers".
  Never "admin", "organisation", "workspace" or "account".

### Documentation at delivery

- `PRODUCT.md` Users: the SuperAdmin gains a line of its own ("looks after
  the Tenants of the deployment; sees each Tenant's name and Managers, never
  its work"), replacing "has no dedicated screens this iteration".
- `docs/journeys.md`: **Sign in** says four roles; **Administer the Tenants
  themselves** is `partial`.
- `DESIGN.md` Navigation: the SuperAdmin Console's item list (Tenants).
- `ARCHITECTURE.md`: the fifth browser surface.
- The `role.ts` comment that says SuperAdmin has no shell is removed.

## Design direction

One surface, **Operate**: a console shell and one table, all from shipped
components (`ShellFrame`, `NavRail`, the viewer menu, the table and empty-state
styles the Manager lists use). **The `design` slot is not needed.**

- The shell is the Manager Console's, with its own role label and one nav
  item. No new colour, no accent of its own: the role label is what tells the
  consoles apart.
- The table follows the Manager's Clients list: hairline rows, the number
  column tabular and right-aligned, the date muted.
- The empty state is the quiet text the other empty lists use, without an
  action (creating a Tenant is the next feature).
- At the mobile breakpoint the three columns fit without horizontal scroll;
  the date may wrap under the name if needed.

Visual goldens:

- **New goldens:** the Tenant list with three Tenants and with none, per
  theme and breakpoint, against stub-backend fixtures. The stub backend gains
  the list route.
- **No existing golden moves.** A golden moving on any other surface is a
  finding.

## Constraints

- **Migration: the next free Flyway version at merge (V60 today)**, additive
  only: one column with a default, one partial unique index, two inserts.
  No row is rewritten.
- `users.tenant_id` stays NOT NULL, the token's claims are unchanged, and
  `AuthenticatedPrincipal` is unchanged.
- No existing security matcher is edited. The two new rules are placed before
  them.
- The Tenant directory reads only `tenants` and `users`, and returns only id,
  name, creation date and Manager count. It never returns a username.
- A SuperAdmin is `403`, never `404`, on a Tenant-scoped route: the refusal
  is about the role, and it reveals nothing about any Tenant's data.
- Backend tests run under `IntegrationTest` (singleton Testcontainers Postgres,
  rollback per method). Mocking a repository in an integration test is banned
  (Backend rule 5).
- **No test, integration or e2e, changes the seeded SuperAdmin's password.**
  A test that needs to change one creates its own SuperAdmin Login in the
  Operator Tenant through the repository, as `OtherTenantFixture` does for
  Tenants.
- Maven runs with `JAVA_HOME=/opt/homebrew/opt/openjdk@21`. Checkstyle stays
  clean.
- e2e and visual runs go against the isolated stack, per
  `docs/agents/implementer-notes.md`.
- New backend classes go in the existing `web` and `security` packages (the
  package layout is being decided separately; this feature does not
  anticipate it).

## Testing decisions

Tests assert external behaviour only: HTTP status, the response body, and
where a browser lands. They never read the `operator` column to prove the
list excludes it; they show the Operator Tenant's name is absent from the
list.

- **One seam, and it already exists:** the HTTP API seam, `IntegrationTest` +
  MockMvc against real Postgres. Prior art: `AuthLoginTest`,
  `SecondTenantSignInApiTest`, `ChangeOwnPasswordApiTest`, and
  `OtherTenantFixture` for a second Tenant with its own Manager. No new seam.
- **`IntegrationTest` gains `superAdminToken()`**, signing in the seeded
  SuperAdmin, beside `managerToken()`, `agentToken()` and `testerToken()`.
- **Sign-in:** the seeded SuperAdmin signs in and the response's role is
  `SUPER_ADMIN`; `/api/me` answers with that role; a wrong password is `401`
  with no body, as for every role.
- **The list:** with `OtherTenantFixture`'s second Tenant present, the list
  contains `Default Tenant` and that Tenant with their Manager counts, does
  not contain the Operator Tenant, and is in name order. A Tenant created in
  the test with no Manager shows `0`. A second Manager added to a Tenant
  raises its count by one.
- **The fence, both ways:** `managerToken()`, `agentToken()` and
  `testerToken()` get `403` on `GET /api/super-admin/tenants`; no token gets
  `401`. `superAdminToken()` gets `403` on a representative route from every
  matcher group: a Manager-only list (`/api/clients`, `/api/agents`), the
  open Contract list (`GET /api/contracts`), a Contract-scoped Fleet, Requests,
  Fees and Client Invoice route, `/api/review-queue`, `/api/carriers`,
  `/api/stock`, and an Agent Invoice route. One parameterised test covers
  the list.
- **Self-service password change:** `ChangeOwnPasswordApiTest`'s
  parameterised test gains `SUPER_ADMIN`, on a SuperAdmin Login the test
  creates in the Operator Tenant. The `IllegalArgumentException` branch goes.
- **Every other role unchanged:** the whole suite passes unedited apart from
  new tests and the `ChangeOwnPasswordApiTest` case above.
- **The migration:** a migration test in the shape of
  `GlobalUsernameIndexMigrationTest` shows exactly one Operator Tenant, that a
  second `operator = true` row is refused by the index, and that every
  pre-existing Tenant has `operator = false`.
- **Frontend component tests (Vitest + Testing Library):** the Tenant table
  renders name, count and date, and the empty state; `landingPathForRole`
  returns `/super-admin` for `SUPER_ADMIN`; `requireSuperAdmin` redirects on a
  non-OK `/api/me` and on every other role.
- **One new e2e spec, `super-admin-signs-in.spec.ts`,** using `helpers.ts`'
  `login`/`logout`: sign in as the seeded SuperAdmin, land on the SuperAdmin
  Console, see `Default Tenant` with its Manager count; open `/manager`,
  `/agent` and `/client` and land on `/login`; sign in as the seeded Manager
  and open `/super-admin` and land on `/login`. It never changes the seeded
  password. A failure is judged against a controlled comparison on an idle
  machine, never waved off as a flake.

## Decisions taken

### Following the human's answers

- **A SuperAdmin's Login lives in a hidden Operator Tenant** that holds only
  SuperAdmins and never shows in the Tenant list (human, 2026-10-08, as
  recommended). Sign-in, the token and every Tenant-scoped query keep working
  unchanged, and a mistake would show an empty Tenant rather than another
  Tenant's data. A Login with no Tenant was rejected.
- **The first SuperAdmin is seeded by the database setup** as
  `superadmin@example.com` with a documented password, changed at first
  sign-in through **Change password** (human, 2026-10-08, as recommended),
  exactly as `manager@example.com` exists today. No start-up hook and no
  one-off command create one. Until it is changed, the documented password
  opens the SuperAdmin Console in a deployment; the human accepted that.
- **A SuperAdmin sees the Tenant list and, in later features, that Tenant's
  Manager Logins; never a Tenant's Agents, Clients or invoices** (human,
  2026-10-08, as recommended). This feature shows name, Manager count and
  creation date only; the backend fence (stories 14, 15) holds the line.
- **Resetting a Manager's password is the SuperAdmin's job** (human,
  2026-09-30). It is not in this feature; the console built here is where it
  will go.
- **Passwords are generated by the product, never typed** by one person for
  another (human, 2026-10-01/02). Nothing here sets anyone's password. The
  seeded SuperAdmin's documented password is a deployment seed, like the
  seeded Manager's, and the SuperAdmin replaces it through their own
  **Change password**.
- **A Manager administers only Agents and Testers in their own Tenant**
  (human). Nothing here changes what a Manager can do, and a Manager cannot
  reach the SuperAdmin Console.

### Taken alone

- **The console lives at `/super-admin`, with the API under
  `/api/super-admin/**`.** It mirrors the per-role surfaces and gives the
  security configuration one prefix to fence.
- **A SuperAdmin is fenced out of every other route by one deny rule on the
  role, placed first.** The alternative, auditing every `authenticated()`
  matcher and every per-resource guard for a fourth role, is larger and
  easier to get wrong. One rule is testable with one parameterised test.
- **`403`, not `404`, when a SuperAdmin calls a Tenant-scoped route.** The
  refusal is about the role, decided before any id is looked up, so it
  reveals nothing about a Tenant.
- **The Operator Tenant is marked by a boolean column with a partial unique
  index, not recognised by a fixed id or a name.** A fixed id in code is a
  magic number; a name can be edited later. The column states the meaning
  where the data lives.
- **The Operator Tenant is named `Operator`.** It never appears in the list,
  but it appears in logs and the database, where a plain name helps.
- **The list is ordered by name, case-insensitively.** A person scanning for
  one Tenant reads names; creation order is shown in its own column.
- **`managerCount` counts every Manager Login, deactivated included.** No
  Manager can be deactivated until the epic's fourth feature, which decides
  whether the count should then distinguish them.
- **No paging.** A handful of Tenants, created by hand.
- **The Tenant directory is a separate read service, not a method on an
  existing Tenant-scoped repository or service.** It is the only code that
  reads across Tenants on purpose, so it is kept apart and named for it.
- **Rows are not links, and there is no New Tenant button.** The next
  feature decides both; a dead link or a disabled button would only invite
  questions.
- **The shell reuses the Manager Console's look with its own label and one
  nav item.** A distinct colour for the SuperAdmin would be new design
  language for a one-table surface.
- **`requireSuperAdmin` in the same shape as the other guards; the other
  guards are not changed.** They already compare roles exactly.
- **Testing: the existing HTTP seam, with `superAdminToken()` added to
  `IntegrationTest`.** Prior art is `SecondTenantSignInApiTest`,
  `ChangeOwnPasswordApiTest` and `OtherTenantFixture`. The fence is proven
  by status codes, not by reading configuration.
- **Tests never change the seeded SuperAdmin's password.** Seeded
  credentials underpin the token helpers and the e2e suite, and e2e state
  does not roll back.

## Open questions

None

## Acceptance walkthrough

1. [agent] Run the migrations on a fresh database and show exactly one Operator Tenant, named `Operator`, and one `SUPER_ADMIN` Login, `superadmin@example.com`, belonging to it. Show every pre-existing Tenant has `operator = false`, and that inserting a second operator Tenant is refused. (stories: 1, 11)
2. [agent] `POST /api/auth/login` as `superadmin@example.com` with the documented password and show `200` with role `SUPER_ADMIN`. With a wrong password, show `401` with no body. Call `GET /api/me` with the token and show role `SUPER_ADMIN`. (stories: 1, 2)
3. [agent] With the SuperAdmin token, `GET /api/super-admin/tenants` and show `Default Tenant` with `managerCount: 1` and its creation date, and no `Operator` entry. Add `OtherTenantFixture`'s Tenant and a Tenant with no Manager, and show all three in name order, the empty one with `0`. Add a second Manager to one and show its count rise by one. Show no username appears in the response. (stories: 5, 6, 7, 8, 9, 11)
4. [agent] Call `GET /api/super-admin/tenants` with the Manager, Agent and Tester tokens and show `403` for each, and with no token show `401`. (stories: 16)
5. [agent] With the SuperAdmin token, call a representative route of every matcher group (Clients, Agents, Contracts list, a Contract's Fleet, Requests, Fees and Client Invoice, the Review Queue, Carriers, Stock, an Agent Invoice) and show `403` for every one. (stories: 15)
6. [agent] On a SuperAdmin Login created for the test, change its password through `POST /api/me/password`, show the new one signs in and the old one is refused. (stories: 12)
7. [agent] Show `git diff` adds exactly one migration, at the next free version, that only adds a column, an index and two rows. Run `AuthLoginTest`, `SecondTenantSignInApiTest`, `ChangeOwnPasswordApiTest` and `mvn verify`, then the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green. Show no existing golden moved. (stories: 19, 20)
8. [agent] In a browser, sign in as the seeded SuperAdmin and show the landing page is `/super-admin`, the shell reads **SuperAdmin Console** with one nav item, **Tenants**, and the viewer menu shows the SuperAdmin's email. (stories: 2, 3, 4)
9. [agent] On the console, show the **Tenants** table with name, Manager count and creation date for each Tenant, no `Operator` row, no links on rows and no **New Tenant** button. Against the stub backend with no Tenants, show "No Tenants yet." (stories: 5, 6, 7, 10, 11)
10. [agent] Still signed in as the SuperAdmin, open `/manager`, `/manager/clients`, `/agent` and `/client` and show each lands on `/login`. Sign in as the seeded Manager, open `/super-admin`, and show it lands on `/login`; repeat as the seeded Agent and Tester. Signed out, open `/super-admin` and show `/login`. (stories: 14, 16, 17)
11. [agent] With an expired or deleted SuperAdmin session cookie, load `/super-admin` and show the sign-in page, not an empty table. (stories: 18)
12. [agent] Sign out from the console's viewer menu and show `/login`. (stories: 13)
13. [agent] By keyboard alone, from the sign-in page, sign in, reach the Tenants table, open the viewer menu, open **Change password**, cancel with Escape and sign out, showing a visible focus ring at each stop. At the mobile breakpoint, show the table's three columns inside the viewport. (stories: 21, 22)
14. [agent] Show the README lists `superadmin@example.com` and its documented password beside the other seeded Logins, and that `PRODUCT.md` and `docs/journeys.md` describe the SuperAdmin and four roles. (stories: 23)
15. [human] On the running app, sign in as the SuperAdmin with the documented password, change it from the console, sign out and back in with the new one, and show the documented password is now refused. Confirm the seeded first sign-in plays as decided on 2026-10-08. (stories: 1, 2, 12)
16. [human] Look at the Tenant list as the person who will look after Tenants. Confirm it shows each Tenant's name, Manager count and creation date, no Operator Tenant, and nothing of a Tenant's Agents, Clients or invoices, as decided on 2026-10-08. (stories: 5, 6, 7, 11, 14)

## Execution order

Four slices.

1. `super-admin-exists-and-signs-in` — the migration (operator column, index, Operator Tenant, seeded SuperAdmin), `Tenant.operator`, `superAdminToken()` in `IntegrationTest`, the two security rules (the `/api/super-admin/**` fence and the SuperAdmin deny rule) with their parameterised test, `SUPER_ADMIN` in `ChangeOwnPasswordApiTest`, the migration test, and the README's seeded Login. Labels: `backend`. Depends on nothing. (stories: 1, 2, 12, 15, 16, 19, 20, 23)
2. `tenant-list-api` — the Tenant directory read service, `GET /api/super-admin/tenants`, and its integration tests. Labels: `backend`. Depends on `super-admin-exists-and-signs-in`. (stories: 5, 6, 7, 8, 9, 11)
3. `super-admin-console-shell` — the `/super-admin` surface, `landingPathForRole`, the session proxy's matcher, `requireSuperAdmin`, the shell with its label and nav item, the viewer menu, a placeholder page body, component tests, and the e2e spec's sign-in, landing and fence steps. Labels: `frontend`. Depends on `super-admin-exists-and-signs-in`. (stories: 3, 4, 12, 13, 14, 16, 17, 18, 21)
4. `tenant-list-ui` — the BFF proxy, the Tenants table and empty state, the stub-backend fixture, the new goldens, the e2e spec's list step, and the delivery documentation (`PRODUCT.md`, `docs/journeys.md`, `DESIGN.md`, `ARCHITECTURE.md`). Labels: `frontend`. Depends on `tenant-list-api` and `super-admin-console-shell`. (stories: 5, 6, 7, 9, 10, 11, 22)
