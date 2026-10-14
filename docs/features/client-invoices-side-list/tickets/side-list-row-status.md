---
id: side-list-row-status
title: Each side-list row shows the month's invoice status
status: in-progress
depends_on: [contract-side-list-on-desktop]
labels: [frontend, invoicing]
stories: [18, 27, 28]
---

## Context

`spec.md` `### The side list` (row meta line), `### "Sent back"` (the row's
status), `### Copy` and `## Testing decisions` item 3. Adds to the rows from
`contract-side-list-on-desktop` the badge of `clientInvoiceBadge` for the
Contract's current-month invoice (already loaded), the failed-load behaviour and
its new description, and the `agent-client-invoices` visual goldens. The
`DESIGN.md` side-list entry gains the status-badge text. Satisfies the frontend
DoD in `docs/agents/frontend.md`.

## Acceptance criteria

- [ ] Each row shows the status of that Contract's current-month invoice with the badge the invoice card shows: Draft, Awaiting approval, Approved or Sent back (a `DRAFT` with `sentBackAt`); the link's accessible name includes the status label.
- [ ] A Contract whose invoice failed to load stays listed with no status badge; selecting it shows "Couldn't load this Contract's Client Invoice" with the description "Pick another Client or reload the page to try again.", and every other row still opens its invoice.
- [ ] `tests/visual/surfaces.spec.ts` renders `agent-client-invoices` on `visual-agent-session` at 1440 and 390, light and dark, with four Contracts (two sharing a Client name, so a Country line) showing Draft, Awaiting approval, Approved and Sent back; only new `agent-client-invoices-*` goldens are added and no existing golden changes.

## Tests

- **Component (`client-invoices-view.test.tsx`):** each row's label for Draft, Awaiting approval, Approved and a sent-back draft; a `null` invoice is listed with no badge and, when selected, shows the failure title and new description while another row still opens.
- **Visual (existing stubbed suite; prior art `agent-carriers`, `agent-stock`):** the goldens above. `stub-backend.mjs` gains for the Agent session `/api/contracts` (four Contracts, two sharing a Client name), each Contract's `/client-invoice` (a sent-back draft of this month, a `SENT`, an `APPROVED`, a plain `DRAFT`) and `/api/client-invoices/sent-back` (that sent-back draft only). One non-golden test points one Contract's invoice at `500` and asserts the row has no status badge and the "Couldn't load" state.
- **End to end (`client-invoices-side-list.spec.ts`):** each row reads "Draft" on arrival.

## Regression

At risk: the Agent-session stub responses and every existing golden (none may change). Existing tests expected to change:
- The row badge makes each side-list link's accessible name "<Client> <status>" (spec: accessible names include the status label). So: in `frontend/components/agent/client-invoices-view.test.tsx` the 8 cases of the `contract-side-list-on-desktop` describe append " Draft" to the link names / expected text they look up, assertions otherwise unchanged; `frontend/tests/e2e/helpers.ts` `selectClientInvoiceContract` matches the Client name optionally followed by a status label instead of `exact: true`; `frontend/tests/e2e/client-invoices-side-list.spec.ts` uses "<name> Draft" for its two row links and strips the status before ordering. (Added by the orchestrator after implementation.)
- `frontend/components/agent/client-invoices-view.test.tsx` case `current-month-card-shows-reason-and-badge-when-sent-back` (line 72) asserts `screen.getByText("Sent back")`, which now also matches the row's badge; that lookup is re-scoped to the invoice card, its assertion otherwise unchanged. Every other pre-existing case keeps passing unmodified.
- Unscoped status lookups on `/agent/client-invoices`, which the row badges make ambiguous under Playwright strict mode at the default 1280 viewport: `client-invoice-generation.spec.ts:57`, `client-invoice-submission-and-visibility.spec.ts:51` and `:64`, `edit-client-invoice-lines.spec.ts:121`, `send-a-client-invoice-back.spec.ts:48`, `manager-invoice-review-queue.spec.ts:28` and `:77`, `manager-dashboard.spec.ts:160`. Each lookup is re-scoped to the invoice card with the expected text unchanged (confirm first that `:64` and `:121` run on this page; leave any that don't). Every other line keeps passing unmodified.
- `frontend/tests/visual/stub-backend.mjs` and `surfaces.spec.ts` are extended for the Agent session only; every existing surface keeps passing unmodified.
- `frontend/tests/e2e/client-invoices-side-list.spec.ts` (created by `contract-side-list-on-desktop`) gains a case; its existing cases are unmodified.

## Observability

N/A — presentation of data the page already reads; test fixtures only.
