---
id: invoice-sim-line-carrier
title: Show the Carrier and type on every Postpaid SIM line of a Client Invoice
status: in-progress
depends_on: []
labels: [backend, frontend]
stories: [1, 2, 3, 4, 5, 6]
---

## Context

`spec.md` `### Client Invoice SIM lines: the Carrier`, the `### Prefactoring`
bullet "Stale contract on `basePostpaidSims`", and `## Design direction`
(Invoice SIM lines). Independent of the other tickets: it touches the invoice
DTO and the two invoice views only, so it runs in parallel with
`carriers-side-list`.

`ClientInvoiceBaseSimLineResponse` gains `carrierName`; one `simLineKind`
formatter feeds the Agent card (both Agent invoice pages) and the Manager
detail view. The stale Javadoc and frontend comment on `basePostpaidSims` are
corrected in the same files. Extends the stub's `SIM_LINES` with `carrierName`.

Definition of done: the `ClientInvoiceResponse` Javadoc and the `ClientInvoiceDetail` frontend comment are corrected to say the breakdown is served whenever the base amount is per SIM, at any status, and omitted only for an invoice holding a backfilled `BASE_AMOUNT` line.

Goldens: this ticket recaptures `agent-client-invoices-*` (Carrier text only)
and no other. `frontend/tests/visual/stub-backend.mjs` is also edited by
`fleet-side-list-and-tabs` and `manager-contracts-one-page`: the edits are in
different fixtures, so a rebase is mechanical. Satisfies the frontend DoD in
`docs/agents/frontend.md`.

## Acceptance criteria

- [ ] A draft Client Invoice's Postpaid SIM line serves `carrierName` "Verizon" for a SIM on Verizon, and a sent invoice's stored line serves it too.
- [ ] A SIM with no Carrier serves `carrierName: null` (always serialised), and the Tester's read carries the field.
- [ ] On the Agent's Client Invoice, in a draft, a sent invoice, a single invoice's own page and the one-column rows below 1280px, each SIM line reads its number followed by "Verizon · Postpaid", and "Postpaid" alone when the SIM has no Carrier.
- [ ] On the Manager's Client Invoice page the same line reads the same, before any cancellation note, and a SIM whose Carrier was archived later still shows that Carrier's name with no badge.
- [ ] The Tester's Invoices page renders as before, and the Edit button's description for a SIM line stays "SIM {number}".

## Tests

Seams per spec `## Testing decisions` items 1, 2, 3 and 4.

- **Backend (`ClientInvoiceApiTest`, MockMvc against Testcontainers):** a draft's SIM line carries "Verizon"; a sent invoice's stored line carries it; a SIM whose Carrier is archived afterwards still carries its name; a SIM with no Carrier carries `carrierName: null`, and the Tester's read carries the field.
- **Component:** `client-invoice-detail-view.test.tsx` also gains a case where a line whose `carrierName` belongs to an archived Carrier reads "{name} · Postpaid" with no "Archived" badge. `client-invoices-view.test.tsx` gains a case where a line reads "Verizon · Postpaid" and one with no Carrier reads "Postpaid". `client-invoice-detail-view.test.tsx` gains the same pair.
- **End to end:** `client-invoice-submission-and-visibility.spec.ts` gains a step where the Agent's and the Manager's SIM line reads "Verizon · Postpaid".
- **Visual:** the stub's `SIM_LINES` gains `carrierName` (two "Verizon", one `null`); recapture `agent-client-invoices-*` (desktop and mobile, light and dark).

## Regression

At risk: the Agent invoice card's line layout at xl and below (the number must stay the strongest thing and the grid must not move), `BilledAmount` on the Manager page, the Tester's response shape, and every other suite that asserts `basePostpaidSims`.

The phone-width same-line checks in `edit-client-invoice-lines.spec.ts` (about lines 215-290) protect the line layout and must keep passing unmodified.

Existing tests expected to change: none. `ClientInvoiceApiTest`, `client-invoices-view.test.tsx`, `client-invoice-detail-view.test.tsx` and `client-invoice-submission-and-visibility.spec.ts` each gain the cases above, and every pre-existing case in them keeps passing unmodified. The four other backend suites that touch `basePostpaidSims` keep passing unedited. `frontend/tests/visual/stub-backend.mjs` is extended. Only `agent-client-invoices-*` goldens are recaptured.

## Observability

N/A: an additive read-only field on an existing response; no new operation to log.
