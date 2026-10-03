---
id: deactivate-an-agents-login-api
title: Let a Manager deactivate and reactivate an Agent's Login
status: in-progress
depends_on: [administered-login-lookup, deactivated-login-refused]
labels: [backend]
stories: [1, 7, 8, 9, 16, 18, 19, 20, 21, 22, 23]
---

## Context

Third slice of `spec.md` (`## Execution order`; `## Solution`, "Endpoints", "Read models", "Observability"). Adds the new controller (own `@ExceptionHandler` for the coded `409`, as `PasswordResetController` has) with `POST /api/agents/{agentId}/login/deactivate` and `/reactivate`, the `@Transactional` Login-activation service (resolve through the administered-Login lookup, set or clear `deactivated_at`, save, audit on a real change only), `AuditLog` events `LOGIN_DEACTIVATED` and `LOGIN_REACTIVATED` in the shape of `passwordChanged`, and `loginDeactivatedAt` on the Agent response beside `loginUsername` (null when active or Login-less). No `SecurityConfig` matcher: the routes fall under the existing `ROLE_MANAGER` rules.

Both actions are idempotent `200`s: deactivating again keeps the original `deactivatedAt` and writes no audit line. Nothing is deleted. Reactivation keeps the existing password. A password reset on a deactivated Login still works and leaves it deactivated. Modules: `web`, `dto`, `logging`.

## Acceptance criteria

- [ ] `POST /api/agents/{agentId}/login/deactivate` answers `200 { deactivatedAt }`; the Agent's sign-in with the right password then answers `401 LOGIN_DEACTIVATED`, with a wrong password `401` with no body, and its kept token is `401` on `GET /api/me`.
- [ ] Deactivating again answers the same `deactivatedAt`; `.../reactivate` answers `200 { deactivatedAt: null }`, after which the original generated password signs in and a fresh token works.
- [ ] After deactivation the Agents list, the Agent's standing amounts, Contracts, Agent Invoices and the requests it raised return what they returned before; `GET` of the Agent returns the same `loginUsername` with `loginDeactivatedAt` set (null again after reactivation, and null for a Login-less Agent); `POST /api/agents/{agentId}/login` still answers `409 AGENT_ALREADY_HAS_LOGIN`.
- [ ] Resetting the password of a deactivated Agent Login answers `200` with a password; signing in with it is `401 LOGIN_DEACTIVATED` until reactivation, then succeeds.
- [ ] An unknown or other-Tenant Agent id is `404`; a Login-less Agent is `409 AGENT_HAS_NO_LOGIN`; an Agent or Tester token is `403` on both routes (the seeded Agent's token included); no path or body names a `User`, and no route accepts a Manager Login as target.
- [ ] Each real change writes exactly one `LOGIN_DEACTIVATED` or `LOGIN_REACTIVATED` line naming target user id, actor and Tenant; a no-op, a `403`, `404` or `409` writes none; the other Tenant's Agent still signs in after the refused attempt.

## Tests

- **HTTP API seam (spec `## Testing decisions`):** new `AgentLoginActivationApiTest` (extends `IntegrationTest`, `@Import(OtherTenantFixture.class)`, creates its own Agent, never a seeded or demo Login). Cases: `deactivate-refuses-sign-in-wrong-password-and-kept-token`, `reactivate-restores-original-password`, `deactivate-twice-same-timestamp-one-audit-line`, `reactivate-twice-is-200-null-one-audit-line`, `deactivation-is-not-deletion-lists-standing-amounts-contracts-invoices-requests-unchanged`, `create-login-still-409-already-has-login`, `agent-response-carries-login-deactivated-at-and-null-without-login`, `reset-on-deactivated-login-200-signs-in-only-after-reactivation`, `unknown-agent-404`, `login-less-agent-409-agent-has-no-login`, `agent-and-tester-tokens-403`, `other-tenant-agent-404-and-still-signs-in`, `audit-lines-name-target-actor-tenant-and-none-for-refusals`.
- The guard's Manager-target branch is already covered by `LoginAdministrationGuardTest`; no new test.

## Regression

- At risk: the Agent response shape (a new field), the Agents list and detail reads, `AgentLoginApiTest` (create login), the reset routes now sharing the lookup.
- Existing tests expected to change: none. The additive `loginDeactivatedAt` field does not break `AgentApiTest`, `AgentIdentityApiTest` or `AgentLoginApiTest`, which assert named fields; if one fails on the new field the implementer returns that to the merger rather than editing it.

## Observability

Two `AuditLog` events, `LOGIN_DEACTIVATED` and `LOGIN_REACTIVATED`: `action=… entity=User entityId=<target userId> actorUserId=<Manager> tenantId=…`, one per real change only. Walkthrough step 11 greps them; the idempotency test reads the captured log.
