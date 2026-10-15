---
id: manager-contracts-one-page
title: Browse the Manager's Contracts on one page by Client and Agent
status: ready-for-agent
depends_on: [fleet-side-list-and-tabs]
labels: [frontend]
stories: [31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 48]
---

## Context

`spec.md` `### The Manager's Contracts: one page`, `### Selection in the
address` (Manager row) and `## Design direction` (Manager Contracts). Depends
on `fleet-side-list-and-tabs` for `FleetTabsCard` and `Tabs` (and through it
the shared `SideList`), and because both recapture `manager-contract-fleet-*`.

`/manager/contracts` merges the list and the detail page; it reads only the
selected Contract, swaps by `router.replace` with no prefetch, and
`/manager/contracts/{id}` becomes a server `redirect`. `ManagerContractsView`,
its search and `ManagerContractFleetView` are replaced.

Moves the Manager page from `FleetTableCard` to `FleetTabsCard`, retires
`FleetTableCard`, and makes "Add smartphone" and "Add SIM card" `secondary`
`sm` buttons in their tab header ("Two pills" debt). Definition of done:
`DESIGN.md` is updated where this ticket changes visuals (the Side list entry
lists the Manager's Clients).

Goldens: renames `manager-contract-fleet-*` to `manager-contracts-*` (path
`/manager/contracts?contract=…`) and recaptures them. The stub gains a Manager
current-month Client Invoice for the first Contract. Satisfies the frontend
DoD in `docs/agents/frontend.md`.

## Acceptance criteria

- [ ] At 1280 and wider `/manager/contracts` shows "New Contract" above a "Clients" landmark of Clients that hold a Contract, in alphabetical order, the first current, with the subtitle "{n} in this tenant" still counting Contracts.
- [ ] The detail area shows the Client's name as its heading, an "Agent" select (always shown, even for one Agent) with the first Agent by name selected, "{Country} · {currency}", the current-month invoice summary with "Open invoice", and the tabbed Fleet with "Add SIM card" and "Add smartphone" in their tabs.
- [ ] Picking a Client or an Agent selects that Contract through `?contract=` (keeping `tab`), while pending the area keeps its content with `aria-busy`, and opening or picking requests only the selected Contract's Fleet, invoice and Carriers (no other Contract's), with no link prefetching.
- [ ] An address naming no Contract opens on the first Client with no error; `/manager/contracts/{id}` lands on `/manager/contracts?contract={id}` with that Contract selected.
- [ ] "New Contract" creates a Contract and selects it, its Client appearing in the column; with no Contracts the page shows "No contracts yet" under "New Contract" with no landmark.
- [ ] Below 1280 the page shows "New Contract", then a Clients dropdown, then the Agent select, with no sideways page scroll at 375, 390, 768, 1024, 1279 and 1440.
- [ ] "Add smartphone" and "Add SIM card" are `secondary` `sm` buttons in their tab's header and still work, and the old table, search box and "N of M" count are gone.

## Tests

Seams per spec `## Testing decisions` items 2, 3 and 4.

- **Component (new Manager contracts view test):** the "Clients" landmark of Clients holding Contracts; the Agent select shown with one option; picking an Agent calling `router.replace` with `?contract=` and keeping `tab`; an unknown `?contract=` opening the first Client with no error; the detail area carrying `aria-busy="true"` while the transition is pending; "New Contract" present; the summary and tabs present; the empty state with no landmark.
- **End to end:** `manager-entity-setup.spec.ts` gains one page; first Agent by name; the Agent select shown with one Agent; a second Contract for the same Client switching by Agent; the old `/manager/contracts/{id}` landing on `?contract={id}`; no request for an unselected Contract's Fleet (request log); 390px. `helpers.ts`: `createContractForClient` and `createUnrelatedContract` click "New Contract", scope the Client and Agent selects to the dialog and read the new id from `?contract=`; `addSmartphone`, `addSimCard`, `addPostpaidSimCard` and `installSimCard` open `/manager/contracts?contract=<id>` (plus `&tab=smartphones` for phones). The `goto("/manager/contracts/…")` calls in `client-invoice-submission-and-visibility`, `client-dashboard` and `fee-logging-and-provisioning` move to the new address, and `fleet-management.spec.ts`'s `a[href^="/manager/contracts/"]` selector becomes the side list row.
- **Visual:** `surfaces.spec.ts` renames `manager-contract-fleet` to `manager-contracts` with the new path; recapture those goldens.

## Regression

At risk: every e2e flow that provisions a Manager Contract's Fleet or creates a Contract, the Manager invoice page's link back to the Contract, the dashboard link into a Contract, and `manager-dashboard.spec.ts`, which reads the subtitle.

Existing tests expected to change: `helpers.ts` `addSmartphone` (about lines 139-140) and `fleet-management.spec.ts` direct "Add smartphone" clicks (about lines 35, 63, 147, 179, 267) are modified, because the Add buttons now sit in their tab (smartphones need `&tab=smartphones`) and the Manager page's tables are read per tab, not by position; assertions unchanged. The helpers and `goto` call sites named above move to the new address and the dialog-scoped selects, because the route and the page's own "Agent" select changed; `client-invoice-submission-and-visibility.spec.ts`, `client-dashboard`, `fee-logging-and-provisioning` and `fleet-management` change only those navigation lines, with assertions unchanged. `manager-entity-setup.spec.ts` gains steps, and its pre-existing cases that used the table or search are updated because those controls are removed (named in the result). Any existing component test of `ManagerContractsView` or `ManagerContractFleetView` is replaced by the new view test, with the reason named. `manager-dashboard.spec.ts` and `client-invoices-side-list.spec.ts` pass unedited. `frontend/tests/visual/surfaces.spec.ts` is modified: its `manager-contract-fleet` entry is renamed `manager-contracts` with the new path. `manager-contract-fleet-*` goldens are renamed and recaptured, and no other golden changes. Any existing test of `FleetTableCard` is removed with it, with the reason named in the result.

## Observability

N/A: layout and navigation over existing reads; the invoice draft get-or-create is unchanged and now limited to the selected Contract.
