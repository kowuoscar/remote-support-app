---
feature: manager-resets-a-password
epic: login-lifecycle
status: draft
date: 2026-09-30
---

<!-- sdlc:template spec 1 -->

# Manager resets a password

## Problem

An Agent or a Tester who forgets their password has no way back into the
product. `self-service-password-change` lets a signed-in user change their own
password, but it needs the current one — so it does nothing for someone who is
already locked out. There is no forgotten-password flow either, and there will
not be one: this product sends no email (`docs/roadmap/login-lifecycle.md`
`## Later`).

Today the only fix is somebody editing `users.password_hash` in the database by
hand. The Manager already creates every Agent and Tester Login and hands over
the first password out of band. They are the one person who can plausibly
vouch for the person asking, and they have no button to press.

## Journeys

Advances `docs/roadmap/login-lifecycle.md`, the **second** of its three
features.

- **Keep a login working over time** (`wanted`, stays `wanted`): this feature
  delivers the journey's second clause — "a Manager resets the password of an
  Agent or a Tester who is locked out" — and, with it, two of the epic's four
  closing proofs: "a Manager resets a locked-out Agent's password and that
  Agent signs in; a Manager resets a Tester's password likewise". The journey
  reaches `exists` only when `deactivate-a-login` lands too, so
  `docs/journeys.md` is not edited here.
- **Sign in** (`exists`, stays `exists`): unchanged, and proven unchanged.
  `LoginRequest` keeps `@NotBlank` alone.
- **Set up the operating picture** (`exists`, stays `exists`): creating an Agent,
  giving an existing Agent a Login, and adding a Tester all go through the
  prefactored password write below. They must behave exactly as before, and
  their existing tests prove it.

## Goals / Non-goals

Goals:

- A Manager sets a new password on the Login of an Agent in their own Tenant,
  from that Agent's page.
- A Manager sets a new password on the Login of a Tester in their own Tenant,
  from that Tester's row on the Client's page.
- The Manager does not need the current password, and is told nothing about it.
- Once the reset succeeds, the new password signs the person in and the old one
  does not.
- The new password follows the one rule every password write already follows:
  at least 8 characters.
- No Manager can reach a Login in another Tenant. No Agent or Tester can reset
  anyone's password.
- Every password write in the product goes through one place, as the epic
  planned.

Non-goals — each is something a reasonable agent would otherwise build:

- **No resetting a Manager's Login.** The feature line names "an Agent or a
  Tester". No endpoint takes a Manager's Login as its target, and no screen
  lists one. A Manager changes their own password through the viewer-chip menu
  `self-service-password-change` shipped.
- **No generated password.** The Manager types the new password, just as they
  type the temporary password when creating a Login. The product never
  invents one, shows one, or copies one to a clipboard.
- **No forced change on next sign-in.** The epic lists it under `## Later`.
  After a reset, the person signs in with what they were given and can change
  it themselves from the viewer-chip menu if they want to.
- **No email, notification or in-app message** to the person whose password was
  reset. The product sends no outbound email, and the handover is out of band
  by design.
- **No signing the target out of sessions they already hold.** An existing
  session stays valid until its token expires, within the hour. Revoking it
  would need the per-request account-state check that `deactivate-a-login`
  builds. This follows the human's 2026-09-22 answer for self-service change —
  see `## Decisions taken`.
- **No login deactivation, and no enabled/disabled flag.** That is
  `deactivate-a-login`.
- **No change to a Login's email/username.** It is under the epic's `## Later`.
- **No refusal of a new password that equals the current one.** A self-service
  change refuses that case, but the Manager does not know the current password.
  A refusal would tell them what it is — see `## Decisions taken`.
- **No confirm-password field.** The creation dialogs take the temporary
  password once, and this dialog matches them.
- **No reason field, approval step, or reset history screen.** An audit line
  records the event. Nothing else stores it.
- **No rate limiting or lockout.** The endpoint is Manager-only behind a valid
  JWT.
- **No new top-level Testers list.** A Tester is still reached only from their
  Client's page (`ManagerTestersView`'s own rule).
- **No change to `ChangePasswordService`'s use of `PasswordEncoder.matches`.**
  `deactivate-a-login` owns that (epic `## Reworked`). This feature moves only
  that service's *write* into the shared password write. Its verification stays
  as it is.

## User stories

1. As a Manager, I want to set a new password on the Login of an Agent in my
   Tenant who cannot sign in, so that they can get back to work without anyone
   editing the database.
2. As a Manager, I want to do the same for a Tester at one of my Clients, so
   that the rule does not depend on which kind of person is locked out.
3. As a Manager, I want to set the new password without knowing the current
   one, so that a reset works for exactly the person who has forgotten it.
4. As a Manager, I want the reset on the Agent's page next to the sign-in email
   it changes, so that I find it where the Login is already shown.
5. As a Manager, I want a reset action on each Tester's row on the Client's
   page, so that I reset the Tester I mean without leaving that list.
6. As a Manager looking at an Agent who has no Login, I want to be offered
   Create login and not a reset, so that I am never offered a reset with
   nothing to reset.
7. As a Manager, I want the dialog to name the person and the email whose
   password I am setting, so that I do not reset the wrong Login.
8. As a Manager, I want the new password held to the same 8-character minimum
   as every other password, so that a reset is not the weak way into a Login.
9. As a Manager who types a password that is too short, I want to be told so
   inline on that field and keep what I typed, so that I fix it rather than
   start again.
10. As a Manager, I want a clear confirmation of whose password was reset, and
    a reminder that handing it over is up to me, so that I am not left wondering
    whether it worked or whether the person was told.
11. As an Agent whose password was reset, I want to sign in with the new
    password, so that the reset is real rather than reported.
12. As a Tester whose password was reset, I want the same.
13. As a person whose password was reset, I want the old password refused at
    sign-in, so that a password someone else may know stops working.
14. As a person whose password was reset, I want to change it to one of my own
    through the viewer-chip menu, so that the Manager does not keep knowing my
    password.
15. As a Manager of one Tenant, I want every attempt to reset a Login in another
    Tenant refused as if it did not exist, so that the Tenant boundary holds and
    reveals nothing.
16. As an Agent or a Tester, I want to be unable to reset anyone's password, my
    own included, so that the ability stays with the Manager.
17. As the company, I want no path by which a Manager's Login can be reset
    through this feature, so that one Manager cannot take over another.
18. As the company, I want an audit line recording whose password was reset, by
    which Manager, in which Tenant, and when — with no password and no hash —
    so that the event can be traced afterwards.
19. As a Manager, I want the password I type never shown in the clear, logged,
    or sent back in a response, so that resetting a password does not itself
    leak it.
20. As a Manager, I want a reset to leave everything else about the person as it
    was — the Agent's record, Contracts, standing amounts and invoices, the
    Tester's Client and Primary Contact flag, and the Login's email — so that a
    reset changes one thing only.
21. As a Manager whose page is stale, because the Agent lost its Login or the
    Tester or Agent is gone, I want a message that says so, so that I refresh
    instead of retrying blindly.
22. As a Manager working by keyboard, I want to open the reset, fill it in,
    submit or cancel it, and have focus come back to where I started, so that
    the action is usable without a mouse.
23. As a Manager on a phone, I want the Tester row action and the dialog usable
    at the mobile breakpoint, so that a lockout can be fixed away from a desk.
24. As everyone already using the product, I want sign-in, Login creation for
    Agents and Testers, and my own password change to behave exactly as before,
    so that moving every password write into one place carries no release risk.

## Solution

### Prefactoring: one password write, re-judged

The epic planned this prefactor on the premise that a Tester's password was
hashed inline in `TesterController`. That premise no longer holds.
`TesterLoginService` now owns the Tester write. Three services set a Login's
password today, and each repeats the same step:

- `AgentLoginService.create`, used by both Agent creation and giving an
  existing Agent a Login;
- `TesterLoginService.create`;
- `ChangePasswordService.changeOwnPassword`.

Each one encodes with the shared `PasswordEncoder` bean and sets
`User.passwordHash`. This feature adds a fourth call site.

**What is still owed is narrow, and this feature pays only that.** One small
component — **the password write** — gains a single operation: "set this Login's
password to this raw value". It encodes with the one `PasswordEncoder` bean and
sets the hash on the given `User`. The three existing services call it instead
of encoding themselves, and the reset calls it as well. Nothing else moves:

- each creation service keeps its own username pre-check, flush, and
  constraint-name-to-conflict mapping;
- `ChangePasswordService` keeps its verification with `PasswordEncoder.matches`
  and its `PASSWORD_UNCHANGED` rule;
- the 8-character minimum stays a Bean Validation constraint on each request
  DTO. The password write does not re-validate it (Backend rule 1).

The point is that no code except the password write encodes a password. When
`deactivate-a-login` or a later revocation feature needs to record something at
every password write, such as a `password_changed_at`, there is exactly one
place to add it. The prefactor is behaviour-preserving, and the existing suites
prove that: `AgentLoginApiTest`, `TesterApiTest`, `ChangeOwnPasswordApiTest`,
`PasswordMinimumLengthApiTest` and `AuthLoginTest`, with no test edited to make
it pass.

### Prefactoring: `DialogShell` mounts its children only while open

`docs/tech-debt.md` records that `DialogShell` renders its children even while
closed. A second closed dialog on a page therefore puts a hidden `<form>` in the
DOM, and that is how an index-based selector once resolved to the wrong form.
This feature adds a closed dialog to two pages that already hold one: the
Agent's page (with `AgentStandingAmountsView`, and `CreateAgentLoginDialog` when
the Agent has no Login) and the Client's page (with `CreateTesterDialog`). That
makes this feature the one that reproduces the debt. By the repository's rule,
debt is paid when a later feature touches the module, so it is paid here: the
shell renders its children only while the dialog is open. Every existing
consumer already resets its own state on open, so none of them loses anything.
The consumer-side deferral in the change-password dialog becomes redundant. It
stays, because removing it is churn outside this feature.

### The permission rule: a new guard class

`SecurityConfig` already limits `/api/agents/**` and `/api/clients/**` to
`ROLE_MANAGER`, so Agents and Testers are refused `403` with no matcher change.
That is role-level only. As the epic records, nothing states which Logins a
Manager may administer. This feature adds a guard class for that, in the
`security` package, modelled on `FleetAccessGuard`: a `@Component`, one method,
and `AccessDeniedException` on refusal.

Its one rule: **the caller is a Manager, the target Login is in the caller's
Tenant, and the target Login's role is `AGENT` or `TESTER`.** The reset service
calls it after resolving the target, so the rule has one home, and
`deactivate-a-login` calls the same guard rather than restating it.

The target is resolved within the caller's Tenant before the guard runs, using
the lookups the code already uses for this purpose (`findByIdAndTenantId` on
Agent and Client, then `findByIdAndClientId` on Tester). An unknown id, or an id
in another Tenant, is therefore `404`. That is the repository's settled
convention for the Tenant boundary (`SecurityConfig`: "Tenant scoping (an
unknown or other-tenant id is 404) is enforced in the controllers"), and it
reveals nothing about another Tenant's Logins. The guard's own `403` is defence
in depth. Neither route can reach a Manager's Login today, and the guard makes
sure a later route cannot either.

### Endpoints

The Manager addresses what they are looking at, which is an Agent or a Tester.
They never address a `User` id: no Manager screen carries one, and none gains
one.

```
POST /api/agents/{agentId}/login/password
POST /api/clients/{clientId}/testers/{testerId}/password
{ "newPassword": "..." }

204 No Content                              — reset
400 { "code": "PASSWORD_TOO_SHORT", ... }   — shorter than PasswordPolicy.MIN_LENGTH, or blank
404                                         — no such Agent / Client / Tester in the caller's Tenant
409 { "code": "AGENT_HAS_NO_LOGIN", ... }   — Agent route only: the Agent exists but has no Login
403                                         — caller is not a Manager (role matcher), or the guard refuses
```

- The Agent route sits under the Agent's existing `/login` resource, beside
  `POST /api/agents/{agentId}/login`, which gives a login-less Agent its
  Login. `AGENT_HAS_NO_LOGIN` mirrors the existing `AGENT_ALREADY_HAS_LOGIN`,
  and means "this page is stale".
- The Tester route sits on the Tester itself, because a Tester *is* a Login: the
  link is required and one-to-one, so there is no "Tester without a Login" case.
- Both routes live in one new controller with its own `@ExceptionHandler`s, as
  `ChangePasswordController` does. It reuses `PASSWORD_TOO_SHORT` from
  `ChangePasswordRefusedException.Reason`'s vocabulary, so the frontend reads
  one name for one rule. `AgentController` and `TesterController` are not grown.
- The request DTO carries `newPassword` alone, with
  `@NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, message = PasswordPolicy.TOO_SHORT_MESSAGE)`.
  There is no current password and no confirm field.

### The reset service

A new `@Transactional` service with two operations, one per route. Each
operation:

1. resolves the target `User` within the caller's Tenant: Agent → its Login
   through `UserRepository.findByAgentId`; Client → Tester → `Tester.user`;
2. asks the guard;
3. calls the password write;
4. saves;
5. writes one audit line.

It does not compare the new password with the current one, and it reads no
other part of the target.

### Observability

On success, the service writes one audit line, reusing
`AuditLog.passwordChanged` exactly as its Javadoc anticipated:
`action=PASSWORD_CHANGED entity=User entityId=<target userId> actorUserId=<Manager's userId> tenantId=…`.
A reset is told apart from a self-service change because the subject differs
from the actor. The line carries no password, no hash and no length. A refused
reset writes no audit line.

### Schema

**No Flyway migration.** The feature overwrites `users.password_hash` and stores
nothing new.

### Frontend

All of it is assembled from shipped parts.

**The dialog.** One new client component: the reset-password dialog on
`DialogShell`.

- **Title:** "Reset password for {name}", where {name} is the Agent's name or,
  for a Tester, their email, which is all a Tester row carries.
- **Subtitle:** names the Login's email and says the handover is the Manager's
  job, e.g. "{email} will sign in with this password. Give it to them yourself —
  they can change it afterwards from their own menu."
- **Field:** one masked input, **New password**, with
  `autoComplete="new-password"`, the same shape as `LoginCredentialFields`'
  password half. That component is not reused, because it pairs the password
  with an email field this dialog must not have.
- **Failures:** rendered through `DialogErrorAlert`, branching on status and
  `readErrorCode` the way `create-agent-login-dialog` does:
  - `PASSWORD_TOO_SHORT` flags the field and keeps its value;
  - `AGENT_HAS_NO_LOGIN` says the page is stale;
  - `404` says the person no longer exists and links back to the list;
  - anything else gets a generic retry message.
- **Commit action:** the one pill-radius control in the dialog, labelled "Reset
  password". Cancel stays at the 8px control radius.

**On success:** the dialog closes and focus returns to the trigger. An
always-mounted polite status region announces "Password reset for {email}. Give
them the new password yourself." The password itself is never rendered back.

**One dialog instance per view.** It opens for whichever target was chosen, so
a Client with thirty Testers holds one closed dialog, not thirty.

**Agent's page.** `AgentSignInEmail` gains a **Reset password** row action
(`Button variant="row" size="sm"`) beside the email, shown only when the Agent
has a Login. When the Agent has none, the existing **Create login** action
stays the only one.

**Client's page.** `ManagerTestersView`'s table gains a trailing column. Its
header text is screen-reader-only ("Actions"), and each row holds a **Reset
password** row action whose accessible name includes that Tester's email, so
thirty buttons are thirty distinct names.

**BFF.** Two pass-through proxies, the same shape as every other proxy and using
`backendFetch`, so the JWT never leaves the httpOnly cookie.

Neither `lib/nav.tsx` nor any route changes. No console except the Manager's
changes at all.

**Naming:** "Reset password" throughout, never "account" (`CONTEXT.md` lists it
under _Avoid_ twice).

## Design direction

One surface, **Operate**: the reset-password dialog, plus two trigger
placements. It is assembled entirely from shipped patterns and commits no new
one, so **the `design` slot is not needed** and **`DESIGN.md` does not change**.

- The dialog matches the three creation dialogs and the change-password dialog:
  - `DialogShell`'s 12px radius, `canvas-overlay`, `shadow-elevated-strong`, and
    `bg-ink/40` backdrop;
  - inputs at the 8px radius and 36px height, with the `hairline-strong` border
    turning `primary` on `:focus-visible`;
  - `DialogErrorAlert` in `danger`/`danger-bg`.

  The Pill-Is-Primary Rule holds: exactly one pill in the dialog, the commit
  action.
- The triggers are **row actions**: 28px (`sm`), soft-indigo row tone, 8px
  radius. `DESIGN.md` already describes this and `CreateAgentLoginDialog`'s
  trigger already ships it. No indigo fill appears on a page that already has
  its primary pill ("Add tester" on the Client's page).
- The Testers table's new column follows the existing table cell rhythm
  (`px-4`, `py-2.5–3`), with the action right-aligned. `TableScroll` already
  handles the narrow viewport, so the column needs no mobile variant.

Visual goldens:

- **New goldens:** the dialog **open** on each of the two pages, per theme and
  breakpoint, against the stub-backend fixtures.
- **Existing goldens that will move:** those of the Manager's Agent page and
  Client page, for the new button and column. Those recaptures are expected and
  travel with the ticket that causes them.
- A golden moving on any other surface is a finding.

## Constraints

- **No Flyway migration.**
- Success is `204` with no body. The endpoints never return a password, a hash,
  or anything derived from them. No password or hash ever appears in a log line
  or a URL.
- The password is hashed by the existing `PasswordEncoder` bean
  (`BCryptPasswordEncoder`), called from the password write and nowhere else.
  There is no second encoder, no cost-factor change and no algorithm change.
- The minimum is `PasswordPolicy.MIN_LENGTH` (8), enforced as Bean Validation on
  the reset request DTO. `LoginRequest` stays `@NotBlank` alone.
- A Login in another Tenant is `404`, never `403`, so that it reveals nothing.
  A non-Manager caller is `403`, from the existing role matcher.
- The reset targets only `AGENT`- and `TESTER`-role Logins. No request field or
  path variable may name a `User`.
- The target's username, role, Tenant, Agent link and `created_at` are
  unchanged, and so is every row that references the target: `testers`,
  `agents`, `requests`, `agent_standing_amounts`, invoices.
- `SecurityConfig` gains no matcher. `/api/agents/**` and `/api/clients/**`
  already require `ROLE_MANAGER`, and neither new path matches the earlier,
  broader `/api/agents/*/invoice/**` rule.
- Backend tests run under `IntegrationTest`: a singleton Testcontainers
  Postgres, with each method in a transaction that rolls back. Mocking a
  repository in an integration test is banned (Backend rule 5).
- **No test, integration or e2e, may reset a seeded user's password.**
  `manager@example.com`, `agent@example.com` and `tester@example.com` are relied
  on by `IntegrationTest`'s token helpers and most e2e specs, and e2e state does
  not roll back. A reset test creates its own Agent or Tester first.
- Maven runs with `JAVA_HOME=/opt/homebrew/opt/openjdk@21`. Checkstyle, part of
  `mvn verify`, stays clean.
- e2e and visual runs go against the isolated stack, per
  `docs/agents/implementer-notes.md`. The user's docker-compose stack is never
  written to.
- New backend classes go in the existing `web` and `security` packages, the
  same choice `self-service-password-change` made and for the same reason.

## Testing decisions

Tests assert external behaviour only: HTTP status, the response body's `code`,
and whether a later sign-in succeeds. They never read the hash from the
database, check which repository method ran, or check a `PasswordEncoder`
interaction.

- **One seam, and it already exists:** the HTTP API seam, meaning
  `IntegrationTest` + MockMvc against a real Postgres. No new seam is added.
- **The proof of a reset is a sign-in, not a hash read.** After a reset,
  `IntegrationTest.loginAs(username, newPassword)` returns a token and
  `loginAs(username, oldPassword)` is `401`. This is the same assertion
  `ChangeOwnPasswordApiTest` makes, and the highest seam available.
- **Both routes get the full treatment:**
  - reset then sign-in, with the old password refused;
  - 7 characters refused `400 PASSWORD_TOO_SHORT`, and 8 accepted;
  - an Agent with no Login answers `409 AGENT_HAS_NO_LOGIN`;
  - an unknown id answers `404`;
  - `agentToken()` and `testerToken()` are refused `403`;
  - a failed reset leaves the old password working.
- **Cross-Tenant refusal uses `OtherTenantFixture`.** A Manager of the seeded
  Tenant resetting an Agent's Login and a Tester's Login in the other Tenant
  gets `404` for both. The other Tenant's person then still signs in with their
  original password.
- **The 8-character boundary joins `PasswordMinimumLengthApiTest`'s existing
  `@ParameterizedTest` table** as two more write paths. No copy-pasted tests are
  added (Backend rule 12). That file's stale Javadoc counts (tech-debt F10) are
  corrected while it is open, because the ticket is editing those exact lines.
- **"A reset changes one thing only":**
  - after a Tester reset, the Tester list still returns the same email and
    Primary Contact flag;
  - after an Agent reset, the Agents list still returns the same
    `loginUsername`, and the Agent's standing amounts are unchanged.
- **The guard is tested through the HTTP seam, not in isolation.** Its one
  unreachable-today branch, a Manager-role target, is covered by a narrow
  Spring-context test that calls the guard with a Manager `User`. That branch
  cannot be reached through either route, which is the point, and a test
  proves it stays refused.
- **The prefactor is proven by the existing suites passing unedited:**
  `AgentLoginApiTest`, `TesterApiTest`, `TesterUsernameConflictApiTest`,
  `ChangeOwnPasswordApiTest`, `PasswordMinimumLengthApiTest` (apart from the new
  rows) and `AuthLoginTest`. A grep showing `passwordEncoder.encode` in exactly
  one production class is part of the walkthrough.
- **Frontend component tests (Vitest + Testing Library) for the dialog**, in the
  shape of `create-tester-dialog.test.tsx` and
  `change-password-dialog.test.tsx`:
  - the BFF response is stubbed at the fetch boundary with each status and
    `code`;
  - the test asserts the rendered message, the flagged field, and that typed
    values are kept;
  - on success, the test asserts focus returns to the trigger and the status
    region is announced.
- **Component tests for `DialogShell`'s new mounting:** children are absent from
  the DOM while it is closed, and present and focusable once it is opened.
  Every existing dialog test must stay green unedited.
- **One e2e spec, `manager-resets-a-password.spec.ts`,** using `helpers.ts`'
  `login`/`logout`/`addTester`. As a Manager it:
  1. creates an Agent through the UI;
  2. resets that Agent's password from the Agent's page;
  3. logs out and signs in as the Agent with the new password;
  4. confirms the old password is refused;
  5. repeats steps 2–4 for a Tester added to a Client.

  This is what the walkthrough replays. The suite's load sensitivity
  (tech-debt) means a failure is judged against a controlled comparison on an
  idle machine, never waved off as a flake.
- **No unit test of the reset service or the password write.** Each is a few
  lines behind a Spring context, and a mocked `PasswordEncoder` would only
  assert the mock (Backend rule 6).

## Decisions taken

### Following the human's earlier answers

- **The target's existing sessions survive until their token expires; the reset
  does not sign them out.** This follows the human's 2026-09-22 answer on
  `self-service-password-change`. Revoking issued tokens needs a
  `password_changed_at` column and a check on every request, which is
  `deactivate-a-login`'s per-request account-state check, where the epic already
  parks the identical question. The feature line frames a reset as serving
  someone who *cannot* sign in, and such a person holds no session. Walkthrough
  step 22 puts this in front of the human, and a veto moves the feature onto
  that check.
- **The minimum is the existing 8-character `PasswordPolicy`,** the human's
  2026-09-22 answer, which applies to every path that sets a password. A reset
  is one more such path.

### Taken alone

- **The password-write prefactor is narrowed to the encode-and-set step.** The
  epic's premise, a hash written inline in `TesterController`, is gone because
  `TesterLoginService` exists. What remains is four services each repeating
  one step, so one operation removes it. Merging the services would move
  conflict handling and verification that have nothing to do with the write.
- **`DialogShell` is prefactored to mount children only while open.** This
  feature adds a second closed dialog to two pages, which is exactly how the
  recorded regression happens. The repository pays debt when a feature touches
  the module.
- **One dialog instance per view, opened for the chosen target.** Thirty Tester
  rows should not mean thirty hidden forms, and one instance keeps the DOM the
  same size whatever the list length.
- **The Manager types the password; the product generates none.** This matches
  how every Login's first password is set. The feature line says "sets", and the
  product has no place to show a generated secret safely.
- **One masked field, no confirm field.** This matches the three creation
  dialogs, which take the temporary password once. A mistyped reset is fixed by
  resetting again.
- **No refusal of a password equal to the current one.** The Manager does not
  know the current password, and a refusal would confirm a guess — an oracle on
  another person's secret.
- **The reset lives beside the sign-in email on the Agent's page and as a row
  action on the Client's Testers table.** Those are the only two places a
  Manager already sees these Logins, so a Manager looks there. It is cheap to
  move.
- **Endpoints address the Agent and the Tester, never a `User` id.** The Manager
  looks at Agents and Testers, not Users. No response gains a `User` id, and the
  paths fall under the existing Manager-only matchers.
- **The Agent route is `/login/password` under the Agent; the Tester route is
  `/password` on the Tester.** An Agent's Login is a sub-resource that may be
  absent, beside the existing `POST …/login`. A Tester is itself a Login.
- **Other-Tenant targets are `404`, not `403`.** This is the repository's
  settled Tenant-boundary convention, stated in `SecurityConfig` and used by
  every Manager controller, and it reveals nothing about another Tenant.
- **A new guard class states the permission rule; the service calls it after the
  Tenant-scoped lookup.** The epic asked for one. It gives `deactivate-a-login`
  one rule to reuse, and it stops a later route from reaching a Manager's Login.
  `FleetAccessGuard` is the prior art.
- **A login-less Agent answers `409 AGENT_HAS_NO_LOGIN`.** It mirrors the
  existing `AGENT_ALREADY_HAS_LOGIN`: the Agent exists and the page is stale,
  which is a conflict, not an absence.
- **A too-short password answers `400` with `code: PASSWORD_TOO_SHORT`.** The
  dialog must flag the right field, and the change endpoint already established
  that name for that rule.
- **A new controller holds both routes.** It needs its own
  `@ExceptionHandler`s for a coded `400` and `409`, and `AgentController` and
  `TesterController` have no use for them.
- **The audit line reuses `AuditLog.passwordChanged` with the Manager as the
  actor.** `self-service-password-change` shaped that line for this reuse, and
  subject ≠ actor is what marks a reset.
- **A refused reset is not audited.** This is the same reasoning as the sibling:
  failure counting is the start of a lockout feature nobody asked for.
- **`204` with no body.** There is nothing to return that the Manager does not
  already know.
- **A Tester is named by email in the dialog title.** A Tester carries no
  display name, only the Login's email.
- **`PasswordMinimumLengthApiTest`'s stale Javadoc counts are fixed in passing.**
  The ticket edits that table anyway, so the lines are already open.
- **Testing: the existing HTTP seam, the proof by real sign-in, and
  `OtherTenantFixture` for the Tenant boundary.** Prior art is
  `ChangeOwnPasswordApiTest`, `AgentLoginApiTest`, `TesterApiTest` and
  `PasswordMinimumLengthApiTest`. A hash read would pass with sign-in broken.
- **No unit tests of the service or the password write.** With a mocked encoder
  they would assert the mock (Backend rule 6).
- **Tests create their own Agent or Tester and never reset a seeded Login.**
  Seeded credentials underpin the token helpers and most e2e specs, and e2e
  state does not roll back.

## Open questions

None.

The two candidates were decided rather than filed:

- **Existing sessions:** the human answered the same question for self-service
  change on 2026-09-22, and the reasoning carries over unchanged.
- **Who may be reset:** the feature line names Agents and Testers only.

Both are recorded under `## Decisions taken` and both have a `[human]`
walkthrough step.

## Acceptance walkthrough

1. [agent] As the seeded Manager, create an Agent with a Login through the API, then `POST /api/agents/{agentId}/login/password` with an 8-character password and show `204` with an empty body. (stories: 1, 3, 19)
2. [agent] Sign in as that Agent with the new password and show a token comes back; sign in with the original password and show `401`. (stories: 11, 13)
3. [agent] Create a Tester under a Client through the API, `POST /api/clients/{clientId}/testers/{testerId}/password`, and show `204`; show the new password signs the Tester in and the old one is `401`. (stories: 2, 12, 13)
4. [agent] Send a 7-character password to each route and show `400` with `code: PASSWORD_TOO_SHORT`, and show the old password still signs the person in; then 8 characters is `204`. (stories: 8)
5. [agent] Reset an Agent created with no Login and show `409` with `code: AGENT_HAS_NO_LOGIN`; reset an unknown Agent id and an unknown Tester id and show `404` for both. (stories: 6, 21)
6. [agent] Using `OtherTenantFixture`'s other Tenant, have the seeded Manager reset an Agent's Login and a Tester's Login there and show `404` for both, then show each still signs in with its original password. (stories: 15)
7. [agent] Call both routes with the seeded Agent's token and the seeded Tester's token and show `403` each time. (stories: 16)
8. [agent] Run the guard's Manager-target test and show the refusal. Then show that neither route's path or body can name a `User`, and that no Manager-role Login appears on any screen offering a reset. (stories: 17)
9. [agent] After an Agent reset, show the Agents list still returns the same `loginUsername` and the Agent's standing amounts are unchanged. After a Tester reset, show the Testers list returns the same email and Primary Contact flag. (stories: 20)
10. [agent] Grep the backend log from steps 1 and 3 and show one `PASSWORD_CHANGED` line each, with `entityId` the target's user id, `actorUserId` the Manager's, and the Tenant's id. Show that no line contains the password or a BCrypt hash, and that the refused attempts of steps 4–7 logged no audit line. (stories: 18, 19)
11. [agent] Grep production sources and show `passwordEncoder.encode` in exactly one class, the password write. Show `git diff` touches no Flyway migration, no `SecurityConfig` matcher, no `lib/nav.tsx`, and no `LoginRequest`. (stories: 24)
12. [agent] Run `mvn verify` and show `AgentLoginApiTest`, `TesterApiTest`, `TesterUsernameConflictApiTest`, `ChangeOwnPasswordApiTest` and `AuthLoginTest` passing with no edits to them. Then run the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green. The only recaptured goldens are the Manager's Agent page and Client page. (stories: 24)
13. [agent] In a browser as the Manager, open an Agent with a Login and show **Reset password** beside the sign-in email. Open an Agent without one and show only **Create login**. (stories: 4, 6)
14. [agent] On a Client's page, show a **Reset password** action on every Tester row, each with an accessible name carrying that Tester's email. Show only one dialog element in the DOM, and no `<form>` inside it while it is closed. (stories: 5)
15. [agent] Open the reset for an Agent and show the title naming the Agent and the subtitle naming the email and the handover. Submit 7 characters and show the inline message on the field with the typed value kept, and no reset made. (stories: 7, 9)
16. [agent] Complete a reset in the dialog. Show it closes, focus is back on the **Reset password** trigger, and the status region reads "Password reset for {email}…" with no password on screen. (stories: 10, 19, 22)
17. [agent] Log out, sign in through the UI as that Agent with the new password and reach the Agent console. Open the viewer-chip menu, change the password to one of their own, and show the Manager's password is now refused. (stories: 11, 13, 14)
18. [agent] Repeat steps 15–17 for a Tester from the Client's page. (stories: 2, 5, 12, 14)
19. [agent] Nothing in the product today can remove an Agent's Login or delete an Agent or Tester, so a stale page cannot be produced for real. Instead, run the dialog's component tests with the BFF answering `409 AGENT_HAS_NO_LOGIN` and then `404`. Show the stale-page message for the first, and the "no longer exists" message with its link back to the list for the second, never the generic failure. (stories: 21)
20. [agent] Do the whole Tester reset by keyboard alone: Tab to the row action, open the dialog, type, submit, and Escape on a second open. Show a visible focus ring at each stop and focus returning to the trigger. Repeat at the mobile breakpoint and show the row action reachable and the dialog inside the viewport. (stories: 22, 23)
21. [human] Reset a real Agent's and a real Tester's password on the running app, hand the password over the way you actually would, and confirm the wording tells you the handover is yours and names the right person. (stories: 7, 10)
22. [human] Sign in as an Agent in a second browser, then reset that Agent's password as the Manager. Confirm the second browser keeps working until its session expires within the hour, and that you accept this for a reset as you did for a self-service change. (stories: 13)
23. [human] Confirm you are content that a Manager's Login cannot be reset by another Manager here, so a Manager who is locked out still needs the database. (stories: 17)

## Execution order

The order is final only once `## Open questions` is approved as `None`. Four
slices. Ticket 1 and ticket 2 are enablers, independent of each other. Tickets
3 and 4 are complete vertical paths, each demoable alone.

1. `one-password-write` — the password write component. `AgentLoginService`,
   `TesterLoginService` and `ChangePasswordService` call it instead of encoding,
   and the existing suites pass unedited. Labels: `enabler`, `backend`.
   Depends on nothing. (stories: —)
2. `dialog-shell-mounts-when-open` — `DialogShell` renders its children only
   while open, with its component tests, and every existing dialog test green
   unedited. Labels: `enabler`, `frontend`. Depends on nothing. (stories: —)
3. `reset-an-agents-password` — the guard class, the reset service, the new
   controller with the Agent route and its coded refusals, the audit line, and
   `PasswordMinimumLengthApiTest`'s new row and Javadoc fix. Also the Agent-route
   integration tests including `OtherTenantFixture`, the BFF proxy, the dialog,
   the **Reset password** action in `AgentSignInEmail`, component tests, the
   Agent half of the e2e spec, and the Agent page's goldens. Depends on
   `one-password-write` and `dialog-shell-mounts-when-open`. (stories: 1, 3, 4,
   6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 24)
4. `reset-a-testers-password` — the Tester route on the same controller and
   service, its integration tests including `OtherTenantFixture`, the BFF proxy,
   the Testers table's action column reusing the dialog, the Tester half of the
   e2e spec, and the Client page's goldens. Depends on
   `reset-an-agents-password`. (stories: 2, 5, 12, 15, 20, 21, 22, 23)
