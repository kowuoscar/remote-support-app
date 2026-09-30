---
id: agent-dashboard-invoice-figures
title: Show the Agent's Local Support Fees and invoice status from this month's Agent Invoice
status: in-progress
depends_on: [agent-dashboard-identity-and-standing-amounts]
labels: [frontend]
stories: [3, 4, 5, 6, 8, 20]
---

## Context

Third slice of `spec.md` (`## Execution order`, item 3). The Local Support Fees and My Invoice status cards read this month's Agent Invoice through `GET /api/agents/{agentId}/invoice`, the same get-or-create read My Invoice uses (spec `## Solution`, "Where each figure comes from" and "Frontend: the page"). It needs the Agent's id from the read and the loader and nullable stat props that `agent-dashboard-identity-and-standing-amounts` adds. `agent-dashboard-requests` follows it, because the page's remaining imports from `frontend/lib/demo/agent.ts` (`currentMonthLabel`, `myAgentInvoices`, `runningLocalSupportFees`) are removed here, so that ticket can delete the file.

Constraints from the spec: Local Support Fees is the invoice's own `localSupportFees` line, never summed from Fees; every month label is the invoice's `billingMonth`, never the wall clock; a failed region renders `—`, never `0`.

## Acceptance criteria

- [ ] The Local Support Fees card shows exactly the invoice's `localSupportFees` in the Agent's currency, labelled with the invoice's `billingMonth`.
- [ ] While the invoice is Draft the card's meta reads "Running total, all your Contracts"; from Sent onward it reads "As sent on your invoice".
- [ ] The My Invoice status card shows the invoice's real status label and tone and its month.
- [ ] If the invoice cannot be loaded, both cards render `—` with "Couldn't load your invoice" while the header, standing amounts and other cards still render.
- [ ] Opening the dashboard changes nothing beyond what opening My Invoice already does: the first visit of the month creates the same Draft My Invoice then opens, and the Manager's Review Queue is unchanged.
- [ ] The page no longer imports `currentMonthLabel`, `myAgentInvoices` or `runningLocalSupportFees` from `frontend/lib/demo/agent.ts`.
- [ ] The degraded stub token answers the invoice route with `500`; the stub's Draft invoice for `visual-agent-session` has a fixed `billingMonth`; the four `agent-*` goldens are deleted and recaptured, unmasked.

## Tests

- **Component seam (Vitest, spec Testing decisions 2):** add cases to the existing `AgentDashboardStats` test file for the Local Support Fees month label, Draft versus Sent meta, and the `—` / "Couldn't load your invoice" state on both cards.
- **Visual (Testing decisions 4):** recaptured goldens show the fixed month and Draft meta; a run against the degraded token shows both cards unavailable.
- **E2E (spec Testing decisions 3), new spec `agent-dashboard.spec.ts` in `frontend/tests/e2e`, shape of `agent-invoice-submission-and-approval.spec.ts`, unconditional cases:** `dashboard-shows-invoice-status-and-amounts` (Local Support Fees equals the invoice's `localSupportFees`, month is its `billingMonth`, status card matches); `dashboard-draft-is-the-one-my-invoice-opens`; `dashboard-visit-leaves-review-queue-unchanged` (Manager lists the Review Queue before and after the first visit). `agent-dashboard-requests` extends this file.
- **Backend:** N/A, no backend change; the invoice route is existing and covered by `AgentInvoiceApiTest`.

## Regression

- At risk: My Invoice page (shares the get-or-create read), the Review Queue (a Draft must not enter it), and the other visual goldens.
- Existing tests expected to change: none of the e2e specs (the new spec is a new file); the `AgentDashboardStats` component test file added by `agent-dashboard-identity-and-standing-amounts` gains cases (every pre-existing case keeps passing unmodified); `frontend/tests/visual/stub-backend.mjs` gains the invoice route and the degraded token's `500`; the four `agent-*` goldens are replaced for the reason in the spec's Visual suite section. `frontend/tests/visual/surfaces.spec.ts` gains one case, `degraded-invoice-shows-both-invoice-cards-unavailable`, for the degraded token; existing cases unchanged (added by the orchestrator after the merger's test guard, 2026-09-30).

## Observability

A failed invoice load is logged server-side with the label "agent invoice" and the response status.
