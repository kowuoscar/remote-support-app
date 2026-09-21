---
id: refuse-taken-username-on-agent-login
title: Give an existing Agent a login through the global, normalized username check
status: ready-for-agent
depends_on: [global-username-index]
labels: [backend, auth]
stories: [3, 7, 8]
---

## Context

`spec.md` Solution ("One rule, five creation paths", "Normalization, the
same three layers as `carrier-catalog`") and Decisions taken (the global
index is over `lower(btrim(username))`; the sign-in lookup itself is
untouched — see Non-goals).

`global-username-index` already made both Agent-login endpoints answer 409
`USERNAME_TAKEN` for a cross-Tenant collision, as a side effect of the flush
catch it fixed — `POST /api/agents` has no pre-check at all, so it was
demonstrated there. This ticket is where `AgentLoginService.
requireLoginCreatable` (called from giving an *existing* Agent a login,
`POST /api/agents/{agentId}/login`) stops asking the Tenant-scoped
`existsByTenantIdAndUsername` and asks the global, case-insensitive,
trim-insensitive check `global-username-index` added to `UserRepository` —
the spec's own mandated design (defence in depth, matching Carrier's
three-layer pattern), not merely a fallback on the index catching a failed
insert. `existsByTenantIdAndUsername` has no other caller in the codebase
today and is deleted once this ticket stops calling it.

This ticket also adds the input-layer trim (`.strip()`) the spec's
normalization layer asks for, on both Agent-login creation paths — the one
piece of this that `global-username-index` could not have covered, since
trimming what gets *stored* on a clean, non-colliding creation is not
something the database index does for you.

## Acceptance criteria

- [ ] The username is trimmed (`.strip()`) before it is read, checked or stored on both `POST /api/agents` and `POST /api/agents/{agentId}/login`.
- [ ] A clean (non-colliding) `POST /api/agents` call with a username carrying leading/trailing whitespace succeeds, and the login username in the response is the trimmed value.
- [ ] `AgentLoginService.requireLoginCreatable`'s pre-check uses the global, case-insensitive, trim-insensitive existence check instead of `existsByTenantIdAndUsername`; that method is deleted from `UserRepository` (it has no other caller).
- [ ] Giving an existing Agent a login (`POST /api/agents/{agentId}/login`) with a username already taken in **another** Tenant (built via `OtherTenantFixture`) is refused 409 `USERNAME_TAKEN`.
- [ ] The same endpoint refuses a username differing from one that exists in another Tenant only by letter case, and one differing only by surrounding whitespace — each 409 `USERNAME_TAKEN`.
- [ ] `mvn -f backend/pom.xml verify` (JDK 21) is green, including `AgentLoginApiTest`'s existing same-Tenant cases passing unmodified.

## Tests

- **Seam:** the existing HTTP API seam, `IntegrationTest` + MockMvc, per spec.md Testing decisions. No unit test of `AgentLoginService`, no repository-level test of the new query.
- Cases:
  - Give-existing-agent-login refused for a username taken in another Tenant (story 3) — the walkthrough's own step 4.
  - Give-existing-agent-login refused for a case-only difference from another Tenant's username, and for a whitespace-only difference (stories 7, 8) — walkthrough step 6.
  - A clean `POST /api/agents` call with surrounding whitespace in the username stores and returns the trimmed value (story 7's other half, walkthrough step 6).
  - Same-Tenant collision on `POST /api/agents/{agentId}/login` still 409s (existing `AgentLoginApiTest` coverage, re-run unmodified to prove the pre-check swap didn't change that path's behaviour).
- Never asserted: which query ran, or whether the pre-check or the flush caught a given case — the two are indistinguishable at the HTTP seam by design (spec.md Testing decisions).

## Regression

- `AgentLoginApiTest`'s full suite (same-Tenant taken username, second login on an Agent, already-has-a-login-and-username-taken, missing fields, role checks, audit-without-password) must keep passing unmodified — the pre-check's *source* query changes, its *behaviour* for every case that test already covers does not.
- `AgentCreationAtomicityTest` is unaffected — it exercises `POST /api/agents`'s flush-time catch (already fixed by `global-username-index`), not `requireLoginCreatable`.
- Deleting `existsByTenantIdAndUsername` is safe only because `AgentLoginService` was its one caller (confirmed by search); if a caller has appeared since, this ticket's implementer re-checks before deleting.

## Observability

N/A — no new audit trail. A refused login creation writes no `AuditLog` entry
today (only a successful one calls `AuditLog.agentLoginCreated`), and this
ticket does not change that.
