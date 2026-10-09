---
id: package-layout-adr
title: Propose the backend package layout as an ADR
status: in-progress
depends_on: [layout-research-note]
labels: [docs]
stories: [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 20, 21, 22]
---

## Context

Implements `spec.md` Solution part 2 and the stories listed. It cites `research.md`, so it needs the finished note. The ADR goes in `docs/adr/` at the next free number at merge time (spec Constraints; check for `invoice-adjustment`'s ADR first), follows the shape of ADRs 0001 to 0005, and is `Status: proposed`. The ADR plans no change to any endpoint, response, migration, configuration property or bean name.

## Acceptance criteria

- [ ] The ADR file has the next free number in `docs/adr/`, `Status: proposed`, `Date`, `Feature`, then `## Context`, `## Decision`, `## Consequences`, and links to `research.md`
- [ ] `## Decision` has the eight subsections in the spec's order: layout, where each cluster goes, global pieces, cross-feature references, the `@Lazy` cycle, boundary tool, move order, move recipe
- [ ] The cluster table accounts for every main and test class at the note's pinned commit, each row giving classes, target package and the glossary term the package is named for; a recount shows no class left out
- [ ] The global pieces subsection has one line each for `SecurityConfig`, `AuditLog`, the Flyway migrations, `IntegrationTest` and fixtures, `demo`, and shared types such as `BillingMonth`; the cross-feature rule names `Contract` and `Request` explicitly; the `@Lazy` ruling names the feature that carries it out and whether it is a prefactor or a feature of its own
- [ ] The boundary tool subsection gives name, version, scope, licence, the cited Spring Boot 3.3.4 compatibility, the rules it encodes, how existing classes are let through, and which feature adds it and fails `verify`; any main-scope dependency is flagged for the approval item
- [ ] Each move step has a reason; the recipe covers green at every step and the in-flight rule, and addresses `invoice-adjustment` and `real-manager-dashboard` by name; `## Consequences` lists each rejected candidate with the reason it lost, and the `docs/tech-debt.md` items a move absorbs
- [ ] The ADR states the recommendation, the cost in move features, and each new dependency with its scope and licence; the ticket's diff touches only the one new ADR file, and `verify` passes

## Tests

No code, so no test is written (spec Testing decisions; seam: reading against the code, as for ADRs 0001 to 0005). Checks: grep the ADR for `Status: proposed` and the eight subsection headings in order; recount classes against the cluster table; confirm the number is unused in `docs/adr/`; `git diff --name-only` lists only the new ADR file; run `verify`; grep the ADR for the recommendation, the move-feature cost and a scope-and-licence line for each dependency.
- Global pieces subsection: grep for `SecurityConfig`, `AuditLog`, `Flyway`, `IntegrationTest`, `demo` and `BillingMonth`. Grep the cross-feature rule for `Contract` and `Request`. Grep the `@Lazy` ruling for a named feature and for "prefactor" or "own feature".
- Boundary tool subsection: grep for its name, version, scope, licence, a cited compatibility passage, its rules, and how existing classes are let through. Confirm any main-scope dependency is flagged.
- Move order and Consequences: check each move step has a reason; find `invoice-adjustment` and `real-manager-dashboard` by name; confirm each rejected candidate and each absorbed `docs/tech-debt.md` item appears in `## Consequences`.

## Regression

Nothing shipped is at risk: no file outside `docs/` changes and `ARCHITECTURE.md` stays unchanged (spec Non-goals). `verify` and `sdlc-check-harness` stay green. No existing test is modified.

## Observability

N/A — documentation only, nothing runs.
