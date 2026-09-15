---
id: fleet-management
title: Manage a Contract's Fleet of Smartphones and SIM Cards
status: in-progress
depends_on: [manager-entity-setup]
labels: [backend, frontend, fleet]
---

## Context

Implements the Fleet, Smartphone and SIM Card model from the spec's Solution and user stories 4, 21, 32.

## Acceptance criteria

- [ ] Manager can add a Smartphone to a Contract's Fleet
- [ ] Manager can add a SIM Card (Postpaid or Prepaid) to a Contract's Fleet
- [ ] Agent can view the Fleet of their own Contracts, filtered by Contract
- [ ] Tester can view their Client's Fleet, loaded per Contract
- [ ] Agent can change a Smartphone's status (Active → In Repair → Active, or Retired)
- [ ] Agent can change a SIM Card's status (Active or Retired)
- [ ] An Agent cannot view or modify Fleet on a Contract that isn't theirs; a Tester cannot view Fleet outside their own Client's Contracts

## Tests

Backend HTTP API seam: add Smartphone/SIM to a Contract; status transitions for each resource type; an Agent is rejected on a Contract they don't hold; a Tester is rejected on another Client's Contract.

## Regression

N/A — new capability.

## Observability

Status-change events logged with resource id, old/new status, actor.
