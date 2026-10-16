---
id: approve-month-closes-on-the-fifth
type: approval
status: open
blocks: [month-closes-on-the-fifth]
created: 2026-10-15
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for closing a month on the 5th?
`docs/features/month-closes-on-the-fifth/spec.md`. Your two answers of
2026-10-14 are now written in, and it has no open questions left.

What it does: until the 5th of the following month, the Agent and the
Manager can still change a Client Invoice, even one already sent or
approved, and the Agent's pay for that month follows. From the 6th, an
approved month is closed for everyone. A month that isn't approved by then
is flagged "Late" to the Manager and stays open until approved.

Your answers, as written in:
- Pay already final (marked paid, or approved and closed) never moves. The
  Client's bill still changes, and anything owed goes on next month's pay as
  an adjustment. Marking pay paid closes it at once.
- The Tester sees an open invoice's figures as they stand, with "Can still
  change until 5 October".

Choices made without you that approving accepts:
1. "Until the 5th" means UTC: September closes at 00:00 UTC on 6 October.
2. Days late count from the 5th: the 6th is 1 day late.
3. The "Late" flag is shown only to the Manager.
4. An edited approved invoice stays approved and is marked "Changed after
   approval".
5. Approving is never blocked by the date, since approving is what closes a
   Late invoice.

## Recommendation

Approve. Read the user stories, the non-goals, the decisions taken and the
walkthrough; the solution is yours to skip.

## Blocks

`month-closes-on-the-fifth`, and `invoice-adjustment` after it.

## Meanwhile

The loop drafts the side lists and tabs spec.

## Answer
