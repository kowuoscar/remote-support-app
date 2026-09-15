---
id: manager-entity-setup
title: Manager creates Clients, Testers, Agents and Contracts
status: in-progress
depends_on: [auth-login-flow]
labels: [backend, frontend, manager]
---

## Context

Implements the core entity setup from the spec's Solution (Client, Tester, Agent, Contract) and user stories 1-3, 36, and the precondition for stories 29-30. Every later ticket operates on entities created here.

## Acceptance criteria

- [ ] Manager can create a Client and see it in a Client list
- [ ] Manager can create a Tester under a Client, including their login; one Tester per Client may be flagged as the primary contact
- [ ] Manager can create an Agent with a country (which fixes their currency) and an initial standing monthly salary
- [ ] Manager can create a Contract linking exactly one Client and one Agent; the Contract's currency is derived from the Agent's country
- [ ] A Client can hold more than one Contract (e.g. with Agents in different countries); an Agent can hold more than one Contract
- [ ] Only a Manager can create Clients, Testers, Agents and Contracts; Agent and Tester attempts are rejected

## Tests

Backend HTTP API seam: creation and listing for each entity; a Contract's currency matches its Agent's country; a non-Manager role is rejected with 403 on each creation endpoint.

## Regression

N/A — new entities, nothing to regress.

## Observability

Audit log entry (actor, tenant, entity, action) on every entity creation.
