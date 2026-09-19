---
id: trim-seed-to-test-baseline
title: Reduce the Flyway seed to the test baseline
status: ready-for-agent
depends_on: []
labels: [backend, seed-data]
---

## Context

Implements `spec.md` Solution (Test baseline) and user story 9.

## Acceptance criteria

- [ ] A new migration deletes the ad hoc demo rows earlier seed migrations inserted — Demo Client, its Contracts, Agents other than Jordan Ellis, the demo Tester login, their Fleets, Requests, Returned units, Fees and anything referencing them — by their fixed ids, in dependency order
- [ ] What remains is exactly: the tenant, the Manager login, the Agent login and Jordan Ellis, the Tester login, and the United States Carrier catalog with its Topup Options and Postpaid Plans
- [ ] No applied migration is edited
- [ ] Tests that used the removed rows create their own fixtures; `DEMO_TESTER_*` and `SEEDED_DEMO_*` constants are gone
- [ ] The migration runs cleanly on a fresh database and on a database that already holds extra, hand-made rows, leaving those rows untouched

## Tests

- **API seam:** the full backend suite on the trimmed baseline.
- **Migration test** (prior art: `SimCardCarrierMigrationTest`): migrate to just before the new version, add an unrelated hand-made Client, run it, and assert the demo rows are gone and the hand-made one remains.
- **E2E and visual:** full suites green on a fresh isolated database.

## Regression

Every test that relied on seed rows. The full backend, e2e and visual suites protect it.

## Observability

The migration logs how many rows it removed per table.
