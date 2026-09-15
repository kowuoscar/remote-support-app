# Issue tracker

**Tracker type:** local markdown, custom layout.

This file overrides the default output shape of the engineering skills.
`to-spec`, `to-tickets`, `triage` and `implement-spec` MUST follow the layout,
templates and rules below instead of their built-in defaults.

## Layout

```
docs/features/<feature-slug>/
  spec.md
  tickets/
    <ticket-slug>.md
```

- `<feature-slug>` and `<ticket-slug>` are kebab-case and descriptive
  (`password-reset.md`, not `03.md`).
- **Never prefix ticket filenames with numbers.** A number goes stale the moment
  a ticket is inserted, and renaming a file loses its git history.
- A ticket's `id` is always equal to its filename without the extension.
- Execution order lives in `spec.md` under `## Execution order`.
  Dependencies live in each ticket's `depends_on`. The two must agree.

## spec.md template

```markdown
---
feature: <feature-slug>
status: draft | approved | implemented
date: YYYY-MM-DD
---

# <Feature name>

## Problem

The problem being solved, and for whom. From the user's perspective.
No solution here.

## Goals / Non-goals

What the feature does. And explicitly what it does NOT do — this is the
guardrail against scope drift, so be specific rather than exhaustive.

## User stories

A long, numbered list covering every aspect of the feature:

1. As a <actor>, I want <capability>, so that <benefit>

## Solution

The chosen approach and why it beat the alternatives that were rejected.
Include implementation decisions: modules built or modified, their
interfaces, schema changes, API contracts, specific interactions.

No file paths, no code snippets — they go stale fast. Exception: a snippet
that encodes a decision more precisely than prose can (state machine,
reducer, schema, type shape). Keep only the decision-rich part.

## Design direction

UI features only — write `N/A — no user interface` otherwise.

The committed visual world, and the references it was pinned against. One
line per surface naming its mode: Persuade (the visitor decides and acts),
Operate (the visitor completes a task), Read (the visitor understands), or
Experience (the visitor is inside the work). The mode comes from the surface,
not the product.

See `~/.claude/sdlc/frontend.md`.

## Constraints

Technical, performance, security and compatibility constraints, with exact
values. This section is authoritative: tickets inherit it and must not
restate or redefine it.

## Testing decisions

The seams at which this feature is tested. Prefer existing seams to new
ones, and place them as high as possible — the fewer seams across the
codebase, the better. Name the prior art: similar tests already in the repo.

Tests assert external behaviour, never implementation details.

## Open questions

What is still undecided. Each entry names who decides it.

Leave the section present and write `None` when empty.

## Execution order

Tickets in dependency order, blockers first:

1. `<ticket-slug>` — one line on what it delivers
2. `<ticket-slug>`
```

## Ticket template

```markdown
---
id: <ticket-slug>
title: <one line, imperative, in domain vocabulary>
status: needs-triage | needs-info | ready-for-agent | ready-for-human | done | wontfix
depends_on: [<ticket-slug>, ...]
labels: [<area>, ...]
---

## Context

Why this ticket exists, in 1-3 sentences. Point at the section of `spec.md`
it implements rather than restating it.

## Acceptance criteria

- [ ] Observable behaviour, never implementation

## Tests

The cases to cover: happy path, boundaries, errors. Written BEFORE the code.
Name the seam from the spec's testing decisions.

## Regression

What already-shipped behaviour this ticket puts at risk, and which test
protects it. For a bugfix: the test reproducing the bug is written first
and must fail.

## Observability

Logs, metrics and traces to add.
```

## Conditional sections

`Regression`, `Observability` and `Open questions` are **always present**.
When one does not apply, write `N/A — <reason>` (e.g. `N/A — project
bootstrap, no user-facing behaviour`).

An absent section cannot be told apart from a forgotten one. One line proves
the question was asked.

## Status lifecycle

`status` is the only record of where a ticket stands, so it must be written
as the work moves, never reconstructed afterwards:

| Value | Set when |
|---|---|
| `needs-triage` | Ticket drafted, not yet assessed |
| `needs-info` | Blocked on an answer — name what is missing in `## Context` |
| `ready-for-agent` | Unambiguous enough to implement without further input |
| `ready-for-human` | Needs a person: judgement call, credentials, external action |
| `in-progress` | An implementer picked it up — set it **before** the first edit |
| `done` | Definition of Done met and the work is merged |
| `wontfix` | Dropped — say why in `## Context` |

Commits reference the ticket id: `<type>(<ticket-id>): <subject>`.

## Definition of Done

Global, and never restated inside a ticket:

- Every acceptance criterion is checked.
- Tests listed in the ticket exist and pass.
- The full test suite passes — not only the new tests.
- Lint and type checks pass.
- Observability described in the ticket is in place, or justified as `N/A`.
- `CONTEXT.md` is updated if the ticket introduced, renamed or narrowed a
  domain term. The glossary is a glossary: no implementation detail in it.
- An ADR is recorded **only** if a decision is hard to reverse, surprising
  without context, and the result of a genuine trade-off. All three, or no ADR.
- Changes are committed referencing the ticket id, and `status` is `done`.

A ticket labelled `frontend` additionally satisfies the frontend DoD in
`~/.claude/sdlc/frontend.md`: goldens green or co-committed with `DESIGN.md`,
craft floor green, no `web-design-guidelines` finding.

## Rules

1. **No tickets while `Open questions` is non-empty.** A ticket built on an
   unvalidated assumption gets implemented, then thrown away.
2. **The spec is authoritative on constraints.** A ticket that needs a
   constraint cites the spec; it never redefines one.
3. **Tickets are vertical slices.** Each cuts a narrow but complete path
   through every layer and is demoable on its own. The exception is a wide
   refactor, which follows expand–migrate–contract as `to-tickets` describes.
4. **One ticket fits one fresh context window.**
5. **`status` uses the triage vocabulary** so `triage` can act on tickets
   without translation, and the values map onto Linear/Jira states on migration.
6. **The domain model is maintained inline, not in batches.** When a term
   is challenged or resolved while writing a spec, a ticket or code, update
   `CONTEXT.md` at that moment. Create `CONTEXT.md` and `docs/adr/` lazily —
   only when there is something to write.
7. **Every visual change either conforms to `DESIGN.md`, or changes
   `DESIGN.md` in the same commit.** A golden updated on its own is drift;
   updated alongside the design document it is a decided evolution. Read
   `~/.claude/sdlc/frontend.md` before any UI work.
8. **Frontmatter is the migration contract.** Keep `id`, `title`, `status`,
   `depends_on` and `labels` machine-readable: no prose, no free text in
   those fields.
