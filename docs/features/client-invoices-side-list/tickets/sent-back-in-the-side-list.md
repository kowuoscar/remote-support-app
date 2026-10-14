---
id: sent-back-in-the-side-list
title: The side list marks a Client with a sent-back invoice
status: in-progress
depends_on: [side-list-row-status, selected-client-in-the-address]
labels: [frontend, invoicing]
stories: [13, 14, 15, 24]
---

## Context

`spec.md` `### "Sent back"`. A row gets a warning-toned "Sent back" mark when
the "Sent back to you" list holds an invoice of that Contract of any month and
the row's own badge does not already read "Sent back" (never twice). The list
stays first in the invoice area and unfiltered. Also pays the sent-back table's
hidden-Country debt. Depends on `side-list-row-status` (the row's badge the mark
sits beside, and the goldens it recaptures with the earlier-month fixture) and
on the address ticket (the journey's send-back flow returns through the
breadcrumb). The `DESIGN.md` side-list entry gains the mark and never-twice
text. Satisfies the frontend DoD in `docs/agents/frontend.md`.

## Acceptance criteria

- [ ] A Contract with a sent-back invoice of an earlier month shows its month's status badge and the "Sent back" mark; a Contract whose own this-month invoice was sent back shows "Sent back" exactly once; no other row shows "Sent back".
- [ ] After the Manager sends an invoice back, the Agent sees the "Sent back to you" region first in the invoice area, above the selected invoice, whichever Client is selected; after the Agent resends, the row's badge reads "Awaiting approval", no "Sent back" is left on the row, and the region is gone with the same Client still selected.
- [ ] With the sent-back read failed, a sent-back draft of this month still reads "Sent back" once and no row carries the extra mark; a failed-load row whose Contract has an invoice in the list shows the "Sent back" mark alone.
- [ ] In the sent-back table, the Contract cell's `title` is the full "Client — Country" and the Open link's accessible name is "Open <Client> — <Country>, <Month YYYY>".
- [ ] `DESIGN.md`'s "Contract side list" entry describes the mark and the never-twice rule, and the `agent-client-invoices-*` goldens are recaptured with the earlier-month fixture (Awaiting approval plus the mark), no other golden changing.

## Tests

- **Component (`client-invoices-view.test.tsx`):** earlier-month sent-back gives month status plus mark; this-month sent-back draft in the list shows "Sent back" once; other rows show none; sent-back read failed gives one "Sent back" and no mark; a `null` invoice plus a sent-back row for that Contract shows only the "Sent back" mark, no status badge; the "Sent back to you" region precedes the invoice in document order for each selected row; Contract cell title and Open link name.
- **End to end (`client-invoices-side-list.spec.ts`):** a send turns the row's badge to "Awaiting approval", a Manager send-back turns it to "Sent back" shown once with the list above the invoice, Open then breadcrumb returns with that Client selected, a resend returns the badge to "Awaiting approval" with no "Sent back" left. Prior art `send-a-client-invoice-back.spec.ts`.
- **Visual:** `stub-backend.mjs` `/api/client-invoices/sent-back` gains an earlier month's invoice of the `SENT` Contract; goldens recaptured.

## Regression

At risk: the sent-back table's Open link, whose accessible name changes. Guards, both unmodified: `rows-show-contract-month-date-reason-and-open-link` (`client-invoices-view.test.tsx` line 49, matching `/Open.*Birch Telecom.*July 2026/`) and `send-a-client-invoice-back.spec.ts` line 96 (`/^Open /`). Also at risk: the goldens from `side-list-row-status`.

Existing tests expected to change: the `agent-client-invoices-*` goldens are recaptured (reason: the earlier-month fixture adds a mark), and `stub-backend.mjs` gains one fixture; `client-invoices-view.test.tsx` and `client-invoices-side-list.spec.ts` gain cases. Every pre-existing case keeps passing unmodified, and no other golden changes.

## Observability

N/A — presentation of data the page already reads; no backend change.
