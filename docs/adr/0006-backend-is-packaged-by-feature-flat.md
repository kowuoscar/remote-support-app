# 6. The backend is packaged by feature, flat, one package per glossary term

Status: proposed
Date: 2026-10-09
Feature: package-layout-decision
Ticket: package-layout-adr

## Context

The human made "package by feature, not by layer" the norm on 2026-09-22 (coding standards, Backend 13).
The backend does not obey it. It is packaged by layer: `web`, `dto`, `domain`, `repository`, `security`,
`logging` and `demo`. `web` holds 78 of the 222 main classes, and one feature's files are spread over five
packages. Moving 222 main classes and 71 test classes is the expensive part of the
`package-by-feature` epic, and the human asked that the target be researched rather than invented.

The evidence is [`research.md`](../features/package-layout-decision/research.md), beside this feature's
spec. Every `path:line` in it, and every class count, refers to commit
`e22e81fd5408f0c427b3e723008fcbf6e748d6db` of `main`. A bracketed `[n]` below is that note's numbered
source, section 7. The note compared four layouts against twelve constraints and recommended the flat one.
This ADR turns that recommendation into a layout, a table that places every class, rules for the global
pieces and for one feature using another, a ruling on the `@Lazy` cycle, a boundary tool, a move order
and a move recipe. Each move feature's spec cites this ADR instead of deciding again.

Three findings of the note shape what follows and correct earlier statements:

- **Eleven of the twelve clusters form one reference cycle** (note C11). Only identity, which nothing else
  references, is outside it. Grouping into six move groups does not remove it. So a "no cycles between
  features" rule cannot be switched on as a plain rule at any granularity, and Spring Modulith's
  verification, which rejects module cycles [6], cannot run until code changes the epic excludes.
- **The epic's dependency direction for invoicing is wrong.** The epic says `AgentInvoiceService` "sits on
  top of both" `ContractAmountService` and `ClientInvoiceService`. At the pinned commit the chain is
  `ClientInvoiceService` to `AgentInvoiceService` to `ContractAmountService`, and `ContractAmountService`
  reaches `ClientInvoiceService` back through a lazy constructor argument (note C3: `ClientInvoiceService.java:62`
  and `:67`, `AgentInvoiceService.java:39`, `ContractAmountService.java:48`, all under `web`). The cycle is
  therefore three services long, not two.
- **Spring Boot's Java support is stated for 3.3.13, not for 3.3.4** [2]. The project is on 3.3.4
  (`backend/pom.xml:7-12`). Decision 6 states what the compatibility claim rests on and what it does not.

This ADR plans no change to any HTTP path, response, migration, configuration property or bean name. A
moved class keeps its simple name, and a Spring bean's default name comes from the simple name, so no bean
name changes.

## Decision

The recommendation: **package by feature, flat. One package per feature directly under
`com.remotesupport.backend`, named for a glossary word, holding the feature's entity, repository, service,
controller, access guard, exceptions and request and response types together. No layer sub-packages.**
The cost is eight features after this one (the boundary check, six moves and the cycle break). Of the 222
main classes, 217 change package and 5 stay; of the 71 test classes, 61 change package and 10 stay. The only
new dependency is ArchUnit, test scope, Apache-2.0. There is no main-scope dependency, so nothing in the
shipped application changes.

### 1. Layout

The package tree under `com.remotesupport.backend` when the epic is done. Main and test trees are
identical, so a test sits in the package of the class it exercises (Backend 6).

```
com.remotesupport.backend
  BackendApplication            stays at the root
  login/                        Login, passwords, JWT, activation, /api/me
  agent/                        Agent and the Agent's own Login creation
  client/                       Client and its Testers
  contract/                     Contract and its access guard
  carrier/                      Carrier, Topup Options, Postpaid Plans, the catalog
  fleet/                        Smartphones, SIM Cards, installation, Agent Stock
  request/                      Requests and their lifecycle
    completion/                 one completion effect per Request type
    requestapproval/            approval handlers and validator
    requestdetails/             one details handler per Request type
  fee/                          Fees
  invoice/                      Client Invoices, Agent Invoices, standing amounts, Review Queue
  tenant/                       Tenant and its repository
  shared/                       BillingMonth, Country, Currency, the three common exceptions
  system/                       the health endpoint
  security/                     SecurityConfig only
  logging/                      AuditLog and the request logging filter
  demo/                         the demo-profile loader
```

Rules for a feature package:

- **A feature package holds everything of the feature and no layer sub-package.** There is no `web`,
  `dto`, `domain`, `repository` or `service` folder inside it. Backend 13 rejects layer names, and a second
  axis would give every class a second decision (note 3.2).
- **`request` keeps its three sub-packages.** They split by Request type (completion effects, approval
  handlers, details handlers), not by layer. Their names are today's last segments, unchanged, so a move
  renames nothing but the prefix.
- **A large feature may add one internal sub-package later** (`invoice` is the likeliest at 49 classes,
  `request` is 41). That is a decision for a feature's own spec, not for a move.
- **Names.** Lowercase, one word, singular, taken from the glossary in `CONTEXT.md`. The examples in
  Backend 13 (`authentication`, `requests`, `invoicing`) were illustrative; this table's names are the
  authoritative ones, and the ticket that amends Backend 13 aligns them.
- **Exception to "a glossary term": `shared` and `system`.** The spec's non-goal prefers a glossary term for every
  package. Two packages have none, because the code in them is not a domain concept: `shared` holds the types
  every feature uses (`BillingMonth`, `Country`, `Currency`, the three common exceptions) and `system` holds the
  health endpoint. Naming them for a glossary word would be wrong (`Currency` is no feature's term) and
  `CONTEXT.md` is not changed by this feature. The exception is deliberate and limited to these two, plus the
  packages that stay as they are (`security`, `logging`, `demo`). A new package needs a glossary term.
- **Visibility does not change in a move.** Every top-level type is `public` today (note C6) and stays so.
  A class written new may be package-private when only its feature uses it.
- **Nothing outside the root package.** `@SpringBootApplication` scans the tree and no `@ComponentScan` or
  `@EntityScan` exists (note C5), so a package under the root is picked up with no wiring [1].

### 2. Where each cluster goes

Twelve clusters, assigned by name and route as in the note's section 5. "Named for" is the glossary term in
`CONTEXT.md`. A `*` marks a test class that does not extend `IntegrationTest`. Where this table assigns a
borderline test class differently from the note's per-cluster counts, the table rules. It places
`CrossTenantUsernameAgentCreationApiTest` with agents (it creates an Agent) and `UsernameTest` with login
(it tests `Username`). The totals
are unchanged: 222 main and 71 test, 57 of the tests extending `IntegrationTest`.

| Cluster | Main classes | Test classes (`*` = not an integration test) | Target package | Named for |
|---|---|---|---|---|
| authentication | (30) AdministeredLoginLookup, AppUserDetailsService, AppUserPrincipal, AuthController, CallerIdentityResolver, CallerLoginDeactivatedException, ChangePasswordController, ChangePasswordRefusedException, ChangePasswordRequest, ChangePasswordService, JwtAuthenticationFilter, JwtService, LoginActivationController, LoginActivationResponse, LoginActivationService, LoginAdministrationGuard, LoginDeactivatedException, LoginRequest, LoginResponse, LoginState, PasswordGenerator, PasswordPolicy, PasswordResetController, PasswordResetResponse, PasswordResetService, PasswordWrite, Role, User, UserRepository, Username | (18) AgentLoginActivationApiTest, AgentLoginApiTest, AgentPasswordResetApiTest, AuthLoginObservabilityTest, AuthLoginTest, ChangeOwnPasswordApiTest, ChangePasswordServiceDeactivatedTest, DeactivatedLoginRefusedApiTest, GeneratedPasswordCreationApiTest, LoginAdministrationGuardTest, PasswordGeneratorTest*, PasswordMinimumLengthApiTest, PasswordWriteGeneratedTest*, PasswordWriteTest*, SecondTenantSignInApiTest, TesterLoginActivationApiTest, TesterPasswordResetApiTest, UsernameTest* | `login` | Login (also Deactivated Login, Password write) |
| identity (`/api/me`) | (6) AgentOwnRecordResponse, MeAgentController, MeClientController, MeClientResponse, MeController, MeResponse | (3) AgentIdentityApiTest, AgentOwnRecordApiTest, MeClientApiTest | `login` | Login |
| agents | (11) Agent, AgentController, AgentCreateRequest, AgentCreatedResponse, AgentHasNoLoginException, AgentLogin, AgentLoginConflictException, AgentLoginCreateRequest, AgentLoginService, AgentRepository, AgentResponse | (3) AgentApiTest, AgentCreationAtomicityTest, CrossTenantUsernameAgentCreationApiTest | `agent` | Agent |
| clients and Testers | (13) Client, ClientController, ClientCreateRequest, ClientRepository, ClientResponse, Tester, TesterConflictException, TesterController, TesterCreateRequest, TesterCreatedResponse, TesterLoginService, TesterRepository, TesterResponse | (3) ClientApiTest, TesterApiTest, TesterUsernameConflictApiTest | `client` | Client (and Tester) |
| contracts | (8) Contract, ContractAccessGuard, ContractCarrierController, ContractController, ContractCreateRequest, ContractRepository, ContractResponse, ContractTestersController | (1) ContractApiTest | `contract` | Contract |
| carriers | (19) Carrier, CarrierCatalogAccessGuard, CarrierCatalogResponse, CarrierCatalogService, CarrierController, CarrierCreateRequest, CarrierOffer, CarrierOfferController, CarrierOfferRepository, CarrierOfferRequest, CarrierOfferResponse, CarrierRenameRequest, CarrierRepository, CarrierResponse, CatalogCarrierResponse, PostpaidPlan, PostpaidPlanRepository, TopupOption, TopupOptionRepository | (2) CarrierApiTest, CarrierOfferApiTest | `carrier` | Carrier |
| fleet | (22) FleetAccessGuard, SimCard, SimCardCancellationRequest, SimCardController, SimCardCreateRequest, SimCardFactory, SimCardFlavor, SimCardInstalledInUpdateRequest, SimCardRepository, SimCardResponse, SimCardStatus, SimCardStatusUpdateRequest, SimInstallationService, Smartphone, SmartphoneController, SmartphoneCreateRequest, SmartphoneOwner, SmartphoneRepository, SmartphoneResponse, SmartphoneSerialUpdateRequest, SmartphoneStatus, SmartphoneStatusUpdateRequest | (5) PostpaidSimPlanApiTest, SimCardApiTest, SimCardCarrierApiTest, SimCardInstalledInApiTest, SmartphoneApiTest | `fleet` | Fleet |
| stock | (3) StockController, StockFulfilmentService, StockUnitResponse | (2) StockApiTest, StockFulfilmentApiTest | `fleet` | Agent Stock (units held outside any Contract's Fleet) |
| requests | (19) Disposition, PendingRequestItemResponse, PendingRequestsController, ProvisioningService, Request, RequestAccessGuard, RequestApprovalRequest, RequestByIdController, RequestController, RequestCreateRequest, RequestRejectRequest, RequestRepository, RequestResponse, RequestStatus, RequestStatusUpdateRequest, RequestType, ReturnedUnit, ReturnedUnitRepository, ReturnedUnitResponse | (7) ManagerApprovesRequestsApiTest, ProvisionRequestDetailsApiTest, RebootAndTopupDetailsApiTest, ReplaceRequestsApiTest, RequestApiTest, ReturnRequestsApiTest, SimSwapRequestDetailsApiTest | `request` | Request |
| requests, completion effects | (8) ProvisionSimCompletionEffect, ProvisionSmartphoneCompletionEffect, ReplaceSimCompletionEffect, ReplaceSmartphoneCompletionEffect, RequestCompletionEffect, RequestCompletionInput, ReturnCompletionEffect, SimSwapCompletionEffect | (0) - | `request.completion` | Request (Completed) |
| requests, approval | (3) RequestApprovalHandler, RequestApprovalValidator, ReturnApprovalHandler | (0) - | `request.requestapproval` | Request (Pending Approval) |
| requests, details | (11) ProvisionSimRequestDetailsHandler, ProvisionSmartphoneRequestDetailsHandler, RebootRequestDetailsHandler, ReplaceSimRequestDetailsHandler, ReplaceSmartphoneRequestDetailsHandler, RequestDetailsHandler, RequestDetailsInput, RequestDetailsValidator, ReturnRequestDetailsHandler, SimSwapRequestDetailsHandler, TopupRequestDetailsHandler | (0) - | `request.requestdetails` | Request type |
| fees | (6) Fee, FeeController, FeeCreateRequest, FeeRepository, FeeResponse, FeeType | (2) FeeApiTest, TopupFeeFromOptionApiTest | `fee` | Fee |
| Client Invoices | (31) CarrierInvoiceFile, CarrierInvoiceFileRepository, CarrierInvoiceFileResponse, CarrierInvoiceFileStorage, ClientInvoice, ClientInvoiceAccessGuard, ClientInvoiceBaseSimLineResponse, ClientInvoiceByIdController, ClientInvoiceConflictException, ClientInvoiceController, ClientInvoiceFeeLineResponse, ClientInvoiceFeeSnapshot, ClientInvoiceFeeSnapshotRepository, ClientInvoiceLine, ClientInvoiceLineEditRequest, ClientInvoiceLineKind, ClientInvoiceLineRepository, ClientInvoicePdfRenderer, ClientInvoiceQueueRow, ClientInvoiceRepository, ClientInvoiceResponse, ClientInvoiceSendBackRequest, ClientInvoiceSentBackController, ClientInvoiceSentBackResponse, ClientInvoiceSentBackRow, ClientInvoiceService, ClientInvoiceStatus, ClientInvoiceSummaryResponse, ContractAmountService, ReviewQueueController, ReviewQueueItemResponse | (11) ClientInvoiceApiTest, ClientInvoiceByIdApiTest, ClientInvoiceConcurrentSendTest, ClientInvoiceLineEditApiTest, ClientInvoiceLineEditRaceTest, ClientInvoiceSendBackApiTest, ClientInvoiceSendBackRaceTest, ClientInvoiceStoredLinesApiTest, LocalSupportFeesFollowClientInvoiceApiTest, LocalSupportFeesRaceTest, ReviewQueueApiTest | `invoice` | Client Invoice (also Review Queue, Carrier Invoice File) |
| Agent Invoices | (18) AgentInvoice, AgentInvoiceAccessGuard, AgentInvoiceByIdController, AgentInvoiceController, AgentInvoiceOverrideRequest, AgentInvoiceQueueRow, AgentInvoiceRepository, AgentInvoiceResponse, AgentInvoiceService, AgentInvoiceStatus, AgentStandingAmount, AgentStandingAmountController, AgentStandingAmountRepository, AgentStandingAmountResponse, AgentStandingAmountUpdateRequest, AgentStandingAmountsResponse, StandingAmountService, StandingAmountType | (2) AgentInvoiceApiTest, AgentInvoiceByIdApiTest | `invoice` | Agent Invoice (also Standing amount) |
| global: shared types | (6) BillingMonth, ConflictException, Country, Currency, InvalidRequestException, NotFoundException | (0) - | `shared` | none (shared code, not a feature) |
| global: Tenant | (2) Tenant, TenantRepository | (0) - | `tenant` | Tenant |
| global: health | (1) HealthController | (1) HealthEndpointTest | `system` | none (not a feature) |
| global: security config (stays) | (1) SecurityConfig | (1) ProtectedEndpointTest | `security` | none (cuts across features) |
| global: logging (stays) | (2) AuditLog, RequestLoggingFilter | (0) - | `logging` | none (cuts across features) |
| global: demo (stays) | (1) DemoDataLoader | (1) DemoDataLoaderApiTest* | `demo` | none (demo profile only) |
| global: root (stays) | (1) BackendApplication | (0) - | (root) | none |
| global: test support and migrations (stay) | (0) - | (9) ClientInvoiceLinesMigrationTest*, GlobalUsernameIndexMigrationTest*, IntegrationTest*, OtherTenantFixture*, RepairToOtherMigrationTest*, SimCardCarrierMigrationTest*, SimCardInstalledInSmartphoneMigrationTest*, SmartphoneOwnerMigrationTest*, TrimSeedToTestBaselineMigrationTest* | `support`, `migration` (test only) | none |
| **Total** | **222** | **71** (57 extend `IntegrationTest`) | | |

Move groups (the unit of a move feature, in the order of decision 7), with the classes that change package:

| Move feature | Group | Main classes moved | Test classes moved |
|---|---|---|---|
| `package-boundary-check` | adds the check; moves nothing | 0 | 0 |
| `move-carrier` | carriers | 19 | 2 |
| `move-fleet-and-stock` | fleet and stock | 25 | 7 |
| `move-request-and-fee` | requests and fees | 47 | 9 |
| `move-people-and-contract` | agents, clients and Testers, contracts | 32 | 7 |
| `move-invoice` | Client Invoices and Agent Invoices | 49 | 13 |
| `break-the-invoice-service-cycle` | code change in `invoice`, no move | 0 | 0 |
| `move-login-and-shared` | authentication, identity and the nine global classes | 45 | 23 |
| **Total** | | **217** | **61** |

Recount, reproducible at the pinned commit: `find backend/src/main -name '*.java' | wc -l` gives 222 and the
same under `backend/src/test` gives 71. Every class name appears once in the table above. Five main classes
stay where they are (`BackendApplication`, `SecurityConfig`, `AuditLog`, `RequestLoggingFilter`,
`DemoDataLoader`) and so do ten test classes (the seven migration tests, `IntegrationTest`,
`OtherTenantFixture`, `DemoDataLoaderApiTest`). **This corrects the note's recommendation**, which counted
"208 of 222" as changing package and left 14 global classes at the root. Nine of those 14 sit in `web`,
`domain` and `repository` today, which the epic ends by deleting, so they have to move into `shared`,
`tenant` and `system`. The cost is 217 main classes, not 208.

### 3. Global pieces

One line each. None of them belongs to a single feature.

- **`SecurityConfig`** stays in `security`, alone in it once the epic is done. Its single matcher chain keeps
  naming the routes of many features (28 `requestMatchers`, note C4); it is the one class allowed to
  depend on every feature, and the one place a route's authorisation is read in full.
- **`AuditLog`** stays in `logging` with `RequestLoggingFilter`. It is a static helper called from 27 main
  files; any package may call it, and it imports no feature.
- **The Flyway migrations** stay in `backend/src/main/resources/db/migration`. Their location is set by
  configuration, not by Java packages [3]. No migration is edited and none is added by a move. The seven
  migration tests stay in the test package `migration`.
- **`IntegrationTest` and its fixtures** (`OtherTenantFixture`) stay in the test package `support`. Every
  integration test extends `IntegrationTest` from its feature package, beside the unit tests of that
  package (Backend 6).
- **`demo`** stays as it is: `DemoDataLoader` runs only under the `demo` profile and imports 72 project types
  from every layer (note C4). It may depend on any feature. No feature depends on it.
- **Shared types.** `BillingMonth`, `Country`, `Currency`, `NotFoundException`, `ConflictException` and
  `InvalidRequestException` go to `shared`; `Tenant` and `TenantRepository` go to `tenant`; `HealthController` goes to
  `system`. `shared` and `tenant` import no feature. A type enters `shared` only when three or more
  features use it and it names no feature's entity; anything else stays with its owner. `shared` is
  where code that fits nowhere ends up, so a reviewer refuses a new class there that one feature uses.

### 4. Cross-feature references

One feature uses another's entity, repository or service in one of these ways, and no other.

- **Entities: a feature may import another feature's entity and hold a JPA association to it.** There are
  56 associations across the codebase and 17 of them point to `Tenant` (note C6), and they cross features
  throughout. `Contract` is imported by 43 main files and `Request` by 29 (note C2). **No facade, interface or
  copy is put in front of `Contract` or `Request`**: both are plain entities with public accessors, and
  hiding them would need 43 and 29 call sites rewritten without any behaviour change to show for it. The
  rule is the same for every other entity.
- **Repositories are private to their feature.** A class outside a feature does not import that feature's
  repository. It calls the owning feature's service, or the owning feature's access guard where one
  already exists (`ContractAccessGuard`, `RequestAccessGuard`). `TenantRepository` is the exception,
  because `tenant` is global. Today about 57 uses in 13 repositories break this rule (an identifier match
  over the main tree, `TenantRepository` excluded, `ContractRepository` 9 and `SimCardRepository` and
  `SmartphoneRepository` 9 each). They go to the boundary tool's frozen list; a move does not fix them, and
  a new feature must not add one.
- **Services: a cross-feature call goes to a public service class of the owner.** Never to a controller, and
  never to a handler, validator or completion effect inside `request`'s sub-packages.
- **Request and response types are private to their feature.** A controller returns its own feature's
  response types. An existing exception goes to the frozen list.
- **Direction.** Because eleven clusters form one cycle (Context), the rule cannot yet say "features form a
  tree". It says no new package-to-package cycle may appear and the existing one may not grow. Breaking it
  is later work (decision 5 and Consequences).

### 5. The `@Lazy` cycle

**Invoicing stays one package, `invoice`, so the cycle does not cross a package boundary, and no move breaks
it.** `ClientInvoiceService`, `AgentInvoiceService` and `ContractAmountService` all land in `invoice`
(decision 2), together with the Review Queue and standing amounts. The chain is the one in Context:
`ClientInvoiceService` to `AgentInvoiceService` to `ContractAmountService`, and `ContractAmountService` back to
`ClientInvoiceService` through `@Lazy`.

**A feature of its own breaks it, `break-the-invoice-service-cycle`, not a prefactor inside the invoicing
move.** It runs after `move-invoice` and does what the recorded debt already proposes: extract the line
resolution that both `ClientInvoiceService` and `ContractAmountService` need into its own class, so no
service depends on `ClientInvoiceService` lazily. It changes code and needs its own tests, which the move
recipe forbids inside a move. It is not on the epic's critical path: the layer packages can be deleted
without it. It absorbs the `docs/tech-debt.md` entry "Dependency cycle bridged by `@Lazy`" and
the neighbouring "Repeated kind filters" entry on `ContractAmountService`.

### 6. Boundary tool

- **Name and artifact.** ArchUnit, `com.tngtech.archunit:archunit-junit5` [12].
- **Version.** 1.4.2.
- **Scope.** Test. Nothing enters main scope.
- **Licence.** Apache License 2.0 ("The Apache Software License, Version 2.0" in the artifact's POM) [12].
- **Compatibility with Spring Boot 3.3.4 and JDK 21, and what it rests on.** ArchUnit added Java 21 support in
  1.1.0 and the project ran its own tests on JDK 21 from 1.4.0 [11]. The `archunit-junit5` 1.4.2 POM declares
  only `archunit-junit5-api` and `archunit-junit5-engine` [12], so it depends on no Spring library and Boot
  has no version to conflict with. Boot 3.3 does not manage ArchUnit's version [4], so `package-boundary-check`
  pins 1.4.2 itself in `backend/pom.xml`. The Spring Boot 3.3 system requirements say 3.3.13 "requires at least
  Java 17 and is compatible with versions up to and including Java 23" [2]. **That page states the range for
  3.3.13, not for 3.3.4**, and no source in the note says 3.3.4 itself runs on JDK 21. The project already
  builds on JDK 21 with 3.3.4 (`backend/pom.xml:7-12` and `:21`); the claim made here is only that the new
  dependency adds no constraint of its own. Two things are unsettled and go to
  `package-boundary-check`, which must run the check once before relying on it: no source states that
  `archunit-junit5` 1.4.2 was run against Boot 3.3.4's JUnit platform (note section 6), and the guide read for
  the rules below covers 1.5.1, not 1.4.2 [10].
- **Rules it encodes.** One test class in the normal test phase. The rules are fixed here; the exact
  ArchUnit expressions are `package-boundary-check`'s.
  1. **Layer packages stay empty of new classes.** No class resides in `web`, `dto`, `domain` or `repository`.
     Frozen (below).
  2. **Direction to the global packages.** `shared`, `tenant` and `logging` depend on no feature package. No
     feature package depends on `demo`. `security`, `system` and `demo` may depend on features.
  3. **No new cycle between feature packages.** a `slices().matching(...)` rule that `should().beFreeOfCycles()` [10], frozen, so the existing cycle is recorded and only a new one fails. The note measured that
     eleven of twelve clusters are one component, so whether a frozen cycle rule stays stable on a graph
     that is one large component is not proven. **If `package-boundary-check` finds it unstable, its fallback
     is a frozen list of the allowed package-to-package dependencies, where a new pair fails.**
  4. **Repositories are private to their feature** (decision 4). Frozen.
  5. **Nothing depends on a controller** of another class (decision 4). Frozen only if the run shows a
     violation.
  6. **`request`'s sub-packages are internal to `request`** (decision 4). Frozen.
- **How existing classes are let through.** A frozen list, in ArchUnit's `FreezingArchRule`: "recording all
  existing violations to a ViolationStore. Consecutive runs will then only report new violations and ignore
  known violations. If violations are fixed, FreezingArchRule will automatically reduce the known stored
  violations to prevent any regression." [10] The store is a file committed with the test. A move
  feature commits the shrunk store in the same merge. The list only shrinks, with one exception that Backend 13
  requires: a feature that adds a class to a cluster that has **not moved yet** puts it in a layer package,
  as today, and adds that one entry to the store in the same commit, where a reviewer sees it. The store may
  hold no entry for a cluster whose move has merged. The last move deletes the store: rule 1 becomes plain.
  This reading of "a shrinking list" differs slightly from the epic's wording ("existing classes allowed by
  a shrinking list") and follows from keeping Backend 13's unmoved-cluster clause.
- **Which feature adds it, and that it fails `verify`.** `package-boundary-check`, the first feature after
  this one. The check is an ordinary JUnit test named `*Test`, so `mvn -f backend/pom.xml verify` (the
  first command of `verify` in `docs/agents/sdlc.json`) fails when a rule is broken. That feature proves it
  bites by showing a red run on a deliberately violating class that is then removed.
- **Main-scope dependency, to be flagged for the approval item: none is proposed.** Spring Modulith's
  `@ApplicationModule` annotations in production code, its event publication registry and jMolecules
  annotations would be main scope and are not part of this ADR. ArchUnit is test scope and replaceable, so it
  is the agents' call (spec Decisions taken); the approval item names it anyway.

### 7. Move order

One feature per step. Each reason is the one that put that step there.

1. **`package-boundary-check`.** First, because it moves nothing, so it is safe before any file moves, and
   because every later move then runs against the frozen list and shrinks it. It also stops layered drift
   from the day it merges.
2. **`move-carrier`** (carrier: 19 main, 2 test). The fewest outgoing links of any group (note section 1). It
   proves the move recipe on a small diff before a coupled group.
3. **`move-fleet-and-stock`** (fleet: 25 main, 7 test). Before Requests because the heaviest link in the
   graph runs from requests-with-fees to fleet-with-stock, 98 class pairs (note C11), so fleet has already
   moved when Requests do. Stock joins it because its three classes handle the same Smartphones and SIM Cards.
4. **`move-request-and-fee`** (request and fee: 47 main, 9 test). Requests are the largest group by class
   count after invoicing, and `Fee` points to `Contract` and `Request`, so the two go together.
5. **`move-people-and-contract`** (agent, client and contract: 32 main, 7 test). They reference one another in
   pairs (note C11: agents and contracts, clients and contracts), so they move together. `Contract` is the hub
   (43 importing files); by now most of its importers are already in feature packages, so the rewrite
   touches only the rest.
6. **`move-invoice`** (invoice: 49 main, 13 test). Late, for two reasons. It imports the most from other
   groups, which are all in feature packages by now. And the three draft specs in this cluster
   (`invoice-adjustment`, `month-closes-on-the-fifth`, `real-manager-dashboard`) change its classes, so the
   longer it waits the more of them are built, and the less a move overtakes (decision 8).
7. **`break-the-invoice-service-cycle`** (decision 5). Straight after `move-invoice`, in the same package. It
   needs the invoicing classes together in one place. It is the only step that changes code, and it is not
   needed to empty the layer packages, so it may be postponed without blocking step 8.
8. **`move-login-and-shared`** (login and the nine global classes: 45 main, 23 test). Last, because it has the
   most inbound edges (`User` points to `Agent` and `Tester` points back to `User`, note C6; the access guards
   and `SecurityConfig` depend on it), and because it ends the epic: it deletes the layer packages and the frozen store, and turns rule 1 into a plain rule. The
   nine global classes move here so the nine types that every cluster imports are rewritten once, while
   nothing else is moving.

The epic's `## Features` list follows this order.

### 8. The move recipe

How every step stays green. A move feature's spec cites this section and adds only what is specific to its
group.

- **One group per feature.** The group is one row of the move-group table in decision 2.
- **Classes, tests and `ARCHITECTURE.md` entries move in the same merge.** The classes are moved with `git mv`,
  so rename detection shows each as a rename whose only changed lines are `package` and `import` lines.
  `sdlc-check-harness` errors on a backticked path in `ARCHITECTURE.md`, `docs/agents/README.md` or
  `docs/tech-debt.md` that no longer exists, and on a package directory that `ARCHITECTURE.md` does not
  name (note C8). So every `tech-debt.md` path to a moved class is rewritten in the merge, and every new
  package is named in `ARCHITECTURE.md`.
- **Nothing in the HTTP surface, schema or bean names changes.** No controller route, response, migration or
  configuration property is touched. A move changes nothing but package declarations, imports and the strings
  below.
- **Find fully qualified names in strings and comments.** The compiler does not see them. At the pinned commit
  there are four JPQL constructor expressions that name a class by its full path
  (`select new com.remotesupport.backend.repository.ClientInvoiceQueueRow(` in `ClientInvoiceRepository` twice,
  and one each in `AgentInvoiceRepository` and `UserRepository`), a handful of Javadoc `{@link}` and inline
  full paths in tests, and `<logger name="com.remotesupport.backend">` in
  `backend/src/test/resources/logback-test.xml`, which names the root package and is unaffected. A move greps for
  `com.remotesupport.backend.` outside `import` and `package` lines for its own old package names. A missed
  JPQL path fails when Hibernate validates the query at startup, and the integration tests catch it.
- **The full `verify` is green and nothing is skipped.** That is the command in `docs/agents/sdlc.json`:
  Maven `verify` (tests, Checkstyle, the boundary check) and the frontend lint, build, typecheck, unit
  tests and the isolated e2e suite. A move feature does not skip e2e because "only Java moved".
- **No behaviour change.** A move adds no logic, fixes no bug and pays no recorded debt. It may rewrite the
  path of a debt entry so the entry still resolves, and nothing else.
- **A move is scheduled only when no feature in the same cluster is mid-build.** A feature is mid-build when
  its tickets are in progress or its branch is unmerged. A draft spec that no one has started does not block.
- **A draft spec whose cluster moves before it is built.** Three drafts sit in the invoicing cluster today:
  `invoice-adjustment`, `month-closes-on-the-fifth` and
  `real-manager-dashboard`. All three say new backend classes go in the existing `web`, `repository` and
  `dto` packages, which stays true until `move-invoice`. The `move-invoice` spec names every draft still in
  its cluster, and its delivery changes each draft's code-location line and cited class paths to the new
  package and nothing else. Behaviour is not touched, so the draft's approval is not reopened. A draft
  built before the move simply moves with its cluster. The same rule applies to any future spec drafted
  before its cluster moves.
- **The boundary store shrinks in the same merge.** The move feature commits the reduced store (decision 6),
  and `ARCHITECTURE.md` and `tech-debt.md` as above.
- **A move feature is cut into tickets by package inside its group, never by main and test.** A test that
  moves without its class (or the reverse) leaves a half-moved package. Each ticket leaves `verify` green.

## Consequences

**What gets harder**

- Eight features, large diffs, and merge conflicts with any feature that touches a cluster being moved.
  The scheduling rule is the only defence, so the orchestrator must check the cluster of every in-progress
  feature before it starts a move.
- `ARCHITECTURE.md` and `docs/tech-debt.md` change in every move. The harness makes a missed path an error,
  so this cost is visible, not silent.
- Until the last move, the code has two layouts at once, and a reviewer has two rules to apply (Backend 13,
  as amended by the ticket that follows this one).
- The frozen store is a file several features edit, so two in-flight features that both add a class to an
  unmoved cluster can conflict on it. The conflict is a one-line merge.
- The cycle stays in the code. Eleven of twelve clusters still depend on each other after the epic, and the
  boundary tool only stops it growing. Breaking it is not in this ADR's plan beyond the invoicing cycle.
- `shared` is a drawer that fills. The rule in decision 3 is the only guard.

**Rejected candidates** (note section 3, comparison in section 4)

- **Package by feature with layer sub-packages (`web`, `domain`, `repository`, `service` inside each
  feature).** Rejected for the first pass. It re-creates the layer names that Backend 13 rejects, gives every
  class a second decision, splits the `web` package's 78 classes a second time, and makes Backend 6's "beside
  its package's unit tests" ambiguous. The note found no primary source recommending it [18]. A feature
  may add one internal sub-package later if it outgrows a flat package.
- **Spring Modulith application modules.** Rejected for now. Its verification rejects cycles between
  modules [6], and the code has them at twelve and at six packages, so verification would fail from day one;
  open modules relax only the internals rule, not the cycle rule. Adopting it first means breaking the
  `User`, `Agent` and `Tester` cycle and the invoicing cycle, which are code changes the epic excludes. It
  also splits the integration tests, because `@ApplicationModuleTest` limits the bootstrap to one module
  [8], and its annotations in production code would be a main-scope dependency. **Reconsider it after the
  cycles are broken.**
- **Hexagonal (ports and adapters).** Rejected. It is a redesign of 222 classes rather than a move: separate
  domain and JPA classes with mappers for 20 entities [16], new port interfaces for the use cases of about
  30 controllers, and a behaviour risk in every step, in an epic that carries none. Its package names
  (`adapter`, `application`, `port`) are layers, against Backend 13. The note found no production Spring
  codebase of this size with a documented migration cost [17].
- **Keeping the layered packages and adding the boundary tool alone.** Rejected: it does not meet the
  human's request, and `web` keeps growing.
- **A facade in front of `Contract` and `Request`.** Rejected (decision 4): 43 and 29 call sites to rewrite,
  no behaviour gained.
- **Breaking the cycle first, or inside the invoicing move.** Rejected (decision 5): a move changes no
  logic, and the break needs its own tests.
- **Using Spring Modulith's test starter only as the verifier.** Not chosen: it brings the same cycle rule
  that blocks it, and it pulls ArchUnit 1.3.1 anyway [9], so ArchUnit directly is the smaller dependency.

**New dependencies, each with scope and licence**

| Dependency | Version | Scope | Licence | Where |
|---|---|---|---|---|
| `com.tngtech.archunit:archunit-junit5` | 1.4.2 | test | Apache-2.0 | added by `package-boundary-check` |

Nothing in main scope.

**Debt in `docs/tech-debt.md` that a move absorbs**

A move absorbs an entry in one of two ways. Only the first pays the debt.

- *Paid by `break-the-invoice-service-cycle`:* "Dependency cycle bridged by `@Lazy`" and "Repeated kind
  filters" on `ContractAmountService`.
- *Path rewritten by the move, debt left open* (a move pays no debt, decision 8). Each entry is rewritten by
  the move that moves its class:
  - `move-invoice`: the entries for `ClientInvoiceService`, `ContractAmountService`, `AgentInvoiceService`,
    `ClientInvoice`, `ClientInvoiceSentBackController`, `ClientInvoiceLineEditApiTest`,
    `LocalSupportFeesRaceTest` and the entry citing ADR 0004;
  - `move-people-and-contract`: `TesterController` ("Business rule in a controller") and `AgentController`
    ("Transaction boundary on a controller");
  - `move-login-and-shared`: `AuthController` ("Refusal body built in try/catch");
  - the whole-directory entry on `backend/src/main/java/com/remotesupport/backend/web` ("Primitive
    Obsession": the billing month computed inline in about seven controllers) is split across the moves
    that touch each controller.

**What the human is asked to approve** (the approval item restates this)

The flat layout; the cost of eight features, 217 main and 61 test classes moved; the one test-scope
dependency; the move order; the amendment to "a shrinking list" described in decision 6; and the amendment to
Backend 13 in `docs/agents/coding-standards.md`, which applies only once this ADR is accepted and
`package-boundary-check` has merged, and which the human annotates "Amended by the human" when approving.
