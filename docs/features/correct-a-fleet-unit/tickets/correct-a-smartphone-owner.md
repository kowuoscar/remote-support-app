---
id: correct-a-smartphone-owner
title: Manager and Tester correct a Smartphone's owner
status: done
depends_on: [correct-a-smartphone-model]
labels: [backend, frontend, fleet]
stories: [4, 8, 23, 24, 25, 26, 27, 28]
---

## Context

Second Smartphone slice of `spec.md`. Reuses `requireCanCorrect` and the
`canCorrect` table split from `correct-a-smartphone-model`. Adds
`PATCH …/smartphones/{id}/owner` (`CLIENT` | `COMPANY`), the
`409 SMARTPHONE_ON_OPEN_RETURN` refusal, the `smartphoneOwnerCorrected` audit
event, `SmartphoneOwnerControl` (a two-option select behind Edit, Save,
Cancel) and its `forwardJson` route handler. The owner decides a Return's path
(Solution, owner rule).

## Acceptance criteria

- [ ] On the Manager's Contract Fleet and the Tester's Fleet, each Smartphone row has an "Edit" button for its owner; saving shows the new owner in the row and after a reload, in both directions (Client and Company).
- [ ] A Manager (any Contract of the Tenant) and a Tester (own Client) get `200`; a Tester on another Client's Contract and any Agent get `403`; no token `401`; another Tenant `404`.
- [ ] While a Return naming the Smartphone is Pending Approval, Submitted or In Progress, an owner change returns `409 SMARTPHONE_ON_OPEN_RETURN`, the owner is unchanged, and the control shows "This smartphone is on an open Return. Change its owner once the Return is done."
- [ ] Once that Return is Completed, Cancelled or Rejected, the owner can be corrected.
- [ ] A Return raised after a Company to Client correction goes to Posted to Client with no approval; one raised after a Client to Company correction is Pending Approval.
- [ ] Saving the owner already held returns `200`, changes nothing and logs nothing; Cancel restores; a failed save shows "Couldn't update. Try again." with the row unchanged; the select is operable by keyboard with visible focus in the scrolling table.
- [ ] A changing correction writes one `smartphoneOwnerCorrected` audit event (Contract, unit, old and new owner, actor user id, role, Tenant).

## Tests

- **Backend, HTTP API seam (`IntegrationTest`):** `SmartphoneCorrectionApiTest`, owner half (prior art `ReturnRequestsApiTest` for building Returns). Cases: Manager 200, Tester own Client 200, Tester other Client 403, Agent 403, no token 401, other Tenant 404; both directions; 409 with a Pending Approval Return, a Submitted Return and an In Progress Return; success after the Return is cancelled; success after Completed; success after a Pending Approval Return is Rejected; a Return after COMPANY to CLIENT goes to `POSTED_TO_CLIENT` with no approval step; a Return after CLIENT to COMPANY goes to Pending Approval; same-value 200 no-op. Audit not asserted.
- **Frontend component tests:** new `smartphone-owner-control.test.tsx`: Edit opens the select with the current owner; Cancel restores; `SMARTPHONE_ON_OPEN_RETURN` shows its message with the choice kept; unknown code shows the generic message; success leaves editing and refreshes. `smartphone-fleet-table.test.tsx` (created by `correct-a-smartphone-model`) gains an owner-control case. Keyboard case (user-event): Tab reaches "Edit", Enter opens it, Enter saves; visible focus and sideways scrolling stay with walkthrough step 11.
- **Route handler:** one `route.test.tsx` for the `owner` handler.
- **Playwright e2e** in `fleet-management.spec.ts`: Manager and Tester each correct an owner and see it after reload.
- **Visual:** goldens for the two Fleet pages recaptured, deleted first.

## Regression

At risk: Return approval and Disposition paths; the Smartphone table. Protected by `ReturnRequestsApiTest`, `SmartphoneApiTest`.

Existing tests expected to change:
- `frontend/tests/e2e/fleet-management.spec.ts`: in the model-correction tester test (from `correct-a-smartphone-model`), `getByRole("button", { name: "Edit" }).click()` becomes `.first().click()`, because the owner "Edit owner" button also matches "Edit" by substring under strict mode; the assertion is unchanged. (Added by the orchestrator after implementation.)
- `SmartphoneCorrectionApiTest` (created by `correct-a-smartphone-model`): gains the owner cases; every earlier method keeps passing unmodified.
- `frontend/tests/e2e/fleet-management.spec.ts`: gains owner tests; every pre-existing test keeps passing unmodified.
- The two Fleet-page goldens added by `correct-a-smartphone-model`: recaptured, diff limited to the owner "Edit" button.
- `frontend/components/fleet/smartphone-fleet-table.test.tsx` (new in the model ticket): gains a case; every earlier case keeps passing unmodified.

## Observability

Add the audit event `smartphoneOwnerCorrected` (unit id, Contract id, old and new owner, actor user id, role, Tenant).
