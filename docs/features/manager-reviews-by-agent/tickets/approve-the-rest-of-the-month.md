---
id: approve-the-rest-of-the-month
title: Approve the invoices left to review in an Agent's month at once
status: ready-for-agent
depends_on: [agents-side-list-on-invoices, approve-a-month-api]
labels: [frontend]
stories: [17, 18, 19, 20, 21]
---

## Context

`spec.md` `### Frontend` (`AgentMonthReview` header, `ApproveClientInvoiceMonthControl`), `## Design direction` (the one pill, `DESIGN.md` "Month review"), `## Testing decisions` items 4 and 5. Depends on `approve-a-month-api` (the approve route) and on `agents-side-list-on-invoices` (the month header it fills) and on that ticket's `AgentMonthReview`, stub reads, e2e spec file and `manager-invoices-agent-*` goldens, which this ticket changes. Frontend DoD in `docs/agents/frontend.md` applies.

What lands: `ApproveClientInvoiceMonthControl`, following `SendClientInvoiceMonthControl` (two-step inline confirm, focus handling, `aria-busy`), rendered in the month header when {k} > 0; a BFF route under `frontend/app/api/agents/[agentId]/client-invoice-months/[month]/approve/route.ts` mirroring the backend path through `forwardJson`, posting `{invoiceIds}` of the month's To review rows; on 200 the page refreshes through `refreshPage`. `DESIGN.md` gains the "Month review" entry under Components in this commit (the header, the one-pill rule, the Manager status labels; "Next invoice →" is added there by `next-invoice-on-the-detail-page`). The stub keeps a To review row in Jordan's month so the golden shows the pill.

## Acceptance criteria

- [ ] On an Agent's month with {k} > 0 left to review, the header shows the primary pill "Approve the {k} left to review" (below `md` it wraps under the text, right-aligned); with {k} = 0 the header holds no button.
- [ ] Clicking it opens the confirm "Approves the {k} Client Invoices of {Agent} for {Month Year} still to review. An approved invoice is final." with a ghost Back (focus returns to the pill) and the primary "Confirm approve {k}" ("Confirm approve" for one; "Approving…" while pending).
- [ ] Confirming approves exactly the invoices shown as To review: afterwards the page refreshes, those rows read Approved, an Approved or Sent back row is untouched, the header reads "{n} of {n} approved" (plus " · {s} sent back" when any) with no button, and the Agent's count in the side list is lowered ("—" when none is left).
- [ ] When the month changed since the page loaded (an invoice sent back or approved elsewhere, a resend arrived), confirming shows under the confirm, as `role="alert"`, "This month changed since the page loaded. Reload and try again." and approves nothing; after a reload the new count is offered and confirming succeeds.
- [ ] When the month was already fully approved elsewhere, confirming shows "Nothing is left to review. Reload to see the month." and changes nothing; any other failure shows "Couldn't approve. Try again."; a network failure shows the existing "Couldn't reach the server…" line.
- [ ] The Agent reads the invoices as "Approved", exactly as after a single approval (the Agent's page is unchanged).

## Tests

Seams per spec `## Testing decisions` items 4 and 5 (Vitest + Testing Library; Playwright e2e on the isolated stack).

- **`components/manager/approve-client-invoice-month-control.test.tsx` (new):** the label for many and for one; the confirm's text, and focus on open and on Back; the request's URL and `{invoiceIds}` body; the text for `MONTH_CHANGED`, `NOTHING_TO_APPROVE`, any other code and a network failure; pending state (`aria-busy`, repeat activation ignored).
- **`tests/e2e/manager-reviews-by-agent.spec.ts` (extended):** the approval cases: a stale page (the Agent resends an invoice in another context) refuses with `MONTH_CHANGED` and approves nothing, then succeeds after a reload with a shared result "{n} of {n} approved" and the side-list count dropping to "—".
- **Visual:** the `manager-invoices-agent-*` goldens are recaptured with the pill.

## Regression

At risk: the month header from `agents-side-list-on-invoices`, `ApproveClientInvoiceControl` (the single Approve, untouched), `SendClientInvoiceMonthControl` (read for its shape, untouched), the Manager's detail page Approve and Send back.

Existing tests expected to change, each with the reason:

- `frontend/components/manager/agent-month-review.test.tsx`: gains the cases for the button's presence ({k} > 0) and absence ({k} = 0) and the control's mount; every pre-existing case keeps passing unmodified.
- `frontend/tests/e2e/manager-reviews-by-agent.spec.ts`: gains the approval cases; every pre-existing case keeps passing unmodified.
- `frontend/tests/visual/stub-backend.mjs`: only if the stub's Jordan month has no To review row for the pill (it should; it is additive).
- Goldens `manager-invoices-agent-{desktop,mobile}-{light,dark}.png`: recaptured with the pill; no other golden moves.
- `review-queue-view.test.tsx`, `approve-client-invoice-control.test.tsx` and every other existing e2e spec stay unedited.

## Observability

N/A: a presentational control over an existing server action; the server writes the status and audit lines (`approve-a-month-api`) and the control logs nothing.
