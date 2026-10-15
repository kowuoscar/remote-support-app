---
id: side-list-row-one-line
title: Lay each side list row out as one line, name left and status right
status: ready-for-agent
depends_on: []
labels: [frontend]
stories: [1, 2, 3, 4, 5]
---

## Context

`spec.md` `### The side list row`, `## Design direction` (Side list) and the
Constraints "Side list". Only the row layout of `ContractSideList` changes:
name on the left, cut with "…" and full in `title`; status `Badge` on the right
of the same line; a second line only for a Country on a name collision and for
the "Sent back" mark beside a status (open question 1, answered). DOM order and
accessible names stay as today; placement is by CSS grid. Also pays the
"Unused directive" debt in `contract-side-list.tsx`, amends the `DESIGN.md`
Contract side list Row bullet, and recaptures the two desktop
`agent-client-invoices-*` goldens. Satisfies the frontend DoD in
`docs/agents/frontend.md`.

## Acceptance criteria

- [ ] At 1440, in the Agent's Clients column, every row's Client name and status badge share a line (boxes overlap vertically), the name on the left and the badge within the row's right inset with a gap; the column is still 256px wide.
- [ ] A Client renamed to 60 characters keeps its row one line high, its name is cut (`scrollWidth > clientWidth`), its `title` is the full name, and its badge is whole and inside the row.
- [ ] Two rows with the same Client name show the Country on a second line under the name, the status still on line 1; no other row has a second line unless it carries the mark.
- [ ] A row with a status and the "Sent back" mark shows the mark once, on a second line right-aligned under the status; a row with the mark and no status shows it on line 1.
- [ ] Each link's accessible name is unchanged (`"{Client} Draft"`, the collision Country, `"Yarrow Labs Sent back"`).
- [ ] `DESIGN.md`'s Contract side list Row bullet states the one-line name, the right-hand badge, the second line for Country and mark, and the unchanged accessible name order; lint is clean with no unused directive in `contract-side-list.tsx`.

## Tests

Seams per spec `## Testing decisions`.

- **Component (`client-invoices-view.test.tsx`):** the existing link-name cases pass unedited (Draft, collision Country, Sent back), proving the accessible name order did not move.
- **End to end (`client-invoices-side-list.spec.ts`):** at 1440 name and badge share a line; the badge's right edge is in the row's right inset; a 60-character renamed Client stays one line high with `title` equal to the full name and the name overflowing; the column is 256px.
- **End to end / visual stub (same spec, geometry by bounding box):** on a Client-name collision the Country's box sits below the name's box while the badge stays on line 1 (name and badge boxes overlap vertically); a status-plus-mark row has the "Sent back" mark's box below the status badge and right-aligned with it, and the text "Sent back" appears once in that row; a mark-only row (its invoice failed to load, stub invoice answering 500) has the mark on line 1 in the status's place.
- **Visual (existing stubbed suite):** recapture `agent-client-invoices-desktop-light` and `-dark`; the fixtures already hold a row with status and mark.

## Regression

At risk: the side list's selection, click handling, order and arrival scroll, and the link names that e2e helpers (`selectClientInvoiceContract`) and component tests look up. Existing tests expected to change: none in the component tests. `frontend/tests/e2e/client-invoices-side-list.spec.ts` gains new steps; every pre-existing case keeps passing unmodified. If the failed-load row is staged in `frontend/tests/visual/stub-backend.mjs` instead of a Playwright route, that file may gain data; every pre-existing surface keeps rendering as before. Goldens `agent-client-invoices-desktop-light` and `agent-client-invoices-desktop-dark` are recaptured because the rows move; no other golden may change, and the mobile pair is recaptured by the third ticket.

## Observability

N/A — presentation only; no new behaviour to log or measure.
