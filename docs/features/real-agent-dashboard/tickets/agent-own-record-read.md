---
id: agent-own-record-read
title: Let an Agent read their own record and standing amounts
status: done
depends_on: []
labels: [backend]
stories: [21]
---

## Context

First slice of `spec.md` (`## Execution order`, item 1). Adds `GET /api/me/agent` in a small controller beside `MeController` (spec `## Solution`, "Backend: the caller's own Agent"), resolving the Agent from the principal through `CallerIdentityResolver` and the amounts through `StandingAmountService`. It gives Agents a read of their own figures while setting them stays Manager-only (story 21). `agent-dashboard-identity-and-standing-amounts` consumes it.

Constraints are the spec's: no `SecurityConfig` change, no migration, no id in path or query, `MeResponse` unchanged.

## Acceptance criteria

- [ ] `GET /api/me/agent` as an Agent returns `200` with their own `agentId`, `name`, `country`, `currency`, `salaryAmount` and `rolloutAdvanceAmount`.
- [ ] The amounts are those resolved for the current billing month: a future-effective change a Manager just made is not reflected; Rollout Advance is `0` when never set.
- [ ] Two Agents each receive only their own record.
- [ ] `GET /api/me/agent` returns `404` for a Manager, a Tester and an Agent login not linked to an Agent, and `401` without a token.
- [ ] `GET /api/agents/{agentId}/standing-amounts` still answers an Agent with `403`, and a Manager's standing-amount change still succeeds.

## Tests

- **Backend, HTTP API seam (`IntegrationTest` + MockMvc, spec Testing decisions 1):** new test class beside `ChangeOwnPasswordApiTest`, prior art `AgentIdentityApiTest` and `AgentInvoiceApiTest`. Cases: Agent gets own fields; two Agents get only their own; current-month resolution versus a future-effective change; Rollout Advance `0` when never set; Manager `404`; Tester `404`; unlinked Agent login `404`; no token `401`; Agent on the Manager-only standing-amounts route `403`; Manager can still set a standing amount.

## Regression

- At risk: `MeController` and `MeResponse` (unchanged), the Manager-only standing-amounts route, `SecurityConfig` matching for `/api/me/**`. Protected by existing tests, none modified: `AgentInvoiceApiTest` (the Manager-only `/api/agents/{id}/standing-amounts` route), `AgentIdentityApiTest` (`/api/me`, `MeResponse`) and `ChangeOwnPasswordApiTest` (`/api/me/**` matching).
- Existing tests expected to change: none; every existing test keeps passing unmodified.

## Observability

N/A — a read of the caller's own record; the audit trail records state changes, and this changes none.
