---
id: accept-edit-client-invoice-lines
type: acceptance
status: answered
blocks: []
created: 2026-10-03
---

<!-- sdlc:template inbox-item 1 -->

## Question

Does editing a Client Invoice's lines do what you wanted?
`docs/features/edit-client-invoice-lines/delivery.md`.

While a Client Invoice is a draft, the Agent can set any line to what was
really billed. The edited line shows "Edited · computed $X". The Agent's pay
follows the edit until their own invoice is approved, then the difference
carries over. Agents played 21 of 23 steps, with evidence. Two are yours:

22. Take a real carrier bill where a Postpaid SIM went over its plan. As the
    Agent, set that SIM's line to the billed figure and check that the Agent
    Invoice's Local Support Fees went up by the same amount. Send both, then
    review them as the Manager. Is "Edited · computed" enough to approve
    without a written reason? Is the Agent Invoice what you expect to
    reimburse? Does the Tester's statement look right to send to a Client?
23. Read `docs/adr/0004-client-invoice-holds-its-own-lines.md` and the notes
    on ADRs 0001–0003. Do they say what you settled?

## Recommendation

Accept, or tell me what's off and it becomes a follow-up.

## Blocks

Nothing.

## Meanwhile

`send-a-client-invoice-back` is being built on top of it.

## Answer

Accepted (2026-10-05): steps 22 and 23 checked.
