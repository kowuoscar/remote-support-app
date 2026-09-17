---
id: remove-current-month-manager-invoice-actions
title: Remove the Manager's current-month invoice action routes
status: ready-for-agent
depends_on: [invoice-summaries-on-contract-and-agent-pages]
labels: [backend, invoicing]
---

## Context

Contract step: once nothing calls them, the Manager's actions addressed by Contract or Agent for the current month are deleted, so each action exists once, by invoice id. Implements spec.md Solution (Backend (contract)) and the Constraints entry that no such route may remain.

## Acceptance criteria

- [ ] The current-month Client Invoice approve route (addressed by Contract) no longer exists
- [ ] The current-month Agent Invoice override, approve and paid routes (addressed by Agent) no longer exist
- [ ] The current-month read, send and Carrier Invoice File routes still work for the Agent and Tester flows, and the Manager's Contract/Agent summaries
- [ ] No frontend code or test references a removed route

## Tests

- **API seam:** each removed route returns 404/405; by-id equivalents still pass. Existing tests that exercised the removed routes are rewritten against the by-id routes, not deleted, so their rules (transitions, override isolation, access) stay covered.
- **E2E:** full e2e suite green.

## Regression

Agent build/send and Tester view flows — `ClientInvoiceApiTest`, `AgentInvoiceApiTest` (read/send parts), `client-invoice-generation.spec.ts`, `client-invoice-submission-and-visibility.spec.ts`, `agent-invoice-submission-and-approval.spec.ts`.

## Observability

N/A — removes routes; audit logging for these actions continues on the by-id routes.
