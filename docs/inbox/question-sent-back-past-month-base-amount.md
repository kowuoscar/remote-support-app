---
id: question-sent-back-past-month-base-amount
type: question
status: open
blocks: [send-a-client-invoice-back]
created: 2026-09-30
---

<!-- sdlc:template inbox-item 1 -->

## Question

When last month's Client Invoice is sent back, should its **base amount** go
live again, or keep the value it was sent with, with only its Fee lines going
live?

Going live reads **today's** Fleet (`ContractAmountService.baseAmount`). SIMs
record no date for joining or leaving a Contract. So a reopened September
invoice would count Postpaid SIMs added in October and drop SIMs retired
since. The Agent couldn't correct that.

Escalation case 3: you settled "its numbers go live again", but for a past
month's base amount the code can only produce the wrong number.

## Recommendation

**Keep a past month's base amount as sent.** Only a current-month invoice
goes fully live. Fee lines always go live. The Fleet's history isn't
recorded, so a live past-month base would be wrong, not corrected.

To make a past base amount correctable later, the Fleet would need
membership history: dated SIM-to-Contract rows. That is its own epic, and a
migration with a backfill.

## Blocks

`send-a-client-invoice-back`: spec stays `draft`; no tickets.

## Meanwhile

The same as the other question on this feature: the loop works on anything
not blocked.

## Answer
