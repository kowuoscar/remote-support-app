---
id: contract-side-list-on-desktop
title: An Agent picks a Client's invoice from a side list on a desktop
status: ready-for-agent
depends_on: []
labels: [frontend, invoicing]
stories: [1, 2, 3, 4, 5, 6, 7, 8, 9, 16, 17, 19, 21, 22, 23, 26]
---

## Context

First slice of `spec.md`: at `xl` (1280px) and wider the Agent's Client Invoices
page shows a "Clients" column of Contracts beside the rail, and a click swaps
the invoice in place. Below `xl` it is today's page. Implements `## Solution`
("The layout, by width", "The side list", "Selection" without the address,
"The shell", `DESIGN.md`) and `## Design direction`. Not in this ticket: the
row's status badge and the failed-load row (`side-list-row-status`), the
address (`selected-client-in-the-address`), the extra "Sent back" mark
(`sent-back-in-the-side-list`). Here the initial selection is the first Contract
in column order, rows show the name and, on a collision, the Country, and no
`contract` parameter is read or written. Read `docs/agents/frontend.md`: this is
a frontend ticket and satisfies its DoD, with the `DESIGN.md` amendment in the
same commit (issue-tracker Rule 7). The `DESIGN.md` side-list entry written here
omits the status badge and the mark; the later tickets extend it.

Because the default e2e viewport (1280) now shows the column and hides the
switcher, the nine existing specs that open `/agent/client-invoices` move to the
new helper in this ticket, or they would fail.

## Acceptance criteria

- [ ] At 1280px and wider, `/agent/client-invoices` shows a "Clients" navigation landmark between the rail and the invoice, with one link per Contract, by Client name in alphabetical order (then Country), 256px wide, sticky under the top bar and scrolling on its own while the document scrolls the invoice; no Contract Switcher is displayed. Below 1280px (1279 and 390) there is no "Clients" landmark and the "Sent back to you" list, the Contract Switcher and the invoice appear as today; the switcher lists the same alphabetical order.
- [ ] The first row is selected on arrival (`aria-current="true"`, soft-indigo tone) and the invoice area shows that Contract's current-month invoice. Clicking another row shows that Contract's invoice in place, moves `aria-current`, makes no document request and no backend request, and creates no `client_invoices` row.
- [ ] Picking a Client while the invoice area's top is above the viewport scrolls the document back to the top; the column's own scroll position is kept.
- [ ] Each row shows the Client name (at most two lines, full name in `title`, column width unchanged); the Country appears only on rows whose Client name is shared by two of the Agent's Contracts. The link's accessible name is its text.
- [ ] Every row is reachable with Tab, opens with Enter and shows the global focus ring; a plain click is intercepted while a modified click (Cmd/Ctrl, middle button) falls through to the link's `href`.
- [ ] An Agent with one Contract sees the column with its one row selected; an Agent with no Contracts sees "No contracts yet" across the page and no "Clients" landmark.
- [ ] Requests, Fleet and the Tester's pages render exactly as before (Contract Switcher, no column), and `DESIGN.md` carries the amended dropdown rule, the Contract Switcher entry, the Layout sentence and a "Contract side list" entry as in `spec.md` `### DESIGN.md` (without the status-badge and mark text).

## Tests

Seams are those of `spec.md` `## Testing decisions`.

- **Component (`client-invoices-view.test.tsx`, `stubFetch`, `mockRouter`):** one link per Contract in alphabetical order whatever order props arrive in, switcher in the same order; Country only on a shared name; first row `aria-current`; click swaps invoice, moves `aria-current`, makes no fetch; a single Contract gives one selected row; no Contracts gives "No contracts yet" and no "Clients" landmark; a 60-character name keeps its full name as `title`. Tests scope to the "Clients" landmark or the switcher (jsdom applies no media queries).
- **End to end (new `client-invoices-side-list.spec.ts`):** two Contracts for the seeded Agent, both rows in order; a click swaps the invoice with no navigation request; at 1440, after scrolling down a long invoice, picking a row returns the document to the top with the column's scroll kept, and the column scroll height exceeds its height while the document scrolls; Tab and Enter on a row; a Cmd/Ctrl click and a middle click on a row do not swap in place and open the `href`; at 390px no "Clients" landmark, the switcher present and working, and no sideways scroll (`scrollWidth` equals the window width), with ordinary-length Client names.
- **Helper:** add `selectClientInvoiceContract(page, clientName)` to `tests/e2e/helpers.ts` (clicks the link in the "Clients" landmark; `selectContractInSwitcher` is unchanged) and use it right after `goto("/agent/client-invoices")` in `client-invoice-generation`, `client-invoice-submission-and-visibility`, `edit-client-invoice-lines`, `send-a-client-invoice-back`, `cancelled-sim-billed-through-its-month`, `manager-invoice-review-queue`, `manager-dashboard`, `client-dashboard`, `topup-fee-from-option`.

## Regression

At risk: every page using `SurfacePage` (`bleed` is opt-in; no other golden may move, existing goldens in `tests/visual/surfaces.spec.ts`), and the narrow layout of the Client Invoices page, which must stay as today's. No golden protects it until `side-list-row-status` adds one; its guard is the 375px no-sideways-scroll step of `send-a-client-invoice-back.spec.ts` (lines 88-93), unmodified.

Existing tests expected to change:
- `frontend/components/agent/client-invoices-view.test.tsx`: the fixture `const CONTRACT = { id, label, currency }` (line 10) and the `SENT_BACK_ROW` contract props are reshaped to `{ id, clientName, countryLabel, currency }` because the view now takes that input (`spec.md` `### The page`); every assertion stays untouched. The case `rows-show-contract-month-date-reason-and-open-link` (line 49) finds the switcher with `screen.getByText("Aurora Retail Group")`, which in jsdom now matches both the switcher chip and the side-list row; that one lookup is re-scoped to the switcher, and its other assertions are unchanged. New cases are added; every other pre-existing case keeps passing unmodified.
- The nine e2e specs listed under Tests change one line each (`selectContractInSwitcher` to `selectClientInvoiceContract` on this page only); their assertions are untouched.
- `frontend/tests/e2e/helpers.ts` gains one helper; no existing helper changes.

## Observability

N/A — a presentation-only change: no backend read, write or log line is added or changed.
