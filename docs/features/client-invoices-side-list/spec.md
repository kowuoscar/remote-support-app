---
feature: client-invoices-side-list
epic: agent-client-invoices-side-list
status: approved
date: 2026-10-14
---

<!-- sdlc:template spec 1 -->

# Pick a Client's invoice from a side list

## Problem

An Agent prepares one Client Invoice per Contract each month. On the Client
Invoices page, the Contracts are hidden behind the Contract Switcher. It is a
dropdown labelled "Client — Country", and only the selected Contract's invoice
shows. To see which Clients they bill, or to move from one invoice to the next,
the Agent has to open the dropdown, read the list and pick again. At month end
they do this for every Contract they hold. The page never shows all of their
Clients at once.

The human raised this on 2026-10-14 while testing the page by hand, and
confirmed the shape with a desktop sketch
(`docs/roadmap/evidence/agent-client-invoices-side-list/desktop-sketch.png`).
On a desktop, the Agent's Clients should be in view the whole time, in a
column beside the navigation rail, and clicking one should swap the invoice in
place. On a phone the dropdown stays. Only this page changes.

Three more things come with it. The "Sent back to you" list moves to the top of
the invoice area. Each row in the column shows the status of that Contract's
invoice for the month. A Client with an invoice sent back from an earlier month
is also marked "Sent back", so the Agent sees from the column who needs
correcting.

This amends a written design rule. `DESIGN.md` says to "use a dropdown, never a
tab set" for Contract scoping, and names the Agent's Client Invoices page among
the dropdown's users. This spec carves this one page out of that rule, at
desktop widths only.

## Journeys

Advances `docs/roadmap/agent-client-invoices-side-list.md`, whose only feature
this is.

- **Bill the Client for the month** (`exists`, stays `exists`). What the journey
  does is unchanged: the Agent opens each Contract's Client Invoice for the
  month, attaches files, edits lines and sends. This feature changes only how
  the Agent reaches each Contract's invoice on a desktop. Its proof spec,
  `client-invoice-submission-and-visibility.spec.ts`, keeps passing, with its
  Contract-selection step moved to the side list.

## Goals / Non-goals

Goals:

- At desktop widths, the Agent's Client Invoices page shows a column of the
  Agent's Contracts, by Client name, between the navigation rail and the
  invoice. The column scrolls on its own and stays in view.
- Clicking a row swaps the invoice in place: no dropdown, no page load, no
  request to the backend.
- The selected Contract is kept in the page address, so a reload or a bookmark
  reopens the same Client.
- Each row shows the status of that Contract's invoice for the month, with the
  badge the invoice card already uses.
- The "Sent back to you" list sits at the top of the invoice area. A Client
  with a sent-back invoice, of any month, reads "Sent back" in the column, and
  never twice.
- At narrower widths, phones included, the page is today's page: the Contract
  Switcher above the invoice.
- `DESIGN.md` records the exception and the new side list, in the same commit
  as the change.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No change to any other page.** Requests, Fleet, the Tester's pages and the
  Agent's own invoice keep the Contract Switcher at every width (settled by the
  human on 2026-10-14). The side list is not made a shared shell component, and
  no other page gets the column "for consistency".
- **No change to the `ContractSwitcher` component** beyond the single-Contract
  chip's truncation (debt, `## Solution`). Its behaviour, focus handling and
  `aria-haspopup="listbox"` stay as they are. Bringing it under the Menu
  Returns Focus Rule is separate work.
- **No search, filter or grouping in the column.** No search box, no grouping by
  Country or status, no collapse. An Agent holds a handful of Contracts.
- **Nothing per row beyond the name, the Country on a collision, the month's
  status badge and the "Sent back" mark.** Rows show no total, no month, no
  line count and no new status: the badge is `clientInvoiceBadge`'s, with its
  existing labels and tones. A row whose invoice failed to load gets no error
  badge.
- **No lazy loading of invoices.** The page still reads every Contract's
  current-month invoice up front, as today. Selecting a Client fetches nothing.
  Changing that would change when a draft is created (get-or-create on first
  view, `CONTEXT.md`, Client Invoice), and this feature leaves that alone.
- **No backend or API change.** No new endpoint, no new field, no migration.
  The column orders and marks rows from data the page already reads.
- **No filtering of "Sent back to you" by the selected Client.** The list keeps
  showing every sent-back invoice of every Contract and month, whichever Client
  is selected. It moves; it does not change.
- **No new route per Contract.** The selection is a search parameter on
  `/agent/client-invoices`, not `/agent/client-invoices/contract/{id}`. The
  by-id route `/agent/client-invoices/{invoiceId}` is unchanged apart from its
  breadcrumb link.
- **No change to the page's handling of a failed Contracts read.** It still
  shows "No contracts yet", as today.
- **No change to the invoice card itself** (`AgentClientInvoiceCard`): its
  sections, actions and phone layout (debt: columns hidden at 375px) stay as
  they are.
- **No inner scroll container for the invoice.** The document keeps scrolling
  the page. Only the column scrolls on its own.

## User stories

1. As an Agent on a desktop screen, I want my Clients listed in a column between the navigation rail and the invoice on the Client Invoices page, so that I see every Client I bill without opening anything.
2. As an Agent, I want one row per Contract, shown by its Client's name, so that each row is one invoice I owe this month.
3. As an Agent with two Contracts for Clients of the same name, I want those rows to also show the Contract's Country, so that I can tell them apart. Every other row shows the name alone.
4. As an Agent, I want the rows in alphabetical order by Client name, so that a Client is always in the same place.
5. As an Agent, I want clicking a row to show that Contract's Client Invoice for the month in the wide area, in place, with no page load, so that I can move from invoice to invoice quickly.
6. As an Agent, I want the selected row marked, so that I always know whose invoice I am reading.
7. As an Agent with many Clients, I want the column to scroll on its own and stay in view while I scroll down a long invoice, so that I can switch Client from anywhere on the page.
8. As an Agent, I want the page to return to the top of the invoice area when I pick another Client, so that I read the new invoice from its beginning.
9. As an Agent, I want the first Client in the column selected when I open the page, so that the page always opens on an invoice.
10. As an Agent, I want the selected Client kept in the page address, so that a reload or a bookmark reopens that Client's invoice.
11. As an Agent, I want an address naming a Contract that is not mine, or no longer exists, to open on the first Client with no error, so that a stale link never blocks the page.
12. As an Agent, I want the "Client Invoices" breadcrumb on a single invoice's page to bring me back with that invoice's Client selected, so that after correcting a sent-back invoice I return to where I was.
13. As an Agent, I want the "Sent back to you" list at the top of the invoice area, above the selected invoice, whichever Client is selected, so that work the Manager returned is the first thing I see.
14. As an Agent, I want a Client with an invoice sent back from an earlier month marked "Sent back" in its row, beside the badge of this month's invoice, and a Client whose invoice for this month was sent back to read "Sent back" once, not twice, so that I see from the column who needs correcting.
15. As an Agent, I want "Sent back" gone from the row once I resend the invoice, its badge then reading "Awaiting approval" when it was this month's, so that the column shows only what is still waiting on me.
16. As an Agent with a single Contract, I want the column to show its one row, selected, so that the page keeps the same shape when a second Contract arrives.
17. As an Agent with no Contracts, I want today's "No contracts yet" state across the page, with no empty column, so that nothing suggests a missing list.
18. As an Agent, I want a Client whose invoice couldn't be loaded to stay in the column, with no status badge, and the invoice area saying it couldn't load it, so that one failure never hides my other Clients and never shows a status the page doesn't know.
19. As an Agent on a phone, or in a window narrower than a desktop, I want today's Contract Switcher above the invoice, so that the invoice keeps the whole width.
20. As an Agent using the Contract Switcher on this page, I want my choice kept in the page address too, so that a reload keeps my Client on any screen.
21. As an Agent using a keyboard or a screen reader, I want to reach every row with Tab, open it with Enter, and hear which row is current, so that I can switch Client without a mouse.
22. As an Agent, I want a long Client name to wrap to at most two lines in its row, with the full name on hover, so that it never widens the column or hides the status badge or the mark.
23. As an Agent or a Tester, I want Requests, Fleet and the Tester's pages unchanged, with their Contract Switcher, so that only the page that asked for it changes.
24. As an Agent, I want each row of the "Sent back to you" table to show its full "Client — Country" on hover, and its Open link to name the Country, so that a long Client name never hides which Contract it is.
25. As an Agent with one Contract on a phone, I want a long Client name in the Contract chip to be truncated, so that the page never scrolls sideways.
26. As the team, I want `DESIGN.md` to say where and why the side list replaces the dropdown, so that the written rule stays true and the next page does not copy the exception by accident.
27. As the team, I want visual goldens of this page at desktop and phone widths, so that a change to its two layouts is caught.
28. As an Agent, I want each row to show the status of that Contract's Client Invoice for the month (Draft, Awaiting approval, Approved or Sent back), with the badge the invoice itself shows, so that I see from the column how far my month's billing has got without opening each invoice.

## Solution

### The layout, by width

```
width >= 1280px (xl)                                  width < 1280px
+------+-----------------------------------------+    +------------------------+
| rail | top bar (sticky, 56px)                  |    | top bar (rail: drawer  |
|      +-----------+-----------------------------+    |  below md, as today)   |
|      | Clients   | Sent back to you (if any)   |    +------------------------+
|      | (256px,   | <selected invoice>          |    | Sent back to you       |
|      |  sticky,  |                             |    | [Contract Switcher v]  |
|      |  own      |                             |    | <selected invoice>     |
|      |  scroll)  |  document scrolls this area |    |                        |
+------+-----------+-----------------------------+    +------------------------+
```

- **At `xl` (1280px) and wider**, the page body is two columns. The **side
  list** is 256px wide and sits flush against the rail. It is sticky directly
  under the 56px top bar, as tall as the viewport minus the top bar, and
  scrolls vertically on its own. The **invoice area** takes the rest of the
  width. It holds the "Sent back to you" list when there is one, then the
  selected Contract's invoice. Its content is capped at the shell's existing
  `max-w-6xl` and aligned left against the column. The document still scrolls
  the page, so the rail, the top bar and the side list stay in view while the
  invoice scrolls (`DESIGN.md`, Layout).
- **Below `xl`**, the page is today's page: the "Sent back to you" list, then
  the Contract Switcher, then the invoice, at the shell's usual padding.
- **The switch is CSS only.** Both the side list and the Contract Switcher are
  rendered. The side list is hidden below `xl` and the switcher from `xl` up.
  Only one of them is ever displayed, and so only one is in the accessibility
  tree. No JavaScript media query, so the server render is already right and
  nothing flashes on hydration.

### The side list

A new client component beside the Client Invoices view, `ContractSideList`. It
is presentational and owns no selection state.

```ts
type SideListContract = {
  id: string;            // Contract id
  clientName: string;
  countryLabel: string;  // "Kenya", from countryLabel(country)
  status: { label: string; tone: Tone } | null;
                         // clientInvoiceBadge(current-month invoice);
                         // null when that invoice failed to load
  sentBackMark: boolean; // the extra mark, derived by the view, see "Sent back" below
};

ContractSideList props: {
  contracts: SideListContract[];   // already in column order
  selectedId: string;
  onSelect: (id: string) => void;
}
```

- **Shape.** A `nav` landmark labelled "Clients", with a small visible "Clients"
  heading, and a list with one link per Contract. Each link's `href` is
  `/agent/client-invoices?contract=<id>`. A plain click is intercepted and
  calls `onSelect`. A modified click (Cmd/Ctrl, middle button) falls through,
  so "open in a new tab" works.
- **A row** has two parts, stacked. First the Client name, across the row's
  full width, wrapped to at most two lines with the full name in `title`. Then
  one meta line: the Contract's Country, muted, only when two of the Agent's
  Contracts share a Client name; then the status badge; then, when
  `sentBackMark` is set, the warning-toned "Sent back" badge. The meta line
  wraps rather than widening the column. A row whose `status` is `null` shows
  no status badge. The link's accessible name is its text: name, Country when
  shown, status label when shown, and "Sent back" when marked.
- **The selected row** carries `aria-current="true"` and the soft-indigo tone
  used for the rail's active item and the switcher's selected option.
- **On mount**, the selected row is scrolled into view inside the column
  (`block: "nearest"`), so an address naming a row far down still shows it.

### Selection

`AgentClientInvoicesView` owns the selection, as today, and gains:

- **Column order**, computed once in the view: Contracts sorted by Client name,
  then by Country label, using a locale-aware, case-insensitive comparison. The
  Contract Switcher gets the same order, so the phone and the desktop list the
  Clients alike.
- **Initial selection**: the page's `contract` search parameter when it names
  one of the Agent's Contracts. Otherwise the first Contract in column order.
- **Selecting** a Contract, from the side list or the switcher, sets the state,
  writes `?contract=<id>` with `window.history.replaceState`, and scrolls the
  document to the top of the page when the invoice area's top is above the
  viewport. `replaceState` integrates with the App Router's `useSearchParams`,
  per this Next.js version's own guide (*Linking and navigating*, "Native
  History API"). It adds no history entry and triggers no server render and no
  backend request: every invoice is already on the page.
- **After a mutation**, a control on the card calls `router.refresh()`, as
  today. The refresh re-renders the current address, which already carries
  `?contract=`, so the selection survives. A send or a resend updates the
  row's badge, and clears its "Sent back" and the list entry, with no special
  case.

### The page

`app/agent/client-invoices/page.tsx` keeps its reads exactly as today. It gains
a typed `searchParams` (`{ contract?: string | string[] }`), parsed once at the
top: the first value, or none (Frontend rule 14). It passes that value to the
view as `initialContractId`, unvalidated. The view checks it against the
Contracts.

It now passes each Contract to the view as `{ id, clientName, countryLabel,
currency }`. The view derives the switcher's existing `ContractOption`
(`label` "Client — Country", `meta` Country) and the side list's rows from it,
so neither component's input is a reformatted label.

### "Sent back"

- **The list.** `SentBackSection` renders first in the invoice area at every
  width, as it already renders first today. It is unfiltered.
- **The row's status.** Each row's `status` is `clientInvoiceBadge` of that
  Contract's current-month invoice, unchanged: Draft (neutral), Awaiting
  approval (warning), Approved (success), or Sent back (warning) for a `DRAFT`
  with a `sentBackAt`. A Contract whose invoice failed to load has `status`
  `null`.
- **The extra mark.** A Contract gets `sentBackMark` when the "Sent back to
  you" list holds an invoice of that Contract, of any billing month (answered
  by the human, 2026-10-14), **and** its row's status does not already read
  "Sent back". So "Sent back" is never shown twice on a row: a Contract whose
  own invoice for this month was sent back shows it once, as its status; one
  with an earlier month's invoice sent back shows its month's status and the
  mark. A failed-load row with a sent-back invoice in the list shows the mark
  alone. If the sent-back read fails, a this-month sent-back draft still reads
  "Sent back" through its own badge; only earlier-month marks are lost, as the
  list itself is.
- **Debt paid while moving it** (`docs/tech-debt.md`, `client-invoices-view.tsx`,
  "Long Contract names lose the country"): the Contract cell gets the full
  "Client — Country" as its `title`, and the Open link's accessible name
  becomes "Open <Client> — <Country>, <Month YYYY>".

### The shell

`SurfacePage` gains one optional boolean prop, `bleed`. With it, `main` renders
its children with no padding and no `max-w-6xl` wrapper, and the page lays out
its own body. Every other page omits it and renders exactly as today, so no
other golden moves. The Client Invoices view, given `bleed`, applies the shell's
usual padding and width cap itself below `xl`, so the narrow layout is
pixel-identical to today's. At `xl` it applies them to the invoice area only.

### The single invoice page

The by-id page (`/agent/client-invoices/{invoiceId}`) changes in two small ways:

- Its "Client Invoices" breadcrumb links to
  `/agent/client-invoices?contract=<invoice.contractId>`. The not-found state's
  links stay plain `/agent/client-invoices`.
- **Debt paid** (`docs/tech-debt.md`, `[invoiceId]/page.tsx`, "Silent degraded
  title"): its Contracts read moves from `backendFetchList` to
  `backendFetchJsonOrNull`, so a failure is logged. The title still falls back
  to "Client Invoice".

### Copy

- The column heading and its landmark: "Clients".
- The row's status: `clientInvoiceBadge`'s existing labels, "Draft",
  "Awaiting approval", "Approved" and "Sent back". No new label.
- The extra mark: "Sent back", the label `clientInvoiceBadge` already uses, in
  the same warning tone.
- The invoice area's load failure keeps its title "Couldn't load this Contract's
  Client Invoice". Its description becomes "Pick another Client or reload the
  page to try again." (was "Switch contracts or…"), which reads right beside
  both the column and the dropdown.

### The Contract Switcher's chip

**Debt paid** (`docs/tech-debt.md`, `contract-switcher.tsx`, "Single-contract
label can overflow"): the single-Contract chip gets `max-w-full` and truncates
its label, as the multi-Contract button already does. On this page the chip is
the phone layout's, and the change is the same on every page that shows it.

### `DESIGN.md`

Amended in the same commit as the layout (`docs/agents/frontend.md`, "Drift vs
evolution"):

- **Do's**, the dropdown rule (line 238): "Do use a dropdown, never a tab set,
  when a screen needs Contract-scoping (Agent, Client with multiple Contracts) —
  except the Agent's Client Invoices page at `xl` and wider, which lists the
  Contracts in a Contract side list (client-invoices-side-list spec)."
- **Contract Switcher**: Client Invoices drops out of the list of pages it
  scopes at `xl` and wider, with a pointer to the side list.
- **A new "Contract side list" component entry**: where it is used and why
  there only, its width, its sticky own-scroll column, its row rhythm and
  stacked layout (name, then a meta line), the selected tone, the month's
  status badge on every row (none when the invoice failed to load), the extra
  "Sent back" mark for an earlier month's sent-back invoice and the rule that
  "Sent back" never shows twice, the Country-on-collision rule, and that it is
  flat with no shadow.
- **Layout**: the paragraph saying the document scrolls gains one sentence: the
  side list is the one inner scroll container, and the document still scrolls
  the invoice area.

## Design direction

The surface is **Operate**: the Agent completes a monthly task, one invoice
after another. The page keeps `DESIGN.md`'s Financial Control Room world, with
no token added.

- **The side list reads as part of the page, not as a second rail.** It sits
  on `canvas`, not the rail's `canvas-soft`, with one `hairline` right border
  and no shadow (the Flat-At-Rest Rule). The rail keeps its own tone and
  hairline, so the two strips stay distinct.
- **The heading** "Clients" uses the label style of table column headers
  (`text-label`, `ink-mute`), set at the rail's item inset.
- **Rows** follow the rail's item rhythm: `px-3 py-2`, the 8px control radius,
  `text-sm font-medium`, `ink-secondary` at rest, `canvas-soft` on hover. The
  selected row takes the soft-indigo tone (`primary-soft-bg` /
  `primary-soft-text`), the same "current selection" role the Reserved Indigo
  Rule already allots to the rail and the switcher.
- **Row layout in 256px.** The name takes the row's full width (256px less the
  `px-3` inset, about 232px) and wraps to at most two lines, clamped, so a long
  name never pushes a badge out of the column. Below it, a meta line with a
  small gap: the Country (`text-label-sm`, `ink-mute`) when shown, then the
  badges. The meta line wraps if a Country and two badges don't fit. A row is
  therefore two to four lines tall, and rows keep their `py-2` rhythm whatever
  their height.
- **The status badge and the "Sent back" mark** are the existing `Badge`, with
  `clientInvoiceBadge`'s tones: neutral Draft, warning Awaiting approval,
  success Approved, warning Sent back. They are status badges, so their pill
  radius is allowed (the Pill-Is-Primary Rule). "Awaiting approval" and "Sent
  back" share the warning tone, as they do on the invoice card; their words
  tell them apart. On the selected row the badges keep their own tones over
  the soft-indigo fill.
- **Focus** uses the global focus ring on each link, as every control does.

Design review uses the sketch as its reference. Walkthrough step 15 has the
human judge it at their own screen. The `design` slot does not need to be
engaged: the world is set, and this is one new component inside it.

## Constraints

- Inherits every constraint in the `remote-support-mvp` spec. No backend, API,
  schema or migration change.
- **The breakpoint is `xl`, 1280px.** At 1280px and wider the side list shows
  and the Contract Switcher is hidden. Below 1280px the reverse. At any width
  exactly one of the two is displayed, so exactly one is in the accessibility
  tree.
- **Widths.** The side list is 256px. At 1280px the invoice area keeps at least
  720px of content width (1280 − 240 rail − 256 list − 2 × 24px padding = 736),
  the width at which the invoice's tables stop scrolling sideways (`DESIGN.md`,
  Layout: `min-w-[720px]`). The invoice area's content is capped at
  `max-w-6xl`.
- **Scrolling.** The side list is sticky under the 56px top bar and is the
  viewport's height minus the top bar, with its own vertical overflow. An
  arbitrary height value carries a comment saying why it isn't a token
  (Frontend rule 8). The document remains the scroller of the page. The rail
  and the top bar are unchanged.
- **The address.** The parameter is `contract`, its value a Contract id. It is
  written with `history.replaceState` only, never `router.push` or
  `router.replace`, so a selection adds no history entry and makes no request.
  A value that names none of the caller's Contracts is ignored, with no error,
  no redirect and no log.
- **Reads.** The page makes the same backend reads as today, no more, and
  opening it creates exactly what it creates today: one current-month draft per
  Contract on first view. Selecting a Client makes no request.
- **Scope.** Only `/agent/client-invoices` and its by-id page's breadcrumb
  change in behaviour. `ContractSwitcher`'s only change is the single-Contract
  chip's truncation. `SurfacePage`'s `bleed` is opt-in, and no other page
  passes it.
- New and changed lines use the `@theme` tokens, `text-label-sm` and
  `text-label`, never `text-[12px]` / `text-[13px]` (Frontend rule 8).
- Every visual change lands in the same commit as its `DESIGN.md` amendment
  (`docs/agents/issue-tracker.md`, Rule 7).
- e2e and visual runs use an isolated stack on ports other than
  3000/8080/5432, per `docs/agents/implementer-notes.md`. No test writes to the
  user's compose stack. No e2e step rewrites a `billing_month`.

## Testing decisions

Tests assert external behaviour only: the rendered accessibility tree, the
address, the requests made, and what a user sees. They never assert component
state or which function was called.

1. **Component: the existing `client-invoices-view.test.tsx`.** Vitest and
   Testing Library, with `stubFetch` and `mockRouter` from `tests/component`.
   It is the prior art and the highest seam that can render the view with
   chosen data. jsdom applies no media queries, so both the side list and the
   switcher are in its DOM. Tests scope to the "Clients" navigation landmark or
   to the switcher. The cases:
   - the "Clients" landmark lists one link per Contract, by Client name, in
     alphabetical order whatever order the props arrive in. The switcher's
     options are in the same order;
   - the Country shows only on rows whose Client name is shared;
   - the first row is `aria-current` with no `initialContractId`. The named one
     is current with a valid id. The first is current again with an unknown id;
   - clicking a row shows that Contract's invoice, sets `aria-current`, writes
     `?contract=<id>` to `window.location`, and makes no fetch;
   - choosing in the switcher writes the same parameter;
   - each row shows `clientInvoiceBadge`'s label for its current-month invoice:
     Draft, Awaiting approval, Approved, Sent back;
   - a Contract with a sent-back invoice of an earlier month shows its month's
     status and the "Sent back" mark. One whose current invoice is a sent-back
     draft, listed in "Sent back to you", shows "Sent back" exactly once. No
     other row shows "Sent back";
   - with the sent-back read failed, a sent-back draft of this month still
     reads "Sent back" once, and no row carries the extra mark;
   - the "Sent back to you" region comes before the invoice in document order,
     whichever row is selected;
   - a Contract whose invoice is `null` stays listed with no status badge, and
     selecting it shows "Couldn't load this Contract's Client Invoice" with the
     new description;
   - no Contracts render "No contracts yet" and no "Clients" landmark;
   - a sent-back table row's Contract cell carries the full "Client — Country"
     as its title, and its Open link's name includes the Country;
   - `AgentClientInvoicePageView`'s breadcrumb links to
     `?contract=<contractId>`.
2. **End to end: Playwright against the real backend.** The default e2e
   viewport is 1280 wide, so the existing specs that open
   `/agent/client-invoices` see the side list, not the switcher. Their
   `selectContractInSwitcher` call on that page will fail and must move.
   - **A new helper in `tests/e2e/helpers.ts`**, `selectClientInvoiceContract(page,
     clientName)`, clicks the link named by the Client within the "Clients"
     landmark. It replaces `selectContractInSwitcher` right after every
     `goto("/agent/client-invoices")`: `client-invoice-generation`,
     `client-invoice-submission-and-visibility`, `edit-client-invoice-lines`,
     `send-a-client-invoice-back`, `cancelled-sim-billed-through-its-month`,
     `manager-invoice-review-queue`, `manager-dashboard`, `client-dashboard` and
     `topup-fee-from-option`. Calls on other pages stay as they are.
     `selectContractInSwitcher` is unchanged, and stays the phone path.
   - **A new journey spec, `client-invoices-side-list.spec.ts`.** Prior art:
     `send-a-client-invoice-back.spec.ts` for the send-back flow and its
     `setViewportSize` switches, and `helpers.ts` for creating Contracts. It
     creates two Contracts for the seeded Agent and shows:
     - both rows, in order;
     - a click swapping the invoice, with no navigation request;
     - `?contract=` in the address, and the same Client after a reload;
     - an unknown `?contract=` opening the first row;
     - each row's status badge, "Draft" on arrival;
     - a send turning that row's badge to "Awaiting approval", then a
       send-back by the Manager turning it to "Sent back", shown once, with the
       list above the invoice;
     - Open, then the breadcrumb, returning with that Client selected;
     - a resend turning the badge back to "Awaiting approval", with no
       "Sent back" left on the row;
     - at 390px, no "Clients" landmark, the switcher present and working, and no
       sideways scroll.
3. **Visual goldens: the existing backend-stubbed suite.** A new
   `agent-client-invoices` surface in `tests/visual/surfaces.spec.ts`, on
   `visual-agent-session`, at its desktop (1440) and mobile (390) breakpoints,
   light and dark. Prior art: the `agent-carriers` and `agent-stock` surfaces.
   `stub-backend.mjs` gains, for the Agent session:
   - `/api/contracts`: four Contracts, two of them sharing a Client name;
   - each Contract's `/client-invoice`: one sent-back draft of this month, one
     `SENT` (Awaiting approval) invoice, one `APPROVED`, one plain `DRAFT`, so
     every badge tone is in the golden;
   - `/api/client-invoices/sent-back`: that sent-back draft, and an earlier
     month's invoice of the `SENT` Contract, whose row then shows "Awaiting
     approval" and the "Sent back" mark.

   One non-golden test points one Contract's invoice at `500` and asserts the
   row has no status badge and the "Couldn't load" state. These are new goldens, and no existing golden may
   change.
4. **No new seam.** Each behaviour is tested at the highest seam that already
   exists and can see it.

## Decisions taken

- **The breakpoint is `xl` (1280px), not `md` or `lg`** (answered by the
  human, 2026-10-14). Reason: below 1280 the rail and a 256px column leave the
  invoice under 720px, and the invoice's tables would scroll sideways. A tablet
  or a narrow desktop window therefore gets the dropdown, as the phone does.
  The alternative, 1024px (`lg`), was declined. Walkthrough step 15 has the
  human check it at their own widths.
- **A Client is marked "Sent back" in the column whatever the month of its
  sent-back invoice** (answered by the human, 2026-10-14). Reason: a Manager
  usually reviews after the month has closed, so most sent-back invoices are
  from the month before; "this month only" would leave the column blank exactly
  when the Agent has corrections to make. The list at the top of the invoice
  area stays the way to open such an invoice.
- **Every row shows the month's invoice status, with `clientInvoiceBadge`**
  (answered by the human, 2026-10-14; the human chose this over the drafted
  "only Sent back"). The extra "Sent back" mark appears in addition when the
  Contract has a sent-back invoice the row's own badge does not already show,
  and "Sent back" is never shown twice on a row.
- **A row whose invoice failed to load shows no status badge.** Reason: the
  page doesn't know that invoice's status, and an error badge in the column
  would add a fifth label for a rare state the invoice area already explains
  when the row is selected. The extra mark still shows if the sent-back list
  holds one of that Contract's invoices, since that is known.
- **The extra mark is derived as "in the sent-back list and the row's badge
  isn't already Sent back", not as "earlier month".** Reason: it gives the
  human's rule (never twice; earlier months marked) from one comparison, and it
  also marks a failed-load row whose this-month invoice is in the list.
- **A row stacks the name above a meta line holding the Country and the
  badges.** Reason: in 256px, a name beside an "Awaiting approval" badge would
  keep barely 100px; stacked, the name keeps the full width and the badges
  never squeeze it.
- **The column is ordered alphabetically by Client name, then Country.**
  Reason: a stable order keeps every Client in the same place from day to day.
  Sent-back invoices are already surfaced twice, by "Sent back" in the row and by the list
  at the top of the invoice area, so they need no reordering. The switcher gets
  the same order so the two layouts agree.
- **The first Client in the column is selected on arrival, unless the address
  names one.** Reason: the page should always open on an invoice, and
  predictably. "First sent back" would make the landing row move from week to
  week, while the sent-back list is already the first thing in the invoice
  area.
- **The selection is in the address, as `?contract=<contractId>`, written with
  `history.replaceState`.** Reason: a reload, a bookmark and the by-id page's
  breadcrumb can then return to a Client. `replaceState` adds no history entry
  per click, and makes no server render and no request. Manager pages already
  carry state in search parameters, and the Carriers page uses `replace` for a
  toggle.
- **An unknown or foreign `contract` value opens the first Client silently.**
  Reason: the backend already scopes `/api/contracts` to the caller, so a
  foreign id is simply absent. A stale link should not cost the Agent an error
  page.
- **The rows are links to `?contract=<id>`, with plain clicks handled in place.**
  Reason: the address changes, so a link is the honest role, and "open in a new
  tab" keeps working. Intercepting the plain click keeps the swap free of any
  round trip.
- **The Country shows on a row only when two of the Agent's Contracts share a
  Client name.** Reason: this is the human's "Country when needed". An Agent's
  Contracts are in their own country, so a Country on every row would repeat
  one word down the column.
- **The column shows even for a single Contract.** Reason: the page keeps one
  shape on a desktop, and the one row says whose invoice it is, as the static
  chip does on a phone today.
- **No column with no Contracts.** Reason: today's full-width "No contracts
  yet" state already says it all, and an empty column would suggest a failed
  read.
- **The side list and the switcher are both rendered and swapped by CSS.**
  Reason: the page is server-rendered, so a JavaScript media query would flash
  the wrong control on hydration. A `display: none` control leaves the
  accessibility tree, so only one is ever announced.
- **`SurfacePage` gains an opt-in `bleed` prop; the view lays out its own
  body.** Reason: the column must sit flush to the rail, outside the shell's
  padded, centred `max-w-6xl` wrapper. One opt-in prop leaves every other page
  and golden untouched. A full shared split layout would be built for one user.
- **The invoice area is capped at `max-w-6xl` and aligned left against the
  column.** Reason: centring it, as the shell does, would open a gap between
  the column and the invoice on wide screens, against the sketch's "next to".
  The cap keeps lines readable.
- **Picking a Client scrolls the document back to the top when the invoice
  area's top is out of view.** Reason: the column is sticky, so a click can
  come from deep in a long invoice, and the new invoice should be read from its
  start. The column's own scroll position is kept.
- **The selected row is scrolled into view in the column on arrival.** Reason:
  an address naming a row far down should still show which row is selected.
- **`ContractSideList` is a new component beside the Client Invoices view, not
  in `components/ui`.** Reason: it has one user, and the human scoped the
  change to this page. It can be promoted if a second page ever asks.
- **The row's status and the extra mark reuse `clientInvoiceBadge`'s labels
  and tones.** Reason: the Agent's card already shows that badge for the same
  states. One word, one colour, one meaning, and no new status vocabulary.
- **The load-failure description becomes "Pick another Client or reload the
  page to try again."** Reason: "Switch contracts" names the dropdown, which a
  desktop no longer shows. The new copy reads right in both layouts.
  Walkthrough step 16 checks it.
- **The by-id page's breadcrumb returns with `?contract=`.** Reason: the
  sent-back list's Open is the usual way to that page, and returning to the
  same Client completes the loop the address makes possible.
- **Three recorded debts on the touched files are paid**: the sent-back table's
  hidden Country, the by-id page's silent Contracts read, and the switcher
  chip's phone overflow. Reason: `docs/tech-debt.md` says debt is paid when a
  feature touches its module. Each is a one-line change on a file this feature
  edits.
- **Existing e2e specs switch to a new `selectClientInvoiceContract` helper on
  this page only.** Reason: the default e2e viewport (1280) now shows the
  column, and the switcher is hidden there. A helper keeps the change to one
  line per call site, and the switcher helper stays for every other page.
- **Testing: the existing component test file, the existing e2e suite with one
  new journey spec, and new goldens in the existing stubbed visual suite.**
  Reason: each is the highest existing seam that can see its behaviour, and no
  seam is added.

## Open questions

None

## Acceptance walkthrough

Run against an isolated stack with the backend on the `demo` profile (its own
fresh database, seeded by `DemoDataLoader`), never the user's compose stack.
Logins are in the root `README.md`. "Jordan" is the demo Agent holding more
than one Contract.

1. [agent] At 1440×900, sign in as Jordan and open `/agent/client-invoices`. Show the rail and the top bar as on any page, then a "Clients" navigation landmark beside the rail with one link per Contract, by Client name, in alphabetical order. Show the first link is `aria-current` and the wide area shows that Contract's current-month invoice. Show every row carries one status badge, and that each row's badge reads the same as the status badge on that Contract's invoice when it is selected. Show no Contract Switcher is displayed. (stories: 1, 2, 4, 6, 9, 28)
2. [agent] Click the second row. Show the wide area now shows that Contract's invoice and the current mark has moved. Show that no document request and no backend request were made, and that the address reads `?contract=<its id>`. Reload, and show the same row current and the same invoice. Press Back once, and show it leaves the page rather than stepping back a Client. (stories: 5, 6, 10)
3. [agent] As the Manager, create twelve more Clients with a Contract each for Jordan. As Jordan, reload, and show the column's own scroll height exceeds its height while the rail and the top bar don't move. Scroll the document to the bottom of a long invoice and show the column still in view. Scroll the column to its last row, click it, and show the document back at the top of the invoice area with the column's scroll position unchanged. (stories: 7, 8)
4. [agent] Open `/agent/client-invoices?contract=<a Contract id of Priya's>`, then `?contract=nonsense`. Each time, show the page opens on the first row with no error. Open `?contract=<the last row's id>` and show that row scrolled into view in the column and current. (stories: 10, 11)
5. [agent] As the Manager, create a Client with the same name as one of Jordan's Clients, and a Contract for Jordan. As Jordan, show the two rows of that name each carry a Country line, and no other row does. If the Manager's form refuses a duplicate Client name, show that refusal and the component test's Country-on-collision case passing instead. (stories: 3)
6. [agent] As Jordan, send one Contract's Client Invoice, and show its row's badge now reads "Awaiting approval". As the Manager, send it back with a reason. As Jordan, open the page. Show the "Sent back to you" region is the first thing in the invoice area, above the invoice, both with that Client selected and with another. Show that Client's row reads "Sent back" exactly once (its own badge, no extra mark), and no other row reads "Sent back". Hover the table's Contract cell and show its full "Client — Country", and show its Open link's name includes the Country. (stories: 13, 14, 24)
7. [agent] Click that row's Open, and show the invoice's own page. Click the "Client Invoices" breadcrumb, and show the list page with that Client current and `?contract=` in the address. Resend the invoice from the list page. Show the row's badge reads "Awaiting approval", no "Sent back" is left on the row, and the "Sent back to you" region is gone, with the same Client still selected. (stories: 12, 15)
8. [agent] Against the stub backend's Agent session, show the rows' badges reading Draft, Awaiting approval, Approved and Sent back. Show the Contract whose earlier month's invoice was sent back reads "Awaiting approval" and the "Sent back" mark, while the invoice area shows that Client's current-month invoice. Show the Contract whose this-month invoice was sent back reads "Sent back" once. Point one Contract's invoice at `500`, and show the row still listed with no status badge, and "Couldn't load this Contract's Client Invoice" in the invoice area when it is selected, with every other row still opening its invoice. (stories: 14, 18, 28)
9. [agent] Sign in as Priya, an Agent with one Contract, and show the column with its one row, current. As the Manager, create an Agent with no Contract. Sign in as them and show "No contracts yet" across the page, with no "Clients" landmark. (stories: 16, 17)
10. [agent] As Jordan, at 1279×800 and at 390×844, show no "Clients" landmark, and the "Sent back to you" region, then the Contract Switcher, then the invoice, as today. Pick a Client in the switcher and show the invoice swapped and `?contract=` in the address. Reload and show it kept. Show `document.documentElement.scrollWidth` equals the window width at 390. At 1280 show the column back and the switcher gone. As Priya at 390, with her Client renamed to a 60-character name by the Manager, show the chip truncated with no sideways scroll. (stories: 19, 20, 25)
11. [agent] At 1440, with the keyboard only, Tab from the top bar into the column, through its rows, and press Enter on one. Show its invoice displayed, the focus ring visible on the link, and the accessibility snapshot announcing it as current. Show a 60-character Client name wrapping to two lines in its row, with the full name as its `title`, its status badge fully visible below it, and the column still 256px wide. Show the link's accessible name includes its status label. (stories: 21, 22, 28)
12. [agent] At 1440, as Jordan, open Requests and Fleet, and as Dana (a Tester), open the Tester's pages. Show each still has its Contract Switcher and no "Clients" landmark. (stories: 23)
13. [agent] Show the `DESIGN.md` diff amends the dropdown rule, the Contract Switcher entry and the Layout paragraph, and adds the Contract side list entry, in the same commit as the layout. Run the full visual suite and show that only the new `agent-client-invoices-*` goldens were added and no existing golden changed. Then run `docs/agents/sdlc.json`'s `verify` green. (stories: 26, 27)
14. [agent] Count `client_invoices` rows, open the page as Jordan, click through every row, and show only the rows today's first view creates were added, and none on clicking. (stories: 5)
15. [human] On your own desktop, use the page with Jordan's long list. Confirm that the column reads as part of the page rather than a second navigation rail, that the current row is easy to see, that a status badge on every row still lets a "Sent back" stand out, and that switching feels in place. Narrow the window below 1280px and confirm that the dropdown returning there is acceptable. (stories: 1, 5, 6, 7, 14, 19, 28)
16. [human] Read the copy this feature adds or changes: the "Clients" heading, the status badges and the "Sent back" mark in the column, and "Pick another Client or reload the page to try again." Confirm each reads right to an Agent. (stories: 6, 14, 18, 28)

## Execution order

Three tickets in a line, because all three edit the same view, its test file and
its goldens.

1. `contract-side-list-on-desktop`: the `xl` two-column layout, `SurfacePage`'s `bleed`, `ContractSideList` with Client-name rows and each row's month status badge from `clientInvoiceBadge` (none on a failed load; the invoices are already loaded), alphabetical order (the switcher too), Country on a name collision, first row selected, the swap in place, scroll to top on swap, the single-Contract and no-Contract states, the new load-failure copy, the `DESIGN.md` amendments, the component tests, the `selectClientInvoiceContract` helper replacing the switcher call on this page in the nine existing specs, the switcher chip's truncation, and the new `agent-client-invoices` goldens with their stub fixtures. Modules: Agent Client Invoices page and view, app-shell, `components/ui` (switcher chip), e2e helpers, visual suite, `DESIGN.md`. No dependency. (stories: 1, 2, 3, 4, 5, 6, 7, 8, 9, 16, 17, 18, 19, 21, 22, 23, 25, 26, 27, 28)
2. `selected-client-in-the-address`: the `contract` search parameter parsed on the page, `replaceState` on every selection from the list and the switcher, unknown ids ignored, the selected row scrolled into view on arrival, the by-id breadcrumb's `?contract=`, its Contracts read moved to `backendFetchJsonOrNull`, component tests, and the address, reload and breadcrumb steps of `client-invoices-side-list.spec.ts`. Modules: Agent Client Invoices page, view and by-id page, e2e suite. Depends on `contract-side-list-on-desktop`. (stories: 10, 11, 12, 20)
3. `sent-back-in-the-side-list`: the extra "Sent back" mark for any month's sent-back invoice, never doubled with a row's own "Sent back" badge, the sent-back list first in the invoice area, the sent-back table's full title and Open name, component tests, the stub's earlier-month sent-back fixture, recaptured `agent-client-invoices-*` goldens, and the send-back steps of `client-invoices-side-list.spec.ts`. Modules: Agent Client Invoices view, visual suite, e2e suite. Depends on `selected-client-in-the-address`. (stories: 13, 14, 15, 24)
