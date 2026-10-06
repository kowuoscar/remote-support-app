---
id: approve-package-layout-decision
type: approval
status: open
blocks: [package-layout-decision]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the research plan for reorganising the backend by feature?
`docs/features/package-layout-decision/spec.md`.

In plain terms: before a single file moves, an agent studies how Spring Boot
apps like this one are actually organised, and proposes a layout for this
app. The layouts compared are: one folder per feature, one per feature with
layers inside, Spring Modulith, and hexagonal. Every claim cites a real
source or a real line of this codebase.

It delivers four documents:
- a research note with a one-screen recommendation up front;
- a decision record (ADR), marked "proposed", that says:
  - where each part of the code goes;
  - how features refer to each other;
  - what to do about the two invoicing services that depend on each other;
  - which check stops the layout drifting back;
  - in which order things move, each step keeping everything working;
- the epic's move list, rewritten to match;
- the coding rule on packaging (Backend 13), updated.

**No code changes in this feature.**

What approving this accepts:
1. **You decide the layout afterwards, not now.** Once the documents exist,
   you get one more item, answered approve / amend / reject without reading
   code. Nothing moves until you approve it.
2. **The agents may add a free, test-only checking library** (ArchUnit, or
   Spring Modulith used only in tests). Anything that would ship in the app
   itself comes to you with its licence first.
3. **The `@Lazy` workaround stays for now.** Its fix is decided in the record
   but done later, with the invoicing move.

## Recommendation

Approve. Three documentation tickets. It's the "research first, informed not
invented" step you asked for.

## Blocks

`package-layout-decision`.

## Meanwhile

Nothing else in this epic starts until the layout is approved.

## Answer

