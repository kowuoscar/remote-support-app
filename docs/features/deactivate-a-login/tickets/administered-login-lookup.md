---
id: administered-login-lookup
title: Extract the administered-Login lookup from the password reset
status: in-progress
depends_on: []
labels: [enabler, backend]
stories: []
---

## Context

First slice of `spec.md` (`## Execution order`, "Prefactoring: one lookup for the Login a Manager is administering"). Enables `deactivate-an-agents-login-api` and `deactivate-a-testers-login-api`, which resolve the same targets for four more routes and must not restate the Tenant-scoped resolution or the `LoginAdministrationGuard` call.

Behaviour-preserving. `PasswordResetService.resetAgentPassword` and `resetTesterPassword` today resolve the Agent (or Client plus Tester) within the caller's Tenant, find the Login with `UserRepository.findByAgentId` or `tester.getUser()`, raise `NotFoundException` / `AgentHasNoLoginException`, and call the guard inside the private `reset`. Move exactly that into one component in `web` with two operations (by Agent; by Client and Tester) that returns the target `User` after calling `LoginAdministrationGuard.requireCanAdminister`. `PasswordResetService` calls it instead. Modules: `web` only. Design intent, not a criterion: `PasswordResetService` no longer calls the guard or the Agent, Client, Tester repositories itself; the lookup is the one place that does.

## Acceptance criteria

- [ ] Resetting an Agent's password and a Tester's password answers exactly what it answered before, proven by the unedited reset suites: `200` with a generated password that signs in, `404` for an unknown or other-Tenant Agent, Client or Tester, `409 AGENT_HAS_NO_LOGIN` for a Login-less Agent, `403` for an Agent or Tester caller.

## Tests

- No new test: the seam is the HTTP API seam (spec `## Testing decisions`), and the reset feature's existing suites are the proof that behaviour is preserved: `AgentPasswordResetApiTest`, `TesterPasswordResetApiTest` and `LoginAdministrationGuardTest` run unedited.

## Regression

- At risk: every outcome of both reset routes (status codes, the coded `409`, the `403` role matcher, the single `PASSWORD_CHANGED` audit line, the other-Tenant `404`), and the guard being called exactly once per reset.
- Protected by `AgentPasswordResetApiTest`, `TesterPasswordResetApiTest`, `LoginAdministrationGuardTest`. No existing test is expected to change.

## Observability

N/A — behaviour-preserving move; the reset's `PASSWORD_CHANGED` audit line is unchanged and still covered by the reset suites.
