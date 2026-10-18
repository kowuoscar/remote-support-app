---
id: accept-carrier-invoice-per-sim
type: acceptance
status: open
blocks: []
created: 2026-10-18
---

<!-- sdlc:template inbox-item 1 -->

## Question

Does each Postpaid SIM now get its own carrier invoice, under its computed
name, with no send until every one is there? Report:
`docs/features/carrier-invoice-per-sim/delivery.md`.

Agents played walkthrough steps 1–14 and kept evidence. Step 15 is yours:

15. On your own stack (`rsa-prod`), now rebuilt with this feature, on a
    Contract with Postpaid SIMs:
    - open this month's Client Invoice and check Send is disabled with the
      count note;
    - attach a real carrier PDF for each Postpaid SIM through the form;
    - check each name reads `{Client} - {Month Year} - {Carrier} - {SIM number}.pdf`
      and each line shows its check mark;
    - send;
    - as the Manager, check the marks and send it back;
    - as the Agent, remove one file, attach it again, and resend.

## Recommendation

Accept if it reads and behaves as you expected; anything off becomes a small
follow-up ticket.

## Blocks

Nothing.

## Meanwhile

The loop moves to `carrier-invoice-retention` (its spec goes to you for
approval).

## Answer
