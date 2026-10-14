---
id: agent-client-invoices-side-list
title: Pick a Client's invoice from a side list, not a dropdown
status: in-progress
journeys: [bill-the-client-for-the-month]
---

<!-- sdlc:template epic 1 -->

## Intent

Raised by the human on 2026-10-14 while testing the Agent's Client Invoices
page. Today the page opens with the Contract Switcher, a dropdown of the
Agent's Contracts ("Client — Country"); picking one shows that Contract's
Client Invoice for the month. The human wants the Clients always in view
instead.

What the human confirmed, with their desktop sketch
(`evidence/agent-client-invoices-side-list/desktop-sketch.png`):

- **Desktop:** the app's navigation sidebar and sticky top bar stay as they
  are. Next to the sidebar, a narrower column lists the Agent's Clients — one
  row per Contract, shown by Client name, with the Country when needed — and
  scrolls on its own. The wide area to its right shows the selected Client's
  invoice. Clicking another Client swaps the invoice in place; no dropdown.
- **Phone:** the dropdown stays as today.
- **Scope:** the Agent's Client Invoices page only. Requests, Fleet and the
  Tester's pages keep the Contract Switcher.
- **Sent back:** the "Sent back to you" list moves to the top of the invoice
  area, and a Client whose invoice was sent back is marked "Sent back" in the
  column.

This amends a written design rule: `DESIGN.md` says to "use a dropdown, never
a tab set" for Contract scoping. The spec amends that rule for this page.

`proposed`, not `planned`: written down at the human's request while
implementation is paused for hands-on testing. The human places it in the
epic order.

## Questions for the spec gate

- Which Client is selected on arrival: the first in the list, or the first
  one sent back?
- Does the selected Client survive a reload or a shared link (the Contract in
  the URL)?
- Order of the column: by Client name, or sent-back first?
- Does each row show anything beyond the name, such as the invoice's status
  for the month?
- At which width does the column give way to the dropdown?

## Journeys

- **Bill the Client for the month** → `exists`. Unchanged in what it does;
  this epic changes how the Agent reaches each Contract's invoice.

## Features

- [ ] `client-invoices-side-list` — on desktop, the Agent picks a Contract's Client Invoice from a scrollable list of Clients beside it; a phone keeps the dropdown.
