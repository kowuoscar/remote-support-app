---
id: epic-and-backend-13-follow-the-adr
title: Rewrite the epic's feature list and amend Backend 13 from the ADR
status: in-progress
depends_on: [package-layout-adr]
labels: [docs]
stories: [18, 19, 21]
---

## Context

Implements `spec.md` Solution parts 3 and 4 and stories 18 and 19. The two edits are short and quote the ADR's final move order and number, so they follow it. Files: `docs/roadmap/package-by-feature.md` and `docs/agents/coding-standards.md` only.

## Acceptance criteria

- [ ] The epic's `## Features` has one line per move step of the ADR, in the ADR's order and the epic's existing line format, and `package-layout-decision` is ticked
- [ ] The sentence calling the later lines provisional is replaced by a pointer to the ADR, and no other epic section differs from before
- [ ] Backend 13 says a moved cluster takes new code in its feature package only, and an unmoved cluster takes it in the existing layered package
- [ ] Backend 13 says that after the move the boundary check is the authority, and a reviewer cites the rule against a class the check lets through only when the exception list is wrong
- [ ] Backend 13 cites the ADR by its number and keeps the 2026-09-22 attribution
- [ ] This ticket's diff touches only `docs/roadmap/package-by-feature.md` and `docs/agents/coding-standards.md`, and `verify` passes

## Tests

No code, so no test is written (spec Testing decisions; seam: reading and grep). Checks: compare epic feature names to the ADR's move order one for one; `git diff` of the epic shows changes only in `## Features`; grep Backend 13 for the ADR number and `2026-09-22`; grep the amended Backend 13 for the moved-cluster wording (feature package only) and the unmoved-cluster wording (existing layered package); grep it for the boundary check's authority and the exception-list wording; `git diff --name-only` lists only the two files, and `verify` is run.

## Regression

The in-flight specs `invoice-adjustment` and `real-manager-dashboard` still say new classes go in existing packages; the amended rule keeps that true while their cluster is unmoved. `verify` and `sdlc-check-harness` stay green. No existing test is modified.

## Observability

N/A — documentation only, nothing runs.
