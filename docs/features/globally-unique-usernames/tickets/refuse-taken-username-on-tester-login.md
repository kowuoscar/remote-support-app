---
id: refuse-taken-username-on-tester-login
title: Give a Tester's conflict its own machine-readable code, symmetric with the Agent path
status: done
depends_on: [global-username-index]
labels: [backend, frontend, auth]
stories: [4, 5, 6, 9, 15]
---

## Context

`spec.md` Solution ("The Tester path's asymmetry is fixed here, not left",
"Frontend") and Decisions taken (the shared taken-email wording; `save` →
`saveAndFlush`; no `@Transactional`-level locking).

`TesterController.create` today catches `DataIntegrityViolationException`
and throws a bare `ConflictException` with no `code`, so
`create-tester-dialog.tsx` shows one sentence for two unrelated causes
("this client already has a primary contact, or that email is already in
use"). This feature adds a new reason that path can fail — an address taken
in a Tenant the Manager cannot see — so leaving the ambiguous message would
be a user-visible regression this feature itself introduces. This ticket
gives the Tester path the shape the Agent path already has: a pre-check
before anything is written (the same global, case-insensitive,
trim-insensitive check `global-username-index` added to `UserRepository`),
`saveAndFlush` so a racing violation is caught where the `catch` already
expects it, and a 409 carrying `USERNAME_TAKEN` in the same `{code,
message}` shape the Agent path uses. The primary-contact conflict keeps its
own 409 with its own, different code.

Independent of `refuse-taken-username-on-agent-login` — both consume the
query `global-username-index` added, neither depends on the other.

**Settled by the human, 2026-09-21.** A collision that only the database
catches — two creations racing, so the pre-check saw nothing and the unique
index rejected the second write — **returns 409 `USERNAME_TAKEN`, never an
unmapped 500.** Keep the `saveAndFlush` and the `catch` that map the
constraint violation to that response; do not remove them because the
pre-check now makes them unreachable in ordinary use.

This is a requirement, not an acceptance criterion, and deliberately so: the
critic failed it twice as a criterion because this ticket's own pre-check
makes the flush-time path unreachable by any single sequential request, and
MockMvc cannot manufacture a mid-request collision — so no reviewer could
fail it by running anything. The mapping is proved for real on the sibling
path instead: `refuse-taken-username-on-agent-login` and
`global-username-index` cover `POST /api/agents`, which has no pre-check and
so genuinely reaches the constraint. The human chose this over adding a
concurrency test for a second proof of the same mapping.

## Acceptance criteria

- [ ] The username is trimmed (`.strip()`) before it is read, checked or stored on `POST /api/clients/{clientId}/testers`.
- [ ] A refused Tester creation (either cause) leaves no `User` and no `Tester` row behind.
- [ ] Creating a Tester with a username already taken in **another** Tenant (built via `OtherTenantFixture`) is refused 409 with `code: USERNAME_TAKEN` and the message "That email is already in use. Choose another one and try again." (Decisions taken) — no Tester or User row is left behind.
- [ ] Creating a second Tester as primary contact for a Client that already has one is refused 409 with a distinct `code` (not `USERNAME_TAKEN`), so the two causes are told apart by `code` alone.
- [ ] `create-tester-dialog.tsx` stops showing the two-causes-in-one-sentence message: on a 409 with `code: USERNAME_TAKEN` it shows the wording above and marks the email field invalid; on the primary-contact `code` it keeps its own distinct message. `create-agent-login-dialog.tsx` and `create-agent-dialog.tsx` are untouched.
- [ ] `mvn -f backend/pom.xml verify`, the frontend vitest suite, `npm run typecheck`/`tsc --noEmit` and lint, and the visual suite all stay green, with no golden recaptured (this feature adds no surface, per spec.md Non-goals).

## Tests

- **Seam, backend:** the existing HTTP API seam, `IntegrationTest` + MockMvc, per spec.md Testing decisions. Prior art: `AgentLoginApiTest`'s and `AgentCreationAtomicityTest`'s `$.code == USERNAME_TAKEN` assertions on a same-Tenant collision, adapted to a cross-Tenant one via `OtherTenantFixture`.
  - Case: Tester creation refused 409 `USERNAME_TAKEN` for a username taken in another Tenant (story 4) — walkthrough step 5's first half.
  - Case: a second primary-contact Tester for the same Client refused 409 with its own distinct `code`, told apart from `USERNAME_TAKEN` (story 5) — walkthrough step 5's second half.
  - Case: a same-Tenant username-taken Tester creation (control, same shape as the two Agent test classes' existing coverage) still refused with `code: USERNAME_TAKEN`.
- **Seam, frontend:** a new component test for `create-tester-dialog.tsx`, stubbing a 409 with each `code` in turn and asserting the message shown and which field is marked invalid — the shape spec.md's Testing decisions describes (no such test exists yet for `create-agent-login-dialog.tsx` to mirror file-for-file; the *behavioural* pattern — stub the code, assert message and field — is what carries over).
- Never asserted: which query ran inside the pre-check, or the exception class thrown.

## Regression

- `TesterApiTest.aSecondPrimaryContactForTheSameClientIsRejected` asserts `status().isConflict()` on exactly the response this ticket adds a `code` to — the status must stay 409; the ticket does not need to (and should not) modify this test's assertions to keep it passing.
- `TesterApiTest.managerCanCreateAndListTestersUnderAClient` asserts `$.username` equals the request's own (already-clean, no whitespace) username — the new `.strip()` is a no-op on that input, so this assertion must keep passing unmodified too.
- No existing backend test exercises `TesterController.create`'s conflict path with a `code` in the response, since none exists today — nothing beyond the two tests above to keep passing unmodified there, only new coverage.
- `create-agent-login-dialog.tsx` and `create-agent-dialog.tsx` are named in this ticket's own acceptance criteria as untouched — flagged here since both already read a `code` off a 409 and must keep doing so exactly as before.
- The visual suite must show no recaptured golden — the create-tester dialog's inline error alert is not itself part of any captured surface's default state (it only appears after a failed submit), but the ticket's own AC requires confirming this by actually running the suite, not assuming it.
- `TesterController`'s existing `GET` (list) endpoint and `existsByClientIdAndPrimaryContactTrue` check are unaffected — this ticket only changes `create`'s failure handling and its write order (`save` → `saveAndFlush`).

## Observability

N/A — no new audit trail. A refused Tester creation writes no `AuditLog`
entry today (only a successful one calls `AuditLog.created("Tester", ...)`),
and this ticket does not change that.
