---
id: month-close-tester-sees-changes
type: question
status: open
blocks: [month-closes-on-the-fifth]
created: 2026-10-08
---

<!-- sdlc:template inbox-item 1 -->

## Question

Today the Client's Tester sees an invoice as soon as it is sent, and its
figures never change after that. Under the 5th rule, the Agent can change a
$1,200 September invoice to $1,250 on 4 October, after the Client has
already seen $1,200. Should the Tester see the figures change?

## Recommendation

Yes. The Tester sees the figures as they stand, with a line "Can still
change until 5 October" while the month is open. *Reason:* hiding open
invoices would leave the Tester's Invoices page empty, because it only shows
the current month, which is always open. The note removes the surprise.

Spec: `docs/features/month-closes-on-the-fifth/spec.md`, Open questions 2.

## Blocks

`month-closes-on-the-fifth`.

## Meanwhile

The loop works on the other specs waiting for approval.

## Answer
