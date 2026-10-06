---
feature: package-layout-decision
epic: package-by-feature
status: draft
date: 2026-10-06
---

<!-- sdlc:template spec 1 -->

# Decide the backend's package layout

## Problem

The human has made "package by feature, not by layer" the norm (coding
standards, Backend 13, 2026-09-22). The backend breaks that norm. It is
packaged by layer: `web`, `dto`, `domain`, `repository`, `security`,
`logging` and `demo`. A single feature's files are spread over five packages,
and `web` holds 78 classes (controllers, services, factories and exceptions)
and keeps growing. Today Backend 13 can only say "not yet". A reviewer may
cite it only against a new package that picks the layered shape.

Moving 222 main classes and 71 test classes is the expensive part of the
epic, and the human said so: *"the cost of refactoring now will be big"*.
The human also asked that the target be *informed rather than invented*:
*"It would need to search the best practices when it comes to this layering
too."* Nobody has yet written down:

- which layout this codebase should take;
- where the pieces that belong to no single feature go;
- how one feature refers to another's entity (`Contract` has about 35
  references from `web` and `Request` about 24);
- what happens to the `ContractAmountService` ↔ `ClientInvoiceService`
  cycle, today bridged by `@Lazy`, with `AgentInvoiceService` sitting on top
  of both;
- what stops the layout drifting back (no ArchUnit, no Spring Modulith;
  Checkstyle covers hygiene only);
- in what order the clusters move, and how each move stays green.

The epic's provisional feature list after this one guesses at those answers.
Until they are written and approved, any move ticket would invent its own.

This feature moves no file and changes no behaviour. It produces documents:
a research note, a proposed ADR, the rewritten feature list for the epic and
an amended Backend 13. The human approves the layout once the research is in.

## Journeys

None move. This is the first of the `package-by-feature` epic's features. It
advances no journey in `docs/journeys.md`, and every journey must keep
playing exactly as it does today. The epic's own acceptance test starts
here: no code changes, so nothing can regress.

## Goals / Non-goals

Goals:

- A **research note** that compares the layouts Spring Boot codebases of this
  shape actually use and judges each one against *this* codebase, with
  evidence.
- A **proposed ADR** that states the chosen layout and settles the six
  questions in the Problem, precisely enough that each move feature's spec
  can cite it instead of re-deciding.
- The epic's **`## Features` list rewritten** from the ADR's move order.
- **Backend 13 amended** to say what new code does while the epic is in
  progress and after it.
- An **approval item** that lets the human approve or amend the layout
  without reading code. The delivery files it.

Non-goals. Each is something a reasonable agent would otherwise do:

- **No file moves, renames or package declarations change**, not even a
  "pilot" move of one cluster. The first move belongs to the move feature the
  ADR schedules first.
- **No boundary check is added.** No ArchUnit or Spring Modulith dependency
  goes into `pom.xml`, and no test is added. The ADR picks the tool, and
  `package-boundary-check` (or whatever the rewritten list calls it) adds it.
- **The `@Lazy` cycle is not broken here.** The ADR rules on it, and a move
  feature does it.
- **No paying of backend tech debt** on the way, such as `BillingMonth.current()`
  or the `@Transactional` on `AgentController`. The ADR may say which debt a
  move absorbs. Paying it is the move's job.
- **No change to `ARCHITECTURE.md`.** It describes the code, and the code does
  not change. The move features update it, cluster by cluster.
- **No frontend layout work.** The epic puts it under `## Later`. The research
  may mention the frontend only to say it is out of scope.
- **No change to any HTTP path, response, migration, configuration property
  or bean name**, now or in the ADR's plan. The epic carries no
  user-visible behaviour.
- **No general essay.** The note does not survey layouts in the abstract, and
  it does not compare non-Spring stacks. Every section ends in a verdict
  about this codebase.
- **No `CONTEXT.md` change.** Package names use existing glossary words
  (Client Invoice, Agent Invoice, Fleet, Carrier, Contract, Request, Fee,
  standing amount, Review Queue). The ADR maps each package to a glossary
  term rather than coining a new one.
- **No change to the in-flight specs** (`invoice-adjustment`,
  `real-manager-dashboard`). They keep saying "new backend classes go in the
  existing packages", which stays true until their cluster moves.

## User stories

The "owner" is the human who asked for the epic. "Implementer" and
"spec-writer" are the agents who will cut and build the move features.

1. As the owner, I want a written comparison of the layouts Spring Boot codebases of this shape actually use, so that the choice is informed rather than invented.
2. As the owner, I want at least these four candidates compared: package-by-feature flat; package-by-feature with layer sub-packages inside each feature; Spring Modulith application modules with verified boundaries; and hexagonal (ports and adapters). Each should be described with where it is used in practice, so that I know the field was surveyed rather than one option argued for.
3. As the owner, I want each candidate judged against this codebase's real constraints, with `path:line` evidence from the code, so that I can tell the verdict is about this code and not a blog post.
4. As the owner, I want every claim about practice or tooling cited to a source I can open, with the passage it relies on, so that I don't have to take the agent's word for it.
5. As the owner, I want the recommendation first, in plain words on one screen, so that I can approve without reading the whole note.
6. As the owner, I want the cost sized honestly: classes and tests moved per cluster, the number of move features, and what each candidate would add beyond moving files, so that I can weigh the cost I already called big.
7. As the owner, I want the note to say what it could not settle and why, so that a gap is visible rather than papered over.
8. As the owner, I want the rejected candidates kept in the ADR with the reason each lost, so that nobody re-argues them halfway through the epic.
9. As a spec-writer, I want the ADR to state the chosen layout as a package tree, with the role of each sub-package if any, so that every move lands in the same shape.
10. As a spec-writer, I want every existing main and test class accounted for by cluster in the ADR, each cluster mapped to its target package, so that no class is left with nowhere to go.
11. As an implementer, I want the ADR to name the home of each global piece (`SecurityConfig` and its matcher chain, `AuditLog`, the Flyway migrations, the `IntegrationTest` base class and its fixtures, the `demo` loader, and shared types such as `BillingMonth` and common exceptions), so that no move stalls on "where does this go".
12. As an implementer, I want one rule for how a feature refers to another feature's entity, `Contract` and `Request` above all, so that each move does not invent its own.
13. As an implementer, I want the ADR to rule on the `ContractAmountService` ↔ `ClientInvoiceService` `@Lazy` cycle (invoicing stays one package, or the cycle is broken first), and to say in which feature, so that the invoicing move is not blocked on a design question.
14. As an implementer, I want the ADR to name the boundary tool, its dependency scope and licence, its version compatible with Spring Boot 3.3.4, the rules it encodes, and how existing classes are let through while the moves are in progress, so that `package-boundary-check` can be specified without new research.
15. As the owner, I want the move order with a reason for each position, so that the least risky cluster proves the recipe before the coupled ones.
16. As the owner, I want the ADR to say how every step stays green: one cluster per feature; classes, tests and `ARCHITECTURE.md` moved in the same merge; no behaviour change; `verify` green, so that the epic can stop after any feature and leave the product working.
17. As the owner, I want the move order to respect in-flight work (the draft `invoice-adjustment` and `real-manager-dashboard` specs, and any feature mid-build), so that a move does not collide with a feature being built in the same cluster.
18. As the owner, I want the epic's `## Features` list rewritten to match the ADR, so that the next spec drafted is the right one.
19. As a reviewer, I want Backend 13 amended so that it says where new code goes in a cluster that has moved and in one that has not, and what a reviewer may cite, so that the rule is true of the codebase at every point in the epic.
20. As the owner, I want to approve, amend or reject the chosen layout through one approval item that I can answer without reading code, so that the epic goes no further until I agree.
21. As the owner, I want `verify` to stay green and no file outside `docs/` to change, so that a documentation feature carries no release risk.
22. As the owner, I want any new dependency the ADR proposes stated plainly, with its scope and licence, in the approval item, so that I am never surprised by one in a later diff.

## Solution

Four documents. No code.

### 1. The research note

It lives with the feature, as `research.md` beside this spec, and stays there
as the ADR's evidence. It is pinned to one commit of `main`, named at the top
of the note. Every `path:line` in it refers to that commit, so the citations
do not rot as later features shift lines.

Shape:

1. **Recommendation**: one screen, plain words, no code. The chosen layout,
   the cost in move features, any new dependency, and the one-line answer to
   each of the six questions in the Problem.
2. **This codebase's constraints**: the facts every candidate is judged
   against, each with `path:line` evidence. At least:
   - the size and the twelve clusters from the epic's `## Reworked`;
   - the cross-cluster entity references (`Contract`, `Request`), counted;
   - the `@Lazy` cycle and `AgentInvoiceService` above it;
   - the global pieces;
   - whole-tree component scanning from `@SpringBootApplication`, with no
     `@ComponentScan` and no `@EntityScan`;
   - JPA associations crossing clusters (`@ManyToOne` and the like) and the
     public-visibility needs of Spring Data and Lombok;
   - Backend 6: an integration test sits beside its package's unit tests
     and extends `IntegrationTest`;
   - `ARCHITECTURE.md`'s paths, checked mechanically by
     `sdlc-check-harness`;
   - Checkstyle;
   - Spring Boot 3.3.4 on JDK 21.
3. **One section per candidate**, at least the four in story 2. Each section
   gives:
   - what the candidate is;
   - where it is used in practice (cited);
   - its fit against each constraint;
   - what it would cost here beyond moving files;
   - a verdict.

   A fifth candidate is allowed if the research finds one in real use.
4. **A comparison table**: candidates against constraints, one word per cell
   (fits / costs / blocks), each cell traceable to the section above.
5. **Sizing**: classes and tests per cluster at the pinned commit. The totals
   reconcile with the actual count at that commit. The epic's 222/71 is the
   starting estimate, not a target.
6. **What is not settled**, with the reason (story 7).
7. **Sources**: numbered.

**How sources are cited.** Each claim about practice or tooling ends in a
bracketed number `[n]`. Each source entry gives its title, its author or
publisher, its URL, the version it covers (for tool documentation), the date
it was retrieved, and a short quoted passage that carries the claim. That
lets a reader check the claim even if the page moves.

- Primary sources come first: the Spring Boot reference, the Spring Modulith
  reference, the ArchUnit user guide, and Cockburn's hexagonal architecture
  article.
- Open-source codebases are cited by repository URL and commit, as evidence
  of what practice looks like (Spring PetClinic and the Spring Modulith
  examples, for instance).
- Blog posts and talks are allowed only as evidence that a practice exists,
  and are labelled as such.
- A claim with neither a source nor a `path:line` does not go in the note.

### 2. The proposed ADR

The ADR takes the next free number in `docs/adr/` at merge time, not a number
reserved now. `invoice-adjustment` also plans an ADR. The ADR follows the
existing ADRs' shape (title, `Status`, `Date`, `Feature`, `## Context`,
`## Decision`, `## Consequences`). It is written with `Status: proposed`, and
becomes `accepted` when the human approves.

Its `## Decision` has one subsection per question, in this order:

1. **Layout.** The package tree under `com.remotesupport.backend`, and the
   role of any sub-package inside a feature package.
2. **Where each cluster goes.** A table with one row per cluster: its classes
   (main and test, by name pattern or list), its target package, and the
   glossary term the package is named for.
3. **Global pieces.** One line each for `SecurityConfig`, `AuditLog`, the
   Flyway migrations, `IntegrationTest` and its fixtures, `demo`, and shared
   types.
4. **Cross-feature references.** The rule for one feature's entity, service
   or repository being used by another (for example: entity references
   allowed, repositories private to their feature, cross-feature calls
   through a named service only). Spell out `Contract` and `Request`.
5. **The `@Lazy` cycle.** Keep invoicing as one package, or break the cycle
   first. Say which feature does it, and whether it is a prefactor inside
   the invoicing move or a feature of its own.
6. **Boundary tool.** Its name, version, scope and licence. The rules it
   encodes. How existing classes are let through: a list that only shrinks,
   or Spring Modulith's equivalent. Which feature adds it, and that it fails
   `verify`.
7. **Move order.** One feature per step, each with its reason.
8. **The move recipe.** How every step stays green:
   - one cluster per feature;
   - classes, tests and `ARCHITECTURE.md` entries move in the same merge;
   - nothing in the HTTP surface, schema or bean names changes;
   - the full `verify` is green, and nothing is skipped;
   - a move is scheduled only when no feature in the same cluster is
     mid-build;
   - what to do about a draft spec whose cluster moves before it is built.

`## Consequences` names what gets harder. It gives the rejected candidates,
each with the reason it lost (story 8), and any debt from `docs/tech-debt.md`
that a move will absorb.

### 3. The epic's feature list

The epic's `## Features` is rewritten from the ADR's move order. There is one
line per feature, in the epic's existing format, and the line for this
feature is ticked. The sentence calling the later lines provisional is
replaced by a pointer to the ADR. Every other section of the epic stays as
it is.

### 4. Backend 13

The rule is amended to describe the epic as it stands:

- a cluster that has moved takes new code in its feature package only;
- a cluster that has not moved yet takes new code in the existing layered
  package, as today;
- after the move, the boundary check is the authority, and a reviewer cites
  the rule against a class it lets through only when the check's exception
  list is wrong;
- the rule points to the ADR by number.

The 2026-09-22 attribution stays.

### Approval flow

This spec's approval covers the **research plan**: what is compared, against
what, how it is cited, and what the ADR must answer. It does not cover the
layout, which nobody knows yet. The layout is approved after the research,
through one approval item filed by the delivery (see Decisions taken).

## Design direction

N/A — no user interface. The documents follow the house style of the existing
ADRs and `docs/agents/coding-standards.md`: short declarative sentences and
glossary words, with no marketing tone.

## Constraints

- **Only files under `docs/` change.** The allowed set is:
  - this feature's folder;
  - one new ADR file;
  - the epic file;
  - `docs/agents/coding-standards.md`.

  No change goes in `backend/`, `frontend/`, `scripts/`, `pom.xml`,
  `ARCHITECTURE.md` or `CONTEXT.md`.
- The ADR's number is the next free one when it merges. If `invoice-adjustment`'s
  ADR lands first, this one takes the number after it. Never renumber a merged
  ADR.
- Every `path:line` in the research note resolves at the pinned commit.
- The note's class counts are measured at the pinned commit, not copied from
  the epic.
- Any tool version the ADR names is compatible with Spring Boot 3.3.4 and
  JDK 21, and the note cites the compatibility statement.
- `verify` (`docs/agents/sdlc.json`) is run unchanged and passes. Maven runs
  with `JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- The ADR plans no change to any endpoint, response, migration, configuration
  property or bean name.

## Testing decisions

There is no code to test, so no test is written. Proof comes in three parts,
each checkable by an agent:

- **Nothing broke.** `git diff` against the merge base touches only the
  files allowed in Constraints. The full `verify` passes. `sdlc-check-harness`
  passes, which shows `ARCHITECTURE.md`'s paths still resolve.
- **The documents have their required parts.** Check each heading and item
  listed in the Solution by reading or grepping:
  - the note: the seven parts, the four candidate sections, a Sources list;
  - the ADR: `Status: proposed` and the eight decision subsections;
  - the epic: the rewritten list;
  - Backend 13: the amended text.
- **The evidence is real.** A sample of `path:line` citations is opened at
  the pinned commit and matches the claim. A sample of sources is opened and
  the quoted passage is found. Class counts are recounted at the pinned
  commit.

No new seam is added. The precedent is the documentation half of earlier
deliveries: ADRs 0001–0005 were checked by reading against the code they cite.

## Decisions taken

- **The layout is approved after the research, through an approval item, not
  in this spec.** The order is:
  1. This spec is approved. That approves the research plan.
  2. The tickets deliver the note, the proposed ADR, the rewritten epic list
     and the amended Backend 13, with the ADR at `Status: proposed`.
  3. The delivery files one `approval` item in `docs/inbox/`. It covers the
     recommendation, the cost, any new dependency with its scope and licence,
     and the move order, and it can be answered "approve / amend: … /
     reject".
  4. When the human approves, the ADR becomes `accepted`. When the human
     amends or rejects, the answer is a correction, and a fix pass rewrites
     the four documents before anything else happens.
  5. No spec for the next feature is drafted until the item is answered.

  Reason: the human asked for a researched layout, so it cannot be put to
  them before the research exists. Documents on `main` are cheap to revise,
  so landing them as `proposed` costs nothing if the answer is "amend".
- **A test-scope boundary library is the agents' call. A main-scope dependency
  is not.** ArchUnit, or `spring-modulith-starter-test` used for verification
  only, is free (Apache-2.0), replaceable and test-scope, so the research may
  choose it without asking. Anything that would sit in main scope needs the
  approval item, which states it plainly (story 22). That includes Spring
  Modulith's `@ApplicationModule` annotations in production code, its event
  publication registry, and jMolecules annotations. Reason: per
  `escalation.md`, a free, replaceable library is a *how*, but a runtime
  dependency woven through 222 classes is costly to undo.
- **The note lives in the feature folder, not under a new `docs/research/`.**
  It is this feature's evidence, and the ADR links to it. A new top-level
  folder for one file would be a new convention with no second user.
- **Citations: numbered sources, each with a quoted passage and a retrieved
  date, and `path:line` pinned to one commit.** The passage survives a page
  moving. The pinned commit survives later features shifting lines. Both are
  needed for the owner to check claims without trusting the agent.
- **Four candidates at minimum, with a fifth allowed.** These four are what
  the epic and the human named. A real-world fifth found during research is
  worth including, but a padded list is not.
- **The ADR rules on the `@Lazy` cycle but does not break it.** Breaking it
  is a code change with its own tests. This feature moves nothing.
- **The ADR plans the move order around in-flight specs instead of editing
  them.** The draft `invoice-adjustment` and `real-manager-dashboard` specs
  stay valid under the amended Backend 13: their cluster has not moved, so
  new classes go in the existing packages. When a move overtakes a draft,
  the move's spec says so, not this one.
- **`ARCHITECTURE.md` does not change.** It points at code that has not
  moved. Linking it to a proposed ADR would describe a future that might be
  amended.
- **Backend 13 is amended in this feature, not when the first move lands.**
  The amended text is true from the day it merges, because no cluster has
  moved yet. Waiting would leave the rule's "not yet" wording unexplained
  for one more feature.
- **The epic list is rewritten in place by a ticket.** The epic says the
  delivery of this feature rewrites the list before the next spec is
  drafted. Doing it in a ticket puts the change under review with the ADR it
  follows.
- **Three tickets rather than one.** The note is the long piece and must be
  finished before the ADR can cite it. The epic and rule edits are short and
  depend on the ADR's final wording. Each fits one context window.
- **Testing: no new test, proof by `verify`, the harness check, and sampled
  citations.** A documentation feature has no behaviour to assert. A grep
  for headings proves shape, and a sample of opened citations proves
  substance.

## Open questions

None.

The one decision the human must make, the layout itself, cannot be put to
them until the research exists. It goes to them through the approval item
described in Decisions taken. Whether a new dependency is acceptable is
decided there too, for main scope; test scope is taken alone.

## Acceptance walkthrough

1. [agent] Open `research.md` in the feature folder. Show that the pinned commit is named at the top, the Recommendation fits on one screen and uses no code, and it answers each of the six questions in one line. (stories: 1, 5)
2. [agent] Show the four candidate sections (flat by-feature, by-feature with layers inside, Spring Modulith modules, hexagonal). Show that each has: what it is, where it is used (cited), fit against each constraint, cost here, and a verdict. Show the comparison table, and that each cell traces to a section. (stories: 2, 3)
3. [agent] Pick six `path:line` citations at random. Open each at the pinned commit and show the line says what the note claims. (stories: 3)
4. [agent] Show that every claim about practice or tooling carries `[n]`, and that every Sources entry has a title, author or publisher, URL, version where relevant, retrieved date and quoted passage. Open three source URLs and find the quoted passage, or report which page moved. (stories: 4)
5. [agent] Recount main and test classes per cluster at the pinned commit. Show they match the note's sizing table and that the totals reconcile. Show the not-settled section exists and gives a reason for each item. (stories: 6, 7)
6. [agent] Open the new ADR. Show that its number is the next free one in `docs/adr/`, it carries `Status: proposed`, and its Decision has the eight subsections in order. (stories: 9, 10, 11, 12, 13, 14, 15, 16)
7. [agent] Show that the cluster table covers every main and test class at the pinned commit, with no class left out, and that each target package is named for a glossary term. (stories: 9, 10)
8. [agent] Show one line each for `SecurityConfig`, `AuditLog`, the Flyway migrations, `IntegrationTest` and fixtures, `demo` and shared types. Show the cross-feature rule naming `Contract` and `Request`, and the ruling on the `@Lazy` cycle naming the feature that carries it out. (stories: 11, 12, 13)
9. [agent] Show that the boundary tool section names the tool, version, scope and licence, cites compatibility with Spring Boot 3.3.4, lists its rules, and says how existing classes are let through. Show that any main-scope dependency is flagged for the approval item. (stories: 14, 22)
10. [agent] Show that the move order gives each step a reason, and that the move recipe covers green-at-every-step and the scheduling rule for in-flight features. Show that `invoice-adjustment` and `real-manager-dashboard` are addressed by name. (stories: 15, 16, 17)
11. [agent] Show that the ADR's Consequences list each rejected candidate with its reason. (stories: 8)
12. [agent] Show that the epic's `## Features` matches the ADR's move order one for one, that `package-layout-decision` is ticked, that the "provisional" sentence points to the ADR, and that no other section of the epic changed. (stories: 18)
13. [agent] Show that the amended Backend 13 covers moved and unmoved clusters and the boundary check's authority, cites the ADR by number, and keeps the 2026-09-22 attribution. (stories: 19)
14. [agent] Show that `git diff` against the merge base touches only the files allowed by Constraints. Run `verify` and `sdlc-check-harness` and show both green. (stories: 21)
15. [agent] Show that the delivery's approval item exists in `docs/inbox/`, can be answered "approve / amend / reject" without opening code, and states the cost and any new dependency with its scope and licence. (stories: 20, 22)
16. [human] Read the Recommendation and the approval item. Answer it: approve, or amend with what should change. (stories: 5, 20)
17. [human] Read the sizing and the rejected alternatives in the ADR. Confirm the cost is one you accept and that the reasons the others lost convince you. (stories: 6, 8)
18. [human] Read the amended Backend 13 as a reviewer would. Confirm it tells you what to cite against a new class today, and what to cite once a cluster has moved. (stories: 19)

## Execution order

Three slices, all documentation.

1. `layout-research-note` — `research.md` pinned to one commit: the constraints with evidence, the candidate sections, the comparison table, the sizing, the not-settled items and the Sources. Labels: `docs`. Depends on nothing. (stories: 1, 2, 3, 4, 5, 6, 7)
2. `package-layout-adr` — the proposed ADR at the next free number, with the eight decision subsections and the rejected alternatives, citing the note. Labels: `docs`. Depends on `layout-research-note`. (stories: 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 22)
3. `epic-and-backend-13-follow-the-adr` — the epic's `## Features` rewritten from the ADR's move order, and Backend 13 amended. Labels: `docs`. Depends on `package-layout-adr`. (stories: 18, 19)

Stories 20 and 21 are carried by the delivery (the approval item) and by
every ticket's Definition of Done (`verify` green), not by one ticket.
