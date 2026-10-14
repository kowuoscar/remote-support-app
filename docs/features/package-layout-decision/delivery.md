# Delivery report — package-layout-decision

<!-- sdlc:template delivery 1 -->

## What was built

Documentation only; no code moved. Three tickets:

- **Stories 1–7, 21** — `layout-research-note`: `research.md`, pinned to one
  commit, compares four layouts (flat by feature, feature with layer
  sub-packages, Spring Modulith, hexagonal) against this codebase, with cited
  sources and a sizing table. Its one-screen Recommendation was corrected in
  review to agree with the ADR (F3).
- **Stories 8–17, 20–22** — `package-layout-adr`: ADR 0006 (`proposed`),
  flat package by feature: where each of the 222 main and 71 test classes
  goes, the global pieces, how features refer to each other, the `@Lazy`
  invoicing cycle (broken later by its own feature), ArchUnit 1.4.2 as the
  boundary check (test scope, Apache-2.0), an eight-step move order and the
  move recipe.
- **Stories 18, 19, 21** — `epic-and-backend-13-follow-the-adr`: the epic's
  feature list now follows ADR 0006 decision 7; Backend 13 gains a paragraph
  that applies only once you accept ADR 0006 and `package-boundary-check`
  has merged.

The approval of the layout itself is `docs/inbox/approve-package-layout.md`.

## Acceptance walkthrough

1. [agent] Open `research.md` in the feature folder. Show that the pinned commit is named at the top, the Recommendation fits on one screen and uses no code, and it answers each of the six questions in one line. (stories: 1, 5) — played — evidence: `evidence/step-1.txt`
2. [agent] Show the four candidate sections (flat by-feature, by-feature with layers inside, Spring Modulith modules, hexagonal). Show that each has: what it is, where it is used (cited), fit against each constraint, cost here, and a verdict. Show the comparison table, and that each cell traces to a section. (stories: 2, 3) — played — evidence: `evidence/step-2.txt`
3. [agent] Pick six `path:line` citations at random. Open each at the pinned commit and show the line says what the note claims. (stories: 3) — played — evidence: `evidence/step-3.txt`
4. [agent] Show that every claim about practice or tooling carries `[n]`, and that every Sources entry has a title, author or publisher, URL, version where relevant, retrieved date and quoted passage. Open three source URLs and find the quoted passage, or report which page moved. (stories: 4) — played — evidence: `evidence/step-4.txt`
5. [agent] Recount main and test classes per cluster at the pinned commit. Show they match the note's sizing table and that the totals reconcile. Show the not-settled section exists and gives a reason for each item. (stories: 6, 7) — played — evidence: `evidence/step-5.txt`
6. [agent] Open the new ADR. Show that its number is the next free one in `docs/adr/`, it carries `Status: proposed`, and its Decision has the eight subsections in order. (stories: 9, 10, 11, 12, 13, 14, 15, 16) — played — evidence: `evidence/step-6.txt`
7. [agent] Show that the cluster table covers every main and test class at the pinned commit, with no class left out, and that each target package is named for a glossary term. (stories: 9, 10) — played — evidence: `evidence/step-7.txt`
8. [agent] Show one line each for `SecurityConfig`, `AuditLog`, the Flyway migrations, `IntegrationTest` and fixtures, `demo` and shared types. Show the cross-feature rule naming `Contract` and `Request`, and the ruling on the `@Lazy` cycle naming the feature that carries it out. (stories: 11, 12, 13) — played — evidence: `evidence/step-8.txt`
9. [agent] Show that the boundary tool section names the tool, version, scope and licence, cites compatibility with Spring Boot 3.3.4, lists its rules, and says how existing classes are let through. Show that any main-scope dependency is flagged for the approval item. (stories: 14, 22) — played — evidence: `evidence/step-9.txt`
10. [agent] Show that the move order gives each step a reason, and that the move recipe covers green-at-every-step and the scheduling rule for in-flight features. Show that `invoice-adjustment` and `real-manager-dashboard` are addressed by name. (stories: 15, 16, 17) — played — evidence: `evidence/step-10.txt`
11. [agent] Show that the ADR's Consequences list each rejected candidate with its reason. (stories: 8) — played — evidence: `evidence/step-11.txt`
12. [agent] Show that the epic's `## Features` matches the ADR's move order one for one, that `package-layout-decision` is ticked, that the "provisional" sentence points to the ADR, and that no other section of the epic changed. (stories: 18) — played — evidence: `evidence/step-12.txt`
13. [agent] Show that the amended Backend 13 covers moved and unmoved clusters and the boundary check's authority, cites the ADR by number, and keeps the 2026-09-22 attribution. (stories: 19) — played — evidence: `evidence/step-13.txt`
14. [agent] Show that `git diff` against the merge base touches only the files allowed by Constraints. Run `verify` and `sdlc-check-harness` and show both green. (stories: 21) — played — evidence: `evidence/step-14.txt`
15. [agent] Show that the delivery's approval item exists in `docs/inbox/`, can be answered "approve / amend / reject" without opening code, and states the cost and any new dependency with its scope and licence. (stories: 20, 22) — played — evidence: `evidence/step-15.txt`
16. [human] Read the Recommendation and the approval item. Answer it: approve, or amend with what should change. (stories: 5, 20) — yours
17. [human] Read the sizing and the rejected alternatives in the ADR. Confirm the cost is one you accept and that the reasons the others lost convince you. (stories: 6, 8) — yours
18. [human] Read the amended Backend 13 as a reviewer would. Confirm it tells you what to cite against a new class today, and what to cite once a cluster has moved. (stories: 19) — yours

## Decisions taken alone

See the spec's `## Decisions taken`. Made during implementation or review:

- The ADR records `shared` and `system` as a reasoned exception to "every
  package is named for a glossary term" (after review, F5).
- The frozen exception list may grow by one entry when a feature adds a class
  to a cluster not yet moved, rather than only shrinking (after review, F6;
  put to you in the approval item).
- Backend 13's new paragraph is conditional on your approval; no "Amended by
  the human" note until then (after review, F4/F8).

## Debt recorded

- `docs/adr/0006-backend-is-packaged-by-feature-flat.md` · smell: a list that
  grows · the frozen list grows for unmoved clusters, against the spec's "only
  shrinks" (F6) · package-layout-decision · 2026-10-09

## How to undo

```
git revert -m 1 bcb7f10
```

Documentation only: nothing to roll back in data.

## What happens next

`package-boundary-check` waits for your answer on the approval item. The loop
moves on to the next unblocked work: `month-closes-on-the-fifth`,
`real-manager-dashboard` and `super-admin-signs-in` wait on your inbox, so
next is drafting the frontend-cleanup and remaining epic features. A veto of
the layout reopens the ADR as an amendment; nothing has moved yet.
