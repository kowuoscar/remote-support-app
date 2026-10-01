---
id: question-edit-lines-who-sees
type: question
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Who can see that a line was edited?

Example: SIM A was computed at $25 and billed at $40.

Spec: `docs/features/edit-client-invoice-lines/spec.md`.

## Recommendation

**The Agent and the Manager** see "Edited · computed $25" under the $40.
**The Client's Tester and the PDF** see only $40. "Computed" is an internal
figure the Client was never promised.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

`manager-resets-a-password` is being built.

## Answer

Ok (human, 2026-10-01): as recommended.
