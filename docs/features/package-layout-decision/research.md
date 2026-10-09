# Package layout research note

Feature: `package-layout-decision`. Evidence for the proposed ADR.

**Pinned commit.** Every `path:line` in this note refers to commit
`e22e81fd5408f0c427b3e723008fcbf6e748d6db` of `main` (2026-10-09, "docs(package-layout-decision): cut tickets").
Read a citation with `git show e22e81fd5408f0c427b3e723008fcbf6e748d6db:<path>`. `backend/` is identical on the
feature branch. Source retrieval date: 2026-10-09. Class counts are measured at this commit.
Where a `[n]` follows a claim, see Sources.

## 1. Recommendation

> **Corrected to agree with the ADR.** The ADR
> (`docs/adr/0006-backend-is-packaged-by-feature-flat.md`) is the document the human approves, and where this
> section and the ADR differ the ADR rules. The cost and global-piece statements in this section were corrected
> from an earlier draft (seven features, 208 and 58 classes, global pieces at the root); the ADR's decision 2
> explains why. Sections 2 to 7 are the unedited evidence and still show the earlier sums in places (for example
> sign-in with identity 36 and 21, global 14 main, which do not count the nine global classes that move).

*Choice.* Package by feature, flat: one package per feature directly under the root package, holding that
feature's entity, repository, service, controller and request/response shapes together. No layer sub-packages
inside a feature. The one exception is Requests, whose three existing handler-family sub-packages stay (they split
by request type, not by layer). Spring Modulith modules and hexagonal were judged and rejected for this codebase.

*Cost.* Eight features after this one: the boundary check, six that each move one group of clusters (see sizing in
section 5), and the break of the invoicing cycle. 217 of 222 main classes and 61 of 71 test classes change package.
The other 5 and 10 stay where they are (`BackendApplication`, `SecurityConfig`, `AuditLog`, `RequestLoggingFilter`,
`DemoDataLoader`; the seven migration tests, `IntegrationTest`, `OtherTenantFixture`, `DemoDataLoaderApiTest`). Net new dependency: ArchUnit 1.4.2, test scope, Apache-2.0 [11][12]. Nothing
new in main scope, so nothing in the shipped application changes.

The six questions from the spec's Problem, one line each:

1. *Which layout?* Flat package by feature, named for glossary words; the twelve clusters become six move groups
   (carriers; fleet with stock; requests with fees; agents, clients, testers and contracts; invoicing; sign-in with
   identity).
2. *Where do global pieces go?* Security configuration stays in `security`, the audit log and request logging in `logging`, the demo
   loader in `demo`. Tenant goes to `tenant`; country, currency, billing month and the three common exceptions go to
   `shared`; the health endpoint goes to `system`. Flyway scripts stay where they are, because their location is set by configuration, not by Java packages [3]. The test base
   class and fixtures stay in `support`.
3. *How does one feature refer to another's entity?* By importing the public entity type (Contract and Request
   included, with 43 and 29 importing classes). Another feature's repository is used only through a frozen,
   shrinking list. No facade is added in front of Contract or Request.
4. *What of the lazy cycle?* Invoicing is one package, so the cycle between the contract-amount and client-invoice
   services does not cross a boundary. Leave it as it is during the move; break it later as its own feature.
5. *What stops drift?* ArchUnit rules run in the normal test phase, with a stored list of existing violations that
   only shrinks [10]. Spring Modulith is not chosen, because its verification rejects module cycles [6] and the code has
   them at both twelve- and six-package granularity.
6. *Order and staying green?* Carriers first (fewest outgoing links), then fleet with stock, requests with fees,
   people and contracts, invoicing, and sign-in with identity last. One group per feature; classes, tests and
   `ARCHITECTURE.md` entries move in one merge; full `verify` stays green at every step.

*What the human must approve.* The flat layout, the eight-feature cost, and the one test-scope dependency.

## 2. This codebase's constraints

Paths are written in full in each citation (root package `com.remotesupport.backend`).

### C1. Size and the twelve clusters

222 main classes and 71 test classes (section 5 gives the recount). Seven layer packages hold the main classes:
`web` 78, `dto` 60, `domain` 38, `repository` 25, `security` 17, `logging` 2, `demo` 1, plus `BackendApplication`
at the root. The HTTP surface shows the clusters: 25 controller classes declare route prefixes for agents, agent
invoices, standing amounts, auth, carriers, clients, testers, contracts, fees, requests, Smartphones, SIM Cards,
stock and `/api/me`; for example `backend/src/main/java/com/remotesupport/backend/web/AgentController.java:45`
(`/api/agents`), `backend/src/main/java/com/remotesupport/backend/web/ClientInvoiceController.java:65`
(`/api/contracts/{contractId}/client-invoice`), `backend/src/main/java/com/remotesupport/backend/web/FeeController.java:71`,
`backend/src/main/java/com/remotesupport/backend/web/RequestController.java:55`,
`backend/src/main/java/com/remotesupport/backend/web/StockController.java:34`,
`backend/src/main/java/com/remotesupport/backend/web/MeAgentController.java:23`. The clusters used in this note
are: authentication, identity (`/api/me`), agents, clients and testers, contracts, carriers, fleet, stock, requests,
fees, Client Invoices, Agent Invoices (with standing amounts). Assigning each class to a cluster is a judgement call
made by name and route; section 5 lists the rule and the borderline assignments.

### C2. Contract and Request are imported everywhere

Counted as main-source files that import the entity type: `domain.Contract` 43 files (35 in `web` and its
sub-packages, 4 in `security`, 2 in `dto`, 1 in `repository`, 1 in `demo`), `domain.Request` 29 files (24 in `web`
and its sub-packages, 2 in `dto`, 2 in `repository`, 1 in `demo`). The spec's "about 35" and "about 24" are the `web`
numbers. Tests: 7 test files import `Contract`, 4 import `Request`. Both are plain JPA entities with public getters
and setters from Lombok: `backend/src/main/java/com/remotesupport/backend/domain/Contract.java:22-27`,
`backend/src/main/java/com/remotesupport/backend/domain/Request.java:49-63`.

### C3. The lazy cycle and the services above it

`ContractAmountService` takes `ClientInvoiceService` as a lazy constructor argument
(`backend/src/main/java/com/remotesupport/backend/web/ContractAmountService.java:48`), with the reason written at
`backend/src/main/java/com/remotesupport/backend/web/ContractAmountService.java:39-43`: the invoice resolves its lines with
this service and this service resolves pay through the invoice's lines (call at
`backend/src/main/java/com/remotesupport/backend/web/ContractAmountService.java:132`). `ClientInvoiceService` takes
`ContractAmountService` and `AgentInvoiceService` as ordinary arguments
(`backend/src/main/java/com/remotesupport/backend/web/ClientInvoiceService.java:62`,
`backend/src/main/java/com/remotesupport/backend/web/ClientInvoiceService.java:67`). `AgentInvoiceService` takes
`ContractAmountService` (`backend/src/main/java/com/remotesupport/backend/web/AgentInvoiceService.java:39`). Correction
to the epic's wording: `AgentInvoiceService` sits above `ContractAmountService` and below `ClientInvoiceService`, not
above both. It reaches the Client Invoice side only through `ContractAmountService`'s lazy link, and imports the
`ClientInvoice` entity (`backend/src/main/java/com/remotesupport/backend/web/AgentInvoiceService.java:6`).

### C4. Global pieces

- `SecurityConfig` holds the single matcher chain: 28 `requestMatchers` calls naming routes of many features, ending
  `anyRequest` plus the JWT and logging filters
  (`backend/src/main/java/com/remotesupport/backend/security/SecurityConfig.java:67-68`,
  `backend/src/main/java/com/remotesupport/backend/security/SecurityConfig.java:239-242`).
- `AuditLog` is a static helper called from 27 main files
  (`backend/src/main/java/com/remotesupport/backend/logging/AuditLog.java:17`).
- Flyway: 50 migration scripts in `backend/src/main/resources/db/migration` (Spring Boot's default location [3]), location set at
  `backend/src/main/resources/application.yml:12-14`. `ddl-auto` is `validate`
  (`backend/src/main/resources/application.yml:9-10`).
- `IntegrationTest` base class with seeded ids
  (`backend/src/test/java/com/remotesupport/backend/support/IntegrationTest.java:52-77`).
- `DemoDataLoader` imports 72 project types from every layer, runs only under the `demo` profile
  (`backend/src/main/java/com/remotesupport/backend/demo/DemoDataLoader.java:121-123`).
- Shared types: `BillingMonth` is used by 13 other main files across invoicing, fees and `/api/me/agent`
  (`backend/src/main/java/com/remotesupport/backend/domain/BillingMonth.java:7`); the common exceptions
  `NotFoundException` 25 other files, `ConflictException` 23, `InvalidRequestException` 24
  (`backend/src/main/java/com/remotesupport/backend/web/NotFoundException.java:7-8`,
  `backend/src/main/java/com/remotesupport/backend/web/ConflictException.java:7-8`,
  `backend/src/main/java/com/remotesupport/backend/web/InvalidRequestException.java:10-11`).

### C5. Whole-tree scanning

`@SpringBootApplication` sits on the root class
(`backend/src/main/java/com/remotesupport/backend/BackendApplication.java:7-8`). A search of `backend/src` finds no
`@ComponentScan`, `@EntityScan`, `@EnableJpaRepositories` or `@ConfigurationPropertiesScan`. Spring Boot searches
the root class's package tree for components and for `@Entity` items [1]. So moving a class inside the tree rewires
nothing. A new package must stay below the root package.

### C6. JPA associations across clusters; public visibility

20 entities, 56 `@ManyToOne`/`@OneToOne` associations (17 to `Tenant`, 39 between other entities), many crossing the clusters above. Examples:
`Fee` points to `Contract` and `Request`
(`backend/src/main/java/com/remotesupport/backend/domain/Fee.java:59-69`), `Request` points to `Contract`, `Tester`,
`User`, `Smartphone`, `SimCard`, `Carrier` and `PostpaidPlan`
(`backend/src/main/java/com/remotesupport/backend/domain/Request.java:53-63`,
`backend/src/main/java/com/remotesupport/backend/domain/Request.java:125-135`,
`backend/src/main/java/com/remotesupport/backend/domain/Request.java:175-187`), `SimCard` points to `Contract`,
`Agent`, `Carrier`, `PostpaidPlan` and `Smartphone`
(`backend/src/main/java/com/remotesupport/backend/domain/SimCard.java:43-92`), `ClientInvoice` points to `Contract`
(`backend/src/main/java/com/remotesupport/backend/domain/ClientInvoice.java:57-59`), `User` points to `Agent` and
`Tester` points back to `User`
(`backend/src/main/java/com/remotesupport/backend/domain/User.java:50-52`,
`backend/src/main/java/com/remotesupport/backend/domain/Tester.java:39-41`). Every top-level type in the main tree is
`public` (a search for a top-level type without `public` finds none); entities use Lombok public accessors and a
public no-argument constructor
(`backend/src/main/java/com/remotesupport/backend/domain/Contract.java:22-27`) and repositories are public interfaces
(`backend/src/main/java/com/remotesupport/backend/repository/ContractRepository.java:9`). Today nothing relies on
package-private access, and cross-feature entity associations need the target type to be visible.

### C7. Backend 6: integration tests beside their package

An integration test extends `IntegrationTest`, keeps the `*Test` name and sits beside the unit tests of its package
(`docs/agents/coding-standards.md:51-54`). 57 test files extend `IntegrationTest` (the epic said 47); 56 of the
71 test classes sit in `web`. The base class boots the full context against a Testcontainers PostgreSQL
(`backend/src/test/java/com/remotesupport/backend/support/IntegrationTest.java:52-56`).

### C8. `ARCHITECTURE.md` paths, checked by `sdlc-check-harness`

`ARCHITECTURE.md` names the layer packages and 11 distinct `backend/src/...` paths
(`ARCHITECTURE.md:15-27`, `ARCHITECTURE.md:82-83`, `ARCHITECTURE.md:93-94`). `docs/tech-debt.md` cites 12 more
(`docs/tech-debt.md:71-84`). The check errors on a backticked path that no longer exists, in `ARCHITECTURE.md`, in
`docs/agents/README.md` and in `docs/tech-debt.md`, and errors when an immediate sub-directory of a configured
module root is not named in `ARCHITECTURE.md`. The module root for the backend is
`backend/src/main/java/com/remotesupport/backend` (`docs/agents/sdlc.json:13-14`). Check source [19]: lines 65-83 (path scan), 100-104 (files scanned) and
128-142 (module-undocumented). Run at this commit it passes. Consequence: each move must update `ARCHITECTURE.md`
and the `tech-debt.md` paths in the same merge, and every new feature package must be named in `ARCHITECTURE.md`.

### C9. Checkstyle

Hygiene only: line length, unused and star imports, braces and similar
(`backend/checkstyle.xml:6-10`, `backend/checkstyle.xml:15-33`). It runs at `verify` and fails on warning
(`backend/pom.xml:150-168`). It has no package or import-control rule, so it cannot hold a layout.

### C10. Spring Boot 3.3.4 on JDK 21

Parent POM `spring-boot-starter-parent` 3.3.4 (`backend/pom.xml:7-12`), `java.version` 21
(`backend/pom.xml:21`), Lombok as an optional compile-time dependency (`backend/pom.xml:88-92`). Spring Boot 3.3
supports Java 17 up to and including 23 [2]. Spring Boot 3.3's managed dependency list names neither ArchUnit nor
Spring Modulith [4], so any version of either is pinned by this project.

*Tool versions named in this note and their compatibility.* ArchUnit 1.4.2: Java 21 support arrived in 1.1.0 and the
project ran its tests on JDK 21 from 1.4.0 [11]; the artifact depends on no Spring library [12]; Boot 3.3 does not
manage it [4], and Boot 3.3 runs on Java 17 to 23 [2]. Spring Modulith 1.2.13 (considered, not chosen): compiled
against Spring Boot 3.3 [7]; no Modulith page states a JDK range, so JDK 21 is inferred from [7] and [2] (see
section 6).

### C11. Module-level dependency cycles (measured here)

This is a constraint the spec did not list but which decides one candidate. At this commit, with classes assigned
to the twelve clusters of section 5 (the global group excluded), 11 of the 12 clusters (all but identity, which nothing
else references) sit in one strongly connected component of references. Method: for each main class, the set of other main
class simple names appearing in its source with comments removed (an identifier match, so it can over-count a name
that coincides with a type); each hit adds an edge from the source's cluster to the target's cluster. Two-way pairs:
agents and authentication, agents and agent invoices, agent invoices and client invoices, agents and contracts,
authentication and clients-and-testers, clients-and-testers and contracts, contracts and fleet, requests and stock.
Examples with lines: `User` points to `Agent`
(`backend/src/main/java/com/remotesupport/backend/domain/User.java:50-52`) while `AgentLoginService` creates `User`
rows (`backend/src/main/java/com/remotesupport/backend/web/AgentLoginService.java:5`,
`backend/src/main/java/com/remotesupport/backend/web/AgentLoginService.java:33`); `AgentController` uses the standing
amount service and type from invoicing
(`backend/src/main/java/com/remotesupport/backend/web/AgentController.java:4`,
`backend/src/main/java/com/remotesupport/backend/web/AgentController.java:52`) while `AgentStandingAmount` points to
`Agent`; `ReviewQueueController` reads the Agent Invoice repository
(`backend/src/main/java/com/remotesupport/backend/web/ReviewQueueController.java:7`,
`backend/src/main/java/com/remotesupport/backend/web/ReviewQueueController.java:40`) while `AgentInvoiceService` reaches
the Client Invoice side (C3). Grouping into the six move groups of section 1 does not remove the cycle: the six
groups are still one strongly connected component (largest links: requests-with-fees to fleet-with-stock 98 class
pairs, to people 40; people to sign-in 14; sign-in to people 19). A rule "no cycles between features" therefore
cannot be switched on at any of these granularities without first changing code.

### C12. Backend 13, the norm

New code is packaged by feature, not by layer (`docs/agents/coding-standards.md:82-90`); the human decided this on
2026-09-22. A target that re-creates layer names inside each feature argues against the norm.

## 3. Candidates

### 3.1 Package by feature, flat

*What it is.* One package per feature; the feature's entity, repository, service, controller and DTOs live together.
Spring Boot's own reference shows exactly this layout: `customer` and `order` packages, each holding the entity,
controller, service and repository [1].

*Where it is used.* Spring PetClinic's main branch: packages `owner` (entity, controller, repository, validator,
formatter together), `vet`, `system`, and a small shared `model` package for base entities [14].

*Fit.* C1 fits: moves files, no redesign. C2 fits: Contract and Request stay public entities in their feature
package. C3 fits when invoicing is one package. C4 fits: global pieces get a root group. C5 fits [1]. C6 fits: all
types are already public. C7 fits: tests mirror the package and stay beside their unit tests. C8 costs: every move
edits `ARCHITECTURE.md` and `tech-debt.md` paths. C9 fits (Checkstyle does not look at packages). C10 fits.
C11 fits only because no cycle rule is required at the start. C12 fits.

*Cost beyond moving files.* No tool enforces the boundary, so the check feature (ArchUnit) is added: one test class
and a stored list of known violations [10]. A simple Modulith module can hide types behind Java package scope [5], but this code makes every type public (C6), so
flat packaging alone hides nothing. The largest
feature, Requests, would be a 41-class package (a 47-class package with fees) unless its three handler-family
sub-packages stay.

*Verdict.* Chosen.

### 3.2 Package by feature with layer sub-packages

*What it is.* One package per feature, with `web`, `domain` (or `model`), `repository`, `service` sub-packages
inside each.

*Where it is used.* Evidence of existence is thinner than for the other three. The Spring PetClinic microservices
repository splits a service package into `model`, `web` and `web.mapper` sub-packages inside
`customers`, a layered split below one service rather than below many features [18]. Spring Modulith's reference
describes an `internal` sub-package inside a module, which is a visibility split rather than a layer split [5]. No
primary source found recommends layer sub-packages inside a feature (see section 6).

*Fit.* C1 costs: each of the 12 clusters needs its layer folders, up to about 48 sub-packages (12 times four) for 222
classes, and the cluster table gets a second axis. C2 fits. C3 fits. C4 fits. C5 fits. C6 fits: every type is already public. C7 costs: Backend 6 puts the integration test
beside "its package's unit tests", so the test moves into one chosen sub-package, a new convention. C8 costs more
than flat: more directories to name in `ARCHITECTURE.md` (the module-undocumented check covers only immediate
sub-directories, so it does not require them). C9 fits. C10 fits. C11 fits. C12 costs: it re-creates the layer names
that Backend 13 rejects.

*Cost beyond moving files.* A second decision for every class (which layer folder). The `web` package's 78 classes
would be split again into controller and service folders per feature.

*Verdict.* Rejected for the first pass. A feature may later add one internal sub-package if it outgrows a flat
package.

### 3.3 Spring Modulith application modules with verified boundaries

*What it is.* Each direct sub-package of the main package is an application module; its API is the public types in
the module's base package; sub-packages are internal; `verify()` rejects module cycles and references into
internals [5][6]. In Modulith 1.2 the verification rules are: no cycles at module level, references to other
modules through API packages only, and, if declared, explicitly allowed dependencies [6].

*Where it is used.* The project's own example: `example.order` and `example.inventory` modules, with
`order.internal` holding an internal type [15].

*Fit.* C1 fits. C2 fits if Contract and Request are public in the base package. C3 fits if invoicing is one module.
C4 costs: the global group becomes either a module or an open module; the reference says open modules usually hint
at sub-optimal packaging [5]. C5 fits: modules come from the package under the main class [5]. C6 fits for public
entities. C7 costs: module integration tests use `@ApplicationModuleTest`, which restricts the bootstrap to one
module [8]; the existing tests boot the whole context. C8 costs: `ARCHITECTURE.md` plus any `package-info` files.
C9 fits. C10 fits with an explicit version: Spring Modulith 1.2 was compiled against Spring Boot 3.3 [7];
version 1.2.13 is the final 1.2 release shown in the 1.2 reference [7]. C11 blocks: verification rejects cycles
between modules [6] and C11 shows the code has them at every granularity tried. C12 fits.

*Cost beyond moving files.* A runtime or compile dependency on `spring-modulith-api` only if modules are annotated
(main scope, so it needs the approval item). The test-only starter is test scope [7], and pulls ArchUnit 1.3.1 and
is Apache-2.0 [9]. Unless cycles are broken first, verification fails, so adoption means breaking the
`User`/`Agent`/`Tester` and invoicing cycles first (code changes the epic excludes). Open modules only relax the
internals rule, not the cycle rule [6].

*Verdict.* Rejected for now. Reconsider after the cycles are broken.

### 3.4 Hexagonal (ports and adapters)

*What it is.* The application core (domain objects and use cases) has no outward dependencies; it talks to the
outside through input and output ports, with adapters for web and persistence [13]. The Spring walk-through keeps
separate domain and JPA entity classes and a mapper between them [16].

*Where it is used.* Buckpal, the companion repository of a book on the style, with `application/port/in`,
`application/port/out`, `adapter/in/web`, `adapter/out/persistence`, and separate `AccountJpaEntity` and
`Account` plus an `AccountMapper` [17] (a teaching project, evidence that the style exists in Spring, not of a
production codebase of this size). ArchUnit ships a predefined rule for this shape, called onion architecture [10].

*Fit.* C1 costs: it is a redesign of 222 classes, not a move. C2 costs: Contract and Request would become domain
objects with separate JPA entities and mappers. C3 costs: services above ports. C4 fits. C5 fits. C6 costs: 20
entities to split. C7 costs. C8 costs. C9 fits. C10 fits. C11 costs: cycles are between use cases and remain.
C12 costs: its package names are layers (`adapter`, `application`, `port`).

*Cost beyond moving files.* New port interfaces for 30 controllers' use cases, 20 JPA entity and domain pairs and
mappers, and a behaviour-risk to every move. The epic carries no behaviour change.

*Verdict.* Rejected.

## 4. Comparison

One word per cell. "blocks" means it cannot be done without a change the epic excludes.

| Constraint | Flat by feature | Feature plus layer sub-packages | Spring Modulith | Hexagonal |
|---|---|---|---|---|
| C1 size and clusters | fits | costs | fits | costs |
| C2 Contract and Request | fits | fits | fits | costs |
| C3 lazy cycle | fits | fits | fits | costs |
| C4 global pieces | fits | fits | costs | fits |
| C5 whole-tree scanning | fits | fits | fits | fits |
| C6 JPA associations, visibility | fits | fits | fits | costs |
| C7 Backend 6 | fits | costs | costs | costs |
| C8 `ARCHITECTURE.md` and harness | costs | costs | costs | costs |
| C9 Checkstyle | fits | fits | fits | fits |
| C10 Boot 3.3.4, JDK 21 | fits | fits | fits | fits |
| C11 module cycles | fits | fits | blocks | costs |
| C12 Backend 13 norm | fits | costs | fits | costs |

Trace: flat is 3.1, layer sub-packages 3.2, Modulith 3.3, hexagonal 3.4; each cell's reason is the "Fit" paragraph
of that section.

## 5. Sizing

Counts at the pinned commit. "IT" means test classes that extend `IntegrationTest`. Rule: each class is assigned
by name and route to one cluster; classes used by all features go to "global". Borderline assignments:
`ContractAmountService`, the carrier-invoice-file classes and `ReviewQueueController` go to Client Invoices;
`ProvisioningService`, returned units and `Disposition` go to requests; `PostpaidPlan`, `TopupOption` and
`CarrierOffer` go to carriers; the tester and agent login services go to their person's cluster, the password and
activation services to authentication; `CallerIdentityResolver` goes to authentication; `ChangePasswordController`
(route `/api/me`) goes to authentication.

| Cluster | Main | of which web / dto / domain / repository / security | Test | of which IT |
|---|---|---|---|---|
| authentication | 30 | 10 / 6 / 3 / 1 / 10 | 18 | 15 |
| identity (`/api/me`) | 6 | 3 / 3 / 0 / 0 / 0 | 3 | 3 |
| agents | 11 | 4 / 4 / 1 / 2 / 0 | 3 | 3 |
| clients and testers | 13 | 4 / 5 / 2 / 2 / 0 | 3 | 3 |
| contracts | 8 | 3 / 2 / 1 / 1 / 1 | 1 | 1 |
| carriers | 19 | 3 / 7 / 4 / 4 / 1 | 2 | 2 |
| fleet | 22 | 4 / 9 / 6 / 2 / 1 | 5 | 5 |
| stock | 3 | 2 / 1 / 0 / 0 / 0 | 2 | 2 |
| requests | 41 | 26 / 7 / 5 / 2 / 1 | 7 | 7 |
| fees | 6 | 1 / 2 / 2 / 1 / 0 | 2 | 2 |
| Client Invoices | 31 | 9 / 9 / 6 / 6 / 1 | 11 | 11 |
| Agent Invoices | 18 | 5 / 5 / 4 / 3 / 1 | 2 | 2 |
| global (root, security config, logging, demo, tenant, shared types, migrations tests, support) | 14 | 4 / 0 / 4 / 1 / 1, plus logging 2, demo 1, root 1 | 12 | 2 |
| **Total** | **222** | | **71** | **57** |

Move groups (section 1): carriers 19 main and 2 test; fleet with stock 25 and 7; requests with fees 47 and 9; agents,
clients, testers and contracts 32 and 7; invoicing (both) 49 and 13; sign-in with identity 36 and 21. Sum with
global: 19+25+47+32+49+36+14 = 222 main; 2+7+9+7+13+21+12 = 71 test. Not moved: the 14 global main classes and
12 global test classes (migration tests 7, `support` 2, `demo` 1, `domain` 1, health and protected-endpoint tests 2).

Recount, reproducible: `find backend/src/main -name '*.java' | wc -l` gives 222; the same under `backend/src/test`
gives 71; per-package counts are `web` 78, `dto` 60, `domain` 38, `repository` 25, `security` 17, `logging` 2,
`demo` 1, root 1.

The epic's 222/71 matches. The epic's 47 integration tests does not: 57 test classes extend `IntegrationTest`.

## 6. What is not settled

- *Which cycles the ADR breaks, and when.* C11 measures cycles by identifier match, not by a compiler or ArchUnit
  run; the exact edges that close them need a real tool run in `package-boundary-check`. Reason: no tool is added by
  this feature.
- *ArchUnit and the JUnit platform of Boot 3.3.4.* The chosen ArchUnit artifact declares no JUnit platform version
  [12]; no source states it was run against Boot 3.3.4's JUnit. Reason: building it would edit `pom.xml`, which this
  feature may not. `package-boundary-check` must run it once before relying on it.
- *Evidence for layer sub-packages inside features.* Only a per-service example was found [18]; no primary source
  recommends or warns against it. Reason: the sources surveyed describe flat modules (Boot, Modulith) or hexagonal
  layers, not this hybrid.
- *Hexagonal in a codebase this size.* Buckpal is a teaching project [17]; no production Spring codebase of 200+
  classes was found with a documented migration cost. Reason: only blogs and books exist, and they count as
  existence evidence only.
- *Spring Modulith on JDK 21.* No Modulith page states a JDK range; the note infers it from Boot 3.3's range [2][7].
  Reason: the reference documents Boot compatibility only. Not needed for the recommendation, which does not use it.
- *Spring Boot 3.3.4 itself.* The system requirements page states Java support for the 3.3.13 line, not for 3.3.4
  [2]. Reason: the reference is kept per minor line.
- *Cluster assignment.* Borderline classes (section 5) may move when the ADR names target packages.
- *Frontend.* Out of scope (the epic's `## Later`).

## 7. Sources

Primary sources first. All retrieved 2026-10-09.

1. *Spring Boot Reference: Structuring Your Code.* VMware (Spring). Version covered: Spring Boot 3.3 (URL path
   `/3.3/`). https://docs.spring.io/spring-boot/3.3/reference/using/structuring-your-code.html
   Passages: "We generally recommend that you locate your main application class in a root package above other
   classes." / "For example, if you are writing a JPA application, the package of the @SpringBootApplication
   annotated class is used to search for @Entity items." / "If you wish to enforce a structure based on domains,
   take a look at Spring Modulith." The page's typical layout lists `customer` and `order` packages, each with its
   entity, controller, service and repository.
2. *Spring Boot Reference: System Requirements.* VMware. Version covered: Spring Boot 3.3.13 page of the 3.3 line.
   https://docs.spring.io/spring-boot/3.3/system-requirements.html
   Passage: "Spring Boot 3.3.13 requires at least Java 17 and is compatible with versions up to and including
   Java 23."
3. *Spring Boot Reference: Database Initialization (how-to).* VMware. Version covered: 3.3.
   https://docs.spring.io/spring-boot/3.3/how-to/data-initialization.html
   Passage: "By default, they are in a directory called classpath:db/migration, but you can modify that location by
   setting spring.flyway.locations."
4. *Spring Boot Reference: Dependency Versions, Coordinates.* VMware. Version covered: 3.3.
   https://docs.spring.io/spring-boot/3.3/appendix/dependency-versions/coordinates.html
   Observation: a search of the page text finds no ArchUnit coordinate; "Spring Modulith" appears only in the site
   navigation. (The Boot 3.3.4 BOM file on Maven Central,
   https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.3.4/spring-boot-dependencies-3.3.4.pom,
   contains neither "archunit" nor "modulith".)
5. *Spring Modulith Reference: Fundamentals.* VMware (Spring). Version covered: 1.2.13.
   https://docs.spring.io/spring-modulith/reference/1.2/fundamentals.html
   Passages: "By default, each direct sub-package of the main package is considered an application module
   package." / "Thus, naturally, the module's API consists of all public types in the package." / "It allows to hide code inside it by using Java’s package scope to hide types from being referred to by code residing in other packages" / "In a
   fully-modularized application, using open application modules usually hints at sub-optimal modularization and
   packaging structures."
6. *Spring Modulith Reference: Verifying Application Module Structure.* VMware. Version covered: 1.2.13.
   https://docs.spring.io/spring-modulith/reference/1.2/verification.html
   Passages: "No cycles on the application module level — the dependencies between modules have to form a directed
   acyclic graph." / "Efferent module access via API packages only — all references to types that reside in
   application module internal packages are rejected."
7. *Spring Modulith Reference: Appendix A (Spring Boot Compatibility) and C (modules).* VMware. Version covered:
   1.2.13. https://docs.spring.io/spring-modulith/reference/1.2/appendix.html
   Passages: table row "1.2 | 3.3 | 3.1, 3.2, 3.3, 3.4" (Spring Modulith version, Spring Boot compiled against,
   Spring Boot examples tested against); starter table row "spring-modulith-starter-test | test | spring-modulith-docs,
   spring-modulith-test".
8. *Spring Modulith Reference: Integration Testing Application Modules.* VMware. Version covered: 1.2.13.
   https://docs.spring.io/spring-modulith/reference/1.2/testing.html
   Passage: "This will run your integration test similar to what @SpringBootTest would have achieved but with the
   bootstrap actually limited to the application module the test resides in."
9. *spring-modulith-core 1.2.13 POM.* VMware, Maven Central.
   https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-core/1.2.13/spring-modulith-core-1.2.13.pom
   Passages: dependency `com.tngtech.archunit:archunit:1.3.1` scope compile; license "Apache License, Version 2.0".
10. *ArchUnit User Guide.* TNG Technology Consulting GmbH. Version covered: 1.5.1.
    https://www.archunit.org/userguide/html/000_Index.html
    Passages: "FreezingArchRule can help in these scenarios by recording all existing violations to a
    ViolationStore. Consecutive runs will then only report new violations and ignore known violations. If
    violations are fixed, FreezingArchRule will automatically reduce the known stored violations to prevent any
    regression." (section 8.6) / "slices().matching(\"com.myapp.(*)..\").should().beFreeOfCycles()" (section 4.7) /
    "In an \"Onion Architecture\" (also known as \"Hexagonal Architecture\" or \"Ports and Adapters\")..."
    (section 8.1.2).
11. *ArchUnit releases.* TNG Technology Consulting GmbH. Covers 1.0.0 to 1.5.0.
    https://github.com/TNG/ArchUnit/releases (read through the API, https://api.github.com/repos/TNG/ArchUnit/releases)
    Passages: v1.1.0 (2023-08-09) "Add support for Java 21 (see #1098)"; v1.4.0 (2025-02-10) "Extend CI to run tests
    with JDK 21 (#1408)"; v1.4.2 (2026-04-18) "Support Java 26 / class file major version 70 (#1544)"; v1.0.0-rc1
    "ArchUnit now needs at least Java 8 to run (see #833)".
12. *archunit-junit5 1.4.2 POM.* TNG Technology Consulting GmbH, Maven Central. Version covered: 1.4.2.
    https://repo1.maven.org/maven2/com/tngtech/archunit/archunit-junit5/1.4.2/archunit-junit5-1.4.2.pom
    Passages: license "The Apache Software License, Version 2.0"; dependencies are `archunit-junit5-api` and
    `archunit-junit5-engine` at 1.4.2 only.
13. Cockburn, Alistair. *Hexagonal architecture.* Alistair Cockburn's site (original article dated 2005-09-04).
    https://alistair.cockburn.us/hexagonal-architecture/
    Passage: "Allow an application to equally be driven by users, programs, automated test or batch scripts, and
    to be developed and tested in isolation from its eventual run-time devices and databases."
14. *spring-petclinic*, repository by the Spring team, `main` at commit
    `500158f732419217507c7656904b8e6aa1bcc0d6` (2026-09-29). https://github.com/spring-projects/spring-petclinic
    Code evidence (file list): `src/main/java/org/springframework/samples/petclinic/owner/Owner.java`,
    `.../owner/OwnerController.java`, `.../owner/OwnerRepository.java`, `.../vet/Vet.java`, `.../vet/VetController.java`,
    `.../vet/VetRepository.java`, `.../model/BaseEntity.java`.
15. *spring-modulith-example-full*, repository by the Spring team, `main` at commit
    `c103395eedd6909eab57648c6d55246808723b48` (2026-10-02).
    https://github.com/spring-projects/spring-modulith/tree/main/spring-modulith-examples/spring-modulith-example-full
    Code evidence: `src/main/java/example/order/OrderManagement.java`,
    `src/main/java/example/order/internal/OrderInternal.java`, `src/main/java/example/inventory/InventoryManagement.java`.
16. Hombergs, Tom. *Hexagonal Architecture with Java and Spring.* Reflectoring, 2019-11-03. **Blog: evidence of
    existence only.** https://reflectoring.io/spring-hexagonal/
    Passage: "The main feature of \"Hexagonal Architecture\", as opposed to the common layered architecture style, is
    that the dependencies between our components point \"inward\", towards our domain objects"; the article's
    persistence adapter calls `accountMapper.mapToDomainEntity(` and `accountMapper.mapToJpaEntity(activity)`.
17. *buckpal*, repository by Tom Hombergs, `master` at commit `dc819c66640be4f42100a622b9e97b1e82ad75a7`
    (2023-07-23). **Teaching project: evidence of existence only.** https://github.com/thombergs/buckpal
    Passage (README): "This repository implements a small web app in the Hexagonal Architecture style, as discussed in
    the book \"Get Your Hands Dirty on Clean Architecture\"." Code evidence: `application/port/in/SendMoneyUseCase.java`,
    `adapter/out/persistence/AccountJpaEntity.java`, `adapter/out/persistence/AccountMapper.java`,
    `application/domain/model/Account.java`.
18. *spring-petclinic-microservices*, repository by the Spring team, `main` at commit
    `1d76b00d683e86b62867a6bf59530f8ab301244f` (2026-10-07).
    https://github.com/spring-petclinic/spring-petclinic-microservices
    Code evidence: `spring-petclinic-customers-service/src/main/java/org/springframework/samples/petclinic/customers/model/Owner.java`,
    `.../customers/web/OwnerResource.java`, `.../customers/web/mapper/OwnerEntityMapper.java`.
19. *sdlc-check-harness* (`scripts/sdlc_lib/checks_harness.py`), the sdlc project's own tool, local checkout at commit
    `cc24d1c`; path `/Users/oscar/myspace/projects/sdlc/scripts/sdlc_lib/checks_harness.py`, no public URL.
    Passages (module docstring): "path-missing          a backticked path cited in a checked doc does not exist"
    and "module-undocumented   an immediate subdir of a module_roots entry is not named in ARCHITECTURE.md".
