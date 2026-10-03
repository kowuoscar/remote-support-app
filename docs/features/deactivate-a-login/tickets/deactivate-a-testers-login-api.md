---
id: deactivate-a-testers-login-api
title: Let a Manager deactivate and reactivate a Tester's Login
status: done
depends_on: [deactivate-an-agents-login-api]
labels: [backend]
stories: [2, 7, 8, 17, 19, 20, 22, 23]
---

## Context

Fourth slice of `spec.md` (`## Execution order`). Adds `POST /api/clients/{clientId}/testers/{testerId}/deactivate` and `/reactivate` to the controller and Login-activation service built in `deactivate-an-agents-login-api`, resolving through the administered-Login lookup's Client-and-Tester operation, and `deactivatedAt` on the Tester response (null when active). A Tester always has a Login, so there is no `409`. Same idempotency, audit and role rules as the Agent routes. Modules: `web`, `dto`.

## Acceptance criteria

- [ ] Deactivating a Tester answers `200 { deactivatedAt }`; the Tester's right-password sign-in then answers `401 LOGIN_DEACTIVATED`, a wrong password `401` with no body, and its kept token `401` on `GET /api/me`.
- [ ] Deactivating again keeps the same `deactivatedAt` and writes no second audit line; reactivating answers `{ deactivatedAt: null }` and the original password signs in again.
- [ ] After deactivation the Client's Testers list still returns the same email and Primary Contact flag with `deactivatedAt` set (null when active), and the Client's requests still name the Tester as raiser.
- [ ] Resetting a deactivated Tester's password answers `200`; sign-in with it is `401 LOGIN_DEACTIVATED` until reactivation, then succeeds.
- [ ] An unknown Client or Tester, a Tester under another Client, and the other Tenant's Client and Tester are `404` (and the other Tenant's Tester still signs in); a Tester or Agent token is `403` on both routes.
- [ ] Each real change writes one `LOGIN_DEACTIVATED` or `LOGIN_REACTIVATED` line with target, actor and Tenant; no-ops and refusals write none.

## Tests

- **HTTP API seam (spec `## Testing decisions`):** new `TesterLoginActivationApiTest` (extends `IntegrationTest`, `@Import(OtherTenantFixture.class)`, creates its own Client and Tester, never a seeded Login). Cases: `deactivate-refuses-sign-in-wrong-password-and-kept-token`, `reactivate-restores-original-password`, `deactivate-twice-same-timestamp-one-audit-line`, `testers-list-keeps-email-and-primary-contact-and-shows-deactivated-at`, `client-requests-still-name-the-tester-as-raiser`, `reset-on-deactivated-tester-200-signs-in-only-after-reactivation`, `unknown-client-or-tester-and-tester-of-another-client-404`, `agent-and-tester-tokens-403`, `other-tenant-client-and-tester-404-and-still-signs-in`, `audit-lines-name-target-actor-tenant-and-none-for-refusals`.

## Regression

- At risk: the Tester response shape (a new field) and the Testers list and creation responses that reuse `TesterResponse.of`, the Tester reset route.
- Existing tests expected to change: none. `TesterApiTest` and `TesterPasswordResetApiTest` assert named fields and pass unedited; if one fails on the new field the implementer returns that to the merger rather than editing it.

## Observability

Same `LOGIN_DEACTIVATED` / `LOGIN_REACTIVATED` audit lines as the Agent routes, one per real change, none for no-ops or refusals (spec `## Solution`, "Observability").
