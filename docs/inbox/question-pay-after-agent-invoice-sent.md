---
id: question-pay-after-agent-invoice-sent
type: question
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

If the Agent has already sent their own monthly invoice, does a later Client Invoice edit still change it?

Example: the Agent sends their own monthly invoice on the 28th, with Local
Support Fees of $70. On the 30th they edit a SIM line on a Client Invoice
that isn't approved yet, from $25 to $31.40.

Follows from your answer that "an edit before approval updates the Agent's
pay, otherwise it's a carry-over". Spec:
`docs/features/edit-client-invoice-lines/spec.md`.

## Recommendation

**Yes.** The Agent's sent invoice goes up by $6.40, to $76.40, as long as
the Manager hasn't approved it yet. Once the Agent's invoice is approved,
the difference becomes a carry-over to the next month. This follows your
rule (an edit before approval updates the Agent's pay) without making Agents
wait to send their own invoice.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

`manager-resets-a-password` is being built; `real-client-dashboard` is being
ticketed.

## Answer

Yes (human, 2026-10-02): a sent but not yet approved Agent Invoice moves by the edit's difference; once approved, the difference carries over.
