---
id: question-edit-lines-reason
type: question
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Must the Agent give a reason when they edit a line?

Example: the Agent changes SIM A from $25 to $40. Do they have to type why
("usage overage, see carrier bill")?

Spec: `docs/features/edit-client-invoice-lines/spec.md`.

## Recommendation

**No reason required.** The Manager sees each edit next to the computed
amount, has the carrier invoice file attached, and can send the invoice back
with a question. A required reason would slow routine monthly work.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

`manager-resets-a-password` is being built.

## Answer

Ok (human, 2026-10-01): no reason required.
