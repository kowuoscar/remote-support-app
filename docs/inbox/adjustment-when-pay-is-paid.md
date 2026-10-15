---
id: adjustment-when-pay-is-paid
type: question
status: open
blocks: [invoice-adjustment]
created: 2026-10-15
---

<!-- sdlc:template inbox-item 1 -->

## Question

A paid Agent Invoice never moves, even while its month is open (your answer
of 2026-10-14). Say you marked October's pay paid on 28 October. On 29
October you record a $20 adjustment for September. It lands on October's
Client Invoice, but October's pay can't move. The Client would be billed
$20 and the Agent never paid it. Can you record an adjustment while the
current month's pay is already paid?

## Recommendation

No. It is refused with "October's pay is already paid. Record this from
1 November, and it goes on November's invoice." The one exception is a
charge picked from the "paid to the Agent but not billed to the Client"
list. That one is still accepted, since it moves no pay. *Reason:* the bill
and the pay stay equal, and waiting a few days costs nothing.

Spec: `docs/features/invoice-adjustment/spec.md`, Open questions 2.

## Blocks

`invoice-adjustment`.

## Meanwhile

The loop works on the other specs.

## Answer
