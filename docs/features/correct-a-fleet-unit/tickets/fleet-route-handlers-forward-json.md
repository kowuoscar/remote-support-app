---
id: fleet-route-handlers-forward-json
title: Fleet status, serial and installed-in route handlers use forwardJson
status: in-progress
depends_on: []
labels: [enabler, frontend]
stories: []
---

## Context

Prefactor enabling the correction tickets (`correct-a-smartphone-model`, `correct-a-smartphone-owner`, `correct-a-sim-card-number`, `correct-a-postpaid-plan`), which add four route handlers built with
`forwardJson` (Frontend rule 13) beside these four. `spec.md` Solution,
"Frontend: route handlers", and Decisions taken. It changes no behaviour.

The handlers: `smartphones/[smartphoneId]/{serial,status}` and
`sim-cards/[simCardId]/{status,installed-in}`, under
`frontend/app/api/contracts/[contractId]/`.

## Acceptance criteria

- [ ] `grep -L forwardJson` over the four route files lists none, and `grep -l NextResponse` over them finds no hand-built response.
- [ ] Each handler forwards the same method, path and body to the same backend route, returns the backend's status and JSON body unchanged, and still refuses a call with no session as it did.
- [ ] The Agent's Fleet page still changes a Smartphone's serial and status and a SIM Card's status and "Installed in"; the Manager's Contract page still sets a serial and "Installed in".

## Tests

- **Seam:** a route-handler test per handler (four new `route.test.tsx` files beside the routes; prior art `frontend/app/api/session/route.test.tsx` and `frontend/lib/api/forward-json.test.tsx`). Cases for each: the method, path and body reach the backend route; a success status and JSON body come back unchanged; an error status (for example `400` with a `{code, message}` body) comes back unchanged; a call with no session is refused as before.
- **Success-path proof:** the existing Playwright e2e `frontend/tests/e2e/fleet-management.spec.ts` (serial, status and installed-in through the real backend). Component tests stub `fetch` and do not reach these routes, so they are not the proof.

## Regression

At risk: the serial, status and installed-in controls on the Agent and Manager Fleet pages. Protected by `frontend/tests/e2e/fleet-management.spec.ts` (and the serial and installed-in control component tests). No existing test is expected to change; the four route-handler test files are new.

## Observability

N/A — a refactor with no behaviour change; the routes log as before.
