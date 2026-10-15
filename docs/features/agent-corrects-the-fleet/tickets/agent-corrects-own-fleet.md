---
id: agent-corrects-own-fleet
title: Let the Agent correct the Fleet of their own Contracts
status: done
depends_on: [serial-edit-matches-corrections]
labels: [backend, frontend, fleet]
stories: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 25, 28]
---

## Context

Gives the Contract's own Agent the four Fleet corrections (SIM number,
Postpaid plan, Smartphone model, Smartphone owner); the Manager is unchanged.
Everything a correction does is the predecessor's
(`docs/features/correct-a-fleet-unit/spec.md`) and is not touched. See
`spec.md`, "Backend: authorization", "Frontend: the three Fleet pages" (Agent
row) and "Product and glossary amendments". It depends on
`serial-edit-matches-corrections`: the Agent's Smartphone row now holds the
matched serial, model and owner Edits, and both recapture goldens.

Scope notes:
- `FleetAccessGuard.requireCanCorrect` gets its Agent arm (the existing `requireOwnsContractAsAgent` check) and its Javadoc is reworded; the `AuditLog` Javadocs and the frontend route handlers' doc comments that say "Manager or Tester" are reworded to "Manager or Agent". The Tester arm is left to `tester-fleet-read-only`.
- The Agent's Fleet page reads the catalog once with `loadActiveCarriers()` and passes `canCorrect`, `carriers` and `carriersHref="/agent/carriers"`.
- `PRODUCT.md`'s Local Support Agent line is edited as in the spec; the glossary change to **Agent** (and the **Correction** and **Owner** terms as they concern the Agent) is declared in the result for the merger, not edited.
- Stories 22-24 and 26 belong to `tester-fleet-read-only`. Story 28 is shared: this ticket carries the Agent half.

## Acceptance criteria

- [ ] An Agent gets `200` on each of the four corrections on a unit of their own Contract, and the next Fleet read shows the new value.
- [ ] An Agent gets `403` on each of the four corrections on a Contract of another Agent. A unit in the Agent's own Agent Stock, sent under their own Contract's path, gets `404` and is unchanged.
- [ ] An Agent's plan correction moves the current month's draft Client Invoice and the draft Agent Invoice; an Agent's owner correction while an open Return names the phone returns `409 SMARTPHONE_ON_OPEN_RETURN`. The Manager keeps all four corrections on any Contract of the Tenant.
- [ ] An Agent's changing correction writes exactly one audit line with `role=AGENT`, the Contract, the unit and the old and new value; saving the value already held writes none.
- [ ] The Agent's Fleet page shows an Edit beside each SIM number, each Postpaid SIM's plan, each Smartphone's model and owner, beside the status, serial and "Installed in" controls, which still work. The plan picker offers the active plans of the SIM's Carrier with the fee, and points to `/agent/carriers` when the Carrier has no active plan.
- [ ] After an Agent saves a plan, the Monthly fee column shows the new price; after a reload the corrected values still show, and the Agent's table still scrolls sideways at 375px wide.
- [ ] `PRODUCT.md`'s Local Support Agent line reads as in the spec, and the `agent-fleet` visual surface exists.

## Tests

- **Backend HTTP API seam** (`IntegrationTest`, no repository mocks), flipping in place `SmartphoneCorrectionApiTest` and `SimCardCorrectionApiTest` for the Agent cases only:
  - Agent own Contract (`agentToken`, Contracts with `SEEDED_AGENT_ID`): `200` and a later Fleet `GET` shows the value, for each of the four fields (replaces today's Agent `403` cases).
  - Agent on another Agent's Contract: `403`, built as `SimCardInstalledInApiTest.anAgentOnAnotherContractCannotSetInstalledIn` does.
  - Agent-Stock unit under the Agent's own Contract path: `404`, value unchanged (`findByIdAndContractId`).
  - Agent plan correction moves the draft Client Invoice and the draft Agent Invoice by the price difference.
  - Agent owner correction with an open Return naming the phone: `409 SMARTPHONE_ON_OPEN_RETURN`.
  - Agent correction emits one AUDIT line with `role=AGENT` and the old and new value (the appender pattern already in `SimCardCorrectionApiTest`); an Agent no-op emits none.
  - Pre-catalog SIM (a Carrier, no plan) and every Manager case stay green.
- **Frontend component seam** (Vitest and Testing Library): in `smartphone-fleet-table.test.tsx` and `sim-card-fleet-table.test.tsx`, rename the Tester-flavoured `canCorrect` cases to the roles that now pass those props and add one case each for `canCorrect` with `canEditSerial` and `changeStatus` (the Agent's combination) rendering every control in one row, plus a Postpaid SIM with no active plan and `carriersHref="/agent/carriers"` showing the pointer.
- **E2E against the real backend** (`frontend/tests/e2e/fleet-management.spec.ts`, each test makes its own units): the four "the Agent's Fleet page offers no correction" tests become the Agent correcting each field on their own Contract and seeing it after a reload.
- **Visual** (existing backend-stubbed suite): add the `agent-fleet` surface (the stub already serves `GET /api/carriers` for the Agent).

## Regression

- Shipped Manager corrections and the predecessor's value rules, codes, messages, money effects and audit event are at risk from the guard change; the Manager cases in `SmartphoneCorrectionApiTest`, `SimCardCorrectionApiTest` and the Manager e2e tests protect them and must keep passing unmodified.
- Existing tests this ticket changes, and why: `SmartphoneCorrectionApiTest` and `SimCardCorrectionApiTest` (the Agent `403` cases flipped to `200`, new Agent cases added; every other method unmodified); `smartphone-fleet-table.test.tsx` and `sim-card-fleet-table.test.tsx` (Tester-flavoured case names renamed, cases added; every other method unmodified); `fleet-management.spec.ts` (the four Agent tests flipped; every other test unmodified); `frontend/tests/visual/surfaces.spec.ts` (gains the `agent-fleet` slug and surface entry; every pre-existing surface and test keeps passing unmodified); `stub-backend.mjs` only if the Agent's Fleet needs a stub it lacks, adding without altering existing responses.
- The Agent's existing status, serial and installed-in writes and the Agent's Requests page must be unaffected.

## Observability

An Agent's correction emits the existing audit event unchanged, with `role=AGENT`; a no-op emits none. No new events or metrics. Declare for the merger the narrowed glossary terms touching the Agent and the new `agent-fleet` visual surface.
