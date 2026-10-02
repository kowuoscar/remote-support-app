---
id: agent-edits-lines-on-the-client-invoice-page
title: Let the Agent edit and reset invoice lines on the Client Invoices page
status: ready-for-agent
depends_on: [local-support-fees-follow-billed-client-invoice-lines]
labels: [frontend]
stories: [2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 14, 24]
---

## Context

Fifth slice of `spec.md` (`## Execution order`), "Frontend: Agent" and "Design direction". On the draft card in `AgentClientInvoicesView`, each Postpaid SIM and Fee row gets an Edit row action opening a new `EditClientInvoiceLineControl` (inline amount field, Save, Cancel, copied from `AgentInvoiceOverrideControl`, no dialog). An edited line shows "Edited · computed {amount}" and a Reset row action that saves the computed amount. Plus the hint line, one pass-through BFF proxy for the new route using `backendFetch`, and the new fields in `lib/api/types.ts`. Modules: `components/agent`, `app/api`, `lib/api`.

It depends on the backend ticket for the pay rule because its e2e opens the Agent Invoice and sees Local Support Fees move. The Agent Invoice page itself is unchanged. Save is not a pill (Pill-Is-Primary Rule); every amount uses `Money` with `.tnum`. It follows `docs/agents/frontend.md`. The Agent's draft golden, if captured, moves here, re-approved with the reason in the commit; no other golden may move.

## Acceptance criteria

- [ ] On a draft, each Postpaid SIM row and Fee row has an Edit action; saving a valid amount refreshes the page with the base amount, Fees total and total following, and the line showing its billed amount (not the Postpaid SIM's monthly fee) marked "Edited · computed {amount}" with a Reset action.
- [ ] Reset returns a line to its computed amount and its marker disappears.
- [ ] A `400` shows the server's message inline and keeps what was typed (for example `-5`); a `409` shows "This invoice was sent. Refresh to see it."; any other failure shows a generic retry message; no reason is asked for.
- [ ] The draft shows the hint "You can adjust any line to what was actually billed. Your Agent Invoice for this month follows these amounts, unless it is already approved."
- [ ] A sent or approved invoice shows no Edit or Reset and still shows the "Edited · computed" line on edited lines.
- [ ] Edit, Save, Cancel and Reset work by keyboard alone with a visible focus ring, and at the mobile breakpoint the row actions and field are usable within the viewport.
- [ ] End to end on a Contract the spec creates: the Agent edits the SIM line to $31.40 and the Fee line to $40.00 and sees $71.40, the Agent Invoice's Local Support Fees are $1.40 higher, resetting the Fee line clears its marker, and the send succeeds.

## Tests

- **Component seam (spec item 6):** `frontend/components/agent/client-invoices-view.test.tsx` and new `edit-client-invoice-line-control.test.tsx` beside `agent-invoice-override-control.test.tsx`. Cases: `edit-opens-field-save-submits-and-cancel-closes`, `400-shows-inline-message-and-keeps-input`, `409-shows-refresh-copy`, `generic-failure-shows-retry-message`, `edited-line-shows-marker-and-reset`, `no-edit-or-reset-on-sent-invoice`, `hint-line-shown-on-draft`.
- **e2e (spec item 7):** new `frontend/tests/e2e/edit-client-invoice-lines.spec.ts`, prior art `client-invoice-generation.spec.ts`, case `agent-edits-lines-sees-totals-and-agent-invoice-follow-then-resets-and-sends`.
- **Visual:** the Agent draft golden, only if captured.

## Regression

- At risk: the Agent's Client Invoices page and its send, the BFF proxy set, the Agent Invoice page, every other golden.
- Existing tests expected to change: `frontend/components/agent/client-invoices-view.test.tsx` gains the cases above and every pre-existing case passes unmodified; the Agent draft golden moves only if captured, with the reason in the commit. `client-invoice-generation.spec.ts` and `client-invoice-submission-and-visibility.spec.ts` guard the flow, unmodified.

## Observability

N/A — a client-side form over an existing route; the edit's audit line is the backend ticket's, and the BFF proxy logs failures as every other proxy does.
