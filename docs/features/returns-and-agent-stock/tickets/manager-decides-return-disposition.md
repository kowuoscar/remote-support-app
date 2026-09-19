---
id: manager-decides-return-disposition
title: The Manager decides what happens to returned company-owned units
status: ready-for-agent
depends_on: [return-client-owned-smartphones]
labels: [backend, frontend, requests, fleet]
---

## Context

Opens Return to company-owned units. Implements `spec.md` Solution (Approval; Disposition; the Posted-to-company and Cancelled rows of Completion) and user stories 4, 5, 8, 14-17 (without Stock) and 21. "Kept in Stock" arrives in `agent-stock`.

## Acceptance criteria

- [ ] A Return naming any SIM Card or company-owned Smartphone is accepted and starts at Pending Approval, whoever raised it
- [ ] On the Pending Requests page, approving a Return asks for a Disposition per company-owned unit — Posted to company for a Smartphone, Cancelled for a SIM Card (the only choices until `agent-stock`) — and is refused until every one is chosen; a Client-owned Smartphone shows "Posted to Client" with nothing to choose
- [ ] Dispositions can't be changed after approval
- [ ] Completing the Return asks the Agent for an effective cancellation date for each SIM Card being cancelled, past or future, and is refused without one
- [ ] A Smartphone posted to the company is retired; a cancelled SIM Card is retired, uninstalled, and keeps its cancellation date, shown in the Fleet tables
- [ ] Every Requests list shows each unit's Disposition once decided
- [ ] Agent and Tester get 403 on setting a Disposition

## Tests

- **API seam:** start status by ownership mix on both paths; approve refused without all Dispositions, with a Disposition for a Client-owned unit, or with one that doesn't fit the unit kind; completion needs every cancellation date; effects; role matrix; approval of a non-Return unchanged.
- **Component seam:** the Disposition pickers inside the approve control.
- **E2E:** a Tester returns a company-owned Smartphone and a SIM Card → the Manager posts one and cancels the other → the Agent completes with a date → both leave the Fleet.

## Regression

Approval of Provision and Replace Requests. `manager-approves-requests`' API, component and e2e tests must pass unchanged.

## Observability

The approved audit event carries the Dispositions; a SIM-cancelled event carries SIM Card id, effective date, Request id, actor, tenant.
