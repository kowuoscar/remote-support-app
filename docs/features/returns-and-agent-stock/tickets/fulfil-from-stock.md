---
id: fulfil-from-stock
title: An Agent fulfils a Provision or Replace Request from their Stock
status: in-progress
depends_on: [agent-stock]
labels: [backend, frontend, fleet, requests]
---

## Context

Implements `spec.md` Solution (Fulfilment from Stock) and user stories 11-13.

## Acceptance criteria

- [ ] Completing a Provision Smartphone or Replace Smartphone may name a Smartphone from the Agent's own Stock; it joins the Contract as Active and company-owned instead of a new one being added
- [ ] Completing a Provision SIM or Replace SIM may name a SIM Card from the Agent's own Stock with the Request's Carrier and flavor and, for postpaid, its Postpaid Plan; it joins the Contract keeping its number and monthly fee, and no SIM number is asked
- [ ] A Stock unit of another Agent, or a SIM Card that doesn't match, is refused
- [ ] Everything else about completion is unchanged: installing into the target Smartphone, retiring the replaced unit, carrying SIM Cards over
- [ ] Naming nothing from Stock completes exactly as before
- [ ] The completion step offers a "from my Stock" picker only when a matching unit exists
- [ ] The unit leaves the Stock page and appears in the Fleet

## Tests

- **API seam:** each of the four types from Stock; mismatch and other-Agent refusals; nothing named → previous behaviour; the fulfilled SIM Card's fee enters the base amount.
- **Component seam:** the Stock picker shows only matching units and hides when there are none.
- **E2E:** extend the Stock e2e: the Agent completes a Provision Smartphone from Stock and the Fleet shows that Smartphone.

## Regression

Provision and Replace completion. The `provision-request-details` and `replace-requests` API and e2e tests must pass unchanged.

## Observability

A unit-fulfilled-from-Stock audit event: unit id, Agent, to Contract, Request id, actor, tenant.
