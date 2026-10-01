---
id: question-edit-lines-base-per-sim
type: question
status: open
blocks: [edit-client-invoice-lines]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Is the base amount one editable number, or one editable line per postpaid SIM?

Example: a Contract has 3 postpaid SIMs at $25 each, so the base amount is
$75. Either the Agent edits one "$75" figure, or they see three lines
(SIM A $25, SIM B $25, SIM C $25) and edit only the one whose usage went
up.

Spec: `docs/features/edit-client-invoice-lines/spec.md`.

## Recommendation

**One line per SIM.** The draft already lists each SIM, carriers bill per
SIM, and the Manager can see exactly which SIM went up. The Tester and the
PDF still see one base total.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

`manager-resets-a-password` is being built.

## Answer
