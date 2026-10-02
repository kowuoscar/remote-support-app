---
id: approve-edit-client-invoice-lines
type: approval
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for `edit-client-invoice-lines`?
`docs/features/edit-client-invoice-lines/spec.md`.

In plain terms: while a Client Invoice is a draft, the Agent can edit every
line: one line per postpaid SIM, plus one per Fee. Lines start filled in
with the calculated amount. All your answers are in:
- **Pay follows edits:** the Agent's pay follows the edited amounts until
  the Agent's own invoice is approved. After that, a change carries over to
  next month.
- **Late Fees count:** a Fee logged after the Client Invoice was sent still
  counts in that month's pay.
- **Late Fees on a sent-back invoice:** they appear as new lines, and lines
  already sent never change.
- **Who sees edits:** the Agent and the Manager see "Edited · computed $X".
  The Client only sees the billed amount.
- **Database:** one one-way change stores each invoice's lines. Existing
  invoices are copied as they are, and nothing is deleted.

Choices made without you (approving accepts them):

1. **A SIM added after an invoice was sent doesn't get a new line** when that
   invoice is sent back; only Fees do. SIMs carry no dates, so the app can't
   tell whether a new SIM billed in that past month. The SIM still counts in
   the Agent's pay.
2. **One edge case on pay.** A Fee is logged after the Agent sent their own
   invoice, so it isn't in that invoice yet. If its line is then edited, say
   $10 → $15, the Agent's sent invoice moves by the $5 difference only. The
   $10 itself waits for the carry-over. This follows your "an edit moves pay
   by its difference" rule literally.

## Recommendation

Approve. It's a bigger feature: six tickets, one database change, and a new
ADR 0004. Every edit is visible to the Manager.

## Blocks

`edit-client-invoice-lines` stays a `draft`. `send-a-client-invoice-back`
builds on it.

## Meanwhile

`real-client-dashboard` is being built.

## Answer

Approved (2026-10-02), including both choices taken alone.
