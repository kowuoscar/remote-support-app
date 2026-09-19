---
id: replace-requests
title: Replace Smartphone and Replace SIM Requests retire a unit and add its replacement
status: done
depends_on: [provision-request-details]
labels: [backend, frontend, requests, fleet, invoicing]
---

## Context

Implements `spec.md` Solution (Types; Details and Fleet changes for the Replace types) and user stories 11, 12, 29 and 30.

## Acceptance criteria

- [x] Replace Smartphone and Replace SIM are Request types and Fee types, offered to Testers and Agents
- [x] A Replace Smartphone Request requires an Active Smartphone of its Contract and may name a different model; a Replace SIM Request requires an Active SIM Card of its Contract
- [x] Completing a Replace Smartphone retires the named Smartphone, adds a company-owned one with the requested or the same model, and moves the old one's SIM Cards into it; no Agent input
- [x] Completing a Replace SIM asks for the new SIM Card's details, defaulted from the old one's; the old SIM Card is retired and the new one is installed where the old one was
- [x] Completion is refused with a clear message if the named unit is no longer Active
- [x] A Fee can be logged against a Replace Request
- [x] The Requests lists name the unit being replaced

## Tests

- **API seam:** validation on both paths; both completion effects including SIM carry-over and slot take-over; named unit already retired; Fee against a Replace Request; new SIM Card obeys the Carrier and Plan rules.
- **Component seam:** the two details sections; the Replace SIM completion form defaults.
- **E2E:** a Tester submits a Replace SIM; the Agent completes it; the Fleet shows the old SIM Card retired and the new one in the same Smartphone.

## Regression

Provisioning and retire paths, Fee totals. `PostpaidSimPlanApiTest`, `FeeApiTest` and the provisioning e2e must pass unchanged.

## Observability

A unit-replaced audit event: Request id, retired unit id, new unit id, actor, tenant.
