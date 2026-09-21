---
feature: globally-unique-usernames
epic: tenant-scoped-sign-in
status: approved
date: 2026-09-21
---

<!-- sdlc:template spec 1 -->

# Globally unique usernames

## Problem

Sign-in looks a username up across **all** Tenants, but a username is only
unique **within** one. `second-tenant-test-seam` ran the case and recorded what
actually happens: `POST /api/auth/login` answers **401 Unauthorized with an
empty body**. `UserRepository.findByUsername`'s derived `Optional` query fails
on two matching rows and Spring Security turns that into an ordinary
authentication failure.

So the defect is a **lockout, not a cross-tenant leak**. Nobody reaches another
Tenant's data through it. But the day a second customer company is operated in
this deployment, any person whose email already exists in the other Tenant is
locked out of the product entirely, and what they see is indistinguishable from
a mistyped password — there is nothing to support, nothing to read in a log
beyond `login failed username=…`, and no way for a Manager to fix it from any
screen that exists.

It is also invisible at the moment it is created. A Manager creating an Agent
or a Tester today is checked only against their own Tenant, so a Manager can
hand out a login that has already been destroyed by a row they are not allowed
to see and will never be shown.

## Journeys

Advances `docs/roadmap/tenant-scoped-sign-in.md`. It is the second and last
feature of that epic and closes it, together with the audit the epic asks for.

- **Operate a second tenant safely** (`wanted` → `exists`): with two Tenants
  present, no username can exist in both, so each user signs in to their own
  Tenant and reads only their own Tenant's data. Proved at the API seam built
  by `second-tenant-test-seam`, since nothing in the UI creates a Tenant.
- **Sign in** (`exists`, stays `exists`): its recorded known defect is gone.
  `docs/journeys.md`'s "Known defect, scoped to `tenant-scoped-sign-in`" line
  is removed as part of this feature.

## Goals / Non-goals

Goals:

- A username is unique across the whole deployment, enforced by the database.
- Every path that creates a login refuses a username already taken in **any**
  Tenant, with a machine-readable reason the frontend can show on the field.
- Leading/trailing whitespace and letter case cannot produce two logins that a
  person would read as the same address.
- The migration is safe on existing data, and **fails loudly** — naming the
  offending usernames — rather than picking a winner, if a deployment does hold
  a duplicate.
- The audit the epic asks for: every remaining single-tenant assumption named,
  with what was done about each.

Non-goals — each is something a reasonable agent would otherwise build:

- **No tenant selector at sign-in.** No subdomain, no Tenant field, no
  "choose your company" step. The human ruled this out at planning; global
  uniqueness is the chosen alternative and is not reopened here.
- **No change to the sign-in lookup.** `UserRepository.findByUsername(String)`
  keeps its signature and its exact-match semantics;
  `AppUserDetailsService` is not touched. Sign-in does **not** become
  case-insensitive — a user who typed `Manager@example.com` at creation still
  signs in with that exact spelling, as today. Making sign-in
  case-insensitive is a separate, user-visible change, recorded under
  `## Further considerations`.
- **No data rewrite.** The migration never edits, renames, merges or deletes a
  `users` row. If it cannot proceed it stops.
- **No dropping of `uq_users_tenant_username`.** V1's Tenant-scoped constraint
  stays exactly as it is; the global index is added above it.
- **No username change, password reset, or login deactivation.** Those belong
  to the `Keep a login working over time` journey, which is still `wanted`.
- **No SuperAdmin surface and no Tenant-creation API.** Tenants keep being
  created directly in the database.
- **No new e2e or visual golden.** No surface is added or moved; the only
  frontend change is the copy of one existing error message. The journey's
  proof lives at the API seam, because no UI can create the second Tenant it
  needs.
- **No retrofit of `findByAgentId` or `CallerIdentityResolver`.** Both are
  unscoped and both are safe (a UUID does not collide). They are *named in the
  audit*, not rewritten — a scoping change there would be motion with no
  defect behind it.

## User stories

1. As a person whose email address also exists under another Tenant, I want
   sign-in to resolve to exactly one login, so that I am not locked out by a
   401 I cannot tell from a mistyped password.
2. As a Manager creating an Agent together with its login, I want an email
   already taken anywhere in the deployment refused at creation, so that I
   never hand someone a login that cannot work.
3. As a Manager giving an existing Agent a login, I want the same refusal, so
   that the two paths cannot diverge.
4. As a Manager creating a Tester login, I want the same refusal, so that the
   rule does not depend on which kind of person I am creating.
5. As a Manager creating a Tester, I want "that email is already in use" told
   apart from "this client already has a primary contact", so that I know
   which field to correct instead of guessing between two causes.
6. As a Manager, I want the refusal shown inline on the email field with an
   actionable message, so that I can correct it without leaving the dialog.
7. As a Manager, I want a stray leading or trailing space in an email not to
   create a second login, so that two rows that read identically cannot exist.
8. As a Manager, I want a difference in letter case alone not to create a
   second login, so that `A.Patel@example.com` and `a.patel@example.com`
   cannot both be handed out.
9. As a Manager of one Tenant, I want the refusal to say no more about another
   Tenant's data than it must, so that the product does not become a way to
   probe which email addresses exist in the deployment. (Settled at the spec
   gate; see `## Decisions taken`.)
10. As the operator applying this upgrade, I want the migration to stop and
    name the offending usernames if any username exists under more than one
    Tenant, so that a person decides who keeps the address rather than the
    database picking silently.
11. As the operator applying this upgrade to a clean deployment, I want the
    migration to apply with no manual step, so that the normal case costs
    nothing.
12. As the human closing this epic, I want the pinning test replaced by one
    asserting that a login whose username is already taken in another Tenant is
    refused, so that the defect's record becomes the fix's regression net.
13. As the human closing this epic, I want the second-Tenant seam tests to keep
    passing untouched, so that the fix is proven not to have broken legitimate
    second-Tenant operation.
14. As the human closing this epic, I want an audit naming every remaining
    single-tenant assumption and what was done about each, so that the epic
    closes on evidence rather than on a claim.
15. As a Manager, Agent or Tester already using the product in the one seeded
    Tenant, I want nothing else about my day to change, so that a correctness
    fix carries no release risk.

## Solution

### The database is what makes the rule true

`users` gains one **global unique index over the normalized username**, in
migration **V55** (V54 is the highest on `main`):

```
CREATE UNIQUE INDEX uq_users_username_global ON users (lower(btrim(username)));
```

`uq_users_tenant_username` stays. It is now subsumed, but removing a constraint
is a schema change with no defect behind it, and keeping it means no existing
violation-handling path changes meaning.

The migration runs a **pre-check before creating the index**: it selects every
`lower(btrim(username))` having more than one `users` row, and if any exists it
raises an exception naming each offending username with the count and the
Tenant ids holding it. Flyway then fails and the deployment stays on V54 with
nothing written. This is deliberately louder than letting `CREATE UNIQUE INDEX`
fail on its own, whose error names one arbitrary duplicated key and no Tenant.

Reconciled against the data that exists: no username appears under two Tenants
today. V2 seeds `manager@example.com`; V3 seeds `agent@example.com` and
`tester@example.com`; all three sit in the one seeded Tenant
`11111111-1111-1111-1111-111111111111`. `DemoDataLoader` writes
`priya.shah@example.com`, `dana.whitfield@solsticeretail.example` and
`noah.kim@harborline.example` under that same single `TENANT_ID`. The
pre-check exists for the case the seeds cannot describe: a developer's own
database with hand-made rows.

### Normalization, the same three layers as `carrier-catalog`

Usernames follow the pattern the Carrier name already established, because the
failure being prevented is identical — two rows a person reads as the same
name:

1. **Input**: every creation path trims the username (`strip()`) before it is
   read, stored or checked, exactly as the Carrier controller does.
2. **Repository**: the pre-insert existence check is case-insensitive over the
   trimmed value.
3. **Database**: the index itself is over `lower(btrim(username))`, so no path
   — including a direct `INSERT`, a seed, or `DemoDataLoader` — can bypass it.

The stored value keeps the spelling the Manager typed (minus the surrounding
whitespace); only uniqueness is case-insensitive. Sign-in is unchanged and
stays exact-match, which is safe precisely because at most one row can now
match any spelling.

### One rule, five creation paths

The rule is global from here on:

- `AgentLoginService.requireLoginCreatable` stops asking
  `existsByTenantIdAndUsername` and asks a new **global, case-insensitive,
  trim-insensitive** existence query on `UserRepository`. The Tenant-scoped
  method is deleted if it has no other caller.
- `AgentLoginService.create`'s `DataIntegrityViolationException` inspection
  learns the new index name **in addition to** `uq_users_tenant_username`:
  either name means the username is taken, and both map to the same
  `AgentLoginConflictException(Reason.USERNAME_TAKEN)` → 409 `{code, message}`.
  Which of the two indexes Postgres reports first is not something a caller
  should ever be able to tell apart.
- `TesterController.create` gets the same pre-check and the same
  machine-readable conflict — see below.
- The V2/V3 seeds and `DemoDataLoader` need no logic change (every username
  they write is already distinct), but they are covered by the index, and the
  loader's idempotence marker is unaffected.

### The Tester path's asymmetry is fixed here, not left

`TesterController.create` today catches `DataIntegrityViolationException` and
throws a bare `ConflictException` with **no `code`**, so
`create-tester-dialog.tsx` can only say "This client already has a primary
contact, or that email is already in use." — one message for two unrelated
causes. It also calls `save` rather than `saveAndFlush`, so a constraint
violation can escape that `catch` entirely and surface at commit.

This feature fixes it, because this feature *adds a new reason the Tester path
can fail* — an address taken in a Tenant the Manager cannot see. Leaving the
ambiguous message would mean a Manager hitting the new refusal has no way at
all to understand it, which is a user-visible regression introduced by this
work. The Tester path therefore gains the shape the Agent path already has: a
pre-check before anything is written, `saveAndFlush` so a racing violation is
caught where it is thrown, and a 409 carrying `USERNAME_TAKEN` in the same
`{code, message}` body. The primary-contact conflict keeps its own distinct
409, with its own code, so the dialog can tell the two apart and point at the
right field.

### Frontend

No component, route or layout is added. `create-tester-dialog.tsx` stops
showing the two-causes-in-one-sentence message and branches on the 409's
`code`, exactly as `create-agent-login-dialog.tsx` and `create-agent-dialog.tsx`
already do via `readErrorCode`: the taken-email case shows the same wording
those two dialogs use and marks the email field; the primary-contact case keeps
its own message. `create-agent-login-dialog.tsx` and `create-agent-dialog.tsx`
are untouched — the code they already read is the code they keep receiving.

### The audit

The epic closes on an audit, not only on the fix. It is a written sweep of
every place that looks a row up without its Tenant, delivered as an `## Audit`
section of this feature's `delivery.md`, naming each finding and what was done
about it. Three are known going in and must appear:
`UserRepository.findByUsername` (fixed by this feature — now unambiguous),
`UserRepository.findByAgentId` and `CallerIdentityResolver`'s
`findById(principal.userId())` (both safe, both left alone with that reasoning
written down). Anything else the sweep turns up is either fixed inside this
feature, or recorded as debt with the reason — never left unmentioned.

### Prefactoring

None needed. The one piece of debt in the way — the Tester path's codeless
conflict — is not prefactoring; it is part of the feature, because the feature
is what makes that message wrong.

### Left for later, deliberately

- **Case-insensitive sign-in.** Uniqueness becomes case-insensitive; the lookup
  does not. Someone created as `Manager@example.com` still cannot sign in as
  `manager@example.com`. Safe, but a plausible future kindness — it belongs to
  the `Keep a login working over time` journey, alongside password reset.
- **One person, two Tenants** becomes structurally impossible. If that need
  ever appears, the epic's `## Later` already names the answer: choosing or
  being routed to a Tenant at sign-in.
- The `Tenant` glossary entry states that usernames are unique within a Tenant
  and that this is what makes tenant-blind sign-in a defect. Both halves are
  false once this ships and must be narrowed.

## Design direction

Two existing Operate surfaces are touched, by copy only:

- **Create-tester dialog** (Operate — the Manager is completing a task): the
  inline error alert already established by `dialog-error-alert.tsx` and used
  identically by the two Agent dialogs. No new element, no layout change, no
  new visual state; the reference it is pinned against is
  `create-agent-login-dialog.tsx`, whose taken-email wording it adopts verbatim.
- **Create-agent and create-agent-login dialogs** (Operate): unchanged.

No visual golden should move. If one does, that is a finding, not a recapture.

## Constraints

- Migration **V55**, the next free number (V54 is the highest on `main`). One
  migration only.
- The migration must be safe to run on a database that already holds hand-made
  rows: it writes nothing and fails cleanly if it cannot proceed.
- The global index is named `uq_users_username_global`, and that exact name is
  what the violation-handling code matches on.
- The 409 contract for a taken username is unchanged in shape:
  `{"code": "USERNAME_TAKEN", "message": "…"}`. No existing frontend caller
  may need editing to keep working.
- Maven runs with `JAVA_HOME=/opt/homebrew/opt/openjdk@21` (JDK 26 breaks
  Lombok). Checkstyle is part of `mvn verify` and stays clean.
- Backend tests run under `IntegrationTest`: singleton Testcontainers Postgres,
  each method in a transaction that rolls back. The migration test uses its own
  throwaway database, as the existing migration tests do.
- The seeded Tenant id `11111111-1111-1111-1111-111111111111` and the three
  seeded credentials stay exactly as they are.
- `SecondTenantSignInApiTest` must keep passing **untouched** — its usernames
  (`second-tenant-manager@example.com`,
  `second-tenant-clients-manager@example.com`) collide with nothing.
- `OtherTenantFixture.managerLoginInAnotherTenant` keeps its signature; only
  its colliding *caller* goes away.

## Testing decisions

- **Seam: the existing HTTP API seam**, `IntegrationTest` + MockMvc against a
  real Postgres. Every behavioural assertion is made there: creating an Agent
  with its login, giving an existing Agent a login, and creating a Tester, each
  refused 409 with `code: USERNAME_TAKEN` when the username already exists in
  **another** Tenant, built by `OtherTenantFixture`. No unit test of
  `AgentLoginService`, no repository-level test of the new query, no assertion
  about which index Postgres reported.
  Prior art: `AgentLoginApiTest` and `AgentCreationAtomicityTest` (both already
  assert `$.code == USERNAME_TAKEN` on a same-Tenant collision),
  `SecondTenantSignInApiTest` (the cross-Tenant fixture shape).
- **`CollidingUsernameSignInApiTest` is deleted, not adapted.** With the global
  index, its fixture's second insert fails at flush and the test cannot reach
  its assertion. Its own Javadoc already names the replacement, which lands in
  the same commit: creating a login whose username is already taken in another
  Tenant is refused. Its non-colliding control is not lost — the same property
  is what `SecondTenantSignInApiTest` asserts.
- **One second seam, for the migration only**: a Flyway migration test against
  its own throwaway database, because a migration's behaviour on pre-existing
  data cannot be observed from the HTTP seam at all. Prior art:
  `TrimSeedToTestBaselineMigrationTest` and `SmartphoneOwnerMigrationTest` —
  migrate to just before the target version, insert the rows the case needs,
  migrate through it, assert. Two cases: a clean database reaches V55; a
  database holding the same username under two Tenants fails, and the failure
  message names that username.
- **Normalization is asserted through the API**, not through the repository: a
  username differing from an existing one only by case, and one differing only
  by surrounding whitespace, are each refused 409 `USERNAME_TAKEN`; and the
  stored username of a successfully created login is the trimmed input, read
  back through the API response rather than out of the database.
- Tests assert external behaviour only — HTTP status, the response body's
  `code`, and the Flyway outcome. Never the repository method called, the query
  issued, or the exception type thrown inside a service.
- The frontend change is copy branching on an existing code, covered by a
  component test in the shape `create-agent-login-dialog`'s own tests already
  use (stub a 409 with each code, assert the message and the flagged field).

## Decisions taken

- **The refusal says the same thing whether the address is taken in this
  Tenant or in another: "That email is already in use. Choose another one and
  try again."** Answered by the human at the spec gate on 2026-09-21, chosen
  over a vaguer refusal and over an explicit cross-Tenant message. The Manager
  must pick a different address either way, so the actionable part is
  identical; using one message for both cases means the two cannot be told
  apart, which discloses strictly less than two distinct messages would; and a
  vague refusal reads as a bug in an internal tool. The disclosure that remains
  is bounded — the existence of one address, to an authenticated Manager, with
  no hint of which Tenant or which person holds it.

- The global index is over `lower(btrim(username))` rather than raw `username`
  — the failure being prevented is two rows a person reads as the same address,
  and `carrier-catalog` already established exactly this three-layer answer
  (`V19__create_carriers.sql`'s `lower(name)` index, a `lower(…) = lower(…)`
  repository query, `.strip()` at the controller).
- `uq_users_tenant_username` is kept rather than dropped — it is subsumed but
  harmless, and dropping it is schema churn with no defect behind it.
- The violation-name inspection in `AgentLoginService` matches **both** index
  names and maps both to `USERNAME_TAKEN` — a caller must not be able to tell
  from the response which of the two indexes fired.
- The migration fails loudly with the offending usernames rather than
  de-duplicating — silently picking a winner would destroy a working login
  belonging to a person nobody asked, and the seeds show the normal path is
  never affected.
- The pre-check is a separate statement before `CREATE UNIQUE INDEX`, not a
  reliance on the index's own error — Postgres's message names one arbitrary
  key and no Tenant, which is not enough for whoever has to resolve it.
- The sign-in lookup stays exact-match and case-sensitive — at most one row can
  now match any spelling, so it is unambiguous either way, and changing it is a
  user-visible behaviour change this feature does not need.
- The Tester path's conflict gains a `code` and a pre-check, making it
  symmetric with the Agent path — this feature adds a new reason that path can
  fail, and the existing "primary contact **or** email" message would leave a
  Manager unable to act on it.
- `TesterController` switches from `save` to `saveAndFlush`, so a racing
  violation is caught where the `catch` already expects it rather than at
  commit — the Agent path has always done this.
- No `@Transactional`-level locking or serialized check: the pre-check is a
  convenience for the common case, and the index remains the authority, which
  is the pattern already used for Carrier names.
- Tickets are cut as four slices — the index, the Agent path, the Tester path,
  the audit — rather than one, so each is demoable and the frontend copy change
  travels with the path that needs it.
- The audit is delivered as a section of `delivery.md` rather than a new
  document — the epic asks for it as evidence at closing time, and the delivery
  report is where the human reads evidence.
- The existing HTTP seam is reused for every behavioural assertion rather than
  any new one — `IntegrationTest` + `OtherTenantFixture` is already how five
  test classes make a cross-Tenant assertion, and it is the highest seam
  available.
- Exactly one new seam is added, the Flyway migration test — a migration's
  behaviour on pre-existing data is invisible from the HTTP seam, and three
  migration tests already establish the shape.

## Open questions

None. The single question this spec raised — what the refusal says when the
address is taken in a different Tenant — was put to the human at the spec gate
on 2026-09-21 and answered. It is recorded as the last entry of
`## Decisions taken`.

## Acceptance walkthrough

1. [agent] Apply the migrations to a fresh throwaway database and show Flyway reaching V55 with no manual step, and `\d users` listing `uq_users_username_global` alongside `uq_users_tenant_username`. (stories: 11)
2. [agent] Run the migration test and show both cases by name: a clean database reaches V55; a database holding the same username under two Tenants fails, with the failure message naming that username. (stories: 10, 11)
3. [agent] Signed in as the seeded Tenant's Manager, create an Agent with its login using a username that exists only in a second Tenant, and show the response is 409 with `code: USERNAME_TAKEN`, and that no Agent row was left behind. (stories: 2, 12)
4. [agent] Give an existing Agent a login with that same second-Tenant username and show the same 409 and `code`. (stories: 3)
5. [agent] Create a Tester with that same second-Tenant username and show a 409 carrying `code: USERNAME_TAKEN`, then create a second primary-contact Tester and show a 409 carrying the distinct primary-contact code. (stories: 4, 5)
6. [agent] Create a login whose username differs from an existing one only by letter case, then one differing only by surrounding whitespace, and show both refused 409 `USERNAME_TAKEN`; then create a clean login with surrounding whitespace and show the stored username came back trimmed. (stories: 7, 8)
7. [agent] Sign in as the seeded Manager and as a second Tenant's Manager and show each token naming its own Tenant, with `SecondTenantSignInApiTest` passing and its diff empty. (stories: 1, 13)
8. [agent] Show `CollidingUsernameSignInApiTest` deleted and the replacing test present, asserting that a login whose username is taken in another Tenant is refused. (stories: 12)
9. [agent] Run the full backend suite (`mvn verify`), the frontend vitest suite, typecheck and lint, and the visual suite, all green with no golden recaptured. (stories: 15)
10. [human] In the running app, open the create-tester dialog as a Manager, submit an email that already exists, and confirm the inline message names the email as the cause and marks that field — not the "primary contact or email" sentence. (stories: 5, 6)
11. [human] Read the `## Audit` section of the delivery report and confirm every remaining single-tenant assumption is named with what was done about it, including `findByAgentId` and `CallerIdentityResolver`. (stories: 14)
12. [human] Read the refusal message shown for an address taken in another Tenant and confirm it discloses no more than you accepted in `## Open questions`. (stories: 9)

## Execution order

1. `global-username-index` — V55: the duplicate pre-check and the
   `lower(btrim(username))` unique index, its migration test, and the
   violation-name inspection taught the new index name so a flush-time
   collision still answers 409 `USERNAME_TAKEN`. (stories 10, 11)
2. `refuse-taken-username-on-agent-login` — the global, case- and
   trim-insensitive pre-check on both Agent login paths; deletes
   `CollidingUsernameSignInApiTest` and adds its replacement. Depends on
   ticket 1. (stories 1, 2, 3, 7, 8, 12, 13)
3. `refuse-taken-username-on-tester-login` — the same rule on the Tester path,
   with its coded 409 and the dialog copy that tells the two conflicts apart.
   Depends on ticket 1. (stories 4, 5, 6, 9, 15)
4. `single-tenant-assumption-audit` — the sweep and its written record in the
   delivery report. Depends on tickets 2 and 3. (stories 14)
