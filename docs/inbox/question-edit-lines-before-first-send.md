---
id: question-edit-lines-before-first-send
type: question
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Before the first send, what happens to lines when the Fleet or Fees change?

Example: on the 10th the Agent edits SIM A from $25 to $40. On the 15th a
new SIM D is added, and a new Fee is logged.

Spec: `docs/features/edit-client-invoice-lines/spec.md`.

## Recommendation

- SIM D and the new Fee **appear as new pre-filled lines**.
- Lines the Agent hasn't touched **keep updating** with the computation.
- SIM A **keeps the Agent's $40**. A **Reset** button puts it back to the
  computed amount.
- After the first send, **nothing recalculates** at all.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

`manager-resets-a-password` is being built.

## Answer

Ok (human, 2026-10-01): as recommended.
