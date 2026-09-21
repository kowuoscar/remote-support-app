---
id: global-username-index
title: Enforce a global unique username index without leaving the branch red
status: in-progress
depends_on: []
labels: [backend, auth, testing]
stories: [1, 2, 10, 11, 12, 13]
---

## Context

`spec.md` Solution ("The database is what makes the rule true", "One rule,
five creation paths") and Constraints (migration **V55**, the next free
number — `docs/agents/implementer-notes.md` confirms V54 is the frontier;
list `backend/src/main/resources/db/migration` yourself before picking a
number if this has changed). Decisions taken: the pre-check is a separate
statement before `CREATE UNIQUE INDEX` naming every offending username, its
count and its Tenant ids; `uq_users_tenant_username` is kept, not dropped;
the violation-name inspection in `AgentLoginService` must recognize the new
index's name in addition to the old one.

This ticket is the feature's foundation, but it is not inert: `AgentController`'s
`POST /api/agents` (create an Agent with its login) already has **no
username pre-check at all** — its own Javadoc says so — and relies entirely
on `AgentLoginService.create`'s flush-time catch. The moment the global index
exists, a cross-Tenant collision on that endpoint starts hitting it, so this
ticket's own violation-name update is what keeps that endpoint answering 409
`USERNAME_TAKEN` instead of an unmapped 500 — the same catch already handles
same-Tenant collisions today, this only teaches it the new index's name.

It also forces a test-suite consequence that cannot wait: `OtherTenantFixture.
managerLoginInAnotherTenant` inserts its second Tenant's user via
`userRepository.saveAndFlush(...)`, straight through the repository. The
moment the global index exists, `CollidingUsernameSignInApiTest`'s own call
to that method with the seeded Manager's username fails at flush, before the
test can reach its assertion — its own Javadoc already says as much and names
the replacement. That deletion-and-replacement cannot be deferred to a later
ticket without leaving this ticket's own merge red, so it is this ticket's
job, in the same commit, per `docs/agents/ticket-critic.md`'s R6. The
replacement is demonstrated on the same no-pre-check endpoint named above,
which is exactly `CollidingUsernameSignInApiTest`'s own Javadoc's replacing
assertion: "creating a login whose username is already taken in another
Tenant is refused."

This ticket also adds the shared global, case-insensitive, trim-insensitive
existence query `UserRepository` needs (Solution, "One rule, five creation
paths") — `refuse-taken-username-on-agent-login` and
`refuse-taken-username-on-tester-login` each wire it into their own path
independently, which is why neither blocks the other.

## Acceptance criteria

- [ ] Migration **V55** adds a unique index named `uq_users_username_global` over `lower(btrim(username))` on `users`, without dropping or altering `uq_users_tenant_username`.
- [ ] Before creating the index, the same migration runs a pre-check that fails the migration (Flyway stops on V54, nothing written) when any normalized username exists under more than one `users` row, naming each offending username, its row count and the Tenant ids holding it in the failure.
- [ ] Applying every migration to a fresh throwaway database reaches V55 with no manual step, and the database's own catalog (e.g. `\d users`) lists both `uq_users_username_global` and `uq_users_tenant_username`.
- [ ] `AgentLoginService`'s violation-name inspection recognizes `uq_users_username_global` in addition to `uq_users_tenant_username`, mapping either to `AgentLoginConflictException(Reason.USERNAME_TAKEN)` → 409 `{code, message}`: demonstrated by calling `POST /api/agents` (create an Agent with its login) with a username that already exists under a different Tenant (built via `OtherTenantFixture`), and showing 409 `USERNAME_TAKEN` — not a 500 — with no Agent, standing-amount or User row left behind.
- [ ] `CollidingUsernameSignInApiTest` and its control test are deleted, and a replacement test is added in the same commit, in its own test class, asserting exactly the property above (a login whose username is already taken in another Tenant is refused at creation) — the comment/Javadoc explains it replaces the deleted pinning test.
- [ ] `SecondTenantSignInApiTest` keeps passing unmodified; signing in as the seeded Tenant's Manager and as a second Tenant's Manager (built via `OtherTenantFixture`) still each name their own Tenant.
- [ ] `mvn -f backend/pom.xml verify` (`JAVA_HOME` on JDK 21) is green, including `AgentLoginApiTest` and `AgentCreationAtomicityTest` passing unmodified.

## Tests

- **Seam, migration:** a Flyway migration test against its own throwaway database, migrating to just before V55 then through it, following `TrimSeedToTestBaselineMigrationTest`'s/`SimCardCarrierMigrationTest`'s pattern (`IntegrationTest.POSTGRES` as the admin connection, a fresh `CREATE DATABASE`, dropped in `@AfterEach`).
  - Case: a clean database reaches V55.
  - Case: a database holding a hand-inserted duplicate normalized username under two Tenants fails to migrate; the failure message names that username.
- **Seam, HTTP API:** the existing `IntegrationTest` + MockMvc seam, per spec.md Testing decisions.
  - Case: `POST /api/agents` with a username that already exists under a different Tenant (built via `OtherTenantFixture`) is refused 409 `USERNAME_TAKEN`, and no Agent, standing-amount or User row is left behind — this is the replacement for the deleted pinning test, and it is also this ticket's proof that the branch stays safe: it exercises the exact code path (`AgentLoginService.create`'s flush-time catch, with no pre-check ahead of it) that would otherwise 500 the moment V55 lands.
  - Case: `SecondTenantSignInApiTest`'s two existing tests, run unmodified, still pass.
- Never asserted: which repository method ran, the query issued, or the exception type thrown inside a service (spec.md Testing decisions).

## Regression

- `AgentLoginApiTest`'s same-Tenant `aUsernameAlreadyInUseIsRejectedAndTheAgentStaysWithoutALogin` and `AgentCreationAtomicityTest` must keep passing unmodified — neither touches a second Tenant, and the pre-check (`existsByTenantIdAndUsername`) stays exactly as it is in this ticket; only the flush-time catch's index-name matching is widened.
- `CollidingUsernameSignInApiTest` is **deleted by this ticket**, not merely modified — flagged here per `docs/agents/ticket-critic.md` R6, since the merger must not mistake this for an unreviewed regression. Its Javadoc already names `globally-unique-usernames` as the deleting feature; this ticket is where that actually happens, earlier than the spec's own prose might suggest, for the reason given in `## Context`.
- `SecondTenantSignInApiTest` must keep passing byte-for-byte untouched (spec.md Constraints) — its two usernames collide with nothing this ticket inserts.
- No other existing test writes a second Tenant's `User` row with a username that collides with anything, so no other test is at risk from the new index.

## Observability

The migration's own failure message is this ticket's operator-facing
observability: naming every offending username, its count and its Tenant ids
(Goals: "fails loudly, naming the offending usernames") is what a person
resolving a blocked upgrade reads. No application-level log or audit entry is
added — a refused Agent-login creation writes no `AuditLog` entry today, and
this ticket does not change that.
