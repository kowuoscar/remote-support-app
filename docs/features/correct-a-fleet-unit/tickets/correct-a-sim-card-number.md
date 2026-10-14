---
id: correct-a-sim-card-number
title: Manager and Tester correct a SIM Card's number
status: done
depends_on: [correct-a-smartphone-model]
labels: [backend, frontend, fleet]
stories: [1, 5, 9, 10, 12, 13, 21, 25, 26, 27, 28]
---

## Context

Second slice of `spec.md`. Reuses `requireCanCorrect` from
`correct-a-smartphone-model` (the real gate: the guard method and the first
version of the two Fleet-page visual surfaces come from it). Adds `PATCH …/sim-cards/{simCardId}/number`,
the `simCardNumberCorrected` audit event, `canCorrect` on `SimCardFleetTable`,
`SimCardNumberControl` and its route handler (built with `forwardJson`), and
records the duplicate-number gap in `docs/tech-debt.md`. A SIM number is not
checked for duplicates (Decisions taken).

## Acceptance criteria

- [ ] On the Manager's Contract Fleet and the Tester's Fleet, each SIM Card row has an "Edit" button for its number. Saving "  +44 7700 900123  " shows "+44 7700 900123" in the row and after a reload.
- [ ] A Manager on any Contract of the Tenant and any Tester on their own Client's Contract get `200`; a Tester on another Client's Contract and any Agent get `403`; no token `401`; another Tenant `404`. A Tester still gets `403` on a SIM's status and "Installed in".
- [ ] A blank number returns `400 NUMBER_BLANK`; 65 characters after trimming returns `400 NUMBER_TOO_LONG`, 64 succeeds; the value is unchanged. In the browser a blank number sends no request ("Enter a number."), and the length and failure messages show with the typed text kept.
- [ ] A corrected number shows in Fleet tables, Requests, returned units and Agent Stock, and on a sent Client Invoice's SIM line with the line's amount unchanged.
- [ ] Saving the number already held returns `200`, changes nothing and logs nothing; Cancel restores the old value; a failed save shows "Couldn't update. Try again." with the row unchanged.
- [ ] The control is reachable and operable by keyboard with visible focus inside the scrolling table; the Agent's Fleet offers no correction and "Installed in" stays editable only where it was.
- [ ] A changing correction writes one `simCardNumberCorrected` audit event with Contract, unit, old and new value, actor and role, and Tenant. `docs/tech-debt.md` records that two SIM Cards may share a number.

## Tests

- **Backend, HTTP API seam (`IntegrationTest`):** new `SimCardCorrectionApiTest`, number half. Cases: Manager 200 and Tester-own-Client 200 with a Fleet GET showing the trimmed value; Tester other Client 403; Agent 403; no token 401; other Tenant 404 (`OtherTenantFixture`); Tester 403 on status and installed-in; blank, 64/65 boundary; same-value 200 no-op; corrected number read through a Request naming the SIM, a returned unit's view and Agent Stock after the SIM is returned, and on a sent Client Invoice's SIM line with its amount unchanged. Audit not asserted.
- **Frontend component tests:** `sim-card-number-control.test.tsx`: Edit shows current value; Cancel restores; blank refused with no request; each code maps to its message with typed value kept; success refreshes. new `frontend/components/fleet/sim-card-fleet-table.test.tsx`: with `canCorrect` the number control renders, without it plain text; "Installed in" unchanged. Keyboard case (user-event): Tab reaches "Edit", Enter opens it, Enter saves; visible focus and sideways scrolling stay with walkthrough step 11.
- **Route handler:** one `route.test.tsx` for the `number` handler.
- **Playwright e2e** in `fleet-management.spec.ts`: Manager and Tester each correct a SIM number and see it after reload; Agent's page offers none.
- **Visual goldens:** Manager Contract Fleet and Tester Fleet recaptured, deleted first.

## Regression

At risk: SIM status and "Installed in" controls, Tester read-only SIM columns, Client Invoice SIM lines. Protected by `SimCardApiTest`, `SimCardInstalledInApiTest`, `sim-card-installed-in-control.test.tsx` and the Client Invoice API tests.

Existing tests expected to change:
- `frontend/tests/e2e/fleet-management.spec.ts`: gains tests; every pre-existing test keeps passing unmodified.
- The Manager Contract Fleet and Tester Fleet goldens (added by `correct-a-smartphone-model`): deleted, then recaptured, diff limited to the number "Edit" button.
- `frontend/components/fleet/sim-card-fleet-table.test.tsx` is a new file, not a modification.

## Observability

Add the audit event `simCardNumberCorrected` (unit id, Contract id, old and new number, actor user id, role, Tenant).
