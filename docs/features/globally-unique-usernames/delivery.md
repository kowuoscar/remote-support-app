# Delivery report — globally-unique-usernames

<!-- sdlc:template delivery 1 -->

## Audit

The epic (`docs/roadmap/tenant-scoped-sign-in.md`) closes on this section, not
only on the fix: every place in the codebase that looks a row up without
scoping it to a Tenant, named, judged, and given exactly one of three
dispositions — **fixed-by-this-feature**, **safe-as-is**, or
**recorded-as-debt**.

**Method.** Every `backend/src/main/java/.../repository/*.java` interface
(19 files) was read in full for a derived-query method or a hand-written
`@Query` whose name/JPQL does not mention `tenantId`/`TenantId`. Every bare
`.findById(...)` call outside a repository interface (Spring Data's own
inherited method, invisible to that first pass) was then grepped for across
`backend/src/main/java` and read at its call site. `DemoDataLoader`, the
Flyway seed migrations, and `frontend/app`, `frontend/components`,
`frontend/lib` were swept separately for a hardcoded Tenant id or any
Tenant-shaped assumption.

### The three known findings

- **`UserRepository.findByUsername(String)`** — **fixed-by-this-feature.**
  This was the defect itself: a username-only lookup with no Tenant to scope
  by, called from `AppUserDetailsService.loadUserByUsername` on every
  sign-in. It keeps its exact signature and exact-match semantics (the spec's
  non-goal), but V55's `uq_users_username_global` index (already merged, on
  `lower(btrim(username))`) now makes at most one `users` row able to match
  any spelling, so the lookup is unambiguous by construction rather than by
  scoping — the fix is at the schema, not at this method.

- **`UserRepository.findByAgentId(UUID)`** — **safe-as-is.** Its only caller
  is `DemoDataLoader` (looking up the login it just created, by the Agent's
  own generated id, to build that Agent's demo principal). A `UUID` primary
  key cannot collide across Tenants — Postgres's own `uuid` type gives no
  Tenant a way to be handed another Tenant's id — so scoping this query
  changes nothing it could ever return. Retrofitting it would be motion with
  no defect behind it, exactly as the spec's non-goals section says.

- **`CallerIdentityResolver`'s `findById(principal.userId())`** —
  **safe-as-is.** `resolveAgentId` calls `userRepository.findById(principal.userId())`
  directly; its sibling `resolveClientId` calls
  `testerRepository.findByUserId(principal.userId())` (the identical shape,
  one hop further through `Tester`). Both are keyed only by the id already
  inside the caller's own JWT — verified once at the token's issue and
  re-validated by `JwtAuthenticationFilter` on every request — never by a
  value another Tenant's caller could supply. There is nothing to scope: the
  question being answered is always "who is making this request", not "find
  me an arbitrary row."

**All three carry the reasoning spec.md already gives them; the sweep did not
have to revise any of it.**

### What else the sweep turned up

**Yes — the sweep found more than the three known findings.** Six further
items, all judged safe, none recorded as debt:

- **`UserRepository.existsByAgentId(UUID)`** (`AgentLoginService.requireLoginCreatable`,
  called from `AgentController` when giving an existing Agent a login) —
  **safe-as-is.** Same reasoning as `findByAgentId`: keyed by a UUID, and in
  addition the `Agent` it is called on is already resolved through
  `AgentRepository.findByIdAndTenantId(agentId, principal.tenantId())` one
  line earlier, so the id passed in has already survived a Tenant check
  before this call ever runs.

- **`TesterRepository.findByUserId(UUID)`**, called directly (not only
  through `CallerIdentityResolver`) from `RequestController.createTesterAuthored`
  — **safe-as-is.** Its one and only argument at every call site in the
  codebase is `principal.userId()`; the same "who is making this request"
  reasoning as the third known finding applies without qualification.

- **`TopupOptionRepository.findById(UUID)`**, called bare (not through a
  tenant-scoped derived query) in `FeeController.findPickableTopupOption`
  and `TopupRequestDetailsHandler.requireUsableOption` — **safe-as-is.**
  Both call sites immediately `.filter(...)` the result against a Tenant- or
  Carrier-equality check already resolved from tenant-scoped data (the
  Contract, or the SIM Card's own Carrier) before the Option is ever used,
  and both treat a foreign Tenant's Option identically to an unknown id —
  same 400, same message, no existence disclosed. `FeeController`'s own
  Javadoc already states this in so many words ("An Option of another tenant
  is refused exactly like an unknown one, so its existence never leaks").
  This is the one instance the sweep found where a bare, unscoped `findById`
  is reached with an id supplied in a request body rather than derived from
  the principal — worth naming precisely because of that, even though the
  post-lookup filter makes it safe.

- **A broader class: derived-query repository methods keyed by a child id,
  with no `tenantId` parameter of their own** — e.g.
  `ContractRepository.countByClientId`/`countByAgentId`,
  `TesterRepository.existsByClientIdAndPrimaryContactTrue`/`countByClientId`,
  `CarrierOfferRepository.findByCarrierId`/`findByCarrierIdIn`/`findByIdAndCarrierId`,
  `ReturnedUnitRepository.findByRequestIdOrderByCreatedAtAsc` and its
  Smartphone/SIM Card siblings, `SimCardRepository.findByInstalledInSmartphoneId`.
  **Safe-as-is, as a class.** Every one of these ids (`clientId`, `agentId`,
  `carrierId`, `contractId`, `requestId`, `smartphoneId`) is only ever
  supplied by a caller that already resolved it through a Tenant-scoped
  lookup earlier in the same request (a `findByIdAndTenantId`, or a guard
  class in `security/`) — never from a raw, unverified external value — so
  the same UUID-non-collision reasoning as `findByAgentId` covers the whole
  class. The sweep read every one of these call sites to confirm this rather
  than assuming it from the method name.

- **`DemoDataLoader` and the seed migrations** (`V2`, `V3`, `V17`, `V18`,
  `V20`, `V26`, `V29`, `V31`, `V45`) hardcode the seeded Tenant id
  `11111111-1111-1111-1111-111111111111` — **safe-as-is.** This is
  deliberate: `DemoDataLoader` is a `demo`-profile-only seed tool that always
  builds data under that one Tenant by construction (spec.md's own
  reconciliation already covers this: "all three sit in the one seeded
  Tenant"). It is not a lookup that could return a different Tenant's row —
  it never runs in a real request path, and its few unscoped `findById`/
  `findByAgentId`/`findByUserId` calls are always resolving ids it generated
  itself moments earlier within that same hardcoded Tenant. Not a candidate
  for scoping; there is no second Tenant for it to confuse itself with.

- **Frontend** (`frontend/app`, `frontend/components`, `frontend/lib`) —
  **no finding.** The sweep found no code that resolves or assumes a Tenant
  identity: every occurrence of "tenant" is display copy (`"3 in this
  tenant"`, `nav-rail.tsx`'s default `tenantName` label) or a doc comment.
  The browser reaches the backend only through the BFF, and the backend
  resolves Tenant identity entirely from the JWT — there is nothing in
  `frontend/` for a second Tenant to break. This matches the spec's own
  non-goal: no tenant selector, no UI that could even attempt to create or
  address a second Tenant.

### Conclusion

Three known findings, six further instances/classes found by the sweep —
**nine named items in total**, all resolved as either fixed-by-this-feature
(one) or safe-as-is (eight). **The sweep found no instance it would record as
debt**: nothing left unscoped is unsafe, because every unscoped lookup in the
codebase is keyed by a `UUID` that either cannot collide across Tenants by
type (a primary key) or was already produced by a Tenant-scoped resolution
earlier in the same request — never by a raw external value trusted without
that check. The epic's premise — "the tenant boundary is sound after sign-in;
the entire defect is the one unscoped lookup on the way in" — holds after
this audit, not merely as an assumption carried from `## Reworked`.

### Glossary question settled

The spec-writer's proposed `Login` glossary term for `CONTEXT.md` was never
written into the spec text and a merger declined to invent it there; this
ticket was asked to decide it now that usernames are globally unique.
**Decision: add it.** `CONTEXT.md` has a `User` JPA entity
(`domain/User.java`, `UserRepository`, `AppUserDetailsService`) with no
glossary entry of its own anywhere — "login" is used informally, three times,
inline inside the `Agent` and `Tester` entries, and the `Tenant` entry now
carries the load of stating the global-uniqueness invariant this feature
added. A reader hitting `findByUsername`, `existsByUsernameNormalized`, or
either creation path has no single term to look up. Declared in this result's
`harness.terms` for the merger to write; not edited here.

## What was built

A username is now unique across the whole deployment — compared
case-insensitively and ignoring surrounding spaces — so the sign-in lookup can
match at most one row. That closes the `tenant-scoped-sign-in` epic.

**What the defect actually was.** Not a cross-tenant leak. `findByUsername`
returns `Optional<User>` from a derived query, so two matching rows made it
fail inside Spring Security's filter chain and the caller got **401
Unauthorized with an empty body** — a lockout indistinguishable from a wrong
password. The previous feature observed and pinned that rather than assuming
it; the epic's own prediction had been wrong.

- **Stories 1, 10, 11** — `V55__add_global_username_index.sql`: a pre-check
  that raises, naming each offending username with its row count and the
  Tenant ids holding it, writing nothing; then
  `CREATE UNIQUE INDEX uq_users_username_global ON users (lower(btrim(username)))`.
  It never de-duplicates, and `uq_users_tenant_username` stays. Ticket:
  `global-username-index`.
- **Stories 2, 3, 7, 8** — both Agent paths refuse a username held anywhere in
  the deployment, with `409 USERNAME_TAKEN`; the tenant-scoped
  `existsByTenantIdAndUsername` is gone. Tickets: `global-username-index`,
  `refuse-taken-username-on-agent-login`.
- **Stories 4, 5, 6, 9, 15** — the Tester path gains the same rule *and* the
  error codes it never had: it used to throw a codeless conflict, so the
  dialog could not tell "email taken" from "this Client already has a primary
  contact". Both now carry distinct codes and the dialog branches on them.
  Ticket: `refuse-taken-username-on-tester-login`.
- **Stories 12, 13** — `CollidingUsernameSignInApiTest`, which existed only to
  record the defect, is deleted and replaced by a test asserting a login taken
  in another Tenant is refused — exactly what its own Javadoc promised would
  happen. `SecondTenantSignInApiTest` passes untouched, proving legitimate
  second-Tenant operation still works.
- **Story 14** — the audit above.

## Acceptance walkthrough

1. Fresh database reaches V55 unaided, with both indexes present — played — evidence: `evidence/step-1.txt`
2. The migration test's two cases, clean and duplicate — played — evidence: `evidence/step-2.txt`
3. `POST /api/agents` with a second Tenant's username refused, nothing left behind — played — evidence: `evidence/step-3.txt`
4. Giving an existing Agent that login refused, Agent stays login-less — played — evidence: `evidence/step-4.txt`
5. Tester refused; the two conflict causes carry distinct codes — played — evidence: `evidence/step-5.txt`
6. Case-only and whitespace-only variants refused; a clean padded username stores trimmed — played — evidence: `evidence/step-6.txt`
7. Both Tenants' Managers sign in to their own Tenant; seam tests untouched — played — evidence: `evidence/step-7.txt`
8. The pinning test is gone and its replacement asserts the refusal — played — evidence: `evidence/step-8.txt`
9. Full `verify` green, both halves — played — evidence: `evidence/step-9.txt`
10. Read the inline dialog copy in the running app — **yours**
11. Read and judge the `## Audit` section above — **yours**
12. Judge the disclosure bounds against what you accepted — **yours**

## Decisions taken alone

Thirteen entries in the spec's `## Decisions taken`, all settled by the
intention or by the code. Two were **the human's**, taken at gates rather than
alone, and both are worth re-reading because neither is protected by a test:

- **The refusal says the same thing whether the address is taken in this
  Tenant or another** — "That email is already in use. Choose another one and
  try again." One message means the two cases cannot be told apart, which
  discloses strictly less than two would. The wording now lives once, in
  `frontend/lib/api/errors.ts` and `TesterLoginService`.
- **A collision caught only by the database returns 409, never an unmapped
  500.** Recorded as a stated requirement rather than an acceptance criterion,
  because the pre-check makes that path unreachable by any test at the
  permitted seam. The `saveAndFlush` and its `catch` survive in
  `TesterLoginService` and map both constraint names.

**(after review)** The fix pass corrected a real bug the reviewers found:
Postgres `btrim` and JPQL `trim`, called with no explicit character set, strip
**only** the ASCII space — never a tab or a non-breaking space — while Java's
`String.strip()` strips every Unicode whitespace character it recognises. A
tab-padded username therefore normalised one way in Java and another at the
index enforcing uniqueness. `Username.trim` now matches the database exactly,
at all three call sites, pinned by a unit test and an API test. V55 was not
edited: an applied migration is forward-only, so Java was made to match the
database rather than the reverse.

**(after review)** The Tester path's uniqueness rule moved out of the
controller into `TesterLoginService`, whose `@Transactional create()` also
stopped the `User` and `Tester` inserts committing independently.

## Debt recorded

- `TesterController` · the "one primary contact per Client" rule branches on a fresh repository query in the controller. It predates this feature, which only changed the exception it throws (F11).
- `AgentController` · `@Transactional` on two controller methods, which Backend rule 3 forbids. Pre-exists `main`, found while re-reviewing (F5's re-review).

Seven smells found in lines this feature changed were **fixed** in the one fix
pass rather than recorded. One finding, F3, is left open by choice: the
`CONTEXT.md` `Login` entry has no source in any story — it was decided inside
the audit ticket — but the term is true and useful, and removing it would
leave the glossary poorer.

## How to undo

```
git revert -m 1 d47a587
```

**Read this before reverting.** Unlike the previous feature, this one carries
a migration. `git revert` removes the Java and the frontend, but **it does not
drop `uq_users_username_global`** — Flyway will not un-apply V55, and the index
stays in every database that has run it. A reverted deployment therefore keeps
enforcing global uniqueness at the database level while the application stops
pre-checking it, so a cross-Tenant duplicate would surface as a flush-time
error rather than a clean 409. Dropping the index is a new forward migration,
not part of the revert.

## What happens next

`tenant-scoped-sign-in` closes with this feature. The next epic in
`docs/roadmap/README.md` is `login-lifecycle`, already planned into three
features: self-service password change, Manager reset for Agents and Testers,
and deactivating a login.

If you veto a decision above, it reopens as an open question on the next
spec. The two most worth your eye are the refusal wording and the 409
requirement, since both are load-bearing and neither is protected by a test.
