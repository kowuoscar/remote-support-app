---
id: accept-send-a-client-invoice-back
type: acceptance
status: open
blocks: []
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

Does sending a Client Invoice back do what you wanted?
`docs/features/send-a-client-invoice-back/delivery.md`.

A Manager sends a sent invoice back with a reason. The Agent finds it under
"Sent back to you", corrects it and resends it, and it goes straight back into
the Review Queue. Nothing is recalculated, the Tester never sees the reason,
and the Agent's pay moves only by their edits. Agents played 18 of 21 steps,
with evidence. Three are yours:

19. Send back a real invoice with the reason you'd really write, then read it
    as the Agent would. Does the wording tell the Agent what happened and what
    to do? Does nothing reach the Tester?
20. Read `docs/adr/0005-manager-may-send-a-client-invoice-back-to-draft.md`
    and ADR 0001's new note. Do they say what you settled?
21. Send back a real last-month invoice whose SIM line doesn't match the
    carrier bill. As the Agent, correct that line, attach the carrier's file
    and resend. Is this how you want errors caught before approval? Did the
    numbers stay put until the Agent changed them? Is the Agent's pay for that
    month what you expect?

## Recommendation

Accept, or tell me what's off and it becomes a follow-up.

## Blocks

Nothing.

## Meanwhile

The loop moves on to closing the login epic and to `invoice-adjustment`.

## Answer

