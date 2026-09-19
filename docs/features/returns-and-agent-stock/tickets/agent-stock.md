---
id: agent-stock
title: A returned company-owned unit can be kept in the Agent's Stock
status: ready-for-agent
depends_on: [manager-decides-return-disposition]
labels: [backend, frontend, fleet, requests]
---

## Context

Implements `spec.md` Solution (Agent Stock; the Kept-in-Stock row of Disposition and Completion) and user stories 9 (Stock part), 10, 17 and 18.

## Acceptance criteria

- [ ] The Manager can choose Kept in Stock for a company-owned Smartphone or a SIM Card when approving a Return; on a Postpaid SIM the picker notes that the carrier keeps charging with no Client to bill
- [ ] Completing the Return moves each such unit out of the Contract into the Stock of the Contract's Agent, uninstalled
- [ ] A unit in Stock appears on no Fleet, counts on no invoice base amount, and can't be the target of a Request
- [ ] The Agent's navigation has a Stock page listing their Smartphones and SIM Cards in Stock with model, serial, number, Carrier, flavor, Plan and the Contract each came from
- [ ] The Manager's navigation has a Stock page showing every Agent's Stock, filterable by Agent
- [ ] An Agent sees only their own Stock; a Tester gets 403; another tenant's Stock is invisible
- [ ] A unit is in exactly one place, a Contract's Fleet or an Agent's Stock, enforced in the database

## Tests

- **API seam:** Disposition accepted; completion moves units; Fleet, base amount and unit pickers exclude Stock; Stock read per role and tenant.
- **E2E:** the Manager keeps a returned Smartphone; after completion the Agent's Stock page lists it and the Fleet doesn't.
- **Visual:** goldens for the two Stock pages per theme × breakpoint; the navigation change is recorded in `DESIGN.md` in the same commit as the dashboard goldens it moves.

## Regression

Fleet reads and invoice base amounts, which assumed every unit has a Contract. `SmartphoneApiTest`, `SimCardApiTest`, `ClientInvoiceApiTest` and `AgentInvoiceApiTest` must pass unchanged.

## Observability

A unit-moved-to-Stock audit event: unit id, from Contract, Agent, Request id, actor, tenant.
