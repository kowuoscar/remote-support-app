---
id: side-lists-and-tabs
title: Side lists and tabs across Carriers, Fleet and Contracts
status: in-progress
journeys: [maintain-the-carrier-catalog, put-resources-in-the-field, set-up-the-operating-picture, bill-the-client-for-the-month]
---

<!-- sdlc:template epic 1 -->

## Intent

Raised by the human on 2026-10-15 while testing on a production-like stack,
after accepting `client-invoices-layout-fixes`. The Agent's Client Invoices
page, with its side list, becomes the pattern for the other list-and-detail
pages. Five UI changes, confirmed point by point the same day:

1. **Client Invoice, Postpaid SIM Cards table.** Each SIM row shows its
   Carrier and type next to the number, separated by "·": `Free · Postpaid`.
   On the Agent's Client Invoice and on the Manager's invoice page. A SIM
   with no Carrier (from before the catalog) shows only `Postpaid`.
2. **Carriers page: side list of Carriers.** Same pattern as the Agent's
   Client Invoices page: a Carriers column on the left; picking one shows its
   Postpaid Plans and Topup Options on the right, in place. Narrower than the
   side list breakpoint, a dropdown, as on Client Invoices. For both the
   Agent and the Manager. Archived Carriers are in the list, marked
   "Archived", at the bottom.
3. **Fleet page: side list and tabs.** A "SIM Cards | Smartphones" tab
   switcher shows one table at a time, for the Agent and the Tester. The
   Agent gets a Clients side list in place of the Contract Switcher. The
   Tester gets a side list only when their Client has more than one
   Contract.
4. **Fleet page, SIM Cards table: long Plan names cut with "…"**, shown in
   full on hover, instead of pushing the table sideways. Raised on the
   Tester's table; applies to the Agent's too.
5. **Manager's Contracts page: one page with a side list.** Left, a Clients
   side list, with "New Contract" above it. Right, top to bottom:
   1. an Agent dropdown, which picks that Client's Contract with that Agent
      and loads its data. The first Agent by name is selected first, and the
      dropdown shows even when there is only one Agent;
   2. the Client Invoice section, today's current-month invoice summary;
   3. SIM Cards and Smartphones, behind the same tab switcher.
   This merges today's Contracts list and Contract detail pages.

The human asked for one feature with one PR, cut into as few tickets as
possible (small follow-ups are one ticket each, with one verify).

This amends written design rules: `DESIGN.md` says to "use a dropdown, never
a tab set" for Contract scoping, and limits the side list to the Agent's
Client Invoices page (`CONTEXT.md`, **Contract side list**). The spec amends
both.

## Journeys

- **Maintain the Carrier catalog** → `exists`. How a Carrier is reached
  changes; what can be done does not.
- **Put resources in the field** → `exists`. How the Fleet is browsed
  changes.
- **Set up the operating picture** → `exists`. The Manager reaches a
  Contract through its Client and Agent.
- **Bill the Client for the month** → `exists`. Invoice lines name the
  Carrier.

## Features

- [x] `ui-side-lists-and-tabs` — the five changes above: Carrier and type on invoice SIM lines; Carriers side list (Agent and Manager); Fleet side list and SIM Cards | Smartphones tabs (Agent, Tester when several Contracts); Plan names cut with "…"; the Manager's Contracts page as one side-list page with an Agent dropdown, invoice summary and tabbed fleet.
