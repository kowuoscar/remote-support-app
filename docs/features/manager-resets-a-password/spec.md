---
feature: manager-resets-a-password
epic: login-lifecycle
status: approved
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

And the first password is one the Manager makes up and types. The human's
2026-09-30 answer is that passwords are **generated, not typed** — at a reset
and at every Login creation — so the Manager no longer types a password
anywhere.

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
- **Set up the operating picture** (`exists`, stays `exists`, **changed**):
  creating an Agent, giving an existing Agent a Login, and adding a Tester no
  longer take a password from the Manager. The product generates it and shows
  it once. The journey's proofs, `manager-entity-setup.spec.ts` and
  `create-agent-with-login.spec.ts`, are rewritten to read the shown password
  and sign in with it, so they keep proving the journey.

## Goals / Non-goals

Goals:

- A Manager resets the password of the Login of an Agent in their own Tenant,
  from that Agent's page.
- A Manager resets the password of the Login of a Tester in their own Tenant,
  from that Tester's row on the Client's page.
- The Manager does not need the current password, and is told nothing about it.
- The product **generates** the new password on the backend. The Manager types
  none.
- Creating an Agent, giving an existing Agent a Login, and adding a Tester
  likewise **generate** the first password. No Manager screen has a password
  field any more.
- A generated password is shown to the Manager **exactly once**, right after
  the reset or creation, with a one-click copy. It is never retrievable again:
  not in any later response, not in a log line, not in an audit line.
- The new password is never equal to the one it replaces.
- Once the reset succeeds, the new password signs the person in and the old one
  does not.
- No Manager can reach a Login in another Tenant. No Agent or Tester can reset
  anyone's password.
- Every password write in the product goes through one place, as the epic
  planned.

Non-goals — each is something a reasonable agent would otherwise build:

- **No resetting a Manager's Login.** Resetting a Manager's password is the
  SuperAdmin's job (human, 2026-09-30) and belongs to the
  `tenant-administration` epic (`docs/roadmap/tenant-administration.md`). No
  endpoint here takes a Manager's Login as its target, and no screen lists one.
  A Manager changes their own password through the viewer-chip menu
  `self-service-password-change` shipped; a Manager who is locked out still
  needs the database until that epic lands.
- **No typed password anywhere a Manager sets one.** No "type your own instead"
  option, no field pre-filled with a suggestion that can be edited.
- **No regenerating from the reveal.** The shown password has no "generate
  another" button. Wanting a different one is a fresh reset.
- **No showing the password again.** No "show last password", no password on
  the Agent's page or the Testers table, no password in a list response. A
  Manager who lost it resets again.
- **No typed password for the seeded or demo logins.** The Flyway-seeded
  `manager@`, `agent@` and `tester@example.com`, and `DemoDataLoader`'s demo
  logins, keep their documented passwords. Nothing here regenerates them.
- **No forced change on next sign-in.** The epic lists it under `## Later`.
  After a reset, the person signs in with what they were given and can change
  it themselves from the viewer-chip menu if they want to.
- **No email, notification or in-app message** to the person whose password was
  reset or whose Login was created. The product sends no outbound email, and the
  handover is out of band by design.
- **No signing the target out of sessions they already hold.** An existing
  session stays valid until its token expires, within the hour. Revoking it
  would need the per-request account-state check that `deactivate-a-login`
  builds. This follows the human's 2026-09-22 answer for self-service change —
  see `## Decisions taken`.
- **No login deactivation, and no enabled/disabled flag.** That is
  `deactivate-a-login`.
- **No change to a Login's email/username.** It is under the epic's `## Later`.
- **No change to self-service password change.** A signed-in user still types
  their own new password, with the 8-character minimum and the
  `PASSWORD_UNCHANGED` refusal as shipped. "Generated, not typed" is about the
  passwords a Manager sets for someone else.
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

1. As a Manager, I want to reset the password of the Login of an Agent in my
   Tenant who cannot sign in, so that they can get back to work without anyone
   editing the database.
2. As a Manager, I want to do the same for a Tester at one of my Clients, so
   that the rule does not depend on which kind of person is locked out.
3. As a Manager, I want to reset without knowing the current password, so that
   a reset works for exactly the person who has forgotten it.
4. As a Manager, I want the reset on the Agent's page next to the sign-in email
   it changes, so that I find it where the Login is already shown.
5. As a Manager, I want a reset action on each Tester's row on the Client's
   page, so that I reset the Tester I mean without leaving that list.
6. As a Manager looking at an Agent who has no Login, I want to be offered
   Create login and not a reset, so that I am never offered a reset with
   nothing to reset.
7. As a Manager, I want the reset to name the person and the email, say that
   their current password stops working, and wait for my confirmation, so that
   I neither reset the wrong Login nor reset one by a stray click.
8. As a Manager, I want the product to generate the new password rather than
   have me type one, so that no password I made up, reused or could guess ever
   protects someone else's Login.
9. As a Manager creating an Agent, giving an existing Agent a Login, or adding a
   Tester, I want the first password generated too, so that I never type a
   password for anyone.
10. As a Manager, I want the generated password shown to me once, right after
    the reset or the creation, next to the email it belongs to, so that I can
    hand it over out of band.
11. As a Manager, I want to copy it in one action, and to be able to read it
    aloud or type it on a phone without mixing up look-alike characters, so
    that the handover works by chat, by phone or in person.
12. As a Manager, I want to be told plainly that the password will not be shown
    again and that handing it over is up to me, so that I keep it before I
    close the dialog.
13. As a Manager who closed the dialog before keeping the password, I want to
    recover by resetting that Login, so that a lost password is never a dead
    end — even for a Login I have just created.
14. As the company, I want a generated password to exist in the clear only in
    that one response and that one dialog — never in a later response, a list,
    a log line, an audit line, a URL or a browser cache — so that the reveal is
    the only copy.
15. As the company, I want a reset's new password never to equal the one it
    replaces, so that a reset always changes the password.
16. As an Agent whose password was reset, I want to sign in with the new
    password, so that the reset is real rather than reported.
17. As a Tester whose password was reset, I want the same.
18. As a person whose password was reset, I want the old password refused at
    sign-in, so that a password someone else may know stops working.
19. As a person whose password was reset or whose Login was just created, I
    want to change it to one of my own through the viewer-chip menu, so that
    the Manager does not keep knowing my password.
20. As a Manager of one Tenant, I want every attempt to reset a Login in another
    Tenant refused as if it did not exist, so that the Tenant boundary holds and
    reveals nothing.
21. As an Agent or a Tester, I want to be unable to reset anyone's password, my
    own included, so that the ability stays with the Manager.
22. As the company, I want no path by which a Manager's Login can be reset
    through this feature, so that one Manager cannot take over another — that
    is the SuperAdmin's job.
23. As the company, I want an audit line recording whose password was reset, by
    which Manager, in which Tenant, and when — with no password and no hash —
    so that the event can be traced afterwards.
24. As a Manager, I want a reset to leave everything else about the person as it
    was — the Agent's record, Contracts, standing amounts and invoices, the
    Tester's Client and Primary Contact flag, and the Login's email — so that a
    reset changes one thing only.
25. As a Manager whose page is stale, because the Agent lost its Login or the
    Tester or Agent is gone, I want a message that says so, so that I refresh
    instead of retrying blindly.
26. As a Manager working by keyboard, I want to open the reset, confirm or
    cancel it, copy the password and close the dialog, and have focus come back
    to where I started, so that the action is usable without a mouse.
27. As a Manager on a phone, I want the Tester row action and the dialog,
    including the shown password and its copy action, usable at the mobile
    breakpoint, so that a lockout can be fixed away from a desk.
28. As a newly created Agent or Tester, I want to sign in with the generated
    password I was handed, so that creation still gives me a working Login.
29. As someone trying the demo, I want the documented demo logins to keep their
    known passwords, so that the README still gets me in.
30. As everyone already using the product, I want sign-in and my own password
    change to behave exactly as before, so that moving every password write
    into one place carries no release risk.

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
`User.passwordHash`.

**The prefactor, behaviour-preserving.** One small component — **the password
write** — gains a single operation: "set this Login's password to this raw
value". It encodes with the one `PasswordEncoder` bean and sets the hash on the
given `User`. The three existing services call it instead of encoding
themselves. Nothing else moves:

- each creation service keeps its own username pre-check, flush, and
  constraint-name-to-conflict mapping;
- `ChangePasswordService` keeps its verification with `PasswordEncoder.matches`
  and its `PASSWORD_UNCHANGED` rule;
- the 8-character minimum stays a Bean Validation constraint on
  `ChangePasswordRequest`. The password write does not re-validate it (Backend
  rule 1).

The point is that no code except the password write encodes a password. When
`deactivate-a-login` or a later revocation feature needs to record something at
every password write, such as a `password_changed_at`, there is exactly one
place to add it. At this step the existing suites prove it unedited:
`AgentLoginApiTest`, `TesterApiTest`, `ChangeOwnPasswordApiTest`,
`PasswordMinimumLengthApiTest` and `AuthLoginTest`.

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
It also means a shown password leaves the DOM the moment the dialog closes. The
consumer-side deferral in the change-password dialog becomes redundant. It
stays, because removing it is churn outside this feature.

### The generated password

**Where it is made.** On the backend, in a new **password generator**
component in the `security` package, drawing from one `SecureRandom`. The
browser never generates a password, and no request ever carries one from a
Manager screen.

**Its shape.** Three groups of four characters joined by hyphens, e.g.
`k7mq-x3vh-p9te`:

- alphabet: the 32 symbols `abcdefghijkmnpqrstuvwxyz23456789` — lowercase
  letters and digits without `l`, `o`, `0` and `1`, so no two symbols look
  alike and there is no case to say aloud;
- 12 random symbols, 5 bits each, **60 bits** of entropy. Every guess costs a
  BCrypt check behind a sign-in endpoint, so that is ample for a password that
  the person is invited to replace;
- 14 characters with the hyphens, which are part of the password: what is
  shown, copied, read aloud and typed is the same string. That comfortably
  exceeds `PasswordPolicy.MIN_LENGTH` (8), and a unit test pins that it always
  does.

**Assigning it.** The password write gains its second operation: "give this
Login a freshly generated password, and return it in the clear". It asks the
generator for a candidate and, **when the Login already has a hash, checks the
candidate against it with `PasswordEncoder.matches`**. On a match it discards
the candidate and draws again, up to three draws, after which it fails with an
`IllegalStateException` (a `500`, which in practice never happens). Then it
encodes, sets the hash, and returns the plaintext to its caller, which is the
only holder of that value from then on. A new Login has no hash yet, so creation
skips the check.

This enforces the human's rule — "the new password should not be equal to the
old one being replaced" — without ever refusing anything. The Manager chooses
nothing, so there is no refusal to show and no oracle on anyone's password.

**Where it may appear.** In exactly one response body, and in the dialog that
received it. Specifically:

- every response that carries one is sent with `Cache-Control: no-store`, and
  the two BFF proxies that relay creation plus the two new reset proxies
  forward it;
- every response record that carries one overrides `toString` to redact it, as
  the request DTOs already do;
- no audit line gains a field. `AuditLog.agentLoginCreated`, `AuditLog.created`
  and `AuditLog.passwordChanged` carry the same ids they carry today;
- list responses (`GET /api/agents`, `GET …/testers`) gain nothing;
- the frontend holds the value in the dialog's component state only. It is
  never put in a URL, a query string, router state, `localStorage` or
  `sessionStorage`, and the state is cleared when the dialog closes.

### Login creation switches to a generated password

The three creation routes stop taking a password and start returning one.

| Route | Request | `201` response |
|---|---|---|
| `POST /api/agents` | `AgentCreateRequest` without `password` | the existing Agent body, plus `password` |
| `POST /api/agents/{agentId}/login` | `AgentLoginCreateRequest` without `password` (username only) | the existing Agent body, plus `password` |
| `POST /api/clients/{clientId}/testers` | `TesterCreateRequest` without `password` | the existing Tester body, plus `password` |

- The `password` field and its `@NotBlank @Size(min = PasswordPolicy.MIN_LENGTH …)`
  annotations are removed from the three request DTOs. A body that still sends
  `password` is not refused; the field is ignored like any unknown property,
  and the returned password is the one that works. A test proves that.
- Each creation response is a dedicated creation record: the fields the route
  returns today, flattened, plus `password`. The list endpoints keep returning
  the existing records, so a password can never ride along in a list.
- `AgentLoginService.create` and `TesterLoginService.create` stop taking a
  password. They call the password write's generating operation, and return
  the created row together with the plaintext to their controller, which puts
  it in the creation response. Their conflicts and audit lines are unchanged.
- `PasswordPolicy`'s Javadoc stops listing the three creation DTOs.
  `ChangePasswordRequest` is now its only request-side user; the generator is
  held to it by test.

**The seeded and demo logins keep their known passwords.** The Flyway-seeded
Logins are untouched rows. `DemoDataLoader` builds Priya Shah through
`AgentController.create` and the five demo Testers through
`TesterController.create`, which will now generate. Right after each of those
calls, the loader sets the documented demo password (`PRIYA_PASSWORD`,
`TESTER_PASSWORD`, `HARBOR_TESTER_PASSWORD`) through the password write's
raw-value operation, just as it already bypasses the HTTP layer for everything
else. The README's demo credentials stay true.

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
`deactivate-a-login` calls the same guard rather than restating it. A Manager's
Login is refused by design: that reset belongs to the SuperAdmin, in
`tenant-administration`.

The target is resolved within the caller's Tenant before the guard runs, using
the lookups the code already uses for this purpose (`findByIdAndTenantId` on
Agent and Client, then `findByIdAndClientId` on Tester). An unknown id, or an id
in another Tenant, is therefore `404`. That is the repository's settled
convention for the Tenant boundary (`SecurityConfig`: "Tenant scoping (an
unknown or other-tenant id is 404) is enforced in the controllers"), and it
reveals nothing about another Tenant's Logins. The guard's own `403` is defence
in depth. Neither route can reach a Manager's Login today, and the guard makes
sure a later route cannot either.

### Reset endpoints

The Manager addresses what they are looking at, which is an Agent or a Tester.
They never address a `User` id: no Manager screen carries one, and none gains
one.

```
POST /api/agents/{agentId}/login/password
POST /api/clients/{clientId}/testers/{testerId}/password
(no request body)

200 { "password": "k7mq-x3vh-p9te" }        — reset; Cache-Control: no-store
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
- Both routes live in one new controller with its own `@ExceptionHandler` for
  the coded `409`, as `ChangePasswordController` does. `AgentController` and
  `TesterController` are not grown.
- There is no request body, so there is no request DTO and no `400`.

### The reset service

A new `@Transactional` service with two operations, one per route. Each
operation:

1. resolves the target `User` within the caller's Tenant: Agent → its Login
   through `UserRepository.findByAgentId`; Client → Tester → `Tester.user`;
2. asks the guard;
3. calls the password write's generating operation, which guarantees the new
   password differs from the current one;
4. saves;
5. writes one audit line;
6. returns the plaintext to the controller for the response.

It reads no other part of the target.

### Observability

On success, the service writes one audit line, reusing
`AuditLog.passwordChanged` exactly as its Javadoc anticipated:
`action=PASSWORD_CHANGED entity=User entityId=<target userId> actorUserId=<Manager's userId> tenantId=…`.
A reset is told apart from a self-service change because the subject differs
from the actor. The line carries no password, no hash and no length. A refused
reset writes no audit line. Creation's audit lines are unchanged and, like
every other line, never carry the generated password.

### Schema

**No Flyway migration.** The feature overwrites `users.password_hash` and stores
nothing new.

### Frontend

**The one-time reveal.** One new client component, used by all four dialogs
(reset, Add agent, Create login, Add tester) as their success step. The dialog
does not close on success; it swaps its form for the reveal:

- a heading and line naming the email: "{email} can now sign in with this
  password." — for a reset, "…with this new password. Their old one no longer
  works.";
- the password in a **read-only text input** labelled **Generated password**,
  `autoComplete="off"`, `spellCheck={false}`, rendered with `.tnum` and wide
  tracking so each character stands apart. It receives focus with its text
  selected, so Ctrl/Cmd+C works even without the button;
- a **Copy password** button (`Button variant="secondary" size="sm"`) that
  writes the value with `navigator.clipboard.writeText`. A polite status says
  "Copied". If the clipboard is refused, the status says "Couldn't copy —
  select the password and copy it yourself";
- a plain warning line: "This password won't be shown again. Give it to them
  yourself — they can change it afterwards from their own menu.";
- one action, **Done**, the dialog's only pill. Done, Escape and the backdrop
  all close the dialog; with `DialogShell`'s new mounting, the password leaves
  the DOM at that moment.

Where a dialog hands a result back to its page — `CreateAgentLoginDialog`'s
`onCreated`, which swaps **Create login** for the sign-in email — that happens
when the reveal is closed, not when the response arrives. Otherwise the swap
would unmount the dialog with the password still unread. Dialogs that
`router.refresh()` do so on entering the reveal, so the list behind updates
while the reveal stays.

**The reset dialog.** One new client component on `DialogShell`, in two steps.

1. **Confirm.** Title "Reset password for {name}", where {name} is the Agent's
   name or, for a Tester, their email, which is all a Tester row carries. Body:
   "A new password will be generated for {email}. Their current password stops
   working as soon as you confirm." Actions: **Cancel** (8px control radius)
   and **Reset password**, the step's one pill.
2. **Reveal**, the component above.

Failures on confirm are rendered through `DialogErrorAlert`, branching on
status and `readErrorCode` the way `create-agent-login-dialog` does:

- `AGENT_HAS_NO_LOGIN` says the page is stale;
- `404` says the person no longer exists and links back to the list;
- anything else, including a lost connection, gets "Couldn't reset the
  password. Their old password may already have stopped working — try again to
  get a new one."

**On close after a reset:** focus returns to the trigger, and an
always-mounted polite status region announces "Password reset for {email}." The
password is never in that region.

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

**The creation dialogs.** `CreateAgentDialog`, `CreateAgentLoginDialog` and
`CreateTesterDialog` lose their password field, their "only spaces" check and
the 400 message that mentions a temporary password. Their hint changes from
"They can sign in with this email and password right away." to "A password is
generated when you create the login — you'll see it once." Each shows the
reveal on `201`. `LoginCredentialFields` shrinks to the email field alone and
keeps its name.

**BFF.** Two new pass-through proxies for the reset routes, the same shape as
every other proxy and using `backendFetch`, so the JWT never leaves the
httpOnly cookie. They, and the existing `/api/agents`,
`/api/agents/[agentId]/login` and `/api/clients/[clientId]/testers` proxies,
forward `Cache-Control: no-store` on their `POST` responses.

Neither `lib/nav.tsx` nor any route changes. No console except the Manager's
changes at all.

**Naming:** "Reset password" and "Generated password" throughout, never
"account" or "temporary password" (`CONTEXT.md` lists "account" under _Avoid_
twice, and the password is no longer the Manager's temporary choice).

## Design direction

One surface, **Operate**: the reset dialog, the one-time reveal shared by four
dialogs, and two trigger placements. It is assembled from shipped tokens and
components — `DialogShell`, `Input`, `Button`, `DialogErrorAlert`, `.tnum` —
so **the `design` slot is not needed**. The reveal is a new composition, so
`DESIGN.md` gains one short entry describing it (read-only field, secondary
copy button, warning line, Done as the only pill). No token changes.

- Every dialog matches the three creation dialogs and the change-password
  dialog:
  - `DialogShell`'s 12px radius, `canvas-overlay`, `shadow-elevated-strong`, and
    `bg-ink/40` backdrop;
  - inputs at the 8px radius and 36px height, with the `hairline-strong` border
    turning `primary` on `:focus-visible`;
  - `DialogErrorAlert` in `danger`/`danger-bg`.

  The Pill-Is-Primary Rule holds per step: **Reset password** on confirm,
  **Done** on the reveal.
- The reveal's field sits on `canvas-soft`, the read-only surface the Add agent
  dialog already uses for Currency, so it reads as "shown, not editable".
- The triggers are **row actions**: 28px (`sm`), soft-indigo row tone, 8px
  radius. `DESIGN.md` already describes this and `CreateAgentLoginDialog`'s
  trigger already ships it. No indigo fill appears on a page that already has
  its primary pill ("Add tester" on the Client's page).
- The Testers table's new column follows the existing table cell rhythm
  (`px-4`, `py-2.5–3`), with the action right-aligned. `TableScroll` already
  handles the narrow viewport, so the column needs no mobile variant.

Visual goldens:

- **New goldens:** the reset dialog's confirm step on each of the two pages,
  and its reveal step once (on the Agent's page, with a fixed stub password),
  per theme and breakpoint, against the stub-backend fixtures.
- **Existing goldens that will move:** those of the Manager's Agent page and
  Client page, for the new button and column. Those recaptures are expected and
  travel with the ticket that causes them. The creation dialogs have no goldens
  today, and gain none.
- A golden moving on any other surface is a finding.

## Constraints

- **No Flyway migration.**
- A generated password appears in exactly one response per event, marked
  `Cache-Control: no-store`, and nowhere else: not in a list response, a log
  line, an audit line, a URL, or browser storage. No password or hash ever
  appears in a log line or a URL.
- Generation happens on the backend only, from `SecureRandom`. No frontend code
  generates a password.
- The password is hashed by the existing `PasswordEncoder` bean
  (`BCryptPasswordEncoder`), called from the password write and nowhere else in
  production code. There is no second encoder, no cost-factor change and no
  algorithm change.
- A generated password is always at least `PasswordPolicy.MIN_LENGTH`
  characters. `ChangePasswordRequest` keeps its Bean Validation minimum.
  `LoginRequest` stays `@NotBlank` alone.
- A reset's new password never `matches` the Login's current hash.
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
- The Flyway-seeded Logins and `DemoDataLoader`'s demo Logins keep their
  documented passwords.
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

Tests assert external behaviour only: HTTP status, the response body's `code`
and `password`, and whether a later sign-in succeeds. They never read the hash
from the database, check which repository method ran, or check a
`PasswordEncoder` interaction.

- **One seam, and it already exists:** the HTTP API seam, meaning
  `IntegrationTest` + MockMvc against a real Postgres. No new seam is added.
- **The proof of a password is a sign-in, not a hash read.** After a creation
  or a reset, `IntegrationTest.loginAs(username, <response's password>)`
  returns a token; after a reset, `loginAs(username, oldPassword)` is `401`.
  This is the same assertion `ChangeOwnPasswordApiTest` makes, and the highest
  seam available.
- **How tests learn a generated password: from the response.** Nothing else
  can know it.
  - `IntegrationTest.createAgent` stops sending a password; it still returns the
    id.
  - `IntegrationTest.createTesterAndLogin(managerToken, clientId, username)`
    loses its `password` parameter, reads `$.password` from the `201`, and signs
    in with it. Its callers drop their `"Passw0rd!23"` argument:
    `SimSwapRequestDetailsApiTest`, `ReplaceRequestsApiTest`, `FeeApiTest`,
    `PostpaidSimPlanApiTest`, `SimCardInstalledInApiTest`,
    `ReturnRequestsApiTest`, `SimCardApiTest`, `SimCardCarrierApiTest`,
    `ReviewQueueApiTest`, `ClientInvoiceByIdApiTest`, `ClientInvoiceApiTest`,
    `StockApiTest`, `RebootAndTopupDetailsApiTest`,
    `ManagerApprovesRequestsApiTest`, `StockFulfilmentApiTest`,
    `AgentInvoiceApiTest`, `SmartphoneApiTest`, `ContractApiTest`,
    `ProvisionRequestDetailsApiTest`, `ChangeOwnPasswordApiTest` and
    `RequestApiTest`. The change is mechanical.
  - Tests that build creation bodies themselves drop `password` and, where they
    sign in afterwards, read it from the response: `AgentApiTest` (its `PASSWORD`
    constant and `ana.lima@agents.example` sign-in), `AgentLoginApiTest` (its
    `loginBody` and `sofia.marin@agents.example` sign-in), `TesterApiTest`,
    `TesterUsernameConflictApiTest`, `CrossTenantUsernameAgentCreationApiTest`,
    `AgentCreationAtomicityTest`, `TopupFeeFromOptionApiTest` (its
    `priya.raman@aurora.example` Tester and later sign-in),
    `PasswordMinimumLengthApiTest` (its `freshAgentLogin`) and
    `ChangeOwnPasswordApiTest` (its `freshAgentLogin` and `freshTesterLogin`).
  - Tests of a missing or blank creation password are rewritten, not deleted:
    `AgentApiTest.creatingAnAgentWithoutAPasswordIsRejectedAndCreatesNoAgent`
    and the password half of `AgentLoginApiTest.aMissingUsernameOrPasswordIsRejected`
    become "a body with no password creates the Login, and the returned password
    signs in", plus one test per creation route that **sends** a password and
    shows that value is refused at sign-in while the returned one works.
  - The two audit tests that assert the typed password is absent from the log
    (`AgentApiTest`, `AgentLoginApiTest.creatingALoginLogsAnAuditEntryWithoutThePassword`)
    assert the **returned** password is absent instead. `TesterApiTest`'s audit
    test gains the same assertion.
  - `OtherTenantFixture.managerLoginInAnotherTenant` keeps its known password:
    it writes its row directly, not through a creation route.
- **The 8-character boundary.** `PasswordMinimumLengthApiTest`'s table loses its
  `AGENT_CREATION`, `TESTER_CREATION` and `AGENT_LOGIN_CREATION` rows, because
  those paths no longer accept a password; its `CHANGE_PASSWORD` rows stay. Its
  stale Javadoc counts (tech-debt F10) are corrected while the file is open.
  The generator is held to the minimum by its own unit test.
- **Unit tests where a Spring context adds nothing (Backend rule 6):**
  - the password generator: many draws all match
    `^[a-km-np-z2-9]{4}(-[a-km-np-z2-9]{4}){2}$` and are at least
    `PasswordPolicy.MIN_LENGTH` long;
  - the password write's generating operation, with a scripted generator whose
    first candidate is the Login's current password and a real
    `BCryptPasswordEncoder`: the second candidate is the one set and returned,
    and three matching draws raise `IllegalStateException`. This is the only
    place the "not equal to the old one" rule can be exercised, since a real
    collision never occurs.
- **Both reset routes get the full treatment through the HTTP seam:**
  - reset then sign-in with the returned password, with the old password
    refused, and the returned password different from the old one;
  - the response carries `Cache-Control: no-store`;
  - an Agent with no Login answers `409 AGENT_HAS_NO_LOGIN`;
  - an unknown id answers `404`;
  - `agentToken()` and `testerToken()` are refused `403`;
  - a refused reset leaves the old password working;
  - the captured log holds one `PASSWORD_CHANGED` line and never the returned
    password.
- **Cross-Tenant refusal uses `OtherTenantFixture`**, which gains an Agent with
  a Login and a Tester in the other Tenant, written directly with known
  passwords as its Manager already is. The seeded Manager resetting either gets
  `404`, and the other Tenant's person still signs in with their original
  password.
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
- **The prefactor is proven by the existing suites passing unedited at its own
  step:** `AgentLoginApiTest`, `TesterApiTest`, `TesterUsernameConflictApiTest`,
  `ChangeOwnPasswordApiTest`, `PasswordMinimumLengthApiTest` and
  `AuthLoginTest`. The creation switch then edits them as listed above. A grep
  showing `passwordEncoder.encode` in exactly one production class is part of
  the walkthrough.
- **The demo logins** are proven by a `demo`-profile context test that builds
  the story on the Testcontainers database and signs in as
  `DemoDataLoader.PRIYA_USERNAME`, `DANA_USERNAME` and `NOAH_USERNAME` with
  their documented passwords.
- **Frontend component tests (Vitest + Testing Library)**, in the shape of
  `create-tester-dialog.test.tsx` and `change-password-dialog.test.tsx`, with
  the BFF response stubbed at the fetch boundary:
  - the reveal component: the value is shown in a read-only field that takes
    focus with its text selected; Copy calls the clipboard and announces
    "Copied", and a refused clipboard announces the fallback; closing removes
    the value from the DOM;
  - the reset dialog: Cancel on confirm sends nothing; each status and `code`
    renders its message; success shows the reveal; closing returns focus to the
    trigger and announces the status without the password;
  - the three creation dialogs: no password field; `201` shows the reveal with
    the stubbed password; `CreateAgentLoginDialog` calls `onCreated` only on
    close. `create-tester-dialog.test.tsx` drops its "Temporary password" typing
    and its "closes and refreshes on success" case becomes "shows the reveal
    and refreshes".
- **Component tests for `DialogShell`'s new mounting:** children are absent from
  the DOM while it is closed, and present and focusable once it is opened.
  Every existing dialog test must stay green unedited.
- **e2e: how specs learn a generated password.** From the reveal, never from a
  constant. `helpers.ts`:
  - `addTester(page, clientId, email)` loses its `password` parameter, reads
    the **Generated password** field's value, clicks Done, and returns it;
  - `createContractWithTester` returns the Tester's password with the two ids;
  - `addTesterAndSubmitRequestAsAgent` signs in with the password `addTester`
    returned;
  - `createUnrelatedContract` no longer fills a password and clicks Done;
  - `SEEDED_USERS` is unchanged.

  Every spec that passes `"Passw0rd!23"` to `addTester` and then to `login`
  switches to the returned value: `fee-logging-and-provisioning`,
  `tester-request-submission`, `cancelled-sim-billed-through-its-month`,
  `manager-decides-return-disposition`, `client-invoice-submission-and-visibility`,
  `agent-stock`, `fleet-management`, `sim-swap-moves`,
  `agent-request-fulfillment`, `manager-approves-requests`,
  `return-client-owned-smartphones` and `change-password` (whose "old
  password" becomes the returned one). `topup-fee-from-option` and
  `client-invoice-generation` change only through the helpers.

  Specs that fill **Temporary password** in a dialog themselves read the reveal
  instead: `manager-entity-setup` (Add agent, Add tester),
  `fleet-management` (its inline Add tester), `create-agent-with-login` and
  `create-login-for-existing-agent`, both of which now sign in with the revealed
  value. Their "only spaces" password tests are deleted, since the field is
  gone. The direct API bodies in `manager-entity-setup` and
  `create-login-for-existing-agent` drop `password`.
- **One new e2e spec, `manager-resets-a-password.spec.ts`,** using `helpers.ts`'
  `login`/`logout`/`addTester`. As a Manager it:
  1. creates an Agent through the UI, keeping the revealed password;
  2. resets that Agent's password from the Agent's page and keeps the new one;
  3. logs out and signs in as the Agent with the new password;
  4. confirms the creation password is refused;
  5. repeats steps 2–4 for a Tester added to a Client.

  This is what the walkthrough replays. The suite's load sensitivity
  (tech-debt) means a failure is judged against a controlled comparison on an
  idle machine, never waved off as a flake.
- **No unit test of the reset service.** It is a few lines behind a Spring
  context, and a mocked `PasswordEncoder` would only assert the mock (Backend
  rule 6).

## Decisions taken

### Following the human's answers

- **Passwords are generated, not typed — at reset and at every Login
  creation** (human, 2026-09-30). Agent creation, giving an Agent a Login, and
  Tester creation switch in this feature, so no Manager screen keeps a password
  field.
- **A reset's new password must not equal the one it replaces** (human,
  2026-09-30). Enforced by `matches` against the current hash and a redraw,
  never a refusal.
- **A Manager's Login cannot be reset here; that is the SuperAdmin's job, in
  `tenant-administration`** (human, 2026-09-30).
- **The target's existing sessions survive until their token expires; the reset
  does not sign them out.** This follows the human's 2026-09-22 answer on
  `self-service-password-change`. Revoking issued tokens needs a
  `password_changed_at` column and a check on every request, which is
  `deactivate-a-login`'s per-request account-state check, where the epic already
  parks the identical question. The feature line frames a reset as serving
  someone who *cannot* sign in, and such a person holds no session. Walkthrough
  step 28 puts this in front of the human, and a veto moves the feature onto
  that check.
- **The 8-character `PasswordPolicy` minimum still holds for every password**
  (human, 2026-09-22). A generated one always exceeds it, and a unit test pins
  that.

### Taken alone

- **Generation lives on the backend, from `SecureRandom`.** The backend is the
  only side that must never trust the browser, and it keeps the plaintext out
  of every request body.
- **Format: three hyphen-joined groups of four from a 32-symbol lowercase,
  no-look-alike alphabet — 14 characters, 60 bits.** It reads aloud without
  "capital" or "zero or O", types on a phone keyboard without a shift, and the
  hyphens are part of the value, so what is shown is what is typed. 60 bits
  behind BCrypt at a sign-in endpoint is ample for a password the person is
  invited to replace.
- **"Not equal to the old one" is a redraw on `matches`, capped at three
  draws.** The rule is the human's, and a redraw enforces it invisibly. The cap
  turns a broken generator into a loud `500` rather than a loop.
- **The rule lives in the password write's generating operation, and applies
  whenever the Login already has a hash.** That is the one place every
  generated password passes through.
- **The generated password is shown in the dialog that made it, as a success
  step, and never again.** The response is the only carrier. `no-store`,
  redacted `toString`, component-state-only and `DialogShell`'s unmount on
  close keep it from lingering anywhere.
- **It can be copied, with a button, and the field is selected on reveal.**
  Handover is often a chat message. A selected read-only field keeps copying
  possible when the clipboard API is refused.
- **Done, Escape and the backdrop all close the reveal; there is no "are you
  sure".** Losing it costs one more reset, which is exactly the recovery the
  feature builds, and a trap on Escape would fight the dialog's keyboard
  contract.
- **The reset asks for confirmation first.** The Manager no longer types
  anything, so without a confirm step a single click would invalidate
  someone's password. A two-step dialog costs one click.
- **The creation responses flatten the existing body and add `password`,
  through dedicated creation records.** Callers keep reading `id` where they
  do today, and list responses cannot carry a password by construction.
- **A creation body that still sends `password` is ignored, not refused.**
  Only this app's frontend calls these routes, and tests prove the sent value
  never works.
- **`DemoDataLoader` sets its documented passwords through the password write
  after creating through the controllers.** The demo keeps using the real
  creation paths, and the README's credentials stay true.
- **`LoginCredentialFields` keeps its name with the email field alone.**
  Renaming it is churn across three dialogs for no reader's benefit.
- **`CreateAgentLoginDialog` hands its result to the page on close, not on
  response.** Otherwise the page swaps the trigger and unmounts the reveal
  unread.
- **The password-write prefactor is narrowed to the encode-and-set step.** The
  epic's premise, a hash written inline in `TesterController`, is gone because
  `TesterLoginService` exists. What remains is services each repeating one
  step, so one component removes it. Merging the services would move conflict
  handling and verification that have nothing to do with the write.
- **`DialogShell` is prefactored to mount children only while open.** This
  feature adds a second closed dialog to two pages, which is exactly how the
  recorded regression happens, and the unmount is also what clears a shown
  password from the DOM. The repository pays debt when a feature touches the
  module.
- **One dialog instance per view, opened for the chosen target.** Thirty Tester
  rows should not mean thirty hidden forms.
- **The reset lives beside the sign-in email on the Agent's page and as a row
  action on the Client's Testers table.** Those are the only two places a
  Manager already sees these Logins. It is cheap to move.
- **Endpoints address the Agent and the Tester, never a `User` id.** The Manager
  looks at Agents and Testers, not Users. No response gains a `User` id, and the
  paths fall under the existing Manager-only matchers.
- **The Agent route is `/login/password` under the Agent; the Tester route is
  `/password` on the Tester.** An Agent's Login is a sub-resource that may be
  absent, beside the existing `POST …/login`. A Tester is itself a Login.
- **The reset takes no body and answers `200` with `{ password }`.** There is
  nothing to send, and the response is the only way to deliver the password.
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
- **A new controller holds both reset routes.** It needs its own
  `@ExceptionHandler` for a coded `409`, and `AgentController` and
  `TesterController` have no use for it.
- **The audit line reuses `AuditLog.passwordChanged` with the Manager as the
  actor.** `self-service-password-change` shaped that line for this reuse, and
  subject ≠ actor is what marks a reset.
- **A refused reset is not audited.** This is the same reasoning as the sibling:
  failure counting is the start of a lockout feature nobody asked for.
- **A Tester is named by email in the dialog title.** A Tester carries no
  display name, only the Login's email.
- **`PasswordMinimumLengthApiTest` loses its creation rows and its stale Javadoc
  counts are fixed in passing.** Those paths take no password any more, and the
  ticket edits that table anyway.
- **Testing: the existing HTTP seam, the proof by real sign-in with the returned
  password, and `OtherTenantFixture` for the Tenant boundary.** Prior art is
  `ChangeOwnPasswordApiTest`, `AgentLoginApiTest`, `TesterApiTest` and
  `PasswordMinimumLengthApiTest`. A hash read would pass with sign-in broken.
- **Two unit tests, for the generator and the redraw.** The redraw cannot be
  reached through the HTTP seam, because a real collision never happens; a
  scripted generator with a real encoder is the only honest way to exercise it.
- **Tests learn generated passwords from the response (backend) and the reveal
  (e2e), never from a constant.** Nothing else can know the value, and reading
  the reveal is what a Manager does.
- **Tests create their own Agent or Tester and never reset a seeded Login.**
  Seeded credentials underpin the token helpers and most e2e specs, and e2e
  state does not roll back.

## Open questions

None.

The three objections of 2026-09-30 and their follow-ups settled every *what*
the first draft had decided alone: who may be reset, typed versus generated,
and equality with the old password. What remains — the password's format,
copying, the confirm step, how the reveal closes — is *how*, cheap to change,
and each has a `[human]` walkthrough step.

## Acceptance walkthrough

1. [agent] As the seeded Manager, `POST /api/agents` with no `password` field and show `201` with a `password` matching `^[a-km-np-z2-9]{4}(-[a-km-np-z2-9]{4}){2}$` and `Cache-Control: no-store`; sign in as the new Agent with it and show a token. (stories: 9, 14, 28)
2. [agent] Do the same for `POST /api/agents/{agentId}/login` on a login-less Agent and for `POST /api/clients/{clientId}/testers`. For each of the three routes, send a body that still carries `"password": "Passw0rd!23"` and show that value is `401` at sign-in while the returned one signs in. (stories: 9, 28)
3. [agent] Show `GET /api/agents` and `GET /api/clients/{clientId}/testers` return no `password` field for the Logins just created. (stories: 14)
4. [agent] Reset the Agent from step 1 with `POST /api/agents/{agentId}/login/password` and no body: show `200`, a `password` in the same format that differs from the creation password, and `Cache-Control: no-store`. Sign in with the new one and show a token; sign in with the creation password and show `401`. (stories: 1, 3, 8, 15, 16, 18)
5. [agent] Reset the Tester from step 2 with `POST /api/clients/{clientId}/testers/{testerId}/password` and show the same: `200`, a different password that signs in, the old one `401`. (stories: 2, 15, 17, 18)
6. [agent] Run the generator's unit test and the password write's redraw test. Show every draw matches the format and is at least `PasswordPolicy.MIN_LENGTH` long, that a first candidate equal to the current password is discarded for the second, and that three equal draws fail loudly. (stories: 11, 15)
7. [agent] Reset an Agent created with no Login and show `409` with `code: AGENT_HAS_NO_LOGIN`; reset an unknown Agent id and an unknown Tester id and show `404` for both. (stories: 6, 25)
8. [agent] Using `OtherTenantFixture`'s other Tenant, have the seeded Manager reset an Agent's Login and a Tester's Login there and show `404` for both, then show each still signs in with its original password. (stories: 20)
9. [agent] Call both reset routes with the seeded Agent's token and the seeded Tester's token and show `403` each time. (stories: 21)
10. [agent] Run the guard's Manager-target test and show the refusal. Then show that neither reset route's path or body can name a `User`, and that no Manager-role Login appears on any screen offering a reset. (stories: 22)
11. [agent] After an Agent reset, show the Agents list still returns the same `loginUsername` and the Agent's standing amounts are unchanged. After a Tester reset, show the Testers list returns the same email and Primary Contact flag. (stories: 24)
12. [agent] Grep the backend log from steps 1–5 and show one `PASSWORD_CHANGED` line per reset, with `entityId` the target's user id, `actorUserId` the Manager's, and the Tenant's id. Show that no line contains any password returned in steps 1–5 or a BCrypt hash, and that the refused attempts of steps 7–9 logged no audit line. (stories: 14, 23)
13. [agent] Grep production sources and show `passwordEncoder.encode` in exactly one class, the password write, and `SecureRandom` password generation only in the backend generator, with no password generation in `frontend/`. Show `git diff` touches no Flyway migration, no `SecurityConfig` matcher, no `lib/nav.tsx`, and no `LoginRequest`. (stories: 8, 30)
14. [agent] Run the `demo`-profile context test and show Priya Shah, Dana Whitfield and Noah Kim sign in with the passwords documented in `DemoDataLoader`, and that `AuthLoginTest` still signs in the three Flyway-seeded Logins. (stories: 29, 30)
15. [agent] Run `mvn verify`, then the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green. The only recaptured goldens are the Manager's Agent page and Client page, and the only new ones are the reset dialog's. (stories: 30)
16. [agent] In a browser as the Manager, open an Agent with a Login and show **Reset password** beside the sign-in email. Open an Agent without one and show only **Create login**. (stories: 4, 6)
17. [agent] On a Client's page, show a **Reset password** action on every Tester row, each with an accessible name carrying that Tester's email. Show only one dialog element in the DOM, and no `<form>` inside it while it is closed. (stories: 5)
18. [agent] Open the reset for an Agent and show the title naming the Agent and the body naming the email and saying the current password stops working. Press Cancel and show the Agent's current password still signs in. (stories: 7)
19. [agent] Open it again and press **Reset password**. Show the reveal: the **Generated password** field is read-only, focused and selected, the warning says it won't be shown again, and **Copy password** puts the exact value on the clipboard and announces "Copied". Press Done: show focus back on the trigger, the status region reading "Password reset for {email}." with no password, and no element in the DOM containing the password. Reopen and show the confirm step, not the old password. (stories: 10, 11, 12, 14, 26)
20. [agent] Log out, sign in through the UI as that Agent with the revealed password and reach the Agent console. Open the viewer-chip menu, change the password to one of their own, and show the revealed password is now refused. (stories: 16, 18, 19)
21. [agent] Repeat steps 18–20 for a Tester from the Client's page. (stories: 2, 5, 17, 19)
22. [agent] In a browser, open **Add agent**, **Create login** (on a login-less Agent) and **Add tester** in turn. Show none has a password field and each hint says the password is generated and shown once. Submit each, show the reveal with the email named, press Done, and sign in as that person with the revealed value. For **Create login**, show the Agent's page swaps to the sign-in email only after Done. (stories: 9, 10, 12, 28)
23. [agent] Add a Tester and close the reveal with Escape without copying. Then reset that Tester from their row and sign in with the reset password. (stories: 13)
24. [agent] Nothing in the product today can remove an Agent's Login or delete an Agent or Tester, so a stale page cannot be produced for real. Instead, run the reset dialog's component tests with the BFF answering `409 AGENT_HAS_NO_LOGIN` and then `404`. Show the stale-page message for the first, and the "no longer exists" message with its link back to the list for the second, never the generic failure. (stories: 25)
25. [agent] Do the whole Tester reset by keyboard alone: Tab to the row action, open the dialog, confirm, Tab to **Copy password** and activate it, then close with Done; open again and close with Escape. Show a visible focus ring at each stop and focus returning to the trigger. Repeat at the mobile breakpoint and show the row action reachable, and the dialog, the password field and the copy button inside the viewport. (stories: 26, 27)
26. [human] Reset a real Agent's and a real Tester's password on the running app, and create one new Login. Hand each password over the way you actually would — read aloud, typed on a phone, or pasted into a chat — and confirm the format survives that, and that the wording tells you the handover is yours, names the right person, and warns you it won't be shown again. (stories: 7, 10, 11, 12)
27. [human] Confirm the reset's confirm step is worth its extra click, and that closing the reveal without a warning is acceptable given a lost password means one more reset. (stories: 7, 13)
28. [human] Sign in as an Agent in a second browser, then reset that Agent's password as the Manager. Confirm the second browser keeps working until its session expires within the hour, and that you accept this for a reset as you did for a self-service change. (stories: 18)

## Execution order

The order is final only once `## Open questions` is approved as `None`. Eleven
slices. The switch to generated passwords at creation is cut as expand–contract,
because a backend that stops honouring the typed password while a frontend still
sends one breaks every creation: the backend first learns to generate while still
honouring a typed password (5), the frontend then stops sending one (6), and the
backend finally removes it (7). The two reset routes need only the generating
creation (5), so they run in parallel with 6 and 7. Tickets 1 to 4 are enablers.

1. `one-password-write` — the password write component with its raw-value
   operation. `AgentLoginService`, `TesterLoginService` and
   `ChangePasswordService` call it instead of encoding, and the existing suites
   pass unedited. Labels: `enabler`, `backend`. Depends on nothing.
   (stories: 30)
2. `dialog-shell-mounts-when-open` — `DialogShell` renders its children only
   while open, with its component tests, and every existing dialog test green
   unedited. Labels: `enabler`, `frontend`. Depends on nothing. (stories: 14)
3. `one-time-password-reveal` — the shared reveal component with its component
   tests and the one `DESIGN.md` entry. Labels: `enabler`, `frontend`. Depends
   on nothing. (stories: none)
4. `generated-password-generator` — the password generator and the password
   write's generating operation with their unit tests. Labels: `enabler`,
   `backend`. Depends on `one-password-write`. (stories: none)
5. `new-logins-generate-password-api` — the three creation routes generate and
   return a password when none is sent, with `no-store`, the creation records
   and their tests; a typed password is still honoured. Labels: `backend`.
   Depends on `generated-password-generator`. (stories: 9, 14, 28)
6. `creation-dialogs-reveal-generated-password` — the three creation dialogs drop
   the password field and show the reveal, `LoginCredentialFields` down to email,
   BFF `no-store` forwarding, `helpers.ts` and every e2e spec listed. Labels:
   `frontend`. Depends on `new-logins-generate-password-api`,
   `one-time-password-reveal` and `dialog-shell-mounts-when-open`.
   (stories: 9, 10, 11, 12, 14, 28)
7. `creation-takes-no-typed-password` — `password` leaves the three request DTOs,
   the services always generate, `DemoDataLoader` restores its documented
   passwords with the `demo`-profile test, `PasswordPolicy`'s Javadoc, every
   backend test moved to the returned password, `PasswordMinimumLengthApiTest`'s
   creation rows removed. Labels: `backend`. Depends on
   `creation-dialogs-reveal-generated-password`. (stories: 9, 14, 28, 29, 30)
8. `reset-an-agents-password-api` — the guard class, the reset service, the new
   controller with the Agent route and its coded refusal, the audit line, and the
   Agent-route integration tests including `OtherTenantFixture`'s new Agent.
   Labels: `backend`. Depends on `new-logins-generate-password-api`.
   (stories: 1, 3, 8, 15, 16, 18, 20, 21, 22, 23, 24, 25)
9. `reset-a-testers-password-api` — the Tester route on the same controller and
   service, with its integration tests including `OtherTenantFixture`'s new
   Tester. Labels: `backend`. Depends on `reset-an-agents-password-api`.
   (stories: 2, 15, 17, 18, 20, 21, 24)
10. `reset-an-agents-password-ui` — the BFF proxy, the reset dialog on the shared
    reveal, the **Reset password** action in `AgentSignInEmail`, component tests,
    the Agent half of the e2e spec, and the Agent page's goldens. Labels:
    `frontend`. Depends on `reset-an-agents-password-api` and
    `creation-dialogs-reveal-generated-password`.
    (stories: 4, 6, 7, 10, 11, 12, 13, 14, 19, 25, 26)
11. `reset-a-testers-password-ui` — the BFF proxy, the Testers table's action
    column reusing the dialog, the Tester half of the e2e spec, and the Client
    page's goldens. Labels: `frontend`. Depends on
    `reset-a-testers-password-api` and `reset-an-agents-password-ui`.
    (stories: 2, 5, 13, 17, 19, 25, 26, 27)
