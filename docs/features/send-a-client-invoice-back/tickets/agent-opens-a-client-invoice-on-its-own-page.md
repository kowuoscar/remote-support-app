---
id: agent-opens-a-client-invoice-on-its-own-page
title: Let the Agent open, correct and resend a Client Invoice on its own page
status: ready-for-agent
depends_on: [agent-client-invoice-card-addresses-the-invoice-by-id, manager-send-back-control-on-the-client-invoice-page]
labels: [frontend]
stories: [15, 16, 17, 18, 19, 20, 21, 24, 25, 35]
---

## Context

Second slice of `spec.md` (`## Execution order`), "Frontend: Agent": new page `/agent/client-invoices/{invoiceId}` with a breadcrumb back to Client Invoices, rendering `AgentClientInvoiceCard` (extracted by the blocker). A sent-back draft gets a `warning`/`warning-bg` notice ("Sent back by the Manager on {date}", the reason, "Correct what is needed, then send it again."), its lines in the editor exactly as sent plus any late Fee as a new line, Attach file and Send; after a successful send the page refreshes and shows the invoice as sent. A past-month draft never sent renders read-only, with no editor, Attach file or Send. Another Agent's id or an unknown id shows the not-found state. Also the **Sent back** `warning` badge as a derived display state in `lib/status.ts`'s Client Invoice maps, and the send confirmation copy "Sends to the Manager and Client, and locks the numbers. Only the Manager can send it back to you." Modules: `app/agent`, `components/agent`, `lib/api`. Follows `docs/agents/frontend.md` and `DESIGN.md` (Operate).

## Acceptance criteria

- [ ] Opening the by-id page of a sent-back invoice shows the notice ending "Correct what is needed, then send it again.", a **Sent back** warning badge instead of Draft, the lines as sent plus a late Fee as a new editable line, Attach file and Send.
- [ ] Editing a Postpaid SIM line and a Fee line and attaching a file from that page work; Send asks for confirmation reading "Only the Manager can send it back to you." (the old "This can't be undone" is gone), and confirming leaves the page showing the invoice as sent with the new amounts.
- [ ] A past-month draft never sent opens read-only with no editor, Attach file or Send.
- [ ] Another Agent's invoice id, an unknown id, and another Tenant's id show the not-found state.
- [ ] The current-month page shows the new confirmation copy too, and its draft card is otherwise unchanged.
- [ ] The page is usable by keyboard alone with a visible focus ring, and at the mobile breakpoint within the viewport.

## Tests

- **Component seam (spec Testing decisions 4):** `client-invoices-view.test.tsx` gains `sent-back-badge-and-notice-with-correct-what-is-needed-line`, `editor-present-on-sent-back-draft-and-calls-by-id-route`, `editor-attach-and-send-absent-on-past-month-draft-never-sent`, `send-confirmation-says-only-the-manager-can-send-it-back`; a new case at the component seam, `by-id-page-shows-not-found-for-other-agent-unknown-and-other-tenant-id`; the **Sent back** badge is proven through the rendered view by `sent-back-badge-and-notice-with-correct-what-is-needed-line` (no standalone `lib/status.ts` test).
- No e2e here: `agent-sees-sent-back-invoices-on-the-client-invoices-page` plays the journey.

## Regression

- At risk: the Agent's current-month page and send confirmation, the Client Invoice status badge everywhere `lib/status.ts` is used, the visual golden of the Agent Client Invoices page (must not move, no new goldens).
- Existing tests expected to change: `frontend/components/agent/client-invoices-view.test.tsx` gains the cases above and every pre-existing case passes unmodified. No existing test asserts the old "This can't be undone" copy for the Client Invoice send; if one is found, it is changed for that copy only and named in the commit. `edit-client-invoice-line-control.test.tsx` and the e2e specs are not modified.

## Observability

N/A — presentation over the by-id routes; their audit lines are the backend tickets'.
