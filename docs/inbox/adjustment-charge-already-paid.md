---
id: adjustment-charge-already-paid
type: question
status: open
blocks: [invoice-adjustment]
created: 2026-10-08
---

<!-- sdlc:template inbox-item 1 -->

## Question

You chose one kind of adjustment: it sits on the Client's bill and moves the
Agent's pay by the same amount. That works for almost every correction,
because the Agent's pay already follows what the Client is billed:

| Correction | Agent's pay | Right? |
|---|---|---|
| SIM billed $50 instead of $30 → −$20 credit | −$20 (they'd been paid on the $50) | ✅ |
| Topup never logged in the app → +$15 charge | +$15 (they'd never been paid for it) | ✅ |
| Topup logged **after** September's Client Invoice was sent → +$15 charge | +$15, **but the app already paid them $15 for it in September** | ❌ paid twice |

Only that last case breaks. Today the app deliberately pays the Agent for a
Fee or a new Postpaid SIM logged after the Client Invoice went out, even
though the Client wasn't billed for it. How should that charge avoid paying
the Agent a second time?

## Recommendation

When recording a charge, the Manager can pick that item from a list of
"paid to the Agent but not billed to the Client" items from the closed month.
A charge made this way bills the Client, leaves the Agent's pay alone, and is
marked "Already in the Agent's pay". Every other adjustment moves pay by the
same amount. Without the list, the Manager would have to remember which
items were already paid.

Spec: `docs/features/invoice-adjustment/spec.md`, Open questions.

## Blocks

`invoice-adjustment`: no tickets until this is answered.

## Meanwhile

The loop works on `month-closes-on-the-fifth`, which comes first anyway.

## Answer
