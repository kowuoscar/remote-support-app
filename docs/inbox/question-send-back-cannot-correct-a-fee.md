---
id: question-send-back-cannot-correct-a-fee
type: question
status: open
blocks: [send-a-client-invoice-back]
created: 2026-09-30
---

<!-- sdlc:template inbox-item 1 -->

## Question

A sent-back invoice is usually last month's. The code can't correct a Fee in
last month: Fees have no edit or delete, and a new Fee always lands in the
month it is logged (`FeeController.java:136`). So after a send-back the Agent
can only add files and pick up Fees logged late for that month. The epic's
proof ("the Agent corrects a Fee, resends") doesn't hold for the usual case.
Should send-back ship as specified, with a separate `correct-a-fee` feature
added to the epic?

Escalation case 3: the settled intention asks for something the code doesn't
allow.

## Recommendation

**Yes.** Ship send-back now, and add `correct-a-fee` to
`invoice-correction-and-history` before `send-an-agent-invoice-back`. That
feature would log a Fee into a sent-back invoice's month and void a wrong
Fee. Send-back still has value alone: it stops a wrong invoice being
approved, and it tells the Agent why. Fee correction is its own design
question: who may void, what an audit of a voided Fee looks like, and whether
it touches Agent Invoices too. It shouldn't be buried in this feature.

The alternative is to widen this feature to include Fee correction. That
roughly doubles its size and delays both.

## Blocks

`send-a-client-invoice-back`: spec stays `draft`; no tickets.

## Meanwhile

The loop works on anything not blocked. The rest of this epic follows this
feature, and `manager-resets-a-password` waits on your spec approval.

## Answer
