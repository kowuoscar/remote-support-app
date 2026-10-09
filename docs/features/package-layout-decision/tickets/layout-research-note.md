---
id: layout-research-note
title: Write the package layout research note, pinned to one commit
status: in-progress
depends_on: []
labels: [docs]
stories: [1, 2, 3, 4, 5, 6, 7, 21]
---

## Context

Implements `spec.md` Solution part 1 (the research note) and stories 1 to 7. The note is `docs/features/package-layout-decision/research.md`, the evidence the ADR cites. Only files under `docs/` change (spec Constraints). Maven, if run, uses `JAVA_HOME=/opt/homebrew/opt/openjdk@21`.

## Acceptance criteria

- [ ] The top of `research.md` names one commit of `main`, and every `path:line` in the note resolves to the stated claim at that commit
- [ ] The Recommendation is the first section, fits on one screen, contains no code, and answers each of the six Problem questions in one line, with the cost in move features and any new dependency
- [ ] The constraints section covers every item the spec lists (twelve clusters, `Contract` and `Request` reference counts, the `@Lazy` cycle with `AgentInvoiceService`, global pieces, whole-tree scanning, cross-cluster JPA associations, Backend 6, `sdlc-check-harness`, Checkstyle, Spring Boot 3.3.4 on JDK 21), each with `path:line` evidence
- [ ] There is one section each for package-by-feature flat, by-feature with layer sub-packages, Spring Modulith modules and hexagonal, each giving what it is, where it is used (cited), fit per constraint, cost beyond moving files, and a verdict; a comparison table follows with one word per cell (fits / costs / blocks)
- [ ] A "What is not settled" section lists each gap with its reason, and the sizing table gives main and test classes per cluster as measured at the pinned commit with totals that equal a recount of the tree at that commit
- [ ] Every claim about practice or tooling ends in `[n]`; each Sources entry has title, author or publisher, URL, covered version for tool docs, retrieved date and a quoted passage; primary sources come first; blogs are labelled as evidence of existence only
- [ ] Any tool version named is stated compatible with Spring Boot 3.3.4 and JDK 21 with the cited passage; the ticket's diff touches only `docs/features/package-layout-decision/research.md`, and `verify` passes

## Tests

No code, so no test is written (spec Testing decisions; seam: sampled citations and recount, no new seam). Checks to run on the finished note:
- Grep for the seven part headings and the four candidate headings.
- Open a sample of six `path:line` citations at the pinned commit.
- Open three source URLs and find the quoted passage.
- Recount main and test classes per cluster and compare to the sizing table.
- Find each tool version named in the note and its cited Spring Boot 3.3.4 / JDK 21 compatibility passage.
- Error case: a claim with neither `[n]` nor `path:line` is removed or sourced.
- `git diff --name-only` shows only `docs/features/package-layout-decision/research.md`; run `verify`.

## Regression

Nothing in shipped behaviour is at risk: no file outside `docs/` changes. `verify` (`docs/agents/sdlc.json`) and `sdlc-check-harness` stay green. No existing test is modified.

## Observability

N/A — documentation only, nothing runs.
