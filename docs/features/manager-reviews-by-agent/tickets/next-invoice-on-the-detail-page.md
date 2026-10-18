---
id: next-invoice-on-the-detail-page
title: Go to the next invoice to review from a Client Invoice's detail page
status: done
depends_on: [approve-the-rest-of-the-month]
labels: [frontend]
stories: [22, 23, 24, 25, 26, 27, 36, 37]
---

## Context

`spec.md` `### Frontend` (The detail page, `ReviewMonthNext`, `nextToReview`, the stub), `## Design direction`, `## Testing decisions` items 4 to 6. Depends on `approve-the-rest-of-the-month`, which owns the shared stub, the e2e spec file `manager-reviews-by-agent.spec.ts`, `review-months` test file and the goldens this ticket also changes; it also needs `review-by-agent-reads`' month read and `agents-side-list-on-invoices`'s types and `reviewMonthFor` file (both earlier in the chain). Frontend DoD in `docs/agents/frontend.md` applies.

What lands: `app/manager/invoices/client/[invoiceId]/page.tsx` takes `agentId` and `agentName` from the Contract it already reads and reads `/api/review-queue/agents/{agentId}/months/{YYYY-MM}` of the invoice's billing month; the breadcrumb becomes "Invoices" (unchanged) › "{Agent}" (`/manager/invoices?agent={id}&month={YYYY-MM}`) › "{Client}", with today's two crumbs when the Contract is not found; the page title stays "{Client} — {Agent}". New `ReviewMonthNext` above `ClientInvoiceDetailView`, right-aligned, rendering nothing when the month read failed; `nextToReview(clientInvoices, currentId)` is added to the helper file from `agents-side-list-on-invoices`. `ClientInvoiceDetailView` is unchanged. The stub serves the month of its Manager detail invoice, with a next invoice to review. The journey proof's Manager step goes through the Agents side list. `DESIGN.md`'s "Month review" entry gains "Next invoice →" as a secondary control.

Harness declarations for the result (the merger applies them): `CONTEXT.md` names month approval and "To review"; `ARCHITECTURE.md` entries for the new frontend components, routes and helpers from this feature's three frontend tickets.

## Acceptance criteria

- [ ] On a Client Invoice's detail page, whatever the way in (the month list, the Review Queue, the dashboard's Pending approvals, the Contract page), the breadcrumb reads "Invoices › {Agent} › {Client}" and the Agent crumb opens that Agent's month (`?agent=&month=`); the page title stays "{Client} — {Agent}" and the "Invoices" crumb keeps its name and href.
- [ ] A secondary "Next invoice →" link (accessible name "Next invoice: {Client}") opens the next invoice still To review in that invoice's Agent's month, in the month list's order, wrapping round to the start and never the current one; when the current invoice is not in the list it opens the first To review row.
- [ ] On the month's last invoice to review, "Nothing else to review in {Month Year}." shows where the link would be.
- [ ] Working through a month: from A follow Next to B, send B back, B's Next goes to C, approve C, C's Next goes to A (wrapped), on A "Nothing else to review in {Month Year}.", and the Agent crumb opens the month with A To review, B Sent back, C Approved, "1 of 3 to review".
- [ ] When the Agent's month cannot be loaded the page reads as today, without "Next invoice →" and without blocking Approve or Send back.
- [ ] The journey proof `client-invoice-submission-and-visibility.spec.ts` approves the Postpaid Contract's invoice from the Contract page, then opens the fresh Agent from the Agents side list, sees "1 of 2 to review" and approves the empty Contract's invoice with "Approve the 1 left to review", ending on "2 of 2 approved".
- [ ] The `manager-client-invoice-check-mark-{desktop,mobile}-{light,dark}` goldens move (the Agent crumb and "Next invoice →") and no other golden moves.

## Tests

Seams per spec `## Testing decisions` items 4 to 6 (Vitest + Testing Library; Playwright e2e on the isolated stack; the visual stub).

- **`components/manager/review-month-next.test.tsx` (new):** the link with its accessible name and `href`; "Nothing else to review in {Month Year}."; nothing rendered when the month read failed.
- **`lib/review-months.test.tsx` (extended, table-driven):** `nextToReview` after the current row, wrapping round, never the current, current not listed, no To review row (null).
- **`tests/e2e/manager-reviews-by-agent.spec.ts` (extended):** the working-through case (send back one, approve one, wrap round), the breadcrumb back to the month, an entry from the Review Queue and from the dashboard.
- **`tests/e2e/client-invoice-submission-and-visibility.spec.ts` (journey proof):** the Manager step through the side list.
- **Visual:** `client-invoice-files.spec.ts` captures the Manager detail page with the stub's new month.

## Regression

At risk: every spec that opens the Manager's Client Invoice detail page and uses the breadcrumb or exact text locators: `manager-invoice-review-queue.spec.ts` (breadcrumb link `name: "Invoices"`, substring-matched, still unique because the Agent crumb carries the Agent's name), `send-a-client-invoice-back.spec.ts`, `edit-client-invoice-lines.spec.ts`, `client-invoices-side-list.spec.ts` and `agent-sends-the-month.spec.ts` (exact "Approved", "Send back", "Approve" locators; "Next invoice →" and "Nothing else to review…" repeat none of them), `manager-dashboard.spec.ts`. These are expected to pass unmodified; `ClientInvoiceDetailView` is untouched. The detail page now makes one more backend read, whose failure must not fail the page.

Existing tests expected to change, each with the reason:

- `frontend/tests/e2e/client-invoice-submission-and-visibility.spec.ts`: the Manager step goes through the Agents side list (the spec's stated change); its Contract-page approval step keeps its assertions.
- `frontend/lib/review-months.test.tsx`: gains the `nextToReview` cases; every pre-existing case keeps passing unmodified.
- `frontend/tests/e2e/manager-reviews-by-agent.spec.ts`: gains the Next and breadcrumb cases; every pre-existing case keeps passing unmodified.
- `frontend/tests/visual/stub-backend.mjs`: serves the month of the Manager detail invoice (`99999999-0000-0000-0000-000000000001`'s Contract and month) with a next invoice to review; additive.
- Goldens `manager-client-invoice-check-mark-{desktop,mobile}-{light,dark}.png` (captured by `client-invoice-files.spec.ts`, whose code does not change): recaptured with the Agent crumb and "Next invoice →".
- `frontend/components/manager/client-invoice-detail-view.test.tsx`, `lib/api/types.test.tsx` and every other existing e2e spec stay unedited; a ticket that finds one must change returns `blocked` with the reason.

## Observability

N/A: a read-only link and breadcrumb; the page logs nothing and a failed month read degrades silently to today's page.
