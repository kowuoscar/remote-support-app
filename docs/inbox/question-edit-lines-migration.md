---
id: question-edit-lines-migration
type: question
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the one-way database change that stores each invoice's lines?

To make lines editable, every invoice must store its own lines (amounts)
instead of being recalculated. The change:
- adds a table of invoice lines;
- copies every existing sent and approved invoice into lines, exactly as it
  shows today;
- deletes nothing, and keeps the old structures.

It's one-way only in this sense: once Agents start editing, those edited
amounts exist only in the new table, so going back would lose them.

Spec: `docs/features/edit-client-invoice-lines/spec.md`.

## Recommendation

**Approve.** Every existing invoice reads exactly as it does now, nothing
is deleted, and it's what makes editing, and a send-back that refreshes
nothing, possible.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

`manager-resets-a-password` is being built.

## Answer

Approved (ok), human, 2026-10-01.
