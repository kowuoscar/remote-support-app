---
id: approve-real-agent-dashboard
type: approval
status: open
blocks: [real-agent-dashboard]
created: 2026-09-30
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for `real-agent-dashboard`?
`docs/features/real-agent-dashboard/spec.md`. Read `## User stories`,
`## Goals / Non-goals`, `## Decisions taken` and `## Acceptance walkthrough`
(you can skip `## Solution`).

In one line: an Agent's home page stops showing another person's name,
salary and Rollout Advance. Every figure comes from the database, and
`frontend/lib/demo/agent.ts` is deleted.

The choices you are most likely to want to veto:

1. **"Recent Requests" means recently raised, not recently updated.**
   Requests store no update time. Rows read "raised 3 days ago", ordered by
   creation. Adding an update time would need a migration, and it can come
   later. Walkthrough step 13 asks you to confirm.
2. **Salary and Rollout Advance show the standing rates, not the invoice's
   lines.** They come from a new `GET /api/me/agent`, which answers only for
   the signed-in Agent's own record. A Manager's per-invoice override changes
   the invoice, not the standing rate, so the two can differ after a send.
3. **Local Support Fees is this month's invoice line.** After the invoice is
   sent, the card says "As sent on your invoice", because the number is
   frozen. Visiting the page creates this month's draft invoice if none
   exists yet. My Invoice already does that, and a draft never reaches the
   Review Queue.
4. **A failed load shows "—" with "Couldn't load…", never a zero.** An
   existing helper quietly turns failures into empty lists. This page won't
   use it.

## Recommendation

Approve. There is no migration and no security change. The new read serves
only the caller's own Agent record. Every choice above is cheap to change.
Three walkthrough steps are yours.

## Blocks

`real-agent-dashboard` stays a `draft`, and no tickets are cut until you
answer.

## Meanwhile

The loop moves on to whatever isn't blocked. `real-client-dashboard` is next
in this epic, but it waits behind this feature.

## Answer
