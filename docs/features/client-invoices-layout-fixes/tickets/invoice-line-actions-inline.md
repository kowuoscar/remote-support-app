---
id: invoice-line-actions-inline
title: Put the invoice line amount and its actions on one line at xl
status: ready-for-agent
depends_on: [side-list-row-one-line]
labels: [frontend]
stories: [6, 7, 8, 9, 14, 15, 16, 18]
---

## Context

`spec.md` `### The line amount control, at xl and wider` and
`## Design direction` (Billed column, desktop). `EditClientInvoiceLineControl`
keeps its interface and behaviour; its display and editing layouts follow the
Fleet correction controls: value with a ghost Edit (and Reset), the note under
them; editing is field, `row` Save, ghost Cancel on one line with the refusal
below. `LineAmount` composes `Money` and `EditedLineNote` itself on the
read-only path; `BilledAmount` and the Manager view are untouched. Adds the
`DESIGN.md` Buttons sentence and the stub's draft lines for Aurora. Depends on
`side-list-row-one-line` because both recapture the same desktop goldens and
the golden diff must stay attributable. Satisfies the frontend DoD in
`docs/agents/frontend.md`.

## Acceptance criteria

- [ ] At 1280, in a draft, a SIM or Fee line's amount and Edit share a line in the Billed cell; an edited line shows amount, Edit and Reset on one line with "Edited · computed {amount}" directly under them.
- [ ] Amounts' right edges line up down the Billed column across rows with Edit, with Edit and Reset, and (on a sent invoice) with no buttons.
- [ ] Edit opens the field, Save and Cancel on one line, in that order, with focus in the field; a refused `-5` shows the alert below that line with `-5` kept and focus in the field; Escape and Cancel return focus to Edit.
- [ ] A sent invoice and a closed past-month draft show no Edit or Reset, and each edited line keeps its "Edited · computed" note.
- [ ] Edit and Reset keep their accessible names, described by the line's name; Save and Cancel, the in-flight `aria-busy` pattern and the announcement are unchanged.
- [ ] The Manager's Client Invoice detail page renders unchanged (no diff to `client-invoice-detail-view` or `BilledAmount`).
- [ ] `DESIGN.md` Buttons states that an inline correction is a `ghost` `sm` Edit beside the value, and editing a field, `row` Save and `ghost` Cancel on one line with the refusal below.

## Tests

Seams per spec `## Testing decisions`.

- **Component (`edit-client-invoice-line-control.test.tsx`, `client-invoices-view.test.tsx`):** in the editing state the controls come in order field, Save, Cancel, then the refusal; a sent invoice and a closed past-month draft keep each edited line's note and have no button in any line row; all existing cases keep passing.
- **End to end (`edit-client-invoice-lines.spec.ts`), at 1280:** a SIM line's amount and Edit share a line and its note sits below; after Edit the field, Save and Cancel share a line; a refused `-5` shows the alert below that line; amounts align across rows. The existing `/^(Edit|Reset)$/` count-0 assertion is unchanged.
- **Visual:** the stub's Aurora draft gains a plain, an edited and a cancelled Postpaid SIM, a Fee with a description and an edited Fee with no description, with consistent totals; recapture the two desktop goldens.

## Regression

At risk: the line editing flow (focus handoffs, Escape, validation messages, Reset-as-save-computed), the Manager's detail view that shares `BilledAmount`, and the button-name queries in e2e and component tests. Existing tests expected to change: none; `edit-client-invoice-lines.spec.ts`, `edit-client-invoice-line-control.test.tsx` and `client-invoices-view.test.tsx` each gain cases and every pre-existing case keeps passing unmodified. `frontend/tests/visual/stub-backend.mjs` is extended for the Agent session's draft lines; the desktop `agent-client-invoices-*` goldens are recaptured, and no other golden may change. `client-invoice-submission-and-visibility.spec.ts` keeps passing unchanged.

## Observability

N/A — presentation of existing data; the correction endpoint and its logging are unchanged.
