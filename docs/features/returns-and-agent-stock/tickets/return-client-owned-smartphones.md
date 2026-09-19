---
id: return-client-owned-smartphones
title: A Return Request sends Client-owned Smartphones back without approval
status: ready-for-agent
depends_on: []
labels: [backend, frontend, requests, fleet]
---

## Context

First slice of Return: the type, its details, and the path that needs no Manager. Implements `spec.md` Solution (Return type; the Client-owned rows of Approval, Disposition and Completion) and user stories 1-3, 6, 7 and 9 (retire and uninstall parts).

## Acceptance criteria

- [ ] Return is a Request type offered to Testers and Agents; it never carries a Fee
- [ ] A Return requires at least one Active unit of its Contract, each at most once; in this ticket a Return naming any company-owned unit (any SIM Card, or a company-owned Smartphone) is refused with a message that it isn't supported yet
- [ ] A Return of only Client-owned Smartphones starts at Submitted, or at Completed when an Agent logs it that way
- [ ] Each unit carries the Disposition "Posted to Client", fixed at submission and shown on the Request in every Requests list
- [ ] Completing the Return retires each Smartphone and uninstalls its SIM Cards, which stay in the Fleet
- [ ] Completion is refused with a clear message if a named unit is no longer Active
- [ ] The submit and log-Request dialogs show a Return details section with a multi-select of the Contract's Active units

## Tests

- **API seam:** validation on both paths (empty, duplicate, other Contract, retired unit, company-owned refused for now); start status; completion retires and uninstalls; stale unit; no Fee for Return, proactive or against the Request.
- **Component seam:** the unit multi-select.
- **E2E:** a Tester returns a Client-owned Smartphone; the Agent completes it; the Fleet shows it retired and its SIM Card uninstalled.

## Regression

Request submission, the Installed-in rules and the lifecycle. The `request-types-and-flow` API tests must pass unchanged.

## Observability

A unit-returned audit event per unit: Request id, unit id, Disposition, actor, tenant.
