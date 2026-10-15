---
id: adjustment-pay-the-difference
type: question
status: open
blocks: [invoice-adjustment]
created: 2026-10-15
---

<!-- sdlc:template inbox-item 1 -->

## Question

Your month-close answer says that when pay is already final, anything owed
to the Agent "goes on next month's pay as an adjustment". But in this spec an
adjustment always bills the Client too. Example: the Agent raises a
September line from $30 to $45 on 4 October, after you marked September's
pay paid. The Client is billed $45. To pay the Agent the $15, an ordinary
adjustment would bill the Client $15 again. How should the Agent get the
$15 without the Client paying twice?

## Recommendation

On the closed invoice, offer "Pay the Agent the difference", with the exact
amount the app works out. It lands on the current month's pay only. The
Agent and you see it marked "Pay only, not billed to the Client". It never
appears in the Client's total, the Tester's view or the PDF. *Reason:* the
app knows the exact difference, so you don't have to work it out, and the
Client's bill stays right. This reopens your earlier "no adjustment to the
Agent's pay alone" ruling, for this one case only.

Spec: `docs/features/invoice-adjustment/spec.md`, Open questions 1.

## Blocks

`invoice-adjustment`: no tickets until this is answered.

## Meanwhile

The loop works on the other specs.

## Answer
