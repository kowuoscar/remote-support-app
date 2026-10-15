---
id: tester-fleet-read-only
title: Make the Tester's Fleet a read-only view
status: done
depends_on: [agent-corrects-own-fleet]
labels: [backend, frontend, fleet]
stories: [22, 23, 24, 26, 28]
---

## Context

Removes the four Fleet corrections from the Client Tester, who only views
their Client's Fleet. See `spec.md`, "Backend: authorization" (Tester arm),
"Frontend: the three Fleet pages" (Tester row) and "Product and glossary
amendments". It depends on `agent-corrects-own-fleet` because both change the
same arms of `FleetAccessGuard.requireCanCorrect` and flip role cases in the
same two correction test classes and `fleet-management.spec.ts`; run in
parallel they would conflict on those lines.

Scope notes:
- The guard's Tester arm becomes a refusal (`403`, the existing "Not allowed to correct this Contract's Fleet"); no new code or message.
- The Tester's Fleet page drops `canCorrect`, `carriers` and the per-Contract `GET /api/contracts/{id}/carriers` load, and `contractId` where it only served corrections. Table comments naming the Tester ("Omitted for a Tester", "(Manager, Tester)") are reworded.
- Values a Tester already corrected stay as they are: no migration, no data change.
- `PRODUCT.md`'s Client Tester line returns to the spec's wording; the glossary change to **Tester** (and **Correction**, **Owner** as they concern the Tester) is declared for the merger.

## Acceptance criteria

- [ ] A Tester, including one of the Contract's own Client, gets `403` on each of the four corrections and the value is unchanged on the next read; a Tester of another Client gets `403`, no token `401`, another Tenant's Contract `404`.
- [ ] A Tester still gets `403` on status, serial and "Installed in".
- [ ] On every Contract of their Client, the Tester's Smartphones and SIM Cards tables show number, plan, model and owner as plain text, with no Edit, status, serial or "Installed in" control.
- [ ] The Tester's Requests page still offers its plan picker.
- [ ] A value a Tester corrected before this change still shows as corrected.
- [ ] `PRODUCT.md`'s Client Tester line reads as in the spec.

## Tests

- **Backend HTTP API seam** (`IntegrationTest`, no repository mocks), flipping in place the Tester cases of `SmartphoneCorrectionApiTest` and `SimCardCorrectionApiTest`: Tester of the Contract's own Client (`linkedTesterToken`) `403` on each of the four fields with the value unchanged (today `200`); Tester of another Client `403`; no token `401`; `OtherTenantFixture` `404`; Tester `403` on status, serial and installed-in unchanged.
- **E2E against the real backend** (`fleet-management.spec.ts`): the four Tester correction tests become one "a Tester sees the four values with no Edit" test per table, checking both Contracts of the Client; the Tester Requests flow still loads its catalog.
- **Frontend component seam**: no table test changes expected; the Requests page's plan picker stays covered by its existing tests, unmodified.
- **Visual**: delete the `client-fleet` golden, recapture it without the Edit buttons in the same commit as the code; diff shows only the removed Edits.

## Regression

- Existing tests this ticket changes, and why: `SmartphoneCorrectionApiTest` and `SimCardCorrectionApiTest` (the own-Client Tester `200` cases flipped to `403`; every other method, including the Agent and Manager cases, unmodified); `fleet-management.spec.ts` (the four Tester correction tests replaced; every other test unmodified); the `client-fleet` golden (deleted and recaptured).
- The Tester's Requests page keeps its own `/api/contracts/{id}/carriers` load and its stub in `stub-backend.mjs`, which must stay untouched. Manager and Agent corrections must keep passing unmodified.

## Observability

N/A — a refused correction is a plain `403` with no new event; the audit event is unchanged. Declare for the merger the narrowed glossary term Tester.
