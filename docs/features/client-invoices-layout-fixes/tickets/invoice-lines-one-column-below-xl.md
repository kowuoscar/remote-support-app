---
id: invoice-lines-one-column-below-xl
title: Reflow the invoice lines into one-column rows below xl
status: ready-for-agent
depends_on: [invoice-line-actions-inline]
labels: [frontend]
stories: [10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22]
---

## Context

`spec.md` `### The invoice lines below xl: one markup that reflows`,
`### The rest of the card below xl`, the confirmed mockups in `## Problem` and
the Constraints (breakpoint, no sideways scroll, one markup, semantics at every
width). The Postpaid SIM Cards and Fee lines become one responsive structure
local to `AgentClientInvoiceCard`: table at `xl`, the mockup's rows below, with
explicit ARIA table roles and no 720px minimum below `xl`. Pays "Columns hidden
on phones" and "Bare field on mobile"; amends the `DESIGN.md` Layout rule.
Depends on `invoice-line-actions-inline` because it edits the same control and
card and builds on its `xl` layout. Satisfies the frontend DoD in
`docs/agents/frontend.md`.

## Acceptance criteria

- [ ] At 1279, 1024, 768, 390 and 375 on `/agent/client-invoices` and `/agent/client-invoices/{id}`, `document.documentElement.scrollWidth` equals `window.innerWidth` and each line list's `scrollWidth` equals its `clientWidth`; every amount and Edit is in the viewport.
- [ ] Below `xl`, a SIM row shows number left and amount right on line 1, then its cancellation or edited note left and Edit (and Reset) right; a Fee row shows type and amount, then "Logged {date}" with Edit, then the description when present and nothing (no "—") when absent.
- [ ] Below `xl`, a row being edited shows the line's name, then the field, Save and Cancel on one line, then any refusal below, with the row's own details under it; an edit and a Reset work at 390.
- [ ] A 64-character SIM number and a 500-character Fee description wrap inside their row with the amount whole on the right.
- [ ] A sent invoice and a closed past-month draft show the same rows with no Edit or Reset and each edited line's note, at 1440 and 390; a sent-back invoice's own page lays out the same way.
- [ ] At 1440 and 390 each list is a table with its column headers, one row per line with its cells, Edit and Reset inside their row; no line, amount or note appears twice in the DOM.
- [ ] `DESIGN.md`'s Layout rule carries the Agent Client Invoice lines exception; the shared `Table` and every other table still scroll sideways at 720px.

## Tests

Seams per spec `## Testing decisions`.

- **Component (`client-invoices-view.test.tsx`):** a draft with a plain, an edited and a cancelled SIM and two Fees has exactly one `row` per line plus one header row per table, each amount once in its row and "Edited · computed" once per edited line; a Fee row contains "Logged" and its date, and a Fee with no description renders no description text beyond the desktop "—".
- **End to end (`edit-client-invoice-lines.spec.ts`), at 390:** SIM and Fee rows share line 1 for what and amount; Edit is `toBeInViewport({ ratio: 1 })`; an edit and a Reset work; no sideways scroll; after send, no Edit or Reset and the note is visible. Also a scroll-width check at 375, 768, 1024 and 1279 on both pages, the 64-character SIM number case, and the 500-character Fee description case (it wraps inside its row, the amount stays whole on the right, no sideways scroll).
- **Visual:** recapture `agent-client-invoices-mobile-light` and `-dark` with the stub's draft lines from the previous ticket.
- **Accessibility tree:** checked once in the walkthrough with `playwright-cli snapshot` at 390 and 1440 (not a permanent test, per the spec).

## Regression

At risk: the 1280-wide table layout and its `row`/`cell` queries in every existing e2e spec; the Manager's detail view at 375; the "Sent back to you" table and every other table's sideways scroll; the card header, summary and Carrier invoice files list at phone widths (anything overflowing at 375 is fixed in place and named in the result). Existing tests expected to change: none; `edit-client-invoice-lines.spec.ts` and `client-invoices-view.test.tsx` gain cases and every pre-existing case keeps passing unmodified. The mobile `agent-client-invoices-*` goldens are recaptured; no other golden may change.

## Observability

N/A — layout only; no new behaviour to log or measure.
