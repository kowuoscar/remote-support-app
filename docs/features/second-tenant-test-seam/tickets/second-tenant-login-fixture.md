---
id: second-tenant-login-fixture
title: Sign in against a second Tenant's login and read only that Tenant's data
status: done
depends_on: []
labels: [backend, testing]
stories: [1, 2, 3, 4, 5, 6, 9, 10]
---

## Context

`spec.md`'s Solution ("Extend the fixture that already exists", "Asserting which
Tenant a sign-in resolved to", "What the tests prove") and Decisions taken.
`OtherTenantFixture` already builds a second Tenant through the repositories
for five API test classes; it has no login. This ticket gives it one, gives
`IntegrationTest` the shared way to read a token's Tenant, and proves both
through the real HTTP seam: a login that exists only in a second Tenant signs
in and names that Tenant, and that Tenant's Manager reads only its own data.

No `src/main` change, no migration — see spec.md Constraints. This ticket
does not touch the colliding-username case; that is
`pin-colliding-username-sign-in`, which depends on this one for the fixture
method.

## Acceptance criteria

- [ ] `OtherTenantFixture` gains `managerLoginInAnotherTenant(String username, String password)`, returning a record of the new Tenant's id, the created user's id, the username, the plaintext password and the new Client's id — shape fixed by spec.md's `OtherTenantLogin` record. It creates one brand-new Tenant, one `MANAGER`-role `User` in it whose `passwordHash` comes from the context's `PasswordEncoder` bean, and one `Client` in that same Tenant.
- [ ] `IntegrationTest` gains a protected helper that takes an issued token and returns the Tenant id from its `tenantId` claim, using the `JwtService` bean already in the context; it is the only place in the suite that answers this question (next to `loginAs`/`managerToken`).
- [ ] A test calls `managerLoginInAnotherTenant` with a username that exists nowhere else, signs in through `POST /api/auth/login`: the response is 200, and the helper reads the returned token's Tenant id as equal to the fixture's Tenant id and not equal to `11111111-1111-1111-1111-111111111111`.
- [ ] That same signed-in Manager's token, used against `GET /api/clients`, returns exactly that Tenant's own Client (the one the fixture created) and none of the seeded Tenant's Clients.
- [ ] `mvn -f backend/pom.xml verify` (`JAVA_HOME` on JDK 21) is green, and by name: `ClientInvoiceByIdApiTest`, `AgentInvoiceByIdApiTest` and `DemoDataLoaderApiTest` still pass.
- [ ] The feature's diff so far touches only files under `backend/src/test`; nothing under `backend/src/main` or `db/migration`; V55 stays unused.

## Tests

- **Seam:** the existing HTTP API seam — `IntegrationTest` subclass, MockMvc against real Postgres via Testcontainers (spec.md Testing decisions). No unit test of `AppUserDetailsService`, no repository-level test.
- Cases:
  - Sign-in with a login that exists only in the second Tenant → 200; token's `tenantId` claim equals the fixture's Tenant id and differs from the seeded Tenant id.
  - The same Manager token calling `GET /api/clients` → response contains the fixture's own Client and excludes every seeded-Tenant Client.
  - The token-tenant helper, exercised via the two cases above, is not given its own isolated test — it has no behaviour beyond decoding a claim `JwtService` already covers.
- The clean (non-colliding) username is the only case here; the colliding username is `pin-colliding-username-sign-in`'s case, not this ticket's.

## Regression

- `ClientInvoiceByIdApiTest` and `AgentInvoiceByIdApiTest` assert `repository.count()` deltas (baseline captured inside the test method, re-counted after, transaction rolled back) on `clientInvoiceRepository`, `agentInvoiceRepository` and `agentStandingAmountRepository` — none of which this new fixture method writes to. Checked per spec.md's "Debt and hazards" section; both must keep passing unchanged, and no fixture call is placed between either test's baseline count and its re-count.
- `DemoDataLoaderApiTest` counts `clientRepository` (which this method *does* write to) but does not import `OtherTenantFixture`, so it is unaffected; it must keep passing unchanged.
- `IntegrationTest` is modified, and every subclass in the suite inherits it, so it is named here for the test guard: the change is **additive only** — one import, one `@Autowired protected JwtService` field, and one new `tenantIdOf(String)` helper. No existing field, method body or assertion is touched and nothing is removed. The helper is required by this ticket's own acceptance criteria and by the approved spec's "Asserting which Tenant a sign-in resolved to". Every existing subclass must keep passing unchanged.
- The eight existing importers of `OtherTenantFixture` (`SimCardCarrierApiTest`, `CarrierOfferApiTest`, `StockApiTest`, `TopupFeeFromOptionApiTest`, `ReviewQueueApiTest`, `CarrierApiTest`, plus the two `ByIdApiTest` classes above) call none of the fixture's existing methods differently — this ticket only adds a method, it does not change one — so they need no review beyond staying green.

## Observability

N/A — test-only change; nothing here runs in a deployed environment for anything to observe.
