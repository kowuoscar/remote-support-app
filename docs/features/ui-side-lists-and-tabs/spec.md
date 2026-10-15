---
feature: ui-side-lists-and-tabs
epic: side-lists-and-tabs
status: approved
date: 2026-10-15
---

<!-- sdlc:template spec 1 -->

# Side lists and tabs across Carriers, Fleet and Contracts

## Problem

On 2026-10-15 the human tested the app on a production-like stack, after
accepting `client-invoices-layout-fixes`. The Agent's Client Invoices page
already lists every Client in a side column, and picking one swaps the invoice
in place. The other list-and-detail pages still work the old way:

- **Carriers.** All of a Country's Carriers are stacked in one long card. Each
  one shows both of its offer lists, so to reach one Carrier's Postpaid Plans
  you scroll past every other Carrier. Archived Carriers stay hidden until you
  tick "Show archived".
- **Fleet.** The Agent picks a Client from the Contract Switcher dropdown. The
  Smartphones table and the SIM Cards table are stacked, so the SIM Cards sit
  below a long phone list. A Tester whose Client holds several Contracts
  gets the same dropdown.
- **Long Plan names.** A long Postpaid Plan name pushes the SIM Cards table
  sideways. The human saw it on the Tester's table, and the Agent's table has
  the same problem.
- **The Manager's Contracts.** There are two pages: a searchable table, and a
  detail page for each Contract. To move from one Client's Contract to the
  next, the Manager goes back to the table each time. The Contract's invoice
  and its Fleet are on the detail page, with the Fleet's two tables stacked.
- **Client Invoice lines.** A Postpaid SIM Card line shows only its number.
  The Carrier's own invoice lists SIMs by Carrier, so an Agent or a Manager
  reconciling the two cannot tell which Carrier a line belongs to.

The epic's `## Intent` lists five changes. The human confirmed each one on
2026-10-15. They are settled, not open:

1. On the Agent's Client Invoice and on the Manager's invoice page, each
   Postpaid SIM row shows its Carrier and type next to the number, separated
   by "·": `Free · Postpaid`. A SIM with no Carrier (from before the catalog)
   shows only `Postpaid`.
2. Carriers, for both the Agent and the Manager, get a Carriers side list.
   Picking a Carrier shows its Postpaid Plans and Topup Options on the right,
   in place. Below the side list's breakpoint, a dropdown takes its place.
   Archived Carriers are in the list, marked "Archived", at the bottom.
3. Fleet gets a "SIM Cards | Smartphones" tab switcher that shows one table at
   a time, for the Agent and the Tester. The Agent gets a Clients side list
   instead of the Contract Switcher. The Tester gets a side list only when
   their Client has more than one Contract.
4. In the Fleet's SIM Cards table, a long Plan name is cut with "…" and shown
   in full on hover, on the Tester's table and on the Agent's.
5. The Manager's Contracts become one page with a Clients side list, with "New
   Contract" above it. On the right, top to bottom:
   - an Agent dropdown, which picks that Client's Contract with that Agent.
     The first Agent by name is selected first, and the dropdown shows even
     with only one Agent;
   - the current-month Client Invoice summary;
   - SIM Cards and Smartphones, behind the same tab switcher.

The human asked for one feature with one PR, cut into as few tickets as
possible.

## Journeys

This is the only feature of `docs/roadmap/side-lists-and-tabs.md`.

- **Maintain the Carrier catalog** (`exists`, stays `exists`). The Agent and
  the Manager reach a Carrier differently. What they can do to it is
  unchanged.
- **Put resources in the field** (`exists`, stays `exists`). The Fleet is
  browsed through a side list and tabs. Adding, correcting and changing the
  status of units works as before.
- **Set up the operating picture** (`exists`, stays `exists`). The Manager now
  reaches a Contract through its Client and then its Agent, on one page.
  Creating a Contract is unchanged.
- **Bill the Client for the month** (`exists`, stays `exists`). Invoice SIM
  lines now name their Carrier. Its proof spec,
  `client-invoice-submission-and-visibility.spec.ts`, keeps passing. Only its
  navigation to the Manager's Contract changes.

## Goals / Non-goals

Goals:

- One shared **side list** component. It is used on the Agent's Client
  Invoices page (where it is unchanged), the Agent's and the Tester's Fleet,
  both Carriers pages and the Manager's Contracts. It shows at `xl` (1280px)
  and wider. Narrower than that, a dropdown takes its place.
- One shared **tabs** component, used for "SIM Cards | Smartphones" on the
  Agent's Fleet, the Tester's Fleet and the Manager's Contracts.
- Each Postpaid SIM line of a Client Invoice names its Carrier and type. The
  backend serves the Carrier's name on the line.
- In the Fleet's SIM Cards tables, Plan and Carrier names are cut with "…"
  and shown in full on hover.
- The Manager's Contracts list and Contract detail become one page. Old
  Contract addresses keep working.
- The selected item and the selected tab are kept in the page address, so a
  reload or a bookmark reopens them.
- `DESIGN.md` is amended in the same commits as the visual changes, and the
  glossary's **Contract side list** becomes **Side list** (declared, see
  `terms`).

Non-goals. Each is something a reasonable agent would otherwise build:

- **No side list on any other page.** Requests (Agent and Tester), Stock, the
  dashboards, Clients, Agents and Invoices keep their current layouts. Requests
  keeps the Contract Switcher at every width.
- **No search, filter or grouping in any side list**, and no row content
  beyond what is listed under `## Solution`. The Manager's Contracts search box
  goes away with the table (see Decisions taken). No search is added to the
  side list.
- **No change to the Agent's Client Invoices side list.** Its look, order,
  status badges, "Sent back" mark, address handling and accessible names stay
  as they are. It only moves onto the shared component. The open debts "Two
  looks for Sent back" and "Name cut hard" stay open.
- **No tabs anywhere else.** Requests' status filter and the Review Queue's
  filter keep their own tab strips. They are filters, not panel switchers, and
  they are not migrated onto the new component.
- **No tab for Contract scoping.** Tabs switch between a Contract's two
  tables. They never pick a Contract.
- **No lazy loading on the Agent's or the Tester's Fleet, and none on
  Carriers.** Those pages still read everything up front, as today. Picking a
  Client, a Contract or a Carrier makes no request.
- **No new Manager endpoint.** There is still no single-Contract GET. The page
  finds the Contract in the list, as the detail page does today.
- **No change to the Tester's invoice pages or to the invoice PDF.** The
  Tester's Invoices page does not render Postpaid SIM lines, and the PDF is
  not touched. The Tester's API response does gain the field (Decisions
  taken).
- **No snapshot of the Carrier name on an invoice.** The line reads the SIM's
  Carrier as it is now, as it already does for the SIM's number.
- **No truncation of other Fleet cells.** Smartphone models, SIM numbers and
  "Installed in" keep their current behaviour. Fleet tables keep their 720px
  minimum and their sideways scroll.
- **No change to the Carrier, Topup Option or Postpaid Plan dialogs, or to what
  they accept.**
- **No change to `CreateContractDialog`'s form.** Its title, its fields and
  its submit label ("Add contract") stay the same. Only its trigger is
  relabelled, and what happens after it succeeds changes.
- **No ADR.** Every decision here can be reverted. The one route removal
  leaves a redirect behind.

## User stories

Client Invoice lines:

1. As an Agent, I want each Postpaid SIM Card line of my Client Invoice to show its Carrier and type next to the number, as "Free · Postpaid", so that I can match each line against that Carrier's own invoice.
2. As a Manager, I want each Postpaid SIM Card line on a Client Invoice's page to show its Carrier and type next to the number in the same way, so that I can check the lines against the Carrier invoices attached to it.
3. As an Agent or a Manager, I want a SIM Card from before the catalog, which has no Carrier, to show "Postpaid" alone, so that a line is never left with a blank or a dash.
4. As an Agent or a Manager, I want a SIM Card whose Carrier was archived later to still show that Carrier's name on the line, so that the line keeps saying where it came from.
5. As an Agent, I want the Carrier and type on every view of the invoice (a draft, a sent invoice, a single invoice's own page, and the one-column rows below 1280px), so that the line reads the same wherever I meet it.
6. As a Tester, I want my Invoices page unchanged, so that a fix on the Agent's and Manager's pages moves nothing on mine.

Carriers:

7. As an Agent on a desktop, I want my Country's Carriers in a "Carriers" column, where picking one shows its Postpaid Plans and Topup Options on the right in place, so that I reach one Carrier's offers without scrolling past every other Carrier.
8. As a Manager on a desktop, I want the same column for the Country I pick, with the Country filter above it, so that changing the Country lists that Country's Carriers with the first one selected.
9. As an Agent or a Manager, I want archived Carriers always in the column, at the bottom, each marked "Archived", so that I can still find an old Carrier without hunting for a toggle.
10. As an Agent or a Manager, I want picking an archived Carrier to show its name, when it was archived, and its Postpaid Plans and Topup Options with no actions, so that I can read what it offered and cannot change it.
11. As an Agent or a Manager, I want the first Carrier in the column selected when I open the page, and my pick kept in the page address, so that the page always opens on a Carrier and a reload or a bookmark reopens the one I chose.
12. As an Agent or a Manager, I want "Add carrier" above the column, and the new Carrier selected once added, so that I can fill in its offers straight away.
13. As an Agent or a Manager, I want Rename, Archive and each offer's Add, Edit and Archive to work on the selected Carrier as they do today, with an archived Carrier staying selected, moved to the bottom and marked "Archived", so that maintaining the catalog loses nothing.
14. As an Agent or a Manager, I want "Show archived" to reveal the selected Carrier's archived Postpaid Plans and Topup Options, with their count, so that the old offers stay one click away.
15. As an Agent or a Manager on a phone or a narrow window, I want a Carrier dropdown above the Carrier's offers, so that the offers keep the whole width.
16. As an Agent or a Manager whose Country has no Carriers, or whose catalog failed to load, I want today's empty or failure state across the page, with no empty column, so that nothing suggests a missing list.

Fleet:

17. As an Agent on a desktop, I want my Clients in a "Clients" column on the Fleet page instead of the Contract Switcher, one row per Contract by Client name, in alphabetical order, with the Country on rows whose Client name is shared, so that I see every Client whose Fleet I keep.
18. As an Agent, I want picking a Client to swap the Fleet in place, with no page load, so that I can move from one Fleet to the next quickly.
19. As an Agent, I want the first Client selected when I open the page, and my pick kept in the page address, so that a reload or a bookmark reopens the same Fleet, while an address naming a Contract that is not mine opens on the first Client with no error.
20. As an Agent or a Tester, I want a "SIM Cards | Smartphones" tab switcher that shows one table at a time, with SIM Cards first and selected on arrival, and each tab showing its count, so that the SIM Cards are not buried below the phones and I can see how many of each there are.
21. As an Agent or a Tester, I want the chosen tab kept when I pick another Client or Contract, and in the page address, so that I can go down the SIM Cards of every Client without reopening the tab, and a reload keeps it.
22. As an Agent or a Tester using a keyboard or a screen reader, I want to reach the tabs with Tab, move between them with the arrow keys, Home and End, and hear which tab is selected and which panel it controls, so that switching tables needs no mouse.
23. As an Agent, I want every Fleet action to work inside its tab as it does today (status changes, the number, plan, model, owner and serial corrections, Installed in), so that the tabs cost me nothing.
24. As an Agent on a phone or a narrow window, I want today's Contract Switcher above the tabs, with nothing scrolling sideways but the table itself, so that the Fleet keeps the whole width.
25. As an Agent with a single Contract, I want the column with its one row, selected. As an Agent with no Contracts, I want today's "No contracts yet". That way the page keeps one shape, and an empty page says why it is empty.
26. As a Tester whose Client holds several Contracts, I want those Contracts in a "Contracts" column on a desktop, one row per Contract by Country, and today's Contract Switcher on a narrow screen, so that I can move between my Client's Fleets the way an Agent does.
27. As a Tester whose Client holds one Contract, I want no column, and today's Contract chip above the tabs, so that a column of one row never takes space from my Fleet.
28. As a Tester, I want "Submit request" to stay at the top of the Fleet area, so that the tabs do not move my main action.
29. As an Agent, a Tester or a Manager, I want a long Plan name in the SIM Cards table cut with "…" and shown in full on hover, with its "Archived" badge whole, so that a long name never pushes the table sideways.
30. As an Agent, a Tester or a Manager, I want a long Carrier name in the SIM Cards table cut the same way, so that the table's columns stay put whatever the catalog holds.

The Manager's Contracts:

31. As a Manager on a desktop, I want one Contracts page with a "Clients" column on the left, listing each Client that holds a Contract in alphabetical order, and "New Contract" above it, so that I move from one Client's Contracts to the next without leaving the page.
32. As a Manager, I want picking a Client to select its Contract with the first Agent by name and show that Contract's data on the right, so that one click opens a Client's Contract.
33. As a Manager, I want an Agent dropdown at the top of the right side, listing that Client's Contracts by Agent name and shown even when there is only one Agent, so that I can tell which Agent's Contract I am looking at and pick another.
34. As a Manager, I want the selected Contract named above the dropdown (its Client) and its Country and currency beside it, so that I always know whose Contract and money I am reading.
35. As a Manager, I want the selected Contract's current-month Client Invoice summary under the Agent dropdown, with "Open invoice", as on today's Contract page, so that I reach the invoice from the Contract.
36. As a Manager, I want the Contract's SIM Cards and Smartphones under the summary, behind the same tab switcher, with "Add SIM card" and "Add smartphone" in their tabs and every Fleet correction as today, so that I can provision and correct a Contract's Fleet from this page.
37. As a Manager, I want the selected Contract in the page address, so that a reload or a bookmark reopens it, while an address naming no Contract of mine opens on the first Client with no error.
38. As a Manager with an old Contract page bookmarked (`/manager/contracts/{id}`), I want it to open the Contracts page with that Contract selected, so that no link I saved breaks.
39. As a Manager, I want a Contract I have just created selected, so that I can start provisioning its Fleet straight away.
40. As a Manager, I want the page's subtitle to keep saying how many Contracts the tenant has, so that the count I relied on is still there.
41. As a Manager on a phone or a narrow window, I want a Client dropdown and then the Agent dropdown above the Contract's data, so that the page works without the column.
42. As a Manager with no Contracts, I want "No contracts yet" with "New Contract" above it and no empty column, so that the page tells me what to do first.
43. As a Manager, I want opening the page or picking a Client to read only the selected Contract's invoice and Fleet, so that browsing never creates a current-month invoice draft for Contracts I did not open.

Across the pages:

44. As an Agent, I want the Client Invoices side list unchanged: its rows, status badges, "Sent back" mark, order, address and accessible names, so that the move to a shared component changes nothing I use.
45. As an Agent or a Manager using a keyboard or a screen reader, I want every side list to be a named navigation landmark whose rows I reach with Tab, open with Enter, and hear as current when selected, as on Client Invoices, so that every page with a column works the same way.
46. As a Manager, an Agent or a Tester, I want a row's link to open in a new tab when I Cmd/Ctrl-click or middle-click it, so that I can compare two Carriers, Fleets or Contracts side by side.
47. As the team, I want `DESIGN.md` to describe the side list as a shared component with its users, the tabs and what they may switch, the dropdown rule, and the cut-with-"…" rule for Fleet names, so that the written rules stay true.
48. As the team, I want the goldens of every page this feature changes, at desktop and phone, in light and dark, to show the side lists, the tabs, an archived Carrier, a long Plan name and the Carrier on invoice lines, so that a regression on any of them is caught.

## Solution

### Prefactoring: debt paid on the way

- **"Two pills"** (`contract-fleet-view`, F20). "Add smartphone" and "Add SIM
  card" become `secondary` `sm` buttons in their tab's header. "New Contract"
  is now the page's one pill.
- **Stale contract on `basePostpaidSims`.** The `ClientInvoiceResponse`
  Javadoc and the `ClientInvoiceDetail` comment in the frontend types both say
  the breakdown is null from `SENT` onward. The service actually serves it
  whenever the base amount is per SIM, at any status, and leaves it out only
  for an invoice that holds a backfilled `BASE_AMOUNT` line. Both comments are
  corrected to say this. This feature edits both files anyway.
- **`ContractSideList`'s Contract-specific markup** is replaced by the shared
  side list (below). The two debts recorded on it ("Two looks for Sent back",
  "Name cut hard") move with the code and stay open.

### The side list, shared

A new shared module in the UI components: `SideList` (the column) and
`SideListLayout` (the page body around it). It generalises
`ContractSideList` and the Client Invoices view's two-column layout. Its look
and behaviour are the existing ones from `DESIGN.md`: 256px wide, sticky
under the top bar, scrolling on its own, plain clicks handled in place,
modified clicks falling through, the selected row scrolled into view on
arrival, `aria-current` with the soft-indigo tone.

```ts
type SideListItem = {
  id: string;
  name: string;         // line 1, left: one line, cut with "…", full name in `title`
  detail?: string;      // line 2, left, muted (Country on a name collision)
  badge?: ReactNode;    // line 1, right, never shrinks
  mark?: ReactNode;     // line 2, right — or line 1 when there is no badge
  muted?: boolean;      // name in ink-mute (an archived Carrier)
};

SideList props: {
  label: string;                    // the landmark's name and visible heading: "Clients", "Carriers", "Contracts"
  items: SideListItem[];            // already in column order
  selectedId: string;
  hrefFor: (id: string) => string;  // each row is a real link
  onSelect: (id: string) => void;   // plain click
}

SideListLayout props: {
  list: ReactNode | null;      // the SideList; null renders no column at any width
  header?: ReactNode;          // above the list at xl, above the dropdown below xl
  dropdown: ReactNode | null;  // shown below xl only
  children: ReactNode;         // the detail area
}
```

- **The accessible name** of each row is its DOM text in this order: name,
  detail, badge, mark. The grid places each part. This is exactly
  `ContractSideList`'s rule, so the Client Invoices rows read as they do today.
- **`SideListLayout`** is used with `SurfacePage`'s `bleed`. At `xl` it lays
  out the column flush to the rail and the detail area beside it, with the
  shell's padding and `max-w-6xl` cap, aligned left. Below `xl` it lays out
  the shell's padded, centred page: `header`, then `dropdown`, then the
  detail. The switch is done in CSS only, as today: both controls are
  rendered and one is hidden.
- **The dropdown** is the existing `ContractSwitcher`, given an optional
  `icon` prop: Contracts by default, Carrier for Carriers, Clients for the
  Manager's Clients. Its options come from the page. Client Invoices and
  Fleet keep their "Client — Country" labels. Carriers use the Carrier's
  name, with "Archived" as the meta line. The Manager's Clients use the
  Client's name.
- **Client Invoices on it.** `AgentClientInvoicesView` maps each Contract to a
  `SideListItem`: the name, the Country on a collision, the
  `clientInvoiceBadge` badge, and the "Sent back" mark. It then renders
  `SideListLayout`. `ContractSideList` is removed. The page is pixel-identical,
  and its component tests, its e2e spec and its goldens pass unedited (they
  move only for the Carrier text on SIM lines, below).

### Selection in the address

| Page | Parameter | Written by | Picking makes |
|---|---|---|---|
| Agent Client Invoices | `contract` (unchanged) | `history.replaceState` | no request |
| Agent Fleet, Tester Fleet | `contract` | `history.replaceState` | no request |
| Carriers (Agent, Manager) | `carrier` | `history.replaceState` | no request |
| Manager Contracts | `contract` | `router.replace` (server render) | one render that reads the picked Contract only |
| Fleet tabs (all three pages) | `tab=smartphones`, absent for SIM Cards | `history.replaceState` | no request |

- A value naming nothing the caller can see is ignored silently. There is no
  error, no redirect and no log, as on Client Invoices.
- No selection adds a history entry. A pick through the side list or the
  dropdown writes the same parameter.
- The Manager's Country change (`router.push` with `?country=`, as today)
  drops `carrier`, so the new Country opens on its first Carrier. It keeps
  `archived`.
- Picking a row scrolls the document back to the top when the detail area's
  top is above the viewport. This is the Client Invoices rule.
- A row's link never prefetches. On Manager Contracts a prefetch would render
  the page for that Contract, and that render get-or-creates its
  current-month draft.

### Tabs, shared

A new shared `Tabs` component in the UI components, plus a Fleet-specific
`FleetTabsCard` that replaces `FleetTableCard`. Its only users today are the
three Fleet views.

- **`Tabs`** is a WAI-ARIA tablist: `role="tablist"` with a label, tabs as
  `role="tab"` buttons with `aria-selected` and `aria-controls`, and one
  `role="tabpanel"` labelled by its tab. It uses a roving `tabindex`: Left and
  Right move between tabs and wrap, Home and End jump to the first and last
  tab. Activation is automatic on focus, because both panels' data is already
  on the page. Only the selected panel is rendered. Its look is the segmented
  strip `StatusFilterTabs` and the Review Queue already use (a `canvas-soft`
  track, the selected tab on `canvas`, `rounded-md`). Each tab carries a
  label and an optional count in `tnum` `ink-mute`.
- **`FleetTabsCard`** is a `Card` whose header row holds the tablist
  ("SIM Cards {n} | Smartphones {n}", with each tab's icon), the selected
  tab's action on the right (Manager only: "Add SIM card" or "Add
  smartphone"), and, in the body, that tab's table, or its existing empty
  state when the tab has none. The tablist's label is "Fleet".
- **The tab** is owned by each Fleet view. It is seeded from `tab`, kept when
  the Contract changes, and written to the address. SIM Cards is the default
  and the first tab.

### Fleet: the Agent and the Tester

- **The pages** keep their reads exactly: every Contract and both of its
  Fleets up front, plus the Agent's active Carriers. Each page gains a typed
  `searchParams` (`contract`, `tab`) and passes them to its view, unvalidated.
  They render with `bleed`.
- **`useContractScopedFleet`** gains the initial Contract id. It seeds from it
  when the id names one of the Contracts, and otherwise from the first
  Contract in column order. It also gains a `select(id)` that writes
  `contract` to the address.
- **Column order** is the Client Invoices one for the Agent: Client name, then
  Country, locale-aware and case-insensitive. The Contract Switcher gets the
  same order.
- **The Agent** gets `SideListLayout` with a "Clients" `SideList`: one row per
  Contract by Client name, the Country as `detail` only on a name collision,
  and no badge. Below `xl` the Contract Switcher shows. Then comes
  `FleetTabsCard`.
- **The Tester** with more than one Contract gets a "Contracts" `SideList`:
  one row per Contract by Country label, sorted by Country. Below `xl` the
  Contract Switcher shows. With one Contract the Tester gets no column
  (`list` and `dropdown` null), and today's static chip sits above the tabs at
  every width. "Submit request" stays at the right of the detail area's first
  row: beside the switcher or the chip, or alone at `xl`.

### Plan and Carrier names in the SIM Cards table

- `SimCardPlan` and `SimCardCarrier` cap the name at `max-w-48` (192px). Past
  that the name is cut with "…" (`truncate`), and the full name is its
  `title`. The "Archived" badge comes after the name and never shrinks.
- `SimCardPlanControl`'s display state uses the same capped name, with Edit
  beside it. Its editing state is unchanged.
- The rule applies wherever the table renders: the Agent's, the Tester's and
  the Manager's tables.

### Carriers: one side list page

`CarriersView` keeps its interface and dialogs, and gains the initial
`carrier` id. Both pages render with `bleed`.

- **The column** is a "Carriers" `SideList` holding every Carrier the catalog
  returns, active and archived, in the server's order: active first, then by
  name. That order puts the archived Carriers at the bottom. An archived row
  is `muted` and carries the neutral "Archived" badge.
- **`header`** (above the column at `xl`, above the dropdown below `xl`)
  holds the Manager's Country filter first, then "Add carrier" (the page's one
  pill) and "{n} active".
- **The detail area** is one `Card` for the selected Carrier:
  - a header with the name, Rename and Archive (or, when archived, the muted
    name, "Archived {date}" and the badge), and "Prices in {currency} · shared
    by every agent in {Country}";
  - the "Show archived" checkbox, which now counts and reveals only the
    selected Carrier's archived Topup Options and Postpaid Plans (still
    `?archived=1`);
  - its two offer lists side by side from `md` up, as today.

  An archived Carrier's lists show their entries with no Add, Edit or Archive.
- **Arrival**: `carrier` when it names a Carrier in the catalog, otherwise the
  first row.
- **After a mutation**: `router.refresh()`, as today. The address keeps
  `carrier`, so the selection survives. Archiving a Carrier keeps it selected,
  and it moves to the bottom, marked. `CarrierNameDialog`'s create path, given
  the created Carrier's id from the response, writes `carrier=<id>` before it
  refreshes.
- **Empty and failed**: no column and no dropdown. Today's "No carriers in
  {Country} yet" and "Couldn't load carriers" states fill the detail area. The
  header stays, so "Add carrier" stays reachable. The "Every carrier in
  {Country} is archived" state and its "turn on Show archived" copy are
  removed, because archived Carriers are now always listed.

### The Manager's Contracts: one page

`/manager/contracts` merges the list and the detail page. The contract
detail route becomes a redirect.

- **Reads.** The server page reads `/api/contracts`, `/api/clients` and
  `/api/agents` (the latter two for the dialog). It resolves the selected
  Contract: `contract` when it names one, otherwise the first Client by name,
  then that Client's first Agent by name. It then reads that Contract only:
  its Smartphones, SIM Cards and current-month Client Invoice, plus its
  Country's active Carriers. These are the reads today's detail page makes.
  Picking a Client or an Agent is a `router.replace` to
  `?contract=<id>` (keeping `tab`), wrapped in a transition. While the
  transition is pending, the detail area keeps its content with
  `aria-busy="true"`.
- **The column** is a "Clients" `SideList`: one row per Client that holds at
  least one Contract, by Client name, with no badge. Each row's `href` is
  `?contract=<that Client's first-by-Agent-name Contract>`. `header` is "New
  Contract", the existing `CreateContractDialog` trigger, relabelled.
- **Below `xl`**: `header` ("New Contract"), then a Clients dropdown (the
  Contract Switcher with the Clients icon), then the detail area.
- **The detail area**, top to bottom:
  1. a row with the Client's name as the area's `h2`, the **Agent** select
     (a native `select` labelled "Agent", as the Carriers Country filter is,
     listing that Client's Contracts by Agent name, sorted by name, always
     shown), and "{Country} · {currency}" muted;
  2. `InvoiceSummaryCard` for the current-month Client Invoice, unchanged,
     and absent if the read failed, as today;
  3. `FleetTabsCard` with the Manager's tables, corrections and the two
     create dialogs as tab actions.
- **The subtitle** stays "{n} in this tenant", counting Contracts. The
  `manager-dashboard` e2e still reads it.
- **New Contract.** On success the dialog reads the created Contract's id from
  the response and replaces the address with `?contract=<id>`. The new
  Contract is then selected, and its Client appears in the column.
- **`/manager/contracts/{id}`** becomes a server `redirect` to
  `/manager/contracts?contract={id}`. An unknown id then falls to the first
  Client, by the rule above. `ManagerContractsView`'s table, search box and
  "N of M" count, and the detail page's breadcrumb, are removed.
  `ManagerContractFleetView` is folded into the new view.
- **Empty**: no Contracts means no column and no dropdown. Today's "No
  contracts yet" state fills the detail area, under "New Contract".

### Client Invoice SIM lines: the Carrier

- **Backend.** `ClientInvoiceBaseSimLineResponse` gains `carrierName`
  (String). It is the SIM Card's Carrier's current name, or `null` for a SIM
  with none, and it is always serialised. `of(...)` reads it from the SIM
  Card's eager Carrier. Nothing else changes: the field is on every caller's
  response (Agent, Manager, Tester) and on every status that serves the
  breakdown. `toResponse` is already `@Transactional(readOnly = true)`, so the
  Carrier loads on the stored-line paths too.
- **Frontend type.** `ClientInvoiceBaseSimLine` gains
  `carrierName?: string | null`. It is optional only so that older fixtures
  stay valid, the same way `amount` is.
- **One formatter**, `simLineKind(line)`, used by both views:
  `"{carrierName} · Postpaid"`, or `"Postpaid"` when there is no Carrier.
  "Postpaid" is `SIM_CARD_FLAVOR_LABEL.POSTPAID`. Every line in that section
  is a Postpaid SIM.
- **Agent card** (both Agent invoice pages): in the SIM line's `what` area,
  after the number, a muted `text-label-sm` `font-normal` span. It sits on the
  number's line when there is room and wraps under the number when there is
  not, at `xl` and in the one-column rows alike. The line's name for Edit's
  description stays "SIM {number}".
- **Manager detail** (`client-invoice-detail-view`): the same span after the
  number, before the cancellation note. `BilledAmount` is untouched.
- An archived Carrier shows its name with no badge. On an invoice the
  Carrier's state today is not what the line bills.

### `DESIGN.md`

Each amendment lands in the commit of its visual change:

- **Layout (line 164).** "The one inner scroll container is the **side list**
  (below), on the pages that have one; the document still scrolls the area
  beside it."
- **Contract Switcher (line 222).** It stays "a dropdown — explicitly never a
  tab set — for Contract scoping". At `xl` and wider, the side list replaces
  it on Client Invoices and on Fleet (Agent, and Tester with several
  Contracts). Below `xl` it is also every side list's dropdown, with a
  per-page icon (Carriers, the Manager's Clients). Requests keeps it at every
  width.
- **Contract side list → Side list (lines 224-229).** It is now a shared
  component, with its users (Client Invoices, Fleet, Carriers, Manager
  Contracts) and each one's rows: Client Invoices as today; Fleet by Client
  name or, for the Tester, by Country; Carriers with archived ones muted, at
  the bottom, badged "Archived"; the Manager's Clients. The sentence "is not
  a shared shell component" is removed, and so is the Client-Invoices-only
  rationale. The reason given becomes: list-and-detail pages where seeing
  every item at once beats a dropdown. Width, column, row and selected-row
  bullets stay.
- **A new Tabs entry.** It describes the segmented tablist, the roving focus
  and the count on each tab. It switches between tables of one record (the
  Fleet's SIM Cards and Smartphones). It never scopes a Contract.
- **Do's (line 246).** "Do use a dropdown, never a tab set, when a screen
  needs Contract-scoping — at `xl` and wider, a side list instead on the pages
  that have one. Tabs switch between one Contract's tables, never between
  Contracts."
- **A Tables sentence.** In the Fleet's SIM Cards table, Plan and Carrier
  names are capped at 192px and cut with "…", with the full name in `title`.

## Design direction

The world is `DESIGN.md`'s Financial Control Room, with no token added and no
new colour. The design slot is not engaged. The side list and the segmented
tab strip already exist in this world, and this feature places them on four
more surfaces. The reference is the Agent's Client Invoices page as accepted
on 2026-10-15.

- **Carriers (Agent, Manager): Operate.** The column reads like Client
  Invoices: a quiet list of names, with archived rows muted at the foot of
  the column and their neutral pill badge. The detail card is today's Carrier
  row, given the whole width, with its two offer lists side by side.
- **Fleet (Agent, Tester): Operate.** One card per Contract. Its header *is*
  the tab strip, so no table title repeats the tab's label. The counts sit in
  `ink-mute` `tnum` beside each label. The selected tab is the strip's raised
  `canvas` segment. It is not indigo, because the side list's selected row
  already holds the current-selection indigo (the Reserved Indigo Rule).
- **Manager Contracts: Operate.** "New Contract" is the screen's one pill,
  above the column, where Client Invoices has nothing and Carriers has "Add
  carrier". The detail area reads top to bottom: whose Contract it is (the
  Client heading, the Agent select, Country and currency), what it bills this
  month (the summary card), and what it holds (the tabbed Fleet). The Fleet's
  add buttons are `secondary` `sm` in the card header.
- **Invoice SIM lines: Operate.** The number stays the strongest thing on the
  line. "Free · Postpaid" sits after it as a caption (`text-label-sm`,
  `ink-mute`) and never competes with the amount.
- **Truncation.** A cut name ends in "…". The cell keeps its height, and the
  badge after it stays whole.

## Constraints

- Inherits every constraint of the `remote-support-mvp`,
  `client-invoices-side-list` and `client-invoices-layout-fixes` specs, except
  where this spec states otherwise.
- **The breakpoint is `xl`, `min-width: 80rem` (1280px)**, for every side
  list. At any width exactly one of the column and the dropdown is displayed.
  No JavaScript media query is used.
- **Widths.** The column is 256px. At 1280px the detail area keeps 736px of
  content width (1280 − 240 rail − 256 − 48 padding), which is enough for the
  Fleet tables' 720px minimum.
- **No sideways page scroll** at 375, 390, 768, 1024, 1279 and 1440px on
  `/agent/carriers`, `/manager/carriers`, `/agent/fleet`, `/client/fleet` and
  `/manager/contracts`: `document.documentElement.scrollWidth` equals
  `window.innerWidth`. Fleet tables still scroll inside their own wrapper.
- **Reads.**
  - Agent Fleet, Tester Fleet and both Carriers pages make exactly today's
    reads. A pick makes none.
  - Manager Contracts reads the three lists plus the selected Contract's
    Fleet, invoice and Carriers, and never another Contract's. Opening it
    get-or-creates at most one current-month Client Invoice, the selected
    Contract's, which is what today's detail page does on view. No link on
    the page prefetches.
- **The address.** The parameters and writers are the ones in the `##
  Solution` table. A selection never adds a history entry. Unknown values are
  ignored silently.
- **Accessible names.**
  - Each side list is a `nav` named by its heading: "Clients", "Carriers" or
    "Contracts". Each row's link name is name, detail, badge, mark, in that
    order.
  - The Client Invoices links' names are unchanged.
  - The tablist is named "Fleet". The tabs are named "SIM Cards {n}" and
    "Smartphones {n}", and each panel is labelled by its tab.
- **API.** `carrierName` is additive. No field is removed or renamed. No
  migration and no schema change.
- **Tokens only.** New and changed lines use `text-label-sm` and `text-label`,
  never `text-[12px]` / `text-[13px]` (Frontend rule 8). Existing arbitrary
  sizes on lines this feature does not rewrite may stay. An arbitrary value
  carries its reason in a comment.
- **Goldens.** These goldens move, each in the commit of its change and with
  its `DESIGN.md` amendment:
  - `agent-carriers-*` and `manager-carriers-*`;
  - `agent-fleet-*` and `client-fleet-*`;
  - `manager-contract-fleet-*`, renamed `manager-contracts-*` with its path
    `/manager/contracts?contract=…`;
  - `agent-client-invoices-*`, for the Carrier text only.

  No other golden changes.
- e2e and visual runs use an isolated stack on ports other than
  3000/8080/5432 (`docs/agents/implementer-notes.md`). No test writes to the
  user's compose stack. The e2e suite runs on an idle machine, and a failure
  is never called a flake without a controlled comparison.

## Testing decisions

Tests assert external behaviour: the accessibility tree, text, the address,
the requests made, focus, and geometry in a real browser. They never assert
class names or component state. No new seam is added. Each behaviour is
tested at the highest seam that already exists and can see it.

1. **Backend: the existing `ClientInvoiceApiTest`** (MockMvc against
   Testcontainers PostgreSQL; prior art: its `basePostpaidSims` assertions,
   e.g. around line 245). It gains three cases:
   - a draft's SIM line carries `carrierName` "Verizon";
   - a sent invoice's stored SIM line carries it too;
   - a SIM with no Carrier carries `carrierName: null`, and the Tester's read
     carries the field.

   The other four suites that touch `basePostpaidSims` keep passing unedited.
2. **Component: Vitest and Testing Library**, with `stubFetch` and
   `mockRouter` from `tests/component`. Prior art:
   `client-invoices-view.test.tsx` and `sim-card-fleet-table.test.tsx`.
   - `client-invoices-view.test.tsx` passes **unedited**. That proves the
     move onto the shared side list. One case is added: a SIM line reads
     "Verizon · Postpaid", and a line with no Carrier reads "Postpaid".
   - `client-invoice-detail-view.test.tsx` gains the same pair.
   - `sim-card-fleet-table.test.tsx` gains two cases:
     - a 60-character Plan name and Carrier name are each rendered whole in
       their `title`;
     - the "Archived" badge still follows the name.
   - New view tests, at the view seam the existing tests use:
     - `carriers-view.test.tsx`: the "Carriers" landmark; archived rows last
       with "Archived" in their names; the first row current; a click
       showing that Carrier's lists with no fetch and writing `?carrier=`;
       an archived Carrier's lists with no buttons; "Show archived" counting
       the selected Carrier's archived entries; the empty and failed states
       with no landmark.
     - A fleet view test file, one `describe` per role:
       - Agent: the "Clients" landmark in order; `?contract=` honoured and
         written; the "Fleet" tablist with SIM Cards selected; ArrowRight
         moving to and selecting Smartphones, with focus on it; `?tab=`
         honoured and written; the tab kept across a Client pick.
       - Tester: two Contracts give the "Contracts" landmark by Country; one
         Contract gives no landmark and the chip.
     - A Manager contracts view test: the "Clients" landmark of Clients
       holding Contracts; the Agent select with one option still shown;
       picking an Agent calling `router.replace` with `?contract=`;
       "New Contract" present; the summary and tabs present.
3. **End to end: Playwright against the real backend, in the existing specs.**
   The default viewport is 1280 wide, so every Fleet, Carriers and Contracts
   step sees the side list.
   - **`helpers.ts`:**
     - a new `selectInSideList(page, list, rowName)` clicks a row inside the
       named landmark. `selectClientInvoiceContract` becomes a call to it.
     - a new `fleetTab(page, "SIM Cards" | "Smartphones")` returns the
       selected panel, selecting the tab first. `simCardsTable` reads the
       table inside the SIM Cards panel.
     - `createContractForClient` and `createUnrelatedContract` click "New
       Contract", scope the Client and Agent selects to the dialog (the page
       has its own "Agent" select now), and read the new id from `?contract=`.
     - `addSmartphone`, `addSimCard`, `addPostpaidSimCard` and
       `installSimCard` open `/manager/contracts?contract=<id>`, adding
       `&tab=smartphones` for phones.
     - `addPostpaidPlanToVerizon` selects Verizon in the "Carriers" landmark
       before using its "Add a postpaid plan to Verizon" button.
   - **Call sites:**
     - the `selectContractInSwitcher` calls on `/agent/fleet` and
       `/client/fleet`, in `fleet-management`, `agent-stock`,
       `sim-swap-moves`, `fee-logging-and-provisioning`,
       `return-client-owned-smartphones`, `manager-decides-return-disposition`
       and `manager-approves-requests`, move to `selectInSideList`. Calls on
       Requests pages stay.
     - the direct `goto("/manager/contracts/…")` calls in
       `client-invoice-submission-and-visibility`, `client-dashboard` and
       `fee-logging-and-provisioning` move to the new address.
     - `fleet-management.spec.ts`'s `a[href^="/manager/contracts/"]` selector
       becomes the side list row.
   - **New steps in existing specs.** Prior art:
     `client-invoices-side-list.spec.ts` (address, reload, 390px, no
     sideways scroll).
     - `carrier-catalog.spec.ts`:
       - archived Carrier at the bottom;
       - pick, address and reload;
       - a new Carrier selected after it is added;
       - the Manager's Country change opening the first Carrier;
       - 390px dropdown.
     - `fleet-management.spec.ts`:
       - Agent side list;
       - tabs by keyboard;
       - `?tab=` kept across Clients and reload;
       - a long Plan name's `title`, with the cell's `scrollWidth >
         clientWidth`;
       - Tester with two Contracts;
       - 390px.
     - `manager-entity-setup.spec.ts`:
       - one page;
       - first Agent by name;
       - the Agent select shown with one Agent;
       - a second Contract for the same Client switching by Agent;
       - the old `/manager/contracts/{id}` landing on `?contract={id}`;
       - no request for an unselected Contract's Fleet (request log);
       - 390px.
     - `client-invoice-submission-and-visibility.spec.ts`: the Agent's and
       the Manager's SIM line reading "Verizon · Postpaid".
   - `client-invoices-side-list.spec.ts` and `manager-dashboard.spec.ts` pass
     unedited.
4. **Visual goldens: the existing stubbed suite** (`tests/visual`). Prior art:
   the `agent-client-invoices` surface and its stub fixtures. `stub-backend.mjs`
   gains:
   - `carrierName` on `SIM_LINES`: two "Verizon" and one `null`;
   - a 60-character Postpaid Plan name on one fixture SIM Card;
   - a Manager current-month Client Invoice for the first Contract;
   - nothing new for Carriers, since Sprint is already archived.

   `surfaces.spec.ts`:
   - renames `manager-contract-fleet` to `manager-contracts` with the new
     path;
   - waits on the "Carriers" landmark for the Carriers surfaces;
   - keeps `manager-carriers` on `?archived=1` so archived offers stay in its
     golden.

   The goldens listed under Constraints are recaptured. Goldens are not in
   the `verify` gate (debt), so the merger runs the visual suite.

## Decisions taken

- **`fleet-side-list-and-tabs` touched two e2e reads its Regression did not name** (2026-10-16): the first Agent test's `Pixel 8` rows and the iPhone 15 serial test in `fleet-management.spec.ts` read Smartphone rows that now sit behind the Smartphones tab, so they gain a `fleetTab(page, "Smartphones")` step (and `fleetTab(page, "SIM Cards")` before the retire/reactivate step), assertions unchanged. Same reason as the Regression's own: only the selected tab's table is rendered. The two `/client/fleet` `selectContractInSwitcher` calls were dropped rather than moved, since a one-Contract Tester has no landmark and the call was a no-op.

- **The ticket cut's last R6 gap was closed by the orchestrator, not escalated** (2026-10-15). The critic's second pass failed only R6 on `fleet-side-list-and-tabs` (five more e2e lines that the tabs change) and noted `surfaces.spec.ts` missing from `manager-contracts-one-page`'s Regression. Its own `would_pass` text was copied into both tickets verbatim: a list of tests declared modified, no change to scope or criteria, so it is not a spec gap worth the human's time.

- **Spec approved by the human, 2026-10-15** (`docs/inbox/approve-ui-side-lists-and-tabs.md`, "ok"), accepting the seven choices listed there.

- **The side list becomes one shared component (`SideList` and
  `SideListLayout`), with `ContractSideList` removed and Client Invoices
  moved onto it.** Reason: five pages now share one look and one behaviour,
  and `DESIGN.md` asked for promotion "if a second page ever asks". The slot
  shape (name, detail, badge, mark) is exactly the existing row's grid, so
  Client Invoices stays pixel-identical.
- **Every side list's breakpoint is `xl`, and the dropdown below it is the
  existing `ContractSwitcher` with an `icon` prop.** Reason: it is the
  accepted Client Invoices breakpoint, and the human asked for "a dropdown, as
  on Client Invoices". Renaming the component for its non-Contract users is
  a cheap later change.
- **Selection is in the address on every side list page: `contract` (Fleet,
  Manager Contracts) or `carrier` (Carriers), written without a history
  entry.** Reason: a reload or a bookmark reopens the same item, as on Client
  Invoices, and the e2e helpers and goldens can deep-link.
- **On arrival, the first row is selected unless the address names one.**
  Reason: the page always opens on something, predictably. This is the
  accepted Client Invoices rule.
- **The Fleet's tab is in the address (`?tab=smartphones`; absent means SIM
  Cards) and is kept across Contract picks.** Reason: a reload keeps it, and
  going down every Client's SIM Cards is the common pass. Omitting the default
  keeps the addresses that already exist valid.
- **SIM Cards is the first tab and the default.** Reason: the human named the
  tabs in that order ("SIM Cards | Smartphones"). SIM Cards carry the billing
  (plans and fees).
- **Each tab shows its count, and the tab strip is the card's header, with no
  separate table title.** Reason: today's card headers show the counts. Moving
  them into the tabs keeps that information for the hidden table too, and
  avoids repeating "SIM Cards" twice.
- **The tabs follow the WAI-ARIA pattern with automatic activation, and only
  the selected panel is rendered.** Reason: both tables are already loaded, so
  activation costs nothing. One rendered table keeps the e2e `table` queries
  unambiguous.
- **`StatusFilterTabs` and the Review Queue filter are not migrated onto
  `Tabs`.** Reason: they filter one list and have no panels. Moving them would
  change two more pages and their tests for no user-visible gain.
- **The Tabs entry in `DESIGN.md` says tabs switch between one Contract's
  tables and never scope Contracts, and the "never a tab set" rule stays.**
  Reason: the rule is about Contract scoping, which these tabs do not do. The
  amendment says so instead of weakening it.
- **The Tester's side list rows are labelled by Country, under the heading
  "Contracts".** Reason: every row is the same Client, so its name would
  repeat down the column. The Country is what differs (a Client holds "one
  Contract per country/Agent", `CONTEXT.md`). The dropdown below `xl` keeps
  today's "Client — Country".
- **A Tester with one Contract keeps today's static chip above the tabs, at
  every width.** Reason: the human gave the Tester a side list only with
  several Contracts. The chip is how today's page names the Contract.
- **The Agent's Fleet rows match Client Invoices: the Client name, the Country
  on a collision, the same order, and no badge.** Reason: they are the same
  Contracts, so the two columns should read alike. The Fleet has no status to
  show.
- **Archived Carriers are always in the column, at the bottom, muted and
  badged "Archived". "Show archived" now governs only the selected Carrier's
  archived Topup Options and Postpaid Plans.** Reason: this is the human's
  rule taken literally ("Archived Carriers are in the list, marked
  'Archived', at the bottom"). The server's active-first order already puts
  them last. The toggle keeps its address (`?archived=1`) and its job for
  offers.
- **An archived Carrier's detail shows its offer lists read-only.** Reason:
  the human's "picking one shows its Postpaid Plans and Topup Options"
  applies to every row. The catalog already returns an archived Carrier's
  offers, and the existing rule (nothing under it can change) removes the
  actions.
- **The "Every carrier in {Country} is archived" empty state is removed.**
  Reason: with archived Carriers always listed, that Country shows its
  archived rows instead.
- **"Add carrier", "{n} active" and the Manager's Country filter sit above
  the column (`header`); "Show archived" sits in the selected Carrier's
  card.** Reason: the Country and "Add carrier" act on the list. The toggle
  now acts on one Carrier's offers. This mirrors "New Contract" above the
  Contracts column.
- **A Carrier just created is selected.** Reason: the next thing an Agent
  does is add its Plans and Options. The same applies to "New Contract".
- **The Manager's Country change opens that Country's first Carrier and keeps
  `?archived`.** Reason: a Carrier id from another Country can never match.
- **The Manager's Contracts page reads only the selected Contract and swaps it
  by a server render (`router.replace`), with no prefetch.** Reason: the
  Contract-scoped current-month invoice read get-or-creates a draft. Reading
  every Contract up front, as Fleet does, would create a draft for every
  Contract in the tenant whenever the page opened. A Manager tenant also holds
  many more Contracts than an Agent does.
- **The Clients column lists only Clients that hold a Contract.** Reason: it
  is the Contracts page. A Client with none has nothing to show, and "New
  Contract", directly above, lists every Client.
- **The Agent picker is a native `select` labelled "Agent", not the Contract
  Switcher.** Reason: the human wants it shown even with one Agent, and the
  Switcher collapses to a static chip at one. The Carriers Country filter is
  the native-select prior art.
- **The detail area opens with the Client's name as its heading, and the
  Country and currency beside the Agent select.** Reason: the page title is
  now "Contracts", and the detail page's title and subtitle carried that
  information. A heading also gives the area a name for screen readers.
- **The trigger is relabelled "New Contract", as the human wrote it. The
  dialog keeps "Add a contract" and its "Add contract" submit.** Reason: the
  quoted label is the human's. The dialog's form is out of scope.
- **The Contracts search box, the table and the "N of M" count are removed.
  The subtitle "{n} in this tenant" stays.** Reason: the human's layout has
  no search, and the side list shows every Client at once, as Client Invoices
  does with no search. The subtitle is still true, and
  `manager-dashboard.spec.ts` reads it.
- **`/manager/contracts/{id}` redirects (a server `redirect`, temporary) to
  `/manager/contracts?contract={id}`, and an unknown id opens the first
  Client.** Reason: bookmarks and old links keep working. A temporary
  redirect is free to undo. Ignoring unknown ids is the rule on every side
  list page.
- **"Add smartphone" and "Add SIM card" become `secondary` `sm` buttons in
  their tab's header.** Reason: this pays the recorded "Two pills" debt. "New
  Contract" is the page's one pill.
- **While a Manager pick is pending, the detail area keeps its content with
  `aria-busy`.** Reason: a blank flash between Contracts reads like a failure.
  The render is one server round trip.
- **The invoice line's Carrier is the SIM's current Carrier name, read live,
  with no snapshot and no "Archived" badge.** Reason: the line already reads
  the SIM's live number. A SIM's Carrier cannot be corrected (`CONTEXT.md`,
  Correction), so only a rename can move it. On an invoice, an archived
  Carrier's name is still the Carrier that billed.
- **`carrierName` goes to every caller, the Tester included.** Reason: a
  Tester already sees every SIM's Carrier on their Fleet, so nothing new is
  disclosed. A per-role field would add a branch for nothing.
- **The Carrier text sits after the number, in the line's `what` area, muted,
  wrapping under the number when there is no room.** Reason: the human asked
  for it "next to the number". Keeping it inside `what` lets the
  `client-invoices-layout-fixes` grid stay as it is at every width.
- **The Plan and Carrier cap is 192px (`max-w-48`), with the full name in
  `title`.** Reason: it fits catalog names like "Unlimited Welcome" whole and
  stops a long one widening the table. `title` is the recorded side-list
  precedent. The hover-only debt it carries ("Name cut hard") is already
  recorded.
- **Debt paid: "Two pills", and the stale `basePostpaidSims` comments. Debt
  left open: "Two looks for Sent back" and "Name cut hard" (moved with the
  side list code), and "iOS zoom on focus".** Reason: debt is paid when a
  feature touches its module and the fix stays inside it. The open ones would
  change the Client Invoices badge or every correction field.
- **Testing stays on the existing seams:**
  - `ClientInvoiceApiTest` for the field;
  - view-level component tests (new files for the three views that have none,
    at the seam `client-invoices-view.test.tsx` uses);
  - the existing e2e specs with new steps and moved helpers;
  - the existing stubbed goldens.

  Reason: each is the highest existing seam that sees its behaviour. Layout
  and the address need a real browser.
- **`track` is `full`, not `light`.** Reason: a light feature stays within the
  modules the accepted feature touched (`docs/contracts.md` §6). This one
  changes five pages across three roles, adds two shared components, changes a
  backend DTO and replaces a route. That is far beyond
  `client-invoices-layout-fixes`' Client Invoices card.
- **Four tickets: the invoice line first and independent, then Carriers (with
  the shared side list), then Fleet (with the shared tabs), then Manager
  Contracts.** Reason: Fleet needs the side list, and Manager Contracts needs
  both. The backend change stands alone. Each ticket fits one context window.
  Fewer tickets would put two shared components and three pages in one
  context. The PR is still one.

## Open questions

None

## Acceptance walkthrough

The `[agent]` steps run against an isolated stack with the backend on the
`demo` profile: its own fresh database, seeded by `DemoDataLoader`, never the
user's compose stack. Logins are in the root `README.md`. Jordan is the demo
Agent with several Contracts. Before step 1, the agent sets up the fixtures
through the Manager's own screens (or the e2e helpers):

- one of Jordan's Contracts gets a Postpaid SIM on Verizon;
- a Postpaid Plan with a 60-character name gets a SIM on it;
- one Carrier of Jordan's Country is archived;
- one Client gets a second Contract with another Agent, whose name sorts
  after Jordan's;
- a Tester of a Client with two Contracts, and a Tester of a Client with one.

The "Postpaid"-only line (a SIM with no Carrier) is shown on the stub
backend's Agent invoices session if the demo data has no pre-catalog SIM.

1. [agent] At 1440×900 and at 390×844, as Jordan, open `/agent/client-invoices` on the prepared draft, then its sent version's own page. As the Manager, open the same invoice's `/manager/invoices/client/{id}`. On each, show the Verizon SIM's line reads its number followed by "Verizon · Postpaid", and a SIM with no Carrier reads "Postpaid" alone. Show the Client Invoices side list, badges and "Sent back" mark unchanged. As the Tester, show their Invoices page unchanged. (stories: 1, 2, 3, 4, 5, 6, 44)
2. [agent] As Jordan and then as the Manager, open Carriers. Then (stories: 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 45, 46):
   - at 1440, show the "Carriers" landmark with the first row current, and the archived Carrier last, muted, its link name ending "Archived";
   - click another Carrier and show its Postpaid Plans and Topup Options with no request made and `?carrier=` in the address. Reload, and show it kept;
   - click the archived Carrier and show its archive date and lists with no buttons;
   - tick "Show archived" and show the selected Carrier's archived entries and their count;
   - add a Carrier and show it selected. Archive it and show it stays selected, moved to the bottom, marked;
   - as the Manager, change the Country and show its first Carrier selected;
   - at 390, show the Carrier dropdown above the card, no landmark, and `scrollWidth == innerWidth`.
3. [agent] As Jordan, open `/agent/fleet`. Then (stories: 17, 18, 19, 20, 21, 22, 23, 24, 25, 29, 30):
   - at 1440, show the "Clients" landmark in alphabetical order with the first row current, no Contract Switcher displayed, and the "Fleet" tablist with "SIM Cards {n}" selected;
   - press Tab into the tablist, then ArrowRight: show "Smartphones {n}" selected and focused, its panel shown, and `?tab=smartphones` in the address;
   - pick another Client and show the Smartphones tab kept, with no request made. Reload, and show the Client and the tab kept;
   - on the SIM Cards tab, correct a SIM's plan and change a status, and show both work;
   - show the 60-character Plan name cut with "…", its `title` the full name, and the table no wider than for a short name;
   - at 390, show the Contract Switcher above the tabs and `scrollWidth == innerWidth`;
   - sign in as an Agent with one Contract and show one current row.
4. [agent] As the Tester of the two-Contract Client, open `/client/fleet` at 1440. Show the "Contracts" landmark with one row per Contract by Country, the tabs, and "Submit request" at the top of the area. Pick the other Contract and show the address and tab behave as in step 3. At 390, show the switcher. As the Tester of the one-Contract Client, show no landmark, the Contract chip above the tabs, and the long Plan name cut with "…". (stories: 20, 21, 22, 26, 27, 28, 29)
5. [agent] As the Manager, open `/manager/contracts` at 1440 with the request log on. Then (stories: 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 45):
   - show the subtitle "{n} in this tenant", "New Contract" above the "Clients" landmark, and the first Client current;
   - show the Client's name as the detail heading, the "Agent" select showing the first Agent by name, even for a one-Agent Client, then the Country and currency, the invoice summary with "Open invoice", and the tabs;
   - show that only the selected Contract's Fleet and invoice were requested;
   - pick the Client with two Contracts. Change the Agent and show the other Contract's Fleet and summary, with `?contract=` updated;
   - on the Smartphones tab, add a smartphone, and on the SIM Cards tab, add a SIM card. Show each row appears and the buttons are not pills;
   - create a Contract with "New Contract" and show it selected;
   - open `/manager/contracts/{id}` and show it lands on `?contract={id}` with that Contract selected. Open `?contract=nonsense` and show the first Client;
   - at 390, show "New Contract", then the Client dropdown, then the Agent select, and `scrollWidth == innerWidth`;
   - as a fresh Manager tenant's view, or with the stub's empty session, show "No contracts yet" with no landmark.
6. [human] On your desktop, on your own stack, open Carriers, the Agent's Fleet and the Manager's Contracts. Confirm each reads like the Client Invoices page you accepted: archived Carriers at the bottom marked "Archived", "SIM Cards | Smartphones" switching tables, long Plan names cut with "…" and shown on hover, and on Contracts the Clients column with "New Contract" above, the Agent dropdown, the invoice summary and the tabbed Fleet. Open a Client Invoice and the Manager's invoice page, and confirm "Free · Postpaid" (or your Carrier) reads right next to each number. (stories: 1, 2, 7, 9, 17, 20, 29, 31, 32, 33, 35, 36)
7. [human] On your phone, on your stack, as an Agent, a Tester with several Contracts and the Manager, open the same pages. Confirm the dropdowns that replace the columns, and the tabs, are usable with a thumb, and that nothing but a table scrolls sideways. (stories: 15, 24, 26, 41)

## Execution order

Four tickets, landed as one PR. `invoice-sim-line-carrier` and
`carriers-side-list` are independent and run in parallel. The other two are
real gates: `fleet-side-list-and-tabs` needs the shared side list from
`carriers-side-list`, and `manager-contracts-one-page` needs the `Tabs` and
`FleetTabsCard` from `fleet-side-list-and-tabs`. Goldens: `invoice-sim-line-carrier`
recaptures `agent-client-invoices-*`; `carriers-side-list` recaptures the
Carriers goldens (and leaves `agent-client-invoices-*` untouched);
`fleet-side-list-and-tabs` recaptures `agent-fleet-*` and `client-fleet-*` only (the Manager page stays on `FleetTableCard` until the last ticket); `manager-contracts-one-page` renames and recaptures
`manager-contract-fleet-*` as `manager-contracts-*`, so it follows
`fleet-side-list-and-tabs`.

1. `invoice-sim-line-carrier`: `carrierName` on `ClientInvoiceBaseSimLineResponse`, the corrected `basePostpaidSims` Javadoc and frontend comment, the `simLineKind` formatter, and the Carrier and type on the Agent card's and the Manager detail view's SIM lines. Includes the `ClientInvoiceApiTest` cases, the two component cases, the `client-invoice-submission-and-visibility` step, the stub's `carrierName`, and the recaptured `agent-client-invoices-*` goldens. Labels: backend, frontend. No dependency. (stories: 1, 2, 3, 4, 5, 6)
2. `carriers-side-list`: the shared `SideList` and `SideListLayout`, Client Invoices moved onto them pixel-identically (its tests unedited), and `ContractSwitcher`'s `icon` prop. Then both Carriers pages on the side list. Includes the `DESIGN.md` Layout, Contract Switcher, Side list and Do's amendments; the declared **Side list** term; `carriers-view.test.tsx`; `selectInSideList`; the `carrier-catalog` steps and the `addPostpaidPlanToVerizon` helper; and the recaptured Carriers goldens. Labels: frontend. No dependency. (stories: 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 44, 45, 46, 47)
3. `fleet-side-list-and-tabs`: the shared `Tabs` and `FleetTabsCard`, the Agent's "Clients" and the Tester's "Contracts" side lists, `?contract=` and `?tab=`, the Plan and Carrier name cap, with the Manager detail page left on `FleetTableCard`. Includes the `DESIGN.md` Tabs and Tables amendments, the fleet view tests, `fleetTab`, the Fleet call sites on `selectInSideList`, the `fleet-management` steps, the stub's long Plan name, and the recaptured `agent-fleet-*`, `client-fleet-*` goldens. Labels: frontend. Depends on `carriers-side-list`. (stories: 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 48)
4. `manager-contracts-one-page`: `/manager/contracts` as one side-list page reading only the selected Contract, with the Agent select, the summary, the tabbed Fleet, the redirect of `/manager/contracts/{id}`, the Manager page moved onto `FleetTabsCard`, and the "Two pills" debt paid. Includes the Manager view test, the helpers and moved `goto` call sites, the `manager-entity-setup` steps, the stub's Manager invoice, and the renamed `manager-contracts-*` goldens. Labels: frontend. Depends on `fleet-side-list-and-tabs`. (stories: 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 48)
