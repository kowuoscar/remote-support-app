---
id: approve-real-manager-dashboard
type: approval
status: open
blocks: [real-manager-dashboard]
created: 2026-10-08
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for the Manager's real home page?
`docs/features/real-manager-dashboard/spec.md`.

The Manager's home page stops showing demo figures. It shows:
- the signed-in Manager's own login;
- the real number of Clients, Agents and Contracts;
- "Billed this month" and "Payout this month", counted as you answered today,
  one total per currency (e.g. $135.99 with £20.00 underneath).

A card that can't load says so. It never shows a zero or a made-up figure.

These are choices made without you; approving accepts them:
1. The chip reads "manager@example.com · Manager", because a Manager has no
   name in the data, only a login.
2. A money card with nothing to count reads "None yet", not "$0", because
   "$0" would need a currency to be picked.
3. The Clients card says "In this tenant", not "Active", because a Client
   has no active or archived state.
4. Not included: charts, trends, a month picker, a breakdown by Client or
   Agent, or a figure for invoices still in draft.
5. The demo data and the "Demo data" footer are deleted for good, since this
   was the last page using them.

## Recommendation

Approve. Its three questions are settled, and the rest follows the Agent and
Client dashboards you already accepted.

## Blocks

`real-manager-dashboard`.

## Meanwhile

The loop moves on to the research for the package layout, which you approved
today.

## Answer
