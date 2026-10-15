---
id: fleet-side-list-and-tabs
title: Browse the Fleet through a Clients side list and SIM Cards and Smartphones tabs
status: in-progress
depends_on: [carriers-side-list]
labels: [frontend]
stories: [17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 48]
---

## Context

`spec.md` `### Tabs, shared`, `### Fleet: the Agent and the Tester`,
`### Plan and Carrier names in the SIM Cards table`, `### Selection in the
address` and `## Design direction` (Fleet). Depends on `carriers-side-list`
for `SideList`, `SideListLayout`, `ContractSwitcher`'s `icon` and
`selectInSideList`: none of them exist without it.

Introduces `Tabs` and `FleetTabsCard`. The Agent's and the Tester's Fleet views
move onto `FleetTabsCard`. The Manager's contract detail page stays on
`FleetTableCard` (kept, not removed) until `manager-contracts-one-page` moves
it, so the Manager helpers and direct clicks in the e2e specs stay valid in
between.

Goldens: recaptures `agent-fleet-*` and `client-fleet-*` only. The
`manager-contract-fleet-*` goldens must not move here: the stub's long Plan
name goes on a SIM Card of a Contract those goldens do not show, and the
Manager page keeps its layout. `manager-contracts-one-page`
renames and recaptures them. Definition of done: `DESIGN.md` carries the Tabs entry and the cut-with-"…" Tables sentence, in the same commit as the visual change. Satisfies the frontend DoD in
`docs/agents/frontend.md`.

## Acceptance criteria

- [ ] On `/agent/fleet` at 1280 and wider, a "Clients" landmark lists the Agent's Clients alphabetically (Country only on a shared name), the first is current, picking one swaps the Fleet with no request and writes `?contract=`, and an address naming a Contract that is not the Agent's opens on the first Client; below 1280 the Contract Switcher shows instead, with no sideways page scroll.
- [ ] A "Fleet" tablist shows "SIM Cards {n}" selected first and "Smartphones {n}", one panel at a time; Tab reaches it, Left, Right, Home and End move and select with focus following, and the tab is kept across a Client pick and written as `?tab=smartphones` (absent for SIM Cards), which a reload honours.
- [ ] Every Fleet action (status changes, the number, plan, model, owner and serial corrections, Installed in) works inside its tab for the Agent; the Agent's and the Tester's Fleet pages show no table title repeating the tab label.
- [ ] On `/client/fleet` a Tester with several Contracts gets a "Contracts" landmark, one row per Contract by Country, with the switcher below 1280; a Tester with one Contract gets no landmark and today's chip above the tabs; "Submit request" stays at the top of the area in both.
- [ ] A 60-character Plan name or Carrier name in the SIM Cards table is cut with "…" with the full name in its `title` and its "Archived" badge whole, on the Agent's and the Tester's tables (the shared table also serves the Manager's), and the table is no wider than for a short name.
- [ ] A single-Contract Agent sees the column with one current row, and an Agent with none sees "No contracts yet".

## Tests

Seams per spec `## Testing decisions` items 2, 3 and 4.

- **Component (new fleet view test file, one `describe` per role):** Agent: the "Clients" landmark in order; `?contract=` honoured and written; an unknown contract falling to the first; the "Fleet" tablist with SIM Cards selected; ArrowRight selecting Smartphones with focus on it; Home and End; `?tab=` honoured and written; the tab kept across a Client pick; single-Contract and empty states. Tester: two Contracts give the "Contracts" landmark by Country; one Contract gives no landmark and the chip.
- **Component (`sim-card-fleet-table.test.tsx`, new cases):** a 60-character Plan name and Carrier name are each rendered whole in their `title`; the "Archived" badge still follows the name.
- **End to end:** `fleet-management.spec.ts` gains the Agent side list, tabs by keyboard, `?tab=` kept across Clients and reload, a long Plan name's `title` with the cell's `scrollWidth > clientWidth`, the Tester with two Contracts, and 390px. `helpers.ts` gains `fleetTab` and `simCardsTable` now reads the SIM Cards panel. The `selectContractInSwitcher` calls on `/agent/fleet` and `/client/fleet` in `fleet-management`, `agent-stock`, `sim-swap-moves`, `fee-logging-and-provisioning`, `return-client-owned-smartphones`, `manager-decides-return-disposition` and `manager-approves-requests` move to `selectInSideList` (Requests calls stay).
- **Visual:** the stub gains a 60-character Postpaid Plan name on one SIM Card; recapture `agent-fleet-*` and `client-fleet-*`.

## Regression

At risk: every Fleet correction and status flow, the Manager detail page's Fleet and its add dialogs (now behind a tab), the Requests page's Contract Switcher, every e2e step that reads a `table` on a Fleet page, and the Tester's "Submit request".

Existing tests expected to change: the listed e2e specs have their `selectContractInSwitcher` call sites on `/agent/fleet` and `/client/fleet` moved to `selectInSideList`, because the Switcher is no longer displayed at 1280 on those pages; assertions unchanged. `fleet-management.spec.ts` is also modified at about lines 208 and 219, where `page.locator("table").first()` (Agent, Smartphones) and `.nth(1)` (Tester, SIM Cards) read tables by position on `/agent/fleet` and `/client/fleet`: they become `fleetTab(...)` reads, because only the selected tab's table is rendered. For the same reason (only the selected tab's table is rendered, SIM Cards by default; the Switcher is hidden at 1280) these are also modified: `fleet-management.spec.ts` about lines 304 and 351 (Agent `locator("table").first()` reaching a Smartphone's Edit model and Edit owner), about line 498 (the same on `/client/fleet`), about lines 476-478 (the local `showContractOfTester` clicks `[aria-haspopup="listbox"]`, the Switcher), and `fee-logging-and-provisioning.spec.ts` about lines 214-215 (smartphone rows on `/agent/fleet` with no Smartphones tab picked). Every other pre-existing case keeps passing unmodified. Its direct "Add smartphone" clicks (about lines 35, 63, 147, 179, 267) and `helpers.ts` `addSmartphone` (about lines 139-140) are on the Manager page and stay unchanged. `sim-card-fleet-table.test.tsx` gains two cases and every pre-existing case keeps passing unmodified. Only `agent-fleet-*` and `client-fleet-*` goldens are recaptured.

## Observability

N/A: layout and navigation over existing data; no new operation.
