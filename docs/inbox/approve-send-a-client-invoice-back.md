---
id: approve-send-a-client-invoice-back
type: approval
status: open
blocks: [send-a-client-invoice-back]
created: 2026-09-30
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for `send-a-client-invoice-back`?
`docs/features/send-a-client-invoice-back/spec.md`. Read `## User stories`,
`## Goals / Non-goals`, `## Decisions taken` and `## Acceptance walkthrough`
(you can skip `## Solution`).

In one line: a Manager sends a sent Client Invoice back to its Agent with a
required reason. It leaves the Review Queue, the Agent sees the reason, adds
what was missing (a file, a late Fee of that month), and resends. The resend
freezes a fresh snapshot.

Your two answers are applied: corrections after approval carry forward
through `invoice-adjustment`, and a past month's base amount stays as sent.

The choices you are most likely to want to veto:

1. **What goes live again.** Fee lines always do. The base amount goes live
   only while the invoice's month is still the current month. Otherwise it
   shows "As sent — a wrong base amount is corrected by the Manager on a
   later invoice."
2. **The reason is hidden from Testers and never printed on the PDF.** It is
   a note between the Manager and their Agent. It stays on the invoice after
   the resend, until a later send-back overwrites it. It is capped at 1000
   characters.
3. **The Agent can now open any of their own Client Invoices by id**, not
   just the current month's. That is how they reach a sent-back invoice from
   last month. They can send only a sent-back draft or a current-month draft.
4. **A resent invoice waits in the Review Queue from the resend**, not from
   its first send.
5. **The Manager uses an inline form, not a dialog**, copied from the
   Pending Requests reject form.

## Recommendation

Approve. It needs one additive migration (two nullable columns) and a new
ADR 0004, which amends ADR 0001 with the backward edge. Nothing is
destructive. Two latent bugs are fixed on the way: a send that isn't one
transaction, and a send-back racing an approval.

## Blocks

`send-a-client-invoice-back` stays a `draft`, and no tickets are cut until
you answer.

## Meanwhile

`real-agent-dashboard` is being ticketed and built.

## Answer
