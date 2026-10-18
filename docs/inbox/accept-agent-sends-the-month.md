---
id: accept-agent-sends-the-month
type: acceptance
status: open
blocks: []
created: 2026-10-18
---

<!-- sdlc:template inbox-item 1 -->

## Question

Does the Agent now send the month's Client Invoices together, once all are
ready? Report: `docs/features/agent-sends-the-month/delivery.md`.

Agents played walkthrough steps 1–14 and kept evidence. Step 15 is yours:

15. On your own stack (`rsa-prod`), now rebuilt. An Agent who already sent an
    invoice this month before the update shows the month as sent; use an
    Agent with nothing sent this month to see the bar. As that Agent:
    - open Client Invoices: check the bar's count, the disabled "Send all"
      with its reason, and the side list's "✓ Ready" / "files missing";
    - attach the missing carrier invoices and watch the count reach "all
      ready";
    - send the month; check "Sent to Manager on {date}";
    - as the Manager, send one back; as the Agent, resend it from its card.

## Recommendation

Accept if it reads and behaves as you expected; anything off becomes a small
follow-up ticket.

## Blocks

Nothing.

## Meanwhile

The loop builds `manager-reviews-by-agent`.

## Answer
