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

- [ ] `package-layout-decision` — research how Spring Boot codebases of this shape are packaged and propose this codebase's layout and move order as an ADR; no file moves. Its spec ends in the human's approval of the cut.
- [ ] `package-boundary-check` — an automated check (ArchUnit or Spring Modulith, per the decision) that encodes the chosen layout and fails `verify` on a new class in a layer package; existing classes allowed by a shrinking list.
- [ ] `move-carriers-and-catalog` — the least-coupled cluster moves first, proving the move recipe (classes, tests, ARCHITECTURE.md) on a small feature.
- [ ] `move-fleet-and-stock` — Smartphones, SIM Cards, installation and Agent stock.
- [ ] `move-requests-and-fees` — Requests, their detail and completion handlers, Fees, pending requests.
- [ ] `move-people-and-contracts` — Clients, Testers, Agents and Contracts.
- [ ] `move-invoicing` — Client Invoices, Agent Invoices, standing amounts, the Review Queue; resolves the `@Lazy` cycle the decision rules on.
- [ ] `move-authentication-and-identity` — sign-in, logins, passwords, `/api/me`; ends with the layer packages empty and deleted.

The lines after the first are provisional: `package-layout-decision` may merge,
split or reorder them, and its delivery rewrites this list before the next
spec is drafted.

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
