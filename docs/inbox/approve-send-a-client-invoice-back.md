---
id: approve-send-a-client-invoice-back
type: approval
status: open
blocks: [send-a-client-invoice-back]
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the revised spec for `send-a-client-invoice-back`?
`docs/features/send-a-client-invoice-back/spec.md`.

In plain terms: a Manager sends a sent Client Invoice back to its Agent, with
a reason. The invoice becomes a draft again with exactly the numbers it was
sent with; nothing is recalculated. The Agent edits lines (using the
line-editing feature) and sends it again. A Fee of that month that was logged
after the first send appears as a new line, as you answered. Sending it back
or resending it never moves the Agent's pay on its own; only an edit does.

Kept as you approved:
- the reason is seen only by the Manager and the Agent;
- the Agent can open their own invoices, including last month's;
- a resent invoice waits in the Review Queue from the moment it's resent;
- the Manager uses an inline form.

It's built **after** `edit-client-invoice-lines` and depends on it.

## Recommendation

Approve. It has two tickets and a small additive database change (ADR 0005).

## Blocks

`send-a-client-invoice-back` stays a `draft`.

## Meanwhile

`real-client-dashboard` is being built.

## Answer
