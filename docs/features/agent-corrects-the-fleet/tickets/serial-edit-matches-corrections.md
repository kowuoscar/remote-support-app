---
id: serial-edit-matches-corrections
title: Make the serial Edit look and read like the correction Edits
status: in-progress
depends_on: []
labels: [enabler, frontend, fleet]
stories: [27]
---

## Context

Prefactor for `agent-corrects-own-fleet`, which puts the serial Edit and
the model and owner correction Edits on the Agent's Smartphone row for the
first time. The serial Edit is a soft-indigo Row chip named "Edit"; the
correction Edits are the quiet `ghost` variant named per unit (debt F21 in
`docs/tech-debt.md`). See `spec.md`, "Prefactor: the serial Edit matches the
correction Edits". This also changes the Manager's Contract Fleet, where the
serial Edit already shows. Remove the F21 line from `docs/tech-debt.md`.

## Acceptance criteria

- [ ] On the Manager's Contract Fleet and on the Agent's Fleet, each Smartphone row's serial Edit is the quiet `ghost` button, visibly the same style as the model and owner Edits.
- [ ] Its visible text is "Edit" and its accessible name is "Edit serial, {serial}", or "Edit serial, {model}" while the Smartphone has no serial.
- [ ] Opening it, saving a serial, cancelling and the existing refusals behave as before.
- [ ] The `manager-contract-fleet` visual golden shows the ghost serial Edit and no other change.

## Tests

- **Frontend component seam** (Vitest and Testing Library, `smartphone-serial-control.test.tsx`): the trigger is found by name "Edit serial, SN-123"; with no serial it is found by "Edit serial, {model}"; open, save and cancel still work through that name.
- **Visual** (existing backend-stubbed suite): delete the `manager-contract-fleet` golden, recapture it in the same commit as the code, and check the diff shows only the serial Edit.

## Regression

- Any existing component or e2e test that finds the serial control by the name "Edit" (`smartphone-serial-control.test.tsx`, `smartphone-fleet-table.test.tsx`, `frontend/tests/e2e/fleet-management.spec.ts`) will break on the new name. Each is updated for the name only, with its assertions otherwise unchanged; every other pre-existing case keeps passing unmodified.
- The `manager-contract-fleet` golden is deleted and recaptured (see Tests); no other golden may move.

## Observability

N/A — a presentational and naming change, no new behaviour to log.
