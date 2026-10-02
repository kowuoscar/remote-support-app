---
id: manager-send-back-control-on-the-client-invoice-page
title: Let a Manager send a Client Invoice back from its detail page with an inline reason form
status: ready-for-agent
depends_on: [manager-sends-a-client-invoice-back]
labels: [frontend]
stories: [1, 2, 3, 4, 7, 8, 10, 35]
---

## Context

First slice of `spec.md` (`## Execution order`), the frontend half; spec `## Solution`, "Frontend: Manager", and `## Design direction`. A new client control `SendBackClientInvoiceControl` beside `ApproveClientInvoiceControl`, shown only while `SENT`, following `PendingRequestDecisionControls`' reject flow with no dialog. It takes the endpoint URL, not an invoice id, so `send-an-agent-invoice-back` can mount it. `ClientInvoiceDetailView` gains the sent-back draft note with the quoted reason and the "Previously sent back on {date}: {reason}" line on a resent invoice. Adds one pass-through BFF proxy for `POST /api/client-invoices/{id}/send-back` using `backendFetch`, and `sentBackAt` and `sentBackReason` in `lib/api/types.ts`, added to `ClientInvoiceDetail` as optional fields (the `basePostpaidSims` precedent) so the fixture factories in `client-invoice-detail-view.test.tsx` and `client-invoices-view.test.tsx` still typecheck unmodified. Modules: `components/manager`, `app/api`, `lib/api`. Follows `docs/agents/frontend.md`.

Send back is a secondary control at the 8px radius; Approve stays the only pill (Pill-Is-Primary Rule).

## Acceptance criteria

- [ ] On a `SENT` invoice, **Send back** shows beside Approve; it expands an inline form with the textarea labelled "Reason for sending back", the hint "Tell the Agent what is wrong or missing. They can change any line and attach files before sending it again.", **Confirm send back** and **Back**; Back closes it.
- [ ] Submitting an empty reason shows an inline error, keeps the form open and makes no request.
- [ ] A successful send-back re-renders the page in place as a draft: "Sent back to the Agent on {date} — waiting for them to resend", the reason in a quoted block, the numbers as sent, no Approve and no Send back.
- [ ] A `409` shows the "no longer awaiting approval — refresh" copy `ApproveClientInvoiceControl` uses; any other failure shows a generic retry message and keeps the typed reason.
- [ ] A draft that was never sent back and an approved invoice show no Send back; a resent `SENT` invoice shows "Previously sent back on {date}: {reason}" under its timestamps.
- [ ] The reason form is usable by keyboard alone with a visible focus ring, and at the mobile breakpoint within the viewport.

## Tests

- **Component seam (spec Testing decisions 4):** new `send-back-client-invoice-control.test.tsx` beside `approve-client-invoice-control.test.tsx`; cases `expand-and-back`, `empty-reason-blocked-with-no-request`, `409-shows-refresh-copy`, `generic-failure-keeps-reason`, `success-hands-invoice-to-callback`. `client-invoice-detail-view.test.tsx` gains `send-back-only-while-sent`, `sent-back-draft-shows-note-and-quoted-reason`, `resent-invoice-shows-previously-sent-back-line`, `no-send-back-on-draft-or-approved`.
- No e2e here: the journey is played end to end by `agent-sees-sent-back-invoices-on-the-client-invoices-page`.

## Regression

- At risk: the Manager's Client Invoice detail page and Approve, the Pending Requests reject flow it copies, the BFF proxy set, every golden (the page is backend-driven and not in the visual suite).
- Existing tests expected to change: `frontend/components/manager/client-invoice-detail-view.test.tsx` gains the cases above and every pre-existing case passes unmodified; `approve-client-invoice-control.test.tsx` and `pending-request-decision-controls.test.tsx` are not modified. `manager-invoice-review-queue.spec.ts` guards the approve flow, unmodified.

## Observability

N/A — a client-side form over the backend route; the audit line is the backend ticket's, and the BFF proxy logs failures as every other proxy does.
