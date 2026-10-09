---
id: package-by-feature
title: Package the backend by feature, not by layer
status: in-progress
journeys: []
---

<!-- sdlc:template epic 1 -->

## Intent

Asked for by the human on 2026-09-22, after two independent reviewers reported
that `coding-standards.md`'s "package by feature" rule was false of this
codebase: *"Adopt an implementation by feature/domain instead of layer. eg:
authentication, fleet, phones, requests, etc. This should be the norm."*

The backend is packaged by layer today —
`backend/src/main/java/com/remotesupport/backend/{web,repository,dto,domain,security,logging}`.
`web` alone holds the controllers, the services and the factories, so a single
feature's files are scattered across five packages and the largest package
grows without bound. `ARCHITECTURE.md` already describes this layout
faithfully; the point of the epic is to change what it describes.

**This carries no user-visible behaviour.** No journey moves, no endpoint
changes, no migration runs. Its value is that the next twenty features are
cheaper to find, read and cut — which also means it is the kind of work that
is easy to start and hard to finish, and it must be cut so that each feature
leaves the product working and green.

The human named the cost explicitly: *"the cost of refactoring now will be
big"*. Sizing that honestly, before committing to an order, is part of the
first feature rather than an assumption of the epic.

The human also asked that this be informed rather than invented: *"It would
need to search the best practices when it comes to this layering too."* So the
first feature is research, not code — and its output is a written decision
about *this* codebase, not a summary of blog posts.

## Journeys

None. No journey in `docs/journeys.md` moves; every one of them must keep
playing exactly as it does today, which is the epic's real acceptance test.

The proof that closes this epic: on `main`, the full `verify` is green, every
existing journey still plays end to end, and a named feature's files —
`authentication`, say — live in one package that a reader can open and
understand without opening four others.

## Features

- [x] `package-layout-decision` — research how Spring Boot codebases of this shape are packaged and propose this codebase's layout and move order as an ADR; no file moves. Its spec is approved when the human accepts ADR 0006; until then the ADR is proposed.
- [ ] `package-boundary-check` — an ArchUnit test that encodes the layout of ADR 0006 and fails `verify` on a new class in a layer package; existing classes allowed by a shrinking frozen list.
- [ ] `move-carrier` — the carrier cluster (19 main, 2 test classes) moves first, proving the move recipe on the smallest diff.
- [ ] `move-fleet-and-stock` — fleet and stock: Smartphones, SIM Cards, installation and Agent stock (25 main, 7 test).
- [ ] `move-request-and-fee` — Requests, their detail and completion handlers, Fees, pending requests (47 main, 9 test).
- [ ] `move-people-and-contract` — Agents, Clients, Testers and Contracts (32 main, 7 test).
- [ ] `move-invoice` — Client Invoices, Agent Invoices, standing amounts, the Review Queue (49 main, 13 test).
- [ ] `break-the-invoice-service-cycle` — removes the `@Lazy` cycle between the invoicing services, in the `invoice` package; the only step that changes code, and it may be postponed without blocking the last move.
- [ ] `move-login-and-shared` — sign-in, logins, passwords, `/api/me` and the nine global classes (45 main, 23 test); ends with the layer packages empty and deleted, and the frozen list deleted.

The move order and the names of these features are fixed by
[ADR 0006](../adr/0006-backend-is-packaged-by-feature-flat.md), decision 7.

## Reworked

Sized on 2026-10-06 by an exploration of `main`, before cutting:

- **222 main classes** in seven layer packages: `web` 78 (30 controllers plus
  services, factories and exceptions), `dto` 60, `domain` 38, `repository` 25,
  `security` 17, `logging` 2, `demo` 1. **71 test classes**, 47 of them
  integration tests extending `IntegrationTest`.
- **Twelve clusters are visible from names and tests:** authentication,
  agents, fleet, requests, Client Invoices, Agent Invoices, carriers, stock,
  contracts, fees, clients and Testers, identity (`/api/me`).
- **Coupling is concentrated, not spread.**
  - `Contract` is the most referenced entity, about 35 references from
    `web`, followed by `Request` (about 24).
  - `ContractAmountService` and `ClientInvoiceService` already depend on each
    other through `@Lazy`, and `AgentInvoiceService` sits on top of both. The
    decision has to say whether invoicing stays one package or the cycle is
    broken first.
- **Some pieces stay global whatever the layout:** Flyway migrations,
  `SecurityConfig`'s single matcher chain, `AuditLog`, and the
  `IntegrationTest` base class with its seeded fixtures. The decision has to
  name a home for each.
- **No boundary tooling exists.** Checkstyle covers hygiene only, and there is
  no ArchUnit or Spring Modulith, so nothing would stop the layout drifting
  back. Hence the `package-boundary-check` feature right after the decision.
- **A move is mechanically low-risk.** `@SpringBootApplication` scans the
  whole package tree with no explicit `@ComponentScan` or `@EntityScan`, so
  moving a class rewires nothing. The cost is the size of the diffs and merge
  conflicts with in-flight features. Moves therefore go one cluster at a
  time, each green, scheduled when no other feature is mid-build.

## Later

- The frontend is packaged by role (`app/agent`, `app/manager`, `app/client`)
  with shared components by feature (`components/fleet`, `components/requests`)
  — closer to feature packaging already. Whether it needs anything is out of
  scope until the backend is settled.
