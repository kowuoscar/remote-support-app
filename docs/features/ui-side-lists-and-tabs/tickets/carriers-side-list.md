---
id: carriers-side-list
title: Browse a Country's Carriers through a shared side list on both Carriers pages
status: in-progress
depends_on: []
labels: [frontend]
stories: [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 44, 45, 46, 47]
---

## Context

`spec.md` `### The side list, shared`, `### Selection in the address`,
`### Carriers: one side list page`, the `DESIGN.md` bullets and
`## Design direction` (Carriers). Independent of `invoice-sim-line-carrier`:
that ticket touches the invoice cards, this one the layout of the Client
Invoices view; the goldens they recapture do not overlap.

Introduces `SideList` and `SideListLayout`, moves Client Invoices onto them
pixel-identically (`ContractSideList` removed, `ContractSwitcher` gains `icon`),
then puts both Carriers pages on them. This is the first user of the shared
component, so the move and the first new page are one slice: the move alone
would be an enabler with no behaviour to demo, and the Carriers page proves
the generalisation.

Goldens: recaptures `agent-carriers-*` and `manager-carriers-*`. The
`agent-client-invoices-*` goldens must NOT change. Declares the **Side list**
term (renaming **Contract side list**) in the result, never editing
`CONTEXT.md`. Amends `DESIGN.md` Layout, Contract Switcher, Side list and Do's
in the same commit as the visual change. Satisfies the frontend DoD in
`docs/agents/frontend.md`.

## Acceptance criteria

- [ ] The Agent's Client Invoices page is unchanged: `client-invoices-view.test.tsx` and `client-invoices-side-list.spec.ts` pass unedited, and the `agent-client-invoices-*` goldens do not move.
- [ ] On `/agent/carriers` and `/manager/carriers` at 1280 and wider, a "Carriers" navigation landmark lists every Carrier of the Country, archived last, muted, each archived link's name ending "Archived"; the first Carrier is current on arrival, and picking another shows its Postpaid Plans and Topup Options in place, with no request and `?carrier=` in the address that survives a reload; an unknown `carrier` opens the first row.
- [ ] An archived Carrier shows its name, "Archived {date}" and its lists with no Add, Edit, Rename or Archive; on a live Carrier those actions work as before, and after Archive the Carrier stays selected, moved to the bottom, marked.
- [ ] "Show archived" counts and reveals only the selected Carrier's archived Topup Options and Postpaid Plans (`?archived=1`).
- [ ] "Add carrier" and "{n} active" sit above the column (the Manager's Country filter first); a Carrier just added is selected, and the Manager changing Country opens that Country's first Carrier and keeps `archived`.
- [ ] Below 1280 a Carrier dropdown replaces the column, above the card, with exactly one of the two displayed at any width and no sideways page scroll at 375, 390, 768, 1024, 1279 and 1440 on the Carriers pages.
- [ ] A Country with no Carriers, or a failed catalog, shows today's "No carriers in {Country} yet" or "Couldn't load carriers" across the detail area with no landmark and no dropdown, and "Add carrier" stays reachable; every side list row opens in a new tab on Cmd/Ctrl-click and is reachable by Tab and Enter.

## Tests

Seams per spec `## Testing decisions` items 2, 3 and 4.

- **Component (new `carriers-view.test.tsx`, `stubFetch` and `mockRouter`):** the "Carriers" landmark; archived rows last with "Archived" in their names; the first row current; a click showing that Carrier's lists with no fetch and writing `?carrier=`; an unknown `carrier` falling to the first row; an archived Carrier's lists with no buttons; "Show archived" counting the selected Carrier's archived entries; empty and failed states with no landmark and "Add carrier" present. Unedited: `client-invoices-view.test.tsx`.
- **End to end (`carrier-catalog.spec.ts`, new steps):** archived Carrier at the bottom; pick, address and reload; a new Carrier selected after it is added; the Manager's Country change opening the first Carrier; 390px dropdown with no sideways scroll. `helpers.ts` gains `selectInSideList(page, list, rowName)`, `selectClientInvoiceContract` becomes a call to it, and `addPostpaidPlanToVerizon` selects Verizon in the "Carriers" landmark first.
- **Visual:** `surfaces.spec.ts` waits on the "Carriers" landmark for the Carriers surfaces and keeps `manager-carriers` on `?archived=1`; recapture `agent-carriers-*` and `manager-carriers-*`.

## Regression

At risk: the Agent's Client Invoices page (rows, badges, "Sent back" mark, order, address, accessible names), every Carrier, Plan and Topup dialog, the Requests page's Contract Switcher, and every e2e step that adds a Plan to Verizon.

Existing tests expected to change: `frontend/tests/e2e/carrier-catalog.spec.ts` is modified. Its assertion at about lines 38-39 that the archived Sprint is hidden until "Show archived" is rewritten, because archived Carriers are now always in the column, at the bottom, marked; "Show archived" now governs only the selected Carrier's archived offers. The `carrierRow()` helper (about line 17) and the offer reads (about line 86) assume offers nested inside a Carrier row, and are rewritten to select the Carrier in the "Carriers" landmark and read the detail card, because offers are now shown on the right for the selected Carrier. All other pre-existing cases keep their assertions. The spec also gains the new steps, and `helpers.ts` gains `selectInSideList` and has `selectClientInvoiceContract` and `addPostpaidPlanToVerizon` reimplemented, so every spec that uses them is re-run; every pre-existing case keeps passing unmodified. `client-invoices-view.test.tsx` and `client-invoices-side-list.spec.ts` pass unedited. `surfaces.spec.ts` gains the landmark wait. Only `agent-carriers-*` and `manager-carriers-*` goldens are recaptured.

## Observability

N/A: layout and navigation over existing data; no new operation.
