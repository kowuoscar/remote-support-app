---
id: month-close-paid-pay
type: question
status: open
blocks: [month-closes-on-the-fifth]
created: 2026-10-08
---

<!-- sdlc:template inbox-item 1 -->

## Question

Until the 5th, an Agent's edit to a Client Invoice line moves that month's
pay. What if that pay is already final?

- **(a)** You mark the Agent's September pay **paid** on 3 October. On 4
  October the Agent raises a September Client Invoice line from $30 to $45.
- **(b)** September's Client Invoice is Late. The Agent's September pay was
  approved before the 6th. On 10 October the Agent raises a line from $30 to
  $45.

In both cases, does the Agent's September pay go up by $15?

## Recommendation

No. The Client's bill changes to $45, but the Agent's September pay stays as
it is. If the $15 is owed, you record it as an adjustment on October's pay.
A paid pay invoice counts as closed straight away. *Reason:* money already
paid out can't move, and after the 5th every pay fix is yours anyway. The
alternative is to refuse "mark paid" until the 6th.

Spec: `docs/features/month-closes-on-the-fifth/spec.md`, Open questions 1.

## Blocks

`month-closes-on-the-fifth`, and `invoice-adjustment` after it.

## Meanwhile

The loop works on the other specs waiting for approval.

## Answer
