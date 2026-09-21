---
feature: second-tenant-test-seam
epic: tenant-scoped-sign-in
status: approved
date: 2026-09-21
---

<!-- sdlc:template spec 1 -->

# Second-tenant test seam

## Problem

A second customer company is expected soon, and sign-in looks a username up
across **all** Tenants although usernames are only unique **within** one. The
epic exists to fix that. But nothing in the suite can currently show the
defect, or show a fix working: every test signs in as one of the three
credentials seeded into the one seeded Tenant
(`11111111-1111-1111-1111-111111111111`), and no helper produces a login
anywhere else.

So the fix would land unwitnessed. The human cannot see the defect happen
before it is fixed, and the implementer of `globally-unique-usernames` has no
red test to turn green and no regression net afterwards. Until a second Tenant
can be signed in to from a test, "a user of one Tenant cannot sign in to the
other" is an assertion nobody can make.

## Journeys

Advances `docs/roadmap/tenant-scoped-sign-in.md`. It is the first of that
epic's two features, and moves no journey to `exists` on its own — it is the
enabler the second feature needs.

- **Operate a second tenant safely** (`wanted`): this feature builds the means
  of proving it, and demonstrates the defect that blocks it. The journey
  reaches `exists` only with `globally-unique-usernames`.
- **Sign in** (`exists`): unchanged. Its recorded known defect becomes
  *demonstrable* here, and is removed by the next feature.

## Goals / Non-goals

Goals:

- The test suite can create a second Tenant that has a working login, and sign
  in against it through the real `POST /api/auth/login` endpoint.
- A test can assert *which* Tenant a sign-in resolved to.
- A test can prove a second Tenant's Manager, once signed in, reads only that
  Tenant's own data.
- One test records today's wrong behaviour when a username exists in two
  Tenants, on `main`, before anything is fixed.

Non-goals — each one is something a reasonable agent would otherwise build:

- **Not fixing the defect.** `UserRepository.findByUsername(String)` stays
  tenant-blind and `AppUserDetailsService` keeps calling it. The global
  uniqueness rule, the refusal at creation and the audit of the remaining
  single-tenant assumptions all belong to `globally-unique-usernames`.
- **No production source change at all.** The whole feature lives under the
  backend's test sources. If a change to `src/main` seems necessary, that is a
  finding to report, not work to do.
- **No API change.** In particular the login response is not given a
  `tenantId` field to make the assertion convenient — the token already
  carries the claim, and adding a field would be a user-visible contract
  change in a feature that promises none.
- **No new Tenant-creation surface.** Tenants and their first Manager continue
  to be created directly in the database; the SuperAdmin role keeps having no
  screens.
- **No migration.** V54 is the highest on `main`; V55 stays free for the next
  feature.
- **No e2e, frontend or visual change.** No surface moves, so no golden moves.
- **No second seeded Tenant in the Flyway seed or the demo data loader.** The
  second Tenant is built per-test and rolled back, never seeded into every
  environment.

## User stories

1. As an implementer, I want one fixture call that creates a second Tenant
   with a working login in it, so that a test can sign in somewhere other than
   the seeded Tenant.
2. As an implementer, I want that call to hand back the second Tenant's id
   alongside the credentials it created, so that a test can compare where a
   sign-in landed against where it should have landed.
3. As an implementer, I want a shared helper that answers "which Tenant does
   this token belong to", so that the question is one call in a test rather
   than JWT plumbing repeated in each.
4. As an implementer, I want a test proving a login that exists only in a
   second Tenant signs in successfully and gets a token naming that Tenant, so
   that the seam is proven to work rather than merely written.
5. As an implementer, I want a test proving that Manager then reads only its
   own Tenant's data through a real API call, so that the seam reaches past
   the token into a request and is worth building a fix on.
6. As an implementer, I want the fixture to build the second Tenant and its
   Manager straight through the repositories, so that the bootstrap gap — no
   API creates a Tenant, and creating a Manager needs an existing Manager of
   that Tenant — does not block the seam.
7. As the human closing this epic, I want one test that records what happens
   today when a username exists in two Tenants, so that the defect is
   demonstrated on `main` before anyone claims to have fixed it.
8. As the implementer of `globally-unique-usernames`, I want that pinning test
   to say in its own text what will delete it and why, so that the fix does
   not have to guess which assertions were a deliberate temporary record of a
   defect.
9. As the implementer of `globally-unique-usernames`, I want the second-Tenant
   seam tests to assert only properties that stay true after the fix, so that
   they become its regression net instead of work to rewrite.
10. As a Manager, Agent or Tester using the product, I want this step to change
    nothing I can see, so that the enabler carries no release risk.

## Solution

### Extend the fixture that already exists

`OtherTenantFixture` is already the project's answer to "build data in another
Tenant": it writes through the repositories (its own comment records that no
API creates a Tenant), each of its methods builds one complete scenario in a
brand-new Tenant, and five API test classes already `@Import` it. Its one gap
is that it creates no login rows at all. This feature closes that gap; it does
not introduce a second fixture.

It gains one method, returning a small record rather than a bare id, because a
sign-in assertion needs three things at once — the Tenant to compare against,
the username, and the plaintext password (the stored hash is useless to a
caller). The method takes the username and password, so the *same* method
serves both the clean case (a username nobody else has) and the collision case
(the seeded Manager's own username, with a different password). Shape of the
decision:

```
record OtherTenantLogin(UUID tenantId, UUID userId, String username, String password, UUID clientId)

OtherTenantLogin managerLoginInAnotherTenant(String username, String password)
```

It creates, in one brand-new Tenant: a `MANAGER`-role `User` whose
`passwordHash` comes from the context's own `PasswordEncoder` bean (never a
literal hash pasted into a test), and one `Client` belonging to that same
Tenant. The Client rides along because a login with nothing behind it can only
prove half the point — story 5 needs the second Tenant's Manager to have
something of its own to read, and the seeded Tenant's Clients to be absent
from that read. `MANAGER` is the role because a Manager needs no companion
record to be coherent, unlike an `AGENT` login, which would drag an `Agent`
row and a Country into a fixture about sign-in.

### Asserting which Tenant a sign-in resolved to

The JWT already carries a `tenantId` claim — `JwtService.issueToken` writes
it, `JwtAuthenticationFilter` reads it back, and every authenticated request
takes its Tenant from the token rather than from a lookup. The login response
body does not expose it, and will not be made to. So the assertion reads the
claim: `IntegrationTest` gains a protected helper that takes a token and
returns its Tenant id, using the `JwtService` bean already in the context.
That helper is the single place any test in the suite — including the next
feature's — asks this question.

### What the tests prove

Two seam tests, written to survive the next feature untouched:

- A login that exists **only** in a second Tenant signs in through
  `POST /api/auth/login` and its token names that Tenant — not the seeded one.
- That Manager's token, used against a real listing endpoint, returns that
  Tenant's own Client and does not return the seeded Tenant's Clients.

And one pinning test, written to be deleted:

- A username that exists in **both** the seeded Tenant and a second Tenant.
  This is the record of the defect.

### The pinning test, precisely

The epic's feature line predicts the colliding username "authenticates against
whichever row is found first". The code makes that prediction doubtful:
`findByUsername` is a Spring Data derived query returning `Optional<User>`, and
a derived `Optional` query that matches two rows raises an
incorrect-result-size failure rather than picking one — which would surface at
`/api/auth/login` as a failure response, not as a sign-in to the wrong Tenant.
Either outcome is the same defect (sign-in cannot tell two Tenants' identical
usernames apart) and either is removed by making usernames globally unique.

So the ticket's first act is to **observe** the real response once and pin
exactly that. The test is named for the property — a colliding username does
not sign the caller in to their own Tenant — and its body asserts the concrete
observed response, with a comment recording both what the epic predicted and
what actually happens. Whichever it turns out to be, the observation is
reported back so the epic's wording can be corrected.

The test must not assert anything the fix will simply delete beyond the
defect itself: no assertion about *which* of the two rows wins, no assertion
about an error message's text, nothing about `findByUsername`'s signature.

**How this test changes when `globally-unique-usernames` lands.** It is
deleted and replaced, not adapted. That feature adds a global unique index
above `uq_users_tenant_username`, at which point the fixture's second insert
of a colliding username fails at flush — the test cannot even reach its
assertion. Its replacement, owned by that feature, asserts that creating a
login whose username is already taken in another Tenant is **refused**. The
test therefore carries, in its own Javadoc, the name of the feature that
deletes it and the assertion that replaces it. The fixture method itself
survives the fix unchanged: it takes a username, it is not shaped around
collisions.

### Debt and hazards examined, with nothing to prefactor

- `ClientInvoiceByIdApiTest` and `AgentInvoiceByIdApiTest` assert on
  tenant-blind `repository.count()`. Checked: both are **delta** assertions —
  a baseline captured inside the test method, compared after the requests, in
  a transaction that rolls back — not absolute counts, and both already run
  alongside `OtherTenantFixture` creating extra Tenants today. The counted
  repositories (`clientInvoiceRepository`, `agentInvoiceRepository`,
  `agentStandingAmountRepository`) are ones the new fixture method does not
  write to at all. `DemoDataLoaderApiTest` counts `clientRepository` but does
  not import the fixture. **No prefactoring is needed and none should be
  done.** The only rule this leaves behind: a fixture call must not be placed
  between a baseline count and its re-count.
- `UserRepository.findByAgentId` and `CallerIdentityResolver`'s lookup by user
  id are also unscoped. Both are safe (UUIDs do not collide) and both belong
  to the next feature's audit. Not touched here.
- A schema-level migration test (prior art:
  `SmartphoneOwnerMigrationTest`) is **not** wanted. Nothing about the schema
  changes here, and `uq_users_tenant_username` already permits the collision.

## Design direction

N/A — no user interface.

## Constraints

- Every change is under `backend/src/test`. `backend/src/main` is not modified.
- No Flyway migration. V54 is the highest on `main`; V55 remains free.
- Maven runs with `JAVA_HOME=/opt/homebrew/opt/openjdk@21` (JDK 26 breaks
  Lombok).
- Tests run under `IntegrationTest`: the singleton Testcontainers Postgres
  shared across the run, each test method in a transaction that rolls back.
  The second Tenant must therefore never be seeded or left behind.
- Password hashes are produced by the application's own `PasswordEncoder`
  bean (BCrypt), never hardcoded.
- The seeded Tenant id `11111111-1111-1111-1111-111111111111`, the seeded
  credentials and `SEEDED_AGENT_ID` stay exactly as they are; no existing test
  changes meaning.
- Checkstyle is part of `mvn verify` and must stay clean.
- No e2e, visual-golden or frontend change.

## Testing decisions

- **Seam: the existing HTTP API seam.** Every test here is a subclass of
  `IntegrationTest`, driving the real `POST /api/auth/login` and real listing
  endpoints through MockMvc against a real Postgres. This is the highest seam
  available and the one the suite already uses; no unit test of
  `AppUserDetailsService`, no repository-level test, no new seam.
  Prior art: `AuthLoginTest` (the login endpoint's own tests),
  `ClientInvoiceByIdApiTest` and `AgentInvoiceByIdApiTest` (the shape of a
  cross-tenant assertion driven from `OtherTenantFixture`).
- **Fixture: the existing `OtherTenantFixture`, extended**, imported with
  `@Import` exactly as the five classes already using it do. Not a new fixture
  class, not a test-only Spring configuration.
- **The token-tenant helper lives on `IntegrationTest`**, next to
  `loginAs`/`managerToken`, so the next feature inherits it.
- Tests assert external behaviour only: the HTTP status and body of a
  sign-in, and the `tenantId` claim of the token the endpoint issued — which
  is the contract every authenticated request already reads. No test asserts
  the repository method called, the query issued, or the exception type thrown
  inside the service.
- The pinning test is the one deliberate exception to "assert what will stay
  true": it records a defect and is labelled as disposable in its own text
  (stories 7, 8).

## Decisions taken

- Extend `OtherTenantFixture` rather than add a new login fixture — it is
  already the one place that builds another Tenant through the repositories,
  already imported by five test classes, and its only gap is login rows.
- The new fixture method returns a record (Tenant id, user id, username,
  plaintext password, Client id) rather than a bare id — a sign-in assertion
  needs the Tenant to compare against and the plaintext password, which cannot
  be recovered from the stored hash.
- The method takes the username and password as parameters, so the same call
  serves both the clean case and the colliding case — one method, not a
  separate "colliding" one that the next feature would have to hunt down.
- The second Tenant's login is `MANAGER`-role — it needs no companion `Agent`
  row or Country to be coherent, unlike an `AGENT` login.
- The method also creates one `Client` in that same Tenant — story 5 needs the
  signed-in second-Tenant Manager to have data of its own to read, and every
  existing method of this fixture already builds one complete scenario per
  call.
- Password hashes come from the context's `PasswordEncoder` bean — a pasted
  literal hash would silently rot if the encoder ever changed.
- The Tenant a sign-in resolved to is read from the token's `tenantId` claim,
  via a helper on `IntegrationTest` — the claim is already the contract every
  authenticated request reads, and this avoids adding a field to the login
  response (which would be a user-visible change this feature promises not to
  make).
- No migration test of the schema — nothing about the schema changes, and the
  constraint that permits the collision already exists.
- No prefactoring of the `repository.count()` assertions in the two `ByIdApiTest`
  classes — they were checked and are delta assertions over repositories this
  fixture does not write to.
- The pinning test pins the behaviour actually observed on `main`, named for
  the property rather than the mechanism — the epic's prediction of "whichever
  row is found first" is not certain to be what the code does, and a test must
  record reality, not a forecast.
- Split into two tickets, fixture-and-seam-tests before the pinning test — the
  pinning test needs the fixture, and keeping the disposable test in its own
  ticket and commit makes it trivially revertible by the next feature.

## Open questions

None. This feature is test infrastructure: it changes no behaviour a user can
see, adds no schema, and touches no production source. Nothing here is hard to
undo.

(One observation is reported to the human alongside this spec rather than
asked as a question: the epic's feature line predicts the colliding username
"authenticates against whichever row is found first", and the code suggests a
failure response is more likely. The ticket observes and pins whichever it is;
the epic's wording may then want a correction. No decision is blocked either
way, since the next feature removes both outcomes.)

## Acceptance walkthrough

1. [agent] Run the new sign-in test class alone with `JAVA_HOME` on JDK 21 and
   show every test name and a green result. (stories: 4, 5, 7)
2. [agent] Show the test that signs in with a login existing only in a second
   Tenant: it gets 200, and the Tenant id read from the returned token equals
   the fixture's Tenant and is not
   `11111111-1111-1111-1111-111111111111`. (stories: 1, 2, 3, 4)
3. [agent] Show the test where that second-Tenant Manager calls a listing
   endpoint with its own token: the response contains that Tenant's own Client
   and none of the seeded Tenant's. (stories: 5, 6)
4. [agent] Show the pinning test: a username created in both Tenants, and the
   concrete response asserted, with the comment stating what the epic
   predicted and what actually happens. (stories: 7)
5. [agent] Show that the pinning test's own text names
   `globally-unique-usernames` as the feature that deletes it and states the
   assertion that replaces it. (stories: 8)
6. [agent] Run the full backend suite (`mvn verify`) green, and call out
   `ClientInvoiceByIdApiTest`, `AgentInvoiceByIdApiTest` and
   `DemoDataLoaderApiTest` as passing by name — the `count()` hazard.
   (stories: 9, 10)
7. [agent] Show the feature's full diff stat: every changed file under
   `backend/src/test`, no file under `backend/src/main`, no file under
   `db/migration`, and V55 still unused. (stories: 10)
8. [human] Read the pinning test and confirm that what it pins is the defect
   the epic means to fix, and that deleting it is the right move once
   usernames are globally unique. (stories: 7, 8)

## Execution order

1. `second-tenant-login-fixture` — the `OtherTenantFixture` method that
   creates a Tenant, a Manager login and a Client; the token-tenant helper on
   `IntegrationTest`; and the two seam tests that sign in against a second
   Tenant and read only its data. (stories 1-6, 9, 10)
2. `pin-colliding-username-sign-in` — observes and pins today's behaviour for a
   username present in two Tenants, marked with what deletes it. Depends on
   ticket 1. (stories 7, 8)
