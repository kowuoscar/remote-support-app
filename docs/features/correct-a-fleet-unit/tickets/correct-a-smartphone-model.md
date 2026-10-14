---
id: correct-a-smartphone-model
title: Manager and Tester correct a Smartphone's model
status: ready-for-agent
depends_on: []
labels: [backend, frontend, fleet]
stories: [3, 7, 9, 10, 11, 12, 13, 21, 22, 25, 26, 27, 28, 29]
---

## Context

First slice of `spec.md`. It adds `FleetAccessGuard.requireCanCorrect`, which
the other correction tickets reuse; `PATCH …/smartphones/{id}/model` with the
`smartphoneModelCorrected` audit event; the `canEditSerial` / `canCorrect`
split on `SmartphoneFleetTable`; `SmartphoneModelControl` and its route handler
(built with `forwardJson`); the first Tester-facing Fleet correction control;
the `PRODUCT.md` Client Tester amendment; and declares the glossary terms
Correction, Tester and Owner for the merger. It does not wait for
`fleet-route-handlers-forward-json`: the new handler is its own file. Model is
read live by Requests and returned units; recorded request models stay.

## Acceptance criteria

- [ ] On the Manager's Contract Fleet and the Tester's Fleet, each Smartphone row has an "Edit" button for its model; saving "  Pixel 8 Pro  " shows "Pixel 8 Pro" in the row and after a reload. The Manager's page still has no status control and the Agent's Fleet offers no correction.
- [ ] A Manager on any Contract of the Tenant and any Tester (Primary Contact or not) on a Contract of their own Client get `200`; a Tester on another Client's Contract and any Agent (the Contract's own included) get `403`; no token `401`; another Tenant's Contract `404`.
- [ ] A Tester still gets `403` on a Smartphone's status and serial, and the Tester's Fleet shows no serial or status control.
- [ ] A blank model returns `400 MODEL_BLANK` and 256 characters after trimming `400 MODEL_TOO_LONG` (255 succeeds), leaving the model unchanged; in the browser a blank model sends no request ("Enter a model.") and the length message shows with the typed text kept.
- [ ] A corrected model shows in Fleet tables, Requests and returned units, while a Request's requested model and a Replace Request's recorded old model are unchanged.
- [ ] Saving the model already held returns `200`, changes nothing and logs nothing; Cancel restores the old value; a failed save shows "Couldn't update. Try again." with the row unchanged.
- [ ] The control is operable by keyboard with visible focus inside the scrolling table, and a changing correction writes one `smartphoneModelCorrected` audit event (Contract, unit, old and new value, actor user id, role, Tenant).

## Tests

- **Backend, HTTP API seam (`IntegrationTest`, MockMvc):** new `SmartphoneCorrectionApiTest`, model half. Cases: Manager 200 and Tester-on-own-Client 200 (Primary Contact and not), each followed by a Fleet GET showing the trimmed value; Tester other Client 403; Agent 403; no token 401; other Tenant 404 (`OtherTenantFixture`); a Tester PATCH reaches the guard rather than the broad Manager-only rule; Tester 403 on status and serial; blank, 255 and 256; same-value 200 no-op; corrected model visible on a Request and a returned unit's view; requested and recorded Replace models unchanged. Audit events not asserted (spec).
- **Frontend component tests (Vitest, Testing Library):** new `smartphone-model-control.test.tsx`: Edit opens the field holding the current value; Cancel restores; blank refused with no request; `MODEL_TOO_LONG` and unknown codes map to their messages with typed value kept; success leaves editing and refreshes. New `frontend/components/fleet/smartphone-fleet-table.test.tsx`: with `canCorrect` the model control renders, without it plain text; `canEditSerial` alone governs the serial control. Keyboard case (user-event): Tab reaches "Edit", Enter opens it, Enter saves; visible focus and sideways scrolling stay with walkthrough step 11.
- **Route handler:** one `route.test.tsx` for the new `model` handler (pass-through of status and body, including an error status).
- **Playwright e2e** in `tests/e2e/fleet-management.spec.ts`: Manager corrects a model and sees it after reload; Tester does the same on their own Client's Fleet; Agent's Fleet offers no correction. Each test creates its own units.
- **Visual:** add the two surfaces below, with new goldens.

## Regression

At risk: serial and status controls on the three Fleet pages, the Tester's read-only Fleet, and `SecurityConfig` rule order. Protected by `SmartphoneApiTest`, `smartphone-serial-control.test.tsx` and the existing `fleet-management.spec.ts` tests.

Existing tests expected to change:
- `frontend/tests/e2e/fleet-management.spec.ts`: gains the model correction tests; every pre-existing test keeps passing unmodified.
- `frontend/tests/visual/surfaces.spec.ts`: gains two surfaces, the Manager Contract Fleet (`/manager/contracts/[contractId]`) and the Tester Fleet (`/client/fleet`); every earlier surface keeps passing unmodified. Their goldens do not exist today, so they are added here (new screenshots, not recaptured). Later tickets that move them delete and recapture them.
- `frontend/tests/visual/stub-backend.mjs`: gains the routes those two pages read; every route it already serves keeps working unmodified.
- The pages that pass `contractId` to `SmartphoneFleetTable` to enable the serial control now pass `canEditSerial` (reason: the prop split); `smartphone-serial-control.test.tsx` keeps passing unmodified.
- `PRODUCT.md`: the Client Tester line is amended as `spec.md` states.

## Observability

Add the audit event `smartphoneModelCorrected` beside `smartphoneSerialChanged`, logging unit id, Contract id, old value, new value, actor user id, role and Tenant. Declare to the merger the glossary terms Correction (added), Tester (narrowed) and Owner (narrowed).
