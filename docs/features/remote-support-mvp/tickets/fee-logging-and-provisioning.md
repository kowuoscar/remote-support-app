---
id: fee-logging-and-provisioning
title: Agent logs Fees traced to Requests; provisioning updates the Fleet
status: done
depends_on: [agent-request-fulfillment]
labels: [backend, frontend, fees, fleet]
---

## Context

Implements the Fee entity, its traceability rule, and the provisioning side-effect on Fleet from the spec's Solution and user stories 19-20.

## Acceptance criteria

- [ ] Agent can log a Fee against a Request of type Topup, Provision Smartphone, Provision SIM, or Repair
- [ ] Reboot and a like-for-like SIM Swap never accept a Fee
- [ ] A Fee the Agent logs with no pre-existing Request auto-creates its linking Request (completed, proactive)
- [ ] Completing a Provision Smartphone or Provision SIM Request adds the new unit to the Contract's Fleet, retiring the unit it replaces where one is specified
- [ ] Every Fee is only ever reachable through its linking Request — there is no way to create an untraceable Fee

## Tests

Backend HTTP API seam: Fee creation for each eligible Request type; rejection for Reboot/like-for-like Swap; proactive Fee auto-creates its Request; Provision Request completion adds/retires the correct Fleet items.

## Regression

Fleet views from `fleet-management` correctly reflect units added/retired by provisioning.

## Observability

Fee-logged and Fleet-item-provisioned events logged with Contract, Request id, amount, actor.
