---
id: package-by-feature
title: Package the backend by feature, not by layer
status: proposed
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

Not cut yet. The first feature must be the research and the plan, because how
this is cut depends on what that research concludes:

- research the layouts actually used by Spring Boot codebases of this shape
  (package-by-feature, package-by-feature-with-layers-inside, modulith with
  enforced boundaries), against this project's real constraints: JPA entities
  referenced across features, Flyway migrations that are global by nature,
  Spring Security configuration that spans everything, and a test suite whose
  base class every integration test extends;
- report where the seams genuinely are, and where two "features" share so much
  domain that splitting them would be worse than leaving them;
- propose the cut — which package moves first, and how the epic stays green
  at every step — for the human to approve before any file moves.

## Reworked

## Later

- The frontend is packaged by role (`app/agent`, `app/manager`, `app/client`)
  with shared components by feature (`components/fleet`, `components/requests`)
  — closer to feature packaging already. Whether it needs anything is out of
  scope until the backend is settled.
