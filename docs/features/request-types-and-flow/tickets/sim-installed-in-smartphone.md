---
id: sim-installed-in-smartphone
title: A SIM Card records the Smartphone it is Installed in
status: ready-for-agent
depends_on: [smartphone-owner-and-optional-serial]
labels: [backend, frontend, fleet]
---

## Context

The link SIM Swap, Provision SIM and Replace need. Implements `spec.md` Solution (Fleet model: Installed in) and user stories 18 (Installed-in part), 34 and 35.

## Acceptance criteria

- [ ] A SIM Card is Installed in at most one Smartphone, which must be Active and in the same Contract
- [ ] A Smartphone refuses a third SIM Card with a clear message
- [ ] The Agent (own Contract) or the Manager can set or clear a SIM Card's Smartphone from the Fleet page; a Tester cannot
- [ ] Retiring a Smartphone clears the link on its SIM Cards; retiring a SIM Card clears its own
- [ ] Every Fleet table shows, per SIM Card, the Smartphone it is Installed in, and per Smartphone, its SIM Cards
- [ ] One module owns installing, uninstalling and the two-SIM check, so later tickets call it instead of re-deriving it
- [ ] Seed data holds installed and uninstalled SIM Cards, including one Smartphone with two

## Tests

- **API seam:** set, clear, move; third SIM refused; other Contract, retired Smartphone and retired SIM Card refused; retire clears links; role matrix as for Fleet status changes.
- **E2E:** the Agent installs a SIM Card into a Smartphone from the Fleet page and the Tester's Fleet shows it.

## Regression

Fleet status changes and the existing replace-on-provision retire path. `SimCardApiTest`, `SmartphoneApiTest` and the provisioning tests must pass unchanged.

## Observability

Audit events for a SIM Card installed and uninstalled: SIM Card id, Smartphone id, actor, tenant, and the Request id when a Request caused it (none yet).
