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

Carried by the three prior tickets on this feature branch
(`global-username-index`, `refuse-taken-username-on-agent-login`,
`refuse-taken-username-on-tester-login`), each already merged with its own
`chore(sdlc)` commit. This ticket adds no code — only this `## Audit` section,
closing story 14.
