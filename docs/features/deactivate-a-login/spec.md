---
feature: deactivate-a-login
epic: login-lifecycle
status: approved
date: 2026-10-02
---

<!-- sdlc:template spec 1 -->

# Deactivate a login

## Problem

When an Agent leaves the company, or a Tester leaves a Client, their Login
keeps working. Their password still signs them in, and from there they can
see the Fleet, raise requests and, for an Agent, submit an invoice. The
Manager has no way to switch the Login off. The only remedy today is to edit
the database by hand. A password reset does not help either: it locks the
person out only until someone gives them the new password.

Deleting the Login is not an option, and nothing in this feature deletes one.
Four tables reference `users`: `testers.user_id`, `requests.raised_by_user_id`,
`requests.decided_by_user_id` and `agent_standing_amounts.set_by_user_id`.
Deleting a Login would either fail or wipe out the record of who raised a
request, who decided it and who set a salary. The Agent record and its invoice
history must also stay readable after the person is gone. That is the
journey's own wording.

There is also a hidden trap, recorded in the epic. Today the product has no
notion of a Login being switched off. `AppUserPrincipal` leaves every Spring
Security account-status flag at `true`. `ChangePasswordService` checks the
current password with `PasswordEncoder.matches` directly, which matches sign-in
only because of those defaults. Once a Login can be deactivated, that service
would quietly let a deactivated Login change its own password unless this
feature closes the gap.

## Journeys

Advances `docs/roadmap/login-lifecycle.md`, the **third and last** of its
three features.

- **Keep a login working over time** (`wanted` → **`exists`**): this feature
  delivers the journey's third clause: "a login is deactivated when a person
  leaves, without destroying the Agent record and the invoice history hanging
  off it". With it comes the epic's last closing proof: "a deactivated login
  is refused at sign-in while its Agent record and invoice history remain
  intact and readable". The other two clauses already shipped, so delivering
  this feature moves the journey to `exists` in `docs/journeys.md` and closes
  the epic.
- **Sign in** (`exists`, stays `exists`): unchanged for every active Login,
  and proven unchanged. The only new behaviour is a refusal for a deactivated
  Login.
- **Set up the operating picture** (`exists`, stays `exists`): the Agent's page
  and the Client's Testers table gain a status and two actions. Nothing that
  journey already does changes.

## Goals / Non-goals

Goals:

- A Manager deactivates the Login of an Agent in their own Tenant, from that
  Agent's page, and reactivates it from the same place.
- A Manager deactivates and reactivates the Login of a Tester at one of their
  Clients, from that Tester's row on the Client's page.
- A deactivated Login is refused at sign-in, even with the right password.
- A deactivated Login loses access to everything it already had open. It
  happens at once (the human's answer, 2026-10-02).
- A deactivated Login cannot change its own password. The `ChangePasswordService`
  gap described in the epic is closed and covered by a test.
- Deactivation never deletes anything. The `User` row, its username, its
  password, the Agent record, the Agent's Contracts, standing amounts and
  invoices, the Tester row with its Client and Primary Contact flag, and every
  request the person raised or decided all stay exactly as they were. All of
  them stay readable to the Manager.
- Reactivation restores the Login as it was before. The same email and the
  same password sign in again.
- The Manager can see, wherever the Login is shown, that it is deactivated and
  since when.
- The same permission rule as a password reset applies: only a Manager, only
  an `AGENT` or `TESTER` Login, and only in the Manager's own Tenant.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No deactivating a Manager's Login.** That belongs to the admin/SuperAdmin,
  in the `tenant-administration` epic (human, 2026-10-01/02). No endpoint here
  accepts a Manager's Login as its target, and no screen offers it. A Manager
  cannot deactivate their own Login either.
- **No deleting a Login, an Agent or a Tester.** No "remove" action of any
  kind, and no cascade.
- **No archiving or retiring the Agent itself.** Deactivating the Login
  switches off sign-in, and nothing else. The Agent still appears in the
  Agents list, still holds its Contracts, still gets an Agent Invoice
  generated each month, and can still be picked wherever an Agent is picked
  today. Ending an Agent's engagement is a separate, unplanned piece of work.
- **No freeing the email for reuse.** A deactivated Login keeps its username,
  so the global `uq_users_username_global` index still rejects a new Login
  with that email. A deactivated Agent Login also still fills its Agent's one
  slot under V16's `uq_users_one_login_per_agent`. **Create login** is
  therefore never offered for that Agent.
- **No moving the Primary Contact flag automatically.** A deactivated Tester
  keeps the flag until the Manager moves it, as they do today.
- **No scheduled deactivation** ("switch off on 31 October"), no expiry date,
  and no automatic deactivation after a period without sign-in.
- **No reason field, no history screen, no notification.** An audit line
  records each change, and nothing else stores it. No email or message goes
  to the person. The product sends no outbound email.
- **No filtering or hiding of deactivated people** in the Agents list, the
  Testers table, pickers or request histories. They are shown as they are,
  with a tag where their Login appears.
- **No change to password resets, self-service change or Login creation**
  beyond the refusal for a deactivated Login described below.
- **No general session revocation for password changes or resets.** The
  per-request check built here looks only at whether the Login is
  deactivated. The human's 2026-09-22 answer still stands: after a password
  change, other sessions expire on their own. Adding a
  `password_changed_at` column would be a separate change.
- **No change to a Login's email/username.** It remains under the epic's
  `## Later`.

## User stories

1. As a Manager, I want to deactivate the Login of an Agent in my Tenant who has left, so that their password no longer gets them into the product.
2. As a Manager, I want to do the same for a Tester at one of my Clients, so that the rule does not depend on which kind of person left.
3. As a Manager, I want the deactivate action on the Agent's page next to the sign-in email, so that I find it where the Login is already shown.
4. As a Manager, I want a deactivate action on each Tester's row on the Client's page, so that I switch off the Tester I mean without leaving that list.
5. As a Manager, I want to be asked to confirm, with the dialog naming the person and the email and telling me what will and will not happen, so that I neither switch off the wrong Login nor do it with a stray click.
6. As a Manager, I want to see at a glance that a Login is deactivated, and since when, on the Agent's page and on the Tester's row, so that I know who can sign in without opening anything.
7. As a Manager, I want to reactivate a deactivated Login from the same place, after confirming, so that a mistake or a person's return is fixed in two clicks.
8. As a person whose Login was reactivated, I want to sign in with the same email and password as before, so that coming back needs nothing else from the Manager.
9. As a Manager, I want to reset the password of a deactivated Login, so that I can give a returning person a fresh password before or after reactivating them.
10. As the company, I want a deactivated Login refused at sign-in even when the password is right, so that a departed person cannot get back in.
11. As a person whose Login was deactivated, I want the sign-in page to tell me that my Login is switched off and to ask my Manager, rather than say my password is wrong, so that I don't keep retrying or ask for a reset that would not help.
12. As the company, I want a stranger who types a deactivated person's email with a wrong password to get the same answer as for any wrong password, so that the sign-in page reveals nothing about a Login without its password.
13. As the company, I want a deactivated Login to lose access to the product at once, even in a browser where it is already signed in, so that a person who leaves on bad terms cannot keep working for up to an hour.
14. As a person whose Login was deactivated while I was signed in, I want to land on the sign-in page at my next page load rather than see broken, empty pages, so that what happened is clear.
15. As the company, I want a deactivated Login unable to change its own password, so that switching a Login off cannot be undone from inside it.
16. As a Manager, I want deactivation to leave the Agent's record, Contracts, standing amounts, invoices and requests exactly as they were, and readable to me, so that the history of what the person did is not lost.
17. As a Manager, I want deactivation to leave the Tester's Client, Primary Contact flag and the requests they raised exactly as they were, so that a Client's history stays whole.
18. As a Manager, I want a deactivated Agent's Login still shown on the Agent's page, with no **Create login** offered, so that I am never invited to create a second Login for that Agent.
19. As a Manager of one Tenant, I want every attempt to deactivate or reactivate a Login in another Tenant refused as if it did not exist, so that the Tenant boundary holds and reveals nothing.
20. As an Agent or a Tester, I want to be unable to deactivate or reactivate anyone's Login, my own included, so that the ability stays with the Manager.
21. As the company, I want no path by which a Manager's Login can be deactivated through this feature, so that one Manager cannot lock another out. That is the admin's job.
22. As the company, I want an audit line recording whose Login was deactivated or reactivated, by which Manager, in which Tenant, and when, so that the event can be traced afterwards.
23. As a Manager whose page is stale, because the Login was already deactivated or reactivated elsewhere or the Agent has no Login, I want the page to end up showing the true state rather than an error I cannot act on, so that I refresh instead of retrying blindly.
24. As a Manager working by keyboard, I want to open, confirm and cancel both dialogs and have focus come back to where I started, so that the action is usable without a mouse.
25. As a Manager on a phone, I want the Tester row actions, the status tag and both dialogs usable at the mobile breakpoint, so that a departure can be handled away from a desk.
26. As everyone with an active Login, I want sign-in, every page and my own password change to behave exactly as before, so that the new account-state check carries no release risk.
27. As someone trying the demo, I want the seeded and demo Logins to stay active and keep their documented passwords, so that the README still gets me in.

## Solution

### Prefactoring: one lookup for "the Login a Manager is administering"

`PasswordResetService` already turns an Agent id, or a Client id plus a Tester
id, into the target `User` within the caller's Tenant. It uses
`findByIdAndTenantId` on Agent and Client, `findByIdAndClientId` on Tester and
`UserRepository.findByAgentId`, and it raises `AGENT_HAS_NO_LOGIN` for a Login-less
Agent. This feature needs exactly the same resolution for four more routes.

The prefactor is behaviour-preserving. That resolution moves into one small
component, **the administered-Login lookup**, which has two operations (by
Agent, and by Client and Tester) and then calls
`LoginAdministrationGuard.requireCanAdminister`. `PasswordResetService` calls
it instead of resolving the target itself. The existing reset tests pass
unedited at this step. As a result, the resolution and the guard each live in
one place, and the guard is called once, as the sibling spec intended ("the
same guard rather than restating it").

### Schema (V58)

```sql
ALTER TABLE users ADD COLUMN deactivated_at timestamptz NULL;
```

`NULL` means active. A non-null value means deactivated, and records since
when. The migration is additive: it rewrites no row and drops nothing, so
every existing Login, seeded or real, stays active. `User` gains the field,
plus `deactivate(now)`, `reactivate()` and `isDeactivated()`.

### What "deactivated" means, at three doors

**1. Sign-in.** `AppUserPrincipal.isEnabled()` returns `!user.isDeactivated()`,
which is the first non-default status flag. Spring's default
`DaoAuthenticationProvider` checks `isEnabled()` *before* it checks the
password, which would tell anyone typing an email that the Login exists and is
switched off. The provider is therefore configured so that the enabled check
runs **after** the password check (an empty pre-check plus a post-check that
throws `DisabledException`). This gives two cases:

- A wrong password, whether the Login is active or deactivated, is
  `BadCredentialsException`. The answer is `401` with no body, as today.
- The right password on a deactivated Login is `DisabledException`.
  `AuthController` catches it and answers `401 { "code": "LOGIN_DEACTIVATED" }`.
  It logs `login refused deactivated userId=… tenantId=…`, with no password.

The BFF session route maps `LOGIN_DEACTIVATED` to its own message: "This login
has been deactivated. Ask your Manager if you need access again." Any other
non-OK response keeps "Incorrect email or password." (The human's answer,
2026-10-02. The provider ordering is what stops the sign-in page from
revealing anything to someone without the password.)

**2. Every request with a token.** `JwtAuthenticationFilter`, after a token
parses, asks a new **Login-state check** whether that `userId` is still an
active Login. The check is a primary-key read for `deactivated_at`. If the
Login is deactivated, or the row is gone, the filter leaves the security
context unauthenticated, so the existing rules answer `401`. This is the
per-request account-state check the epic set aside for this feature. It lives
in the filter, not in `CallerIdentityResolver`, because only some endpoints
call the resolver, while the filter covers every authenticated route. (The human's
answer, 2026-10-02, to what was open question 1: at once.)

**3. Self-service password change.** `ChangePasswordService` refuses a
deactivated caller before it verifies anything. It throws a domain exception,
`CallerLoginDeactivatedException`, carrying `@ResponseStatus(UNAUTHORIZED)`
(coding-standards Backend 7), so the answer is `401`. It does
not switch to `AuthenticationManager`. Its `matches` check stays, as the
sibling specs left it. What changes is that it now checks the same flag sign-in
checks, so the two can no longer drift apart quietly. Door 2 already stops a deactivated caller before this
point; door 3 is defence in depth.

A password reset is **not** refused for a deactivated Login (story 9). It
changes the password and nothing else. The Login stays deactivated until it is
reactivated.

### Endpoints

As with the reset, the Manager addresses an Agent or a Tester, never a `User`
id.

```
POST /api/agents/{agentId}/login/deactivate
POST /api/agents/{agentId}/login/reactivate
POST /api/clients/{clientId}/testers/{testerId}/deactivate
POST /api/clients/{clientId}/testers/{testerId}/reactivate
(no request body)

200 { "deactivatedAt": "2026-10-02T09:14:00Z" }   — deactivate (or already deactivated: unchanged timestamp)
200 { "deactivatedAt": null }                     — reactivate (or already active)
404                                               — no such Agent / Client / Tester in the caller's Tenant
409 { "code": "AGENT_HAS_NO_LOGIN", ... }         — Agent routes only
403                                               — caller is not a Manager (role matcher), or the guard refuses
```

- Both actions are **idempotent**. Deactivating a Login that is already
  deactivated keeps its original `deactivatedAt` and writes no audit line.
  The same holds for reactivating a Login that is already active. A stale page
  therefore gets the true state back and shows it (story 23).
- The four routes live in one new controller with its own `@ExceptionHandler`
  for the coded `409`, as `PasswordResetController` does, backed by one new
  `@Transactional` **Login-activation service**. Each operation resolves the
  target through the administered-Login lookup (which runs the guard), sets or
  clears `deactivated_at`, saves, and audits on a real change only.
- `SecurityConfig` gains no matcher. All four paths fall under the existing
  `ROLE_MANAGER` rules for `/api/agents/**` and `/api/clients/**`, and none
  matches `/api/agents/*/invoice/**`.

### Read models

- The Agent response gains `loginDeactivatedAt`, which is `null` when the Login
  is active or when there is no Login. It sits beside `loginUsername`.
- The Tester response gains `deactivatedAt`.
- No other response changes. Request histories keep showing who raised and
  decided each request, and that person may now be deactivated.

### Observability

Two new `AuditLog` events, in the shape of `passwordChanged`:

- `action=LOGIN_DEACTIVATED entity=User entityId=<target userId> actorUserId=<Manager> tenantId=…`
- `action=LOGIN_REACTIVATED entity=User entityId=<target userId> actorUserId=<Manager> tenantId=…`

A refused attempt (`403`/`404`/`409`) and an idempotent no-op write no audit
line. A sign-in refused for a deactivated Login writes one `warn` line
(`login refused deactivated`). A request refused at door 2 writes nothing,
because it would flood the log during the seconds after a deactivation.

### Frontend

**Leaving a dead session.** Today only Manager pages ask the backend who the
caller is (`requireManager`). Agent and Tester pages that get a `401` mostly
render empty lists. The guard helper therefore gains `requireAgent` and
`requireTester` in the same shape: call `/api/me`, and on a non-OK answer or
the wrong role, `redirect("/login")`. Each backend-driven Agent and Tester page
calls its guard, the way Manager pages do. Like `requireManager`, the guards
are scoped per page so that the visual suite's backend-less pages keep
rendering. A client-side action that gets a `401` mid-dialog shows that
dialog's existing generic error, and the next page load lands on sign-in. It also
helps when a token simply expires, which today produces the same empty pages.

**Agent's page.** `AgentSignInEmail`:

- Active Login: the email, then the **Reset password** and **Deactivate login**
  row actions.
- Deactivated Login: the email followed by a **Deactivated** status tag
  ("Deactivated since 2 Oct 2026" as its accessible text and tooltip), then
  **Reset password** and **Reactivate login**.
- No Login: **Create login** alone, as today.

**Client's page.** In `ManagerTestersView`, the email cell carries the same
**Deactivated** tag. The trailing Actions column holds **Reset password** and
then **Deactivate login** or **Reactivate login**. Each action's accessible
name includes that Tester's email.

**The two dialogs.** Each is one confirm step on `DialogShell`, with one
instance per view, opened for the chosen target, as the reset dialog is.

- **Deactivate login for {name}**. Body: "{email} will no longer be able to
  sign in, and is signed out at once. Their record, requests and invoices stay
  as they are. You can reactivate this login later." Actions: **Cancel** and
  **Deactivate login**, the step's one pill.
- **Reactivate login for {name}**. Body: "{email} will be able to sign in
  again with their existing password. If they don't know it, reset it
  afterwards." Actions: **Cancel** and **Reactivate login**.

On `200` the dialog closes and the page is refreshed with `router.refresh()`.
Focus returns to the trigger's position, and an always-mounted polite status
region announces "Login deactivated for {email}." or "Login reactivated for
{email}." Failures render through `DialogErrorAlert`:

- `AGENT_HAS_NO_LOGIN`: the page is stale;
- `404`: the person no longer exists;
- anything else: "Couldn't change this login. Try again."

{name} is the Agent's name, or the Tester's email, as in the reset dialog.

**BFF.** Four pass-through proxies on `backendFetch`, in the same shape as the
reset proxies. The session route gains the `LOGIN_DEACTIVATED` mapping.

**Naming.** "Deactivate login", "Reactivate login" and "Deactivated"
throughout. Never "account", "disable", "suspend", "block" or "delete".

## Design direction

One surface, **Operate**: two confirm dialogs, one status tag and two row
actions. They are built from shipped tokens and components (`DialogShell`,
`Button variant="row" size="sm"`, `DialogErrorAlert`, and the existing status
tag/badge style the request and invoice lists use), so **the `design` slot is
not needed**.

- The dialogs match the reset dialog's confirm step exactly: 12px radius,
  `canvas-overlay`, `shadow-elevated-strong`, `bg-ink/40` backdrop, and one
  pill per step. **Deactivate login** is the pill on its dialog. It is not
  coloured `danger`, because the action is reversible, and the reset's confirm
  pill set that precedent.
- The **Deactivated** tag is a quiet neutral tag (muted ink on `canvas-soft`).
  It is not a danger tag. It describes a state, not an error.
- Row actions are 28px, soft-indigo row tone, 8px radius, beside **Reset
  password**. The Testers table's action cell grows to two actions.
  `TableScroll` already handles the narrow viewport, and the recorded mobile
  horizontal-scroll debt on that row gets no worse in kind.
- `DESIGN.md` gains no entry unless the status tag is a new composition. If
  the implementer finds no shipped neutral tag, it adds one short entry in the
  same commit.

Visual goldens:

- **New goldens:** the deactivate confirm step on the Agent's page and the
  Client's page, and the reactivate confirm step once, per theme and
  breakpoint, against stub-backend fixtures. The stub backend gains one Agent
  and one Tester with a deactivated Login.
- **Existing goldens that will move:** those of the Manager's Agent page and
  Client page, for the new action. The recaptures travel with the ticket that
  causes them.
- A golden moving on any other surface is a finding.

## Constraints

- **Migration V58**, additive only: one nullable column and no data rewrite.
  V56 and V57 are reserved by `edit-client-invoice-lines` and
  `send-a-client-invoice-back`.
- Nothing in this feature deletes a `users`, `agents`, `testers`, `requests`,
  `agent_standing_amounts` or invoice row, or changes any column other than
  `users.deactivated_at`.
- A deactivated Login's username, password hash, role, Tenant, Agent link and
  `created_at` are unchanged by deactivation and by reactivation.
- A wrong password is answered identically for an active and a deactivated
  Login: `401`, no body, same timing path (BCrypt runs in both cases).
- A Login in another Tenant is `404`, never `403`. A non-Manager caller is
  `403` from the existing role matcher. Only `AGENT`- and `TESTER`-role Logins
  can be targeted, and no path or body names a `User`.
- `LoginRequest` stays `@NotBlank` alone. `ChangePasswordRequest` and
  `PasswordPolicy` are unchanged.
- The per-request check adds one primary-key read per authenticated request
  and nothing else. No cache, because a cache would delay the refusal.
- Backend tests run under `IntegrationTest` (singleton Testcontainers
  Postgres, rollback per method). Mocking a repository in an integration test
  is banned (Backend rule 5).
- **No test, integration or e2e, may deactivate a seeded Login**
  (`manager@`, `agent@`, `tester@example.com`) or a `DemoDataLoader` Login.
  Each test creates its own Agent or Tester, because e2e state does not roll
  back.
- Maven runs with `JAVA_HOME=/opt/homebrew/opt/openjdk@21`. Checkstyle stays
  clean.
- e2e and visual runs go against the isolated stack, per
  `docs/agents/implementer-notes.md`.
- New backend classes go in the existing `web` and `security` packages.

## Testing decisions

Tests assert external behaviour only: HTTP status, the response's `code` and
`deactivatedAt`, and whether a later sign-in or request succeeds. They never
read `deactivated_at` from the database to prove a refusal.

- **One seam, and it already exists:** the HTTP API seam, `IntegrationTest` +
  MockMvc against real Postgres. Prior art is `AgentPasswordResetApiTest` and
  `TesterPasswordResetApiTest`, `ChangeOwnPasswordApiTest`,
  `AuthLoginTest` and `AgentLoginApiTest`. No new seam is added.
- **The proof of deactivation is a refused sign-in and a refused request.**
  For each route pair:
  - create an Agent or Tester, sign in, and keep the token;
  - deactivate it, then show that sign-in with the right password is
    `401 LOGIN_DEACTIVATED`, that sign-in with a wrong password is `401` with
    no body, and that `GET /api/me` with the kept token is `401`;
  - reactivate it, then show that the same password signs in and that a fresh
    token works.
- **Self-service change refusal.** Under door 2, `POST /api/me/password` with a
  pre-deactivation token is `401`. Door 3 cannot be reached over HTTP while
  door 2 stands, so a narrow Spring-context test calls `ChangePasswordService`
  with a deactivated `User` and shows the refusal, and shows the password
  still unchanged by a later reactivated sign-in. This test is the one that
  makes the epic's "no test will catch that" false.
- **"Deactivation is not deletion".** After deactivating an Agent's Login,
  show through the Manager's endpoints that the Agents list, the Agent's
  standing amounts, its Contracts, its Agent Invoices and the requests it
  raised all return what they returned before. After deactivating a Tester,
  show that the Testers list still returns the same email and Primary Contact
  flag, and that the Client's requests still name the Tester as raiser.
  `POST /api/agents/{agentId}/login` on that Agent is still
  `409 AGENT_ALREADY_HAS_LOGIN`.
- **Idempotency:** deactivating twice returns the same `deactivatedAt`, and the
  captured log holds one `LOGIN_DEACTIVATED` line.
- **Boundary:** an unknown id is `404`, a Login-less Agent is
  `409 AGENT_HAS_NO_LOGIN`, and `agentToken()`/`testerToken()` are `403`. Using
  `OtherTenantFixture`'s Agent and Tester (added by the reset feature), the
  seeded Manager gets `404`, and the other Tenant's person still signs in.
- **Reset on a deactivated Login** returns `200` with a password. Sign-in with
  it is `401 LOGIN_DEACTIVATED` until reactivation, then succeeds.
- **The guard's Manager-target branch** is already covered by the reset
  feature's narrow test. The lookup prefactor keeps it on the same path, so no
  new test is needed.
- **Every active Login unchanged:** `AuthLoginTest`, `ChangeOwnPasswordApiTest`
  and the whole suite pass unedited apart from new tests. The demo-profile
  context test still signs in the demo Logins.
- **Frontend component tests (Vitest + Testing Library)**, in the shape of the
  reset dialog's tests: each dialog's Cancel sends nothing; `200` closes,
  refreshes and announces; each failure renders its message; the tag and the
  correct action appear for active, deactivated and Login-less states in
  `AgentSignInEmail` and the Testers row. The login form shows the
  deactivated message for `LOGIN_DEACTIVATED` and the generic one otherwise.
  The new guards redirect on a non-OK `/api/me`.
- **One new e2e spec, `deactivate-a-login.spec.ts`,** using `helpers.ts`'
  `login`/`logout`/`addTester`. As a Manager, in one browser context, it
  creates an Agent and keeps the revealed password. In a second context it
  signs in as that Agent. Back as the Manager it deactivates the Login, then
  shows that the Agent's next navigation in the second context lands on
  sign-in, and that signing in again shows the deactivated message. It then
  reactivates and shows sign-in works. It repeats this for a Tester. The
  suite's load sensitivity means a failure is judged against a controlled
  comparison on an idle machine, never waved off as a flake.

## Decisions taken

### Following the human's answers

- **Deactivation takes effect at once** (human, 2026-10-02,
  `deactivate-a-login-when-it-takes-effect`). A signed-in person's next request
  is refused and lands on the sign-in page; no hour of access remains.
- **The sign-in page says so, only with the right password** (human,
  2026-10-02, `deactivate-a-login-sign-in-message`): "This login has been
  deactivated. Ask your Manager if you need access again." A wrong password
  keeps "Incorrect email or password.", so a stranger learns nothing.

- **Only a Manager administers Agent and Tester Logins, in their own Tenant. A
  Manager's own Login is the admin/SuperAdmin's job, in
  `tenant-administration`** (human, 2026-10-01/02).
- **Passwords are always generated by the product, never typed** (human,
  2026-10-01/02, as for the reset). Nothing here sets a password. Reactivation
  keeps the existing one, and a fresh one comes only through the existing
  reset.

### Taken alone

- **Deactivation is a nullable `deactivated_at` timestamp, not a boolean.**
  The Manager is shown "since when" at no extra cost, and null-means-active
  makes the migration a no-op for every existing row.
- **`isEnabled()` is wired, and the provider checks it after the password.**
  This uses Spring's own status flag, so `DaoAuthenticationProvider` refuses
  the Login. The order means the sign-in page never reveals a Login's state to
  someone without its password.
- **The per-request refusal lives in `JwtAuthenticationFilter`, through a small
  Login-state check, not in `CallerIdentityResolver`.** Only the filter covers
  every route. The resolver serves only some endpoints. It follows the human's
  answer that deactivation takes effect at once.
- **No cache on the per-request check.** A primary-key read is cheap at this
  product's scale, and a cache would bring back the delay that acting at once
  removes.
- **`ChangePasswordService` gains a deactivated-caller refusal and keeps
  `matches`.** That closes the epic's divergence with the smallest change and
  follows the sibling specs' "verification stays as it is". Routing it
  through `AuthenticationManager` would have changed how the current password
  is verified.
- **A password reset is allowed on a deactivated Login.** It changes only the
  password, and a Manager preparing someone's return wants that. The Login
  stays switched off until reactivated, so a reset never re-opens access by
  itself.
- **Reactivation keeps the existing password.** The common case is a mistake
  or a short absence. A Manager who wants a fresh password presses **Reset
  password**, which sits right beside it. Walkthrough step 22 puts this to the
  human.
- **Both actions ask for confirmation.** Deactivation signs a working person
  out, and reactivation re-opens a departed person's access. Neither should
  happen on a stray click, and the reset dialog set the pattern.
- **Deactivate and reactivate are idempotent `200`s, not `409`s.** A stale page
  gets the true state back and shows it. There is nothing for the Manager to
  resolve.
- **Four `POST` action routes under the Agent's `/login` and on the Tester.**
  They mirror the reset routes' addressing: an Agent's Login is a
  sub-resource, and a Tester is a Login.
- **A new controller and service; `PasswordResetController` is not grown.**
  Its name is about passwords. The shared part, target resolution plus guard,
  is factored out instead.
- **Prefactor: the administered-Login lookup.** It means two services do not
  restate the same Tenant-scoped resolution and guard call. The sibling spec
  asked that the guard be reused, not restated.
- **The Agent and Tester responses gain a nullable timestamp, and no list is
  filtered.** The Manager sees the state where the Login already appears.
  Hiding deactivated people would hide history.
- **The Agent is untouched: still listed, still pickable, still invoiced.** The
  journey and the feature line speak of the Login only. Retiring an Agent is a
  separate concept.
- **Agent and Tester pages gain a `requireAgent`/`requireTester` guard that
  redirects to sign-in.** Without it, a refused token renders empty pages,
  which is already true today for an expired one. `requireManager` is the
  prior art.
- **No audit line for a no-op, a refused attempt, or a door-2 refusal.** Each
  real state change gets exactly one line. A per-request refusal line would
  flood the log right after every deactivation.
- **The tag is neutral, and the deactivate pill is not `danger`.** Deactivation
  is reversible, and a red treatment would suggest data loss, which this
  feature exists to avoid.
- **The Deactivated tag shows the date only, not who did it.** The audit line
  holds the actor, and a who-did-it display would need a stored actor column
  nobody asked for.
- **Testing: the existing HTTP seam, with proof by a refused sign-in and a
  refused request, not a column read.** The prior art is
  `ChangeOwnPasswordApiTest`, `AuthLoginTest` and the reset feature's API
  tests. A column read would pass even with the refusal broken.
- **One narrow Spring-context test for `ChangePasswordService`'s refusal.**
  It is the one door no HTTP test can reach while door 2 stands, and it is
  the exact gap the epic said no test would catch.
- **Tests never deactivate a seeded or demo Login.** Seeded credentials
  underpin the token helpers and most e2e specs, and e2e state does not roll
  back.

- **The test guard's false positive on `LoginDeactivatedException` is named in the ticket, not treated as a retry (after review, `deactivated-login-refused`).** `sdlc-test-guard` matched `.Disabled` in Spring's `DisabledException` import in production code; no test was skipped or edited. The ticket's `## Regression` now names it and the same commit was re-merged. Reason: no code was wrong, so a fresh implementer would have nothing to change.
- **A deactivated caller's own password change answers 401 through a domain exception, `CallerLoginDeactivatedException` with `@ResponseStatus(UNAUTHORIZED)` (after review: the fix pass replaced the first build's `BadCredentialsException` to obey Backend 7).** The ticket's line that the controller "already maps" it to 401 was inaccurate (`ChangePasswordController` maps its own refusals to 400). Door 2 refuses such a caller first, so this path is defence in depth.

## Open questions

None.

## Acceptance walkthrough

1. [agent] As the seeded Manager, create an Agent and keep the generated password. Sign in as that Agent and keep the token. `POST /api/agents/{agentId}/login/deactivate` and show `200` with a `deactivatedAt` timestamp. (stories: 1)
2. [agent] Sign in as that Agent with the right password and show `401` with `code: LOGIN_DEACTIVATED`. Sign in with a wrong password and show `401` with no body, exactly as for an active Login with a wrong password. (stories: 10, 11, 12)
3. [agent] With the token kept from step 1, call `GET /api/me` and an Agent endpoint, and show `401` for both. Call `POST /api/me/password` with it and show `401`. Run the narrow `ChangePasswordService` test and show the deactivated caller refused. (stories: 13, 15)
4. [agent] As the Manager, show the Agents list, the Agent's standing amounts, Contracts, Agent Invoices and the requests it raised return what they returned before step 1. Show `GET` of the Agent returns the same `loginUsername` with `loginDeactivatedAt` set. Show `POST /api/agents/{agentId}/login` answers `409 AGENT_ALREADY_HAS_LOGIN`. (stories: 6, 16, 18)
5. [agent] Deactivate again and show the same `deactivatedAt` and only one `LOGIN_DEACTIVATED` line in the log. (stories: 22, 23)
6. [agent] Reset the deactivated Agent's password and show `200` with a password. Show sign-in with it is `401 LOGIN_DEACTIVATED`. `POST …/login/reactivate` and show `deactivatedAt: null`. Show the reset password now signs in. (stories: 7, 8, 9)
7. [agent] On a fresh Agent, deactivate then reactivate without a reset, and show the original generated password signs in again. (stories: 7, 8)
8. [agent] Repeat steps 1–3 and 6 for a Tester through `POST /api/clients/{clientId}/testers/{testerId}/deactivate` and `/reactivate`. Show the Testers list keeps the same email and Primary Contact flag, and that the Client's requests still name the Tester as raiser. (stories: 2, 10, 13, 17)
9. [agent] Show `409 AGENT_HAS_NO_LOGIN` for a Login-less Agent, and `404` for unknown Agent and Tester ids. Show `404` for the `OtherTenantFixture` Agent and Tester, and that both still sign in afterwards. (stories: 19, 23)
10. [agent] Call all four routes with the seeded Agent's and Tester's tokens and show `403`. Show that no path or body can name a `User`, and that no Manager-role Login appears on any screen offering deactivation. (stories: 20, 21)
11. [agent] Grep the log and show one `LOGIN_DEACTIVATED` or `LOGIN_REACTIVATED` line per real change, with target user id, actor and Tenant, and none for the refusals of steps 9–10. Show one `login refused deactivated` line per step-2-style sign-in, with no password. (stories: 22)
12. [agent] Show `git diff` adds exactly one migration, V58, which only adds a nullable column. Run `AuthLoginTest`, `ChangeOwnPasswordApiTest`, the demo-profile context test and `mvn verify`, then the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green. Show the only recaptured goldens are the Manager's Agent page and Client page. (stories: 26, 27)
13. [agent] In a browser as the Manager, open an Agent with an active Login and show **Reset password** and **Deactivate login** beside the email. Open one without a Login and show only **Create login**. (stories: 3, 18)
14. [agent] Open **Deactivate login**: show the title naming the Agent, and the body naming the email, saying they are signed out at once and that the record, requests and invoices stay. Press Cancel and show the Agent still signs in. (stories: 5)
15. [agent] Confirm. Show the dialog closes, focus returns, the status region announces "Login deactivated for {email}.", and the email now carries the **Deactivated** tag with its date, with **Reactivate login** offered and no **Create login**. (stories: 6, 18, 24)
16. [agent] In a second browser context already signed in as that Agent, navigate and show the sign-in page. Sign in with the right password and show the deactivated message. Sign in with a wrong one and show "Incorrect email or password." (stories: 11, 12, 13, 14)
17. [agent] As the Manager, open **Reactivate login**, confirm, and show the tag gone and the announcement made. In the second context, sign in with the same password and reach the Agent console. (stories: 7, 8)
18. [agent] Repeat steps 14–17 for a Tester from the Client's page. Show each row's actions carry that Tester's email in their accessible names, and that the Deactivated tag sits in the email cell. (stories: 2, 4, 5, 6, 7)
19. [agent] Run the dialogs' component tests with the BFF answering `409 AGENT_HAS_NO_LOGIN` and `404`, and show the stale-page and no-longer-exists messages, never the generic one. (stories: 23)
20. [agent] Do the whole Tester deactivate and reactivate by keyboard alone: Tab to the row action, open, cancel with Escape, open again, confirm. Show a visible focus ring at each stop and focus returning. Repeat at the mobile breakpoint and show the tag, both row actions and the dialog inside the viewport. (stories: 24, 25)
21. [human] On the running app, deactivate a real Agent who is signed in on a phone, and watch the phone's next tap land on sign-in. Read the sign-in message as that person would, and the dialog wording as the Manager. Confirm both say the right thing and never suggest anything was deleted. (stories: 5, 11, 13, 14)
22. [human] Reactivate that Agent and confirm you accept that their old password works again, with **Reset password** beside it if you want a fresh one, rather than reactivation generating a new password. (stories: 7, 8, 9)
23. [human] Open the deactivated Agent's page, its invoices and a request it raised, and the Client's page for a deactivated Tester. Confirm everything you need about the person who left is still there and readable. (stories: 16, 17)

## Execution order

Eight slices.

1. `administered-login-lookup` — extract the Tenant-scoped target resolution and the guard call from `PasswordResetService` into one component. The reset suites pass unedited. Labels: `enabler`, `backend`. Depends on nothing. (stories: none)
2. `deactivated-login-refused` — V58, `User.deactivatedAt`, `isEnabled()` wiring with the post-password check, `LOGIN_DEACTIVATED` at sign-in, the per-request Login-state check in the filter, and `ChangePasswordService`'s refusal with its narrow test. Tests deactivate through the `User` entity in-transaction. Labels: `backend`. Depends on nothing. (stories: 10, 11, 12, 13, 15, 26, 27)
3. `deactivate-an-agents-login-api` — the Login-activation service, the new controller with the two Agent routes, the audit events, `loginDeactivatedAt` on the Agent response, and the integration tests including "not deletion", idempotency, the boundary and reset-on-deactivated. Labels: `backend`. Depends on `administered-login-lookup` and `deactivated-login-refused`. (stories: 1, 7, 8, 9, 16, 18, 19, 20, 21, 22, 23)
4. `deactivate-a-testers-login-api` — the two Tester routes on the same controller and service, `deactivatedAt` on the Tester response, and their integration tests. Labels: `backend`. Depends on `deactivate-an-agents-login-api`. (stories: 2, 7, 8, 17, 19, 20, 22, 23)
5. `deactivated-message-at-sign-in` — the session route's `LOGIN_DEACTIVATED` message with component tests. Labels: `frontend`. Depends on `deactivated-login-refused`. (stories: 11)
6. `dead-session-lands-on-sign-in` — `requireAgent`/`requireTester` on the backend-driven Agent and Tester pages. Labels: `frontend`. Depends on `deactivated-login-refused`. (stories: 14)
7. `deactivate-an-agents-login-ui` — the two BFF proxies, both dialogs, the tag and the actions in `AgentSignInEmail`, component tests, the Agent half of the e2e spec, the stub-backend fixture and the Agent page's goldens. Labels: `frontend`. Depends on `deactivate-an-agents-login-api` and `deactivated-message-at-sign-in` and `dead-session-lands-on-sign-in`. (stories: 1, 3, 5, 6, 7, 13, 14, 18, 24)
8. `deactivate-a-testers-login-ui` — the two BFF proxies, the tag and actions in the Testers table reusing the dialogs, the Tester half of the e2e spec and the Client page's goldens. Labels: `frontend`. Depends on `deactivate-a-testers-login-api` and `deactivate-an-agents-login-ui`. (stories: 2, 4, 5, 6, 7, 17, 24, 25)
