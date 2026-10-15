---
id: invoice-lines-layout
title: Lay out the invoice lines with inline actions at xl and one-column rows below
status: done
depends_on: [side-list-row-one-line]
labels: [frontend]
stories: [6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22]
---

## Context

Replaces `invoice-line-actions-inline` and `invoice-lines-one-column-below-xl`
(human, 2026-10-15: small follow-ups are one ticket, one verify). They edit the
same control and card and move the same goldens.

`spec.md` `### The line amount control, at xl and wider`,
`### The invoice lines below xl: one markup that reflows`,
`### The rest of the card below xl`, `## Design direction` (Billed column), the
confirmed mockups in `## Problem` and the Constraints (breakpoint, no sideways
scroll, one markup, semantics at every width).

At `xl`: `EditClientInvoiceLineControl` keeps its interface and behaviour; its
display and editing layouts follow the Fleet correction controls. The value
comes with a ghost Edit (and Reset), and the note goes under them. Editing is
field, `row` Save and ghost Cancel on one line, with the refusal below.
`LineAmount` composes `Money` and `EditedLineNote` itself on the read-only
path; `BilledAmount` and the Manager view are untouched.

Below `xl`: the Postpaid SIM Cards and Fee lines become one responsive
structure local to `AgentClientInvoiceCard`. It is a table at `xl` and the
mockup's rows below, with explicit ARIA table roles and no 720px minimum below
`xl`.

Pays "Columns hidden on phones" and "Bare field on mobile". Adds the
`DESIGN.md` Buttons sentence and amends the `DESIGN.md` Layout rule. Extends
the stub's draft lines for Aurora. Depends on `side-list-row-one-line` because
both recapture the desktop goldens. Satisfies the frontend DoD in
`docs/agents/frontend.md`.

## Acceptance criteria

At `xl` (1280 and wider):

- [ ] In a draft, a SIM or Fee line's amount and Edit share a line in the Billed cell; an edited line shows amount, Edit and Reset on one line with "Edited · computed {amount}" directly under them.
- [ ] Amounts' right edges line up down the Billed column across rows with Edit, with Edit and Reset, and (on a sent invoice) with no buttons.
- [ ] Edit opens the field, Save and Cancel on one line, in that order, with focus in the field; a refused `-5` shows the alert below that line with `-5` kept and focus in the field; Escape and Cancel return focus to Edit.

Below `xl`:

- [ ] At 1279, 1024, 768, 390 and 375 on `/agent/client-invoices` and `/agent/client-invoices/{id}`, `document.documentElement.scrollWidth` equals `window.innerWidth` and each line list's `scrollWidth` equals its `clientWidth`; every amount and Edit is in the viewport.
- [ ] A SIM row shows number left and amount right on line 1, then its cancellation or edited note left and Edit (and Reset) right; a Fee row shows type and amount, then "Logged {date}" with Edit, then the description when present and nothing (no "—") when absent.
- [ ] A row being edited shows the line's name, then the field, Save and Cancel on one line, then any refusal below, with the row's own details under it; an edit and a Reset work at 390.
- [ ] A 64-character SIM number and a 500-character Fee description wrap inside their row with the amount whole on the right.

At every width:

- [ ] A sent invoice and a closed past-month draft show no Edit or Reset, and each edited line keeps its "Edited · computed" note, at 1440 and 390; a sent-back invoice's own page lays out the same way.
- [ ] At 1440 and 390 each list is a table with its column headers, one row per line with its cells, Edit and Reset inside their row; no line, amount or note appears twice in the DOM.
- [ ] Edit and Reset keep their accessible names, described by the line's name; Save and Cancel, the in-flight `aria-busy` pattern and the announcement are unchanged.
- [ ] The Manager's Client Invoice detail page renders unchanged (no diff to `client-invoice-detail-view` or `BilledAmount`).
- [ ] `DESIGN.md` Buttons states that an inline correction is a `ghost` `sm` Edit beside the value, and editing a field, `row` Save and `ghost` Cancel on one line with the refusal below; `DESIGN.md`'s Layout rule carries the Agent Client Invoice lines exception; the shared `Table` and every other table still scroll sideways at 720px.

## Tests

Seams per spec `## Testing decisions`.

- **Component (`edit-client-invoice-line-control.test.tsx`, `client-invoices-view.test.tsx`):**
  - in the editing state the controls come in order field, Save, Cancel, then the refusal;
  - a sent invoice and a closed past-month draft keep each edited line's note and have no button in any line row;
  - a draft with a plain, an edited and a cancelled SIM and two Fees has exactly one `row` per line plus one header row per table, each amount once in its row and "Edited · computed" once per edited line;
  - a Fee row contains "Logged" and its date, and a Fee with no description renders no description text beyond the desktop "—";
  - all existing cases keep passing.
- **End to end (`edit-client-invoice-lines.spec.ts`):**
  - **At 1280:** a SIM line's amount and Edit share a line and its note sits below. After Edit, the field, Save and Cancel share a line. A refused `-5` shows the alert below that line. Amounts align across rows.
  - **At 390:** SIM and Fee rows share line 1 for what and amount. Edit is `toBeInViewport({ ratio: 1 })`. An edit and a Reset work, with no sideways scroll. After send, there is no Edit or Reset and the note is visible.
  - **Other widths and long values:** a scroll-width check at 375, 768, 1024 and 1279 on both pages. A 64-character SIM number case, and a 500-character Fee description case: it wraps inside its row, the amount stays whole on the right, and nothing scrolls sideways.
  - The existing `/^(Edit|Reset)$/` count-0 assertion is unchanged.
- **Visual:** the stub's Aurora draft gains a plain, an edited and a cancelled Postpaid SIM, a Fee with a description and an edited Fee with no description, with consistent totals; recapture the four `agent-client-invoices-*` goldens (desktop and mobile, light and dark).
- **Accessibility tree:** checked once in the walkthrough with `playwright-cli snapshot` at 390 and 1440 (not a permanent test, per the spec).

## Regression

At risk:
- the line editing flow: focus handoffs, Escape, validation messages, Reset-as-save-computed;
- the Manager's detail view, which shares `BilledAmount`, including at 375;
- the 1280-wide table layout, its `row`/`cell` queries, and the button-name queries in e2e and component tests;
- the "Sent back to you" table and every other table's sideways scroll;
- the card header, summary and Carrier invoice files list at phone widths. Anything overflowing at 375 is fixed in place and named in the result.

Existing tests expected to change: none. `edit-client-invoice-lines.spec.ts`, `edit-client-invoice-line-control.test.tsx` and `client-invoices-view.test.tsx` gain cases, and every pre-existing case keeps passing unmodified. `client-invoice-submission-and-visibility.spec.ts` keeps passing unchanged. `frontend/tests/visual/stub-backend.mjs` is extended for the Agent session's draft lines. The four `agent-client-invoices-*` goldens are recaptured, and no other golden may change.

## Observability

N/A: layout and presentation of existing data. The correction endpoint and its logging are unchanged.
