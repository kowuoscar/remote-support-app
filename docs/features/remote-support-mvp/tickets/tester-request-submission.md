---
id: tester-request-submission
title: Tester submits a Request; Agent sees it in their queue
status: in-progress
depends_on: [fleet-management]
labels: [backend, frontend, requests]
---

## Context

Implements the Request entity and its submission path from the spec's Solution and user stories 16, 30-31.

## Acceptance criteria

- [ ] Tester can submit a Request (Reboot, Topup, SIM Swap, Provision Smartphone, Provision SIM, Repair) against one of their Client's Contracts
- [ ] The Request starts in status Submitted
- [ ] Any Tester at the same Client can see every Request raised by anyone at that Client, not just their own
- [ ] Agent can see incoming Requests for their own Contracts, filtered by Contract
- [ ] A Tester cannot submit a Request against another Client's Contract; an Agent cannot see Requests on a Contract that isn't theirs

## Tests

Backend HTTP API seam: submit each Request type; a Client-wide Tester visibility check; an Agent's Contract-scoped visibility check; cross-tenant/cross-Client rejection.

## Regression

N/A — new capability.

## Observability

Request-submitted event logged with Contract, Request type, actor.
