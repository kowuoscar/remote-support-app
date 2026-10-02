---
id: question-pay-late-fee-after-client-invoice-sent
type: question
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Does a Fee logged after the Client Invoice was sent still count in the Agent's pay for that month?

Example: the Client Invoice is sent on the 25th. On the 27th a $10 Topup
Fee for that month is logged, so the sent Client Invoice doesn't include
it.

Follows from your answer that "an edit before approval updates the Agent's
pay, otherwise it's a carry-over". Spec:
`docs/features/edit-client-invoice-lines/spec.md`.

## Recommendation

**Yes, count it** at its logged amount. The Agent paid it out of pocket,
and this is how pay works today. If you answer no, the Agent's pay only
covers what the Client Invoices bill, and the $10 waits for a correction.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

`manager-resets-a-password` is being built; `real-client-dashboard` is being
ticketed.

## Answer

Yes (human, 2026-10-02): a Fee logged after the Client Invoice was sent counts in that month's pay at its logged amount.
