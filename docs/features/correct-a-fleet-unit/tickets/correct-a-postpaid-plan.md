---
id: correct-a-postpaid-plan
title: Manager and Tester correct a Postpaid SIM's plan and its monthly fee
status: ready-for-agent
depends_on: [correct-a-sim-card-number]
labels: [backend, frontend, fleet]
stories: [2, 6, 9, 10, 14, 15, 16, 17, 18, 19, 20, 25, 26, 27, 28]
---

## Context

Third slice of `spec.md`. Adds `PATCH …/sim-cards/{simCardId}/postpaid-plan`
(reusing `SimCardFactory.requireUsablePlan` and the archived half of
`requireUsableCarrier`), re-copying the plan's price into `monthlyFeeAmount`
in one transaction; the `simCardPostpaidPlanCorrected` event; `carriers` on
`SimCardFleetTable`; `SimCardPlanControl` (the `PostpaidPlanPicker`, showing
the fee before Save) and its route handler (built with `forwardJson`); the
Tester Fleet page reading each Contract's `GET /api/contracts/{id}/carriers`;
the visual stub serving the Tester's carriers. Depends on
`correct-a-sim-card-number` because both edit the SIM table and its controls
and share `SimCardCorrectionApiTest`. Money effects follow ADR 0003 and 0004
(Solution, "What a plan correction does to money"); no rule is added.
Declares the Carrier glossary term (monthly fee clause) for the merger.

## Acceptance criteria

- [ ] On a Postpaid SIM whose Carrier is active, the Manager's and the Tester's Fleet offer an "Edit" button on the plan listing only the active plans of that SIM's own Carrier, showing the chosen plan's monthly fee before Save. A Prepaid SIM, a SIM with no Carrier and a SIM with an archived Carrier have no plan control.
- [ ] Saving sets the SIM's plan and its monthly fee to the plan's price, and the Manager's Monthly fee column shows it immediately. A Manager (any Contract of the Tenant) and a Tester (own Client) get `200`; a Tester of another Client and any Agent get `403`; no token `401`; another Tenant `404`.
- [ ] `PREPAID_HAS_NO_PLAN`, `CARRIER_NOT_USABLE` (no Carrier or archived Carrier), `PLAN_NOT_ON_CARRIER` and `PLAN_ARCHIVED` return `400` with their code and leave the SIM unchanged; the browser shows "That plan can't be chosen any more. Pick another." with the choice kept.
- [ ] A pre-catalog Postpaid SIM that names a Carrier but no plan can be given a plan, and its fee becomes the plan's price.
- [ ] After a correction, the current month's draft Client Invoice (and an earlier never-sent draft) with no stored line for that SIM shows a base amount moved by the price difference; the draft Agent Invoice's Local Support Fees move by the same amount.
- [ ] A draft Client Invoice storing a line for that SIM, a sent, approved or sent-back Client Invoice, and a sent, approved or paid Agent Invoice return exactly what they did before.
- [ ] Saving the plan already held returns `200`, changes nothing and logs nothing; Cancel restores; a failed save shows "Couldn't update. Try again."; the control is keyboard-operable with visible focus in the scrolling table; the Agent's Fleet offers none. A changing correction writes one `simCardPostpaidPlanCorrected` audit event with old and new plan ids, old and new monthly fee, Contract, unit, actor, role and Tenant.

## Tests

- **Backend, HTTP API seam (`IntegrationTest`):** `SimCardCorrectionApiTest`, plan and money half. Cases: Manager 200, Tester own Client 200, Tester other Client 403, Agent 403, 401, other Tenant 404; Prepaid SIM; other Carrier's plan; archived plan; archived Carrier; no Carrier; pre-catalog SIM with a Carrier given a plan; fee equals the plan's price after; later plan re-pricing leaves the fee alone; draft Client Invoice base amount and draft Agent Invoice Local Support Fees move by the difference; an earlier never-sent (Late) draft Client Invoice moves by the same difference; stored-line draft, sent-back, sent and approved Client Invoices unchanged; sent, approved and paid Agent Invoices unchanged; same-plan no-op. Prior art: `PostpaidSimPlanApiTest`, `ClientInvoiceStoredLinesApiTest`, `ClientInvoiceSendBackApiTest`, `AgentInvoiceApiTest`.
- **Frontend component tests:** new `sim-card-plan-control.test.tsx`: only active plans of the SIM's Carrier offered; fee shows for the chosen plan; Cancel restores; each code maps to the message with the choice kept; success refreshes. `sim-card-fleet-table.test.tsx` (created by `correct-a-sim-card-number`) gains cases: no plan control on a Prepaid SIM, none without `carriers` or with an archived Carrier. Keyboard case (user-event): Tab reaches "Edit", Enter opens it, Enter saves; visible focus and sideways scrolling stay with walkthrough step 11.
- **Route handler:** one `route.test.tsx` for the `postpaid-plan` handler.
- **Playwright e2e** in `fleet-management.spec.ts`: Manager and Tester each correct a Postpaid SIM's plan and see the new fee/plan after reload; Agent offered none.
- **Visual goldens:** Manager Contract Fleet and Tester Fleet recaptured, deleted first; `frontend/tests/visual/stub-backend.mjs` gains the Tester's `/api/contracts/{id}/carriers`.

## Regression

At risk: Client Invoice base amounts, Agent Invoice Local Support Fees, the fee-at-creation rule, Tester Fleet page loading. Protected by `PostpaidSimPlanApiTest` (its re-pricing tests at :147 and :173), `ClientInvoiceStoredLinesApiTest`, `ClientInvoiceSendBackApiTest`, `AgentInvoiceApiTest`.

Existing tests expected to change:
- `SimCardCorrectionApiTest` (created by `correct-a-sim-card-number`): gains the plan and money cases; every earlier method keeps passing unmodified.
- `frontend/tests/e2e/fleet-management.spec.ts`: gains tests; every pre-existing test keeps passing unmodified.
- The Manager Contract Fleet and Tester Fleet goldens (added by `correct-a-smartphone-model`): deleted, then recaptured, diff limited to the plan "Edit" button.
- `frontend/tests/visual/stub-backend.mjs`: gains the carriers route; every route it already serves keeps working unmodified.
- `frontend/components/fleet/sim-card-fleet-table.test.tsx` (created by `correct-a-sim-card-number`): gains `carriers` cases; every earlier case keeps passing unmodified.

## Observability

Add the audit event `simCardPostpaidPlanCorrected` (unit id, Contract id, old and new plan ids, old and new monthly fee, actor user id, role, Tenant). Declare the Carrier glossary term (narrowed, monthly fee clause).
