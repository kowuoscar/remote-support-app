---
id: question-edit-lines-agent-pay
type: question
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Does the Agent's own pay follow their edits to the Client Invoice?

Example: the computed postpaid line for a SIM is $25. The Agent edits it to
$40 because usage went up. The Client is billed $40. Today the Agent's own
monthly pay (their "Local Support Fees" line) is calculated from the
computed amounts, so it would still count $25.

Should the Agent's pay go up to $40 too?

Spec: `docs/features/edit-client-invoice-lines/spec.md`.

## Recommendation

**No, not in this feature.** The Agent's pay keeps using the computed
amounts, and the draft tells the Agent so. Otherwise an Agent could raise
their own pay by editing the Client's invoice, and their pay would depend on
when the Client Invoice gets sent. If you want pay to follow edits, it can
be a later feature with its own checks.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

`manager-resets-a-password` is being built.

## Answer

Not as recommended (human, 2026-10-01): "If an edit occur before approval it
should update the agent own monthly pay otherwise its a carry-over."

So the Agent's Local Support Fees follow the Client Invoice's **edited
(billed) amounts** while it is not yet approved. Once it is approved, a
later correction does not change that month's pay; it carries over to the
next month (through `invoice-adjustment`). This amends ADR 0002, under which
pay bypasses the Client Invoice.
