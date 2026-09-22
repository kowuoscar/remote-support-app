---
feature: self-service-password-change
epic: login-lifecycle
status: draft
date: 2026-09-22
---

<!-- sdlc:template spec 1 -->

# Self-service password change

## Problem

A Login is created once, with a password somebody else chose, and from that
moment nothing about it can ever change. A Manager creating an Agent hands over
an email and a temporary password; a Manager creating a Tester does the same.
The word "temporary" is a courtesy — there is no screen, no endpoint and no
command anywhere in the product that lets the person who received it replace it.

So every person using this product is permanently signed in with a secret that
at least one other person knows and that was very likely typed into a chat
message or an email to deliver it. Someone who suspects their password has been
seen has nothing to do about it. Someone who simply wants a password they can
remember, rather than the one they were assigned, has nothing to do about it
either. `agent-login-on-creation` deferred all of this deliberately
(`docs/features/agent-login-on-creation/spec.md:39-44`), and the deferral has
been in force ever since.

## Journeys

Advances `docs/roadmap/login-lifecycle.md`. It is the **first** of that epic's
three features, and the epic's `## Reworked` section explains why it goes first:
it acts on the caller's own `User` row, reached from the authenticated
principal, so it needs neither the account-state concept `deactivate-a-login`
introduces nor the guard class and unified password write that
`manager-resets-a-password` introduces.

- **Keep a login working over time** (`wanted`, stays `wanted`): this feature
  delivers the journey's first clause — "a user changes their own password" —
  and nothing else. The journey reaches `exists` only when all three of the
  epic's features have landed, so `docs/journeys.md` is not edited by this
  feature beyond nothing at all.
- **Sign in** (`exists`, stays `exists`): unchanged, and must be proven
  unchanged. Sign-in remains exact-match and case-sensitive on the username, and
  `LoginRequest`'s password validation is not touched.

## Goals / Non-goals

Goals:

- A signed-in user of any of the three roles — Manager, Agent, Tester — changes
  their own password from inside the product.
- The change is refused unless the caller supplies their current password
  correctly in the same request.
- After a successful change, the new password signs the user in and the old one
  does not.
- The refusal for a wrong current password is told apart, in the UI, from every
  other way the request can fail, and does not look like an expired session.
- A password is never rendered, logged, echoed in a response, or stored as
  anything but a BCrypt hash produced by the existing `PasswordEncoder` bean.
- A surface exists that a user can actually find — see `## Open questions` 3.

Non-goals — each is something a reasonable agent would otherwise build:

- **No Manager resetting anyone else's password.** That is
  `manager-resets-a-password`, the epic's next feature, and it owns both the
  new guard class and the prefactor below.
- **No prefactor of the password write into one place.** The epic records that
  `manager-resets-a-password` does exactly that (`AgentLoginService.create` and
  `TesterLoginService.create` each encode their own). This feature **leaves that
  duplication exactly as it is** and adds a third, separate write for the
  caller's own row rather than pulling the other two forward. Doing the
  prefactor here would move work out of the feature that was cut to hold it and
  would make this feature's diff mostly about code it does not use.
- **No login deactivation, no enabled/disabled concept.** That is
  `deactivate-a-login`, and it is the feature that introduces a per-request
  account-state check — which is also where global session revocation belongs
  (see `## Open questions` 2).
- **No change to a Login's email/username.** Explicitly left out of scope at
  epic init (`docs/roadmap/login-lifecycle.md` `## Later`).
- **No forgotten-password flow.** A user who cannot sign in at all cannot use
  this feature; their path is a Manager reset, in the next feature. No outbound
  email is sent by this product and none is introduced here.
- **No forced password change on first sign-in.** Named in the original
  deferral and left there.
- **No password history, no reuse ban across time, no expiry.** The product has
  no place to store a password history and no reason yet to.
- **No rate limiting, lockout, or throttling.** The endpoint requires a valid
  JWT, so it is not an anonymous surface; the product has no rate-limiting
  infrastructure and introducing one is its own decision.
- **No two-factor authentication, no session list, no "sign out everywhere"
  button.**
- **No profile or settings surface beyond the password.** Whatever surface
  answers `## Open questions` 3 carries exactly one thing: changing the
  password. No name, no email, no preferences, no avatar.
- **No change to how sign-in validates a password.** `LoginRequest` keeps
  `@NotBlank` and only `@NotBlank`, whatever is decided about rules for *setting*
  a password. Enforcing a shape at sign-in would lock out every existing
  password that does not meet it.

## User stories

1. As a signed-in user of any role, I want to change my own password from
   inside the product, so that I am not permanently using a secret somebody else
   chose and delivered over chat.
2. As a signed-in user, I want to be made to prove my current password in the
   same request, so that someone who finds my screen unattended cannot take my
   Login away from me.
3. As a signed-in user who mistypes the current password, I want to be told
   that specifically and inline, so that I correct the right field instead of
   guessing.
4. As a signed-in user who mistypes the current password, I want to stay signed
   in and stay on the form, so that a typo does not read as a session that
   expired.
5. As a signed-in user, I want to type the new password twice and be told
   before I submit if the two do not match, so that I never set a password I
   cannot reproduce.
6. As a Manager, I want to change my own password even though my Login is
   linked to no Agent and no Tester, so that the role with no linked record is
   not the one left out.
7. As an Agent, I want to change the temporary password my Manager gave me when
   they created me, so that the handover secret stops being my real one.
8. As a Tester, I want the same, so that the rule does not depend on which kind
   of person I am.
9. As a user who has just changed my password, I want to sign in with the new
   one, so that the change is real rather than reported.
10. As a user who has just changed my password, I want the old one refused at
    sign-in, so that the secret that was handed around no longer works.
11. As a user, I want unambiguous confirmation that the change took effect, so
    that I am not left wondering whether to try again.
12. As a user, I want the new password held to the same rule as every other
    place in this product where a password is set, so that a password I am
    allowed to have in one place is not refused in another. (Which rule —
    `## Open questions` 1.)
13. As a user, I want to know what my password change does to sessions — mine
    here, and any other browser I am signed in on. (`## Open questions` 2.)
14. As a user, I want to be able to find the screen without being told a URL,
    so that the capability exists in practice and not only in the API.
    (`## Open questions` 3.)
15. As a user, I want my password never shown on screen in the clear, never
    written to a log, and never returned in a response, so that using the
    feature does not itself leak the secret.
16. As the company, I want an audit line recording that a password was changed,
    by whom and when — carrying no password and no hash — so that the event is
    traceable afterwards.
17. As a user of any of the three consoles, I want the same screen, the same
    wording and the same behaviour, so that support does not depend on which
    console someone is looking at.
18. As a user on a phone, I want the form usable at the mobile breakpoint, so
    that the rail becoming a drawer does not strand the screen.
19. As a user reaching the form by keyboard, I want visible focus and a working
    tab order, so that the product's stated accessibility floor holds on a new
    surface.
20. As everyone else already using the product, I want nothing about sign-in,
    login creation or any existing screen to change, so that this carries no
    release risk.

## Solution

### The endpoint is the first write acting on the caller

There is no write endpoint anywhere in this backend that acts on the signed-in
user. `MeController` is the only "acts on the caller" endpoint and it is
read-only — it reads the authenticated principal and answers "who am I". Every
other write endpoint addresses some *other* entity by id, and every one of those
is guarded by a role matcher or a guard class asking "may this caller touch that
entity".

This feature's endpoint has no such question to ask. There is no id in the path.
The row it writes is the caller's own, resolved from
`AuthenticatedPrincipal.userId()` and from nothing supplied by the request. That
is the whole reason this feature could go first.

It therefore lives in **the caller's own namespace, `/api/me`**, as a `POST` to
`/api/me/password`, in a controller of its own alongside `MeController` rather
than inside it. Two reasons for the separate controller, not one endpoint added
to `MeController`: `MeController`'s entire documented purpose is to be the
reference read-only protected endpoint, and a write path needs its own
`@ExceptionHandler` to return a machine-readable `code` (coding standards
Backend rule 7), which would otherwise be attached to a controller that has no
use for it.

`/api/me/**` is matched by nothing in `SecurityConfig`'s matcher chain and so
falls through to `.anyRequest().authenticated()` — which is exactly right, since
all three roles may do this and no role may do it to anyone else. **No
`SecurityConfig` change is needed**, and one being added would be a defect: a
matcher naming roles here would have to name all of them.

### Request and response contract

```
POST /api/me/password
{ "currentPassword": "...", "newPassword": "..." }

204 No Content                      — changed
400 { "code": "...", "message": "..." }  — every refusal
```

`204` rather than a body: there is nothing to tell the caller that it does not
already know, and anything a response could carry about a password is something
it should not carry.

**Every refusal is `400`, never `401` or `403`.** This is load-bearing. The
frontend's session layer treats `401` as "your session has gone" and its
recovery is to send the user to sign in; a mistyped current password that
bounced someone out to the sign-in page would be a worse experience than the one
this feature exists to fix, and would destroy what they had typed. The `400`
carries a `code` the form branches on, in the same `{code, message}` shape
`USERNAME_TAKEN` already established and `readErrorCode` already reads.

Codes: a wrong current password, and (if `## Open questions` 1 is answered with
a rule) a new password that fails it, each get their own.

### Verifying the current password

Verification calls `PasswordEncoder.matches` against the caller's stored hash
directly — **not** a round-trip through `AuthenticationManager.authenticate`.
Authenticating would mint a fresh `Authentication`, emit Spring Security's
sign-in-shaped events, and make a password change indistinguishable from a
sign-in in the logs. The `PasswordEncoder` bean is the same
`BCryptPasswordEncoder` singleton that `AgentLoginService` and
`TesterLoginService` encode with and that `DaoAuthenticationProvider` verifies
with at sign-in, so the same hash format is read and written by one bean and
there is no second encoder anywhere.

### The service, and what it deliberately does not do

A new `@Transactional` service — the change-password service — sits beside
`AgentLoginService` and `TesterLoginService`, in the same existing package.
Neither of those two gains an update operation; both keep only `create`. Its
whole interface is one method taking the authenticated principal, the current
password and the new one, and it does four things: load the caller's own `User`
by `principal.userId()`, verify, encode-and-set the hash, write one audit line.

It does **not** reach into either login service, and neither of them is
refactored to call it. `manager-resets-a-password` prefactors the password write
into one place, and when it does, this service is one of the three call sites it
unifies. Pulling that forward here would mean touching the Agent and Tester
creation paths — and their tests — for a feature that writes neither.

### Schema

**No Flyway migration.** Nothing new is stored: the feature overwrites
`users.password_hash`, a column that has existed since `V1`. V55 is the highest
migration on `main` and stays the highest.

The one thing that would change this is `## Open questions` 2 being answered
with "changing a password ends other sessions" — a stateless JWT cannot be
revoked, so that answer requires a `password_changed_at` column on `users` and a
check of it in the JWT filter on every request. That is a real feature of its
own, and it is the same per-request account-state check `deactivate-a-login`
must build anyway. If the human chooses it, this feature reserves **V56** and
the recommendation in that question is overridden.

### Observability

One audit line on success, in the existing `AuditLog` vocabulary:
`action=PASSWORD_CHANGED entity=User entityId=<userId> actorUserId=<same>
tenantId=…`. The subject and the actor are always the same user here; both are
written anyway so that `manager-resets-a-password`, where they differ, can reuse
the exact line shape. **No password, no hash, and no length** appears in it. A
failed verification is not audited — a mistyped password is ordinary, and
logging attempts on a per-user basis would be the beginning of a lockout feature
this spec has ruled out.

### Frontend

One new surface, holding one form, plus a plain pass-through BFF proxy in the
caller's own namespace — the same shape every other proxy in this app already
has, calling the backend through the shared `backendFetch` helper so the JWT
never leaves the httpOnly cookie and never reaches the browser.

The form is a Client Component (it has state and validation), with three
password inputs: current, new, and confirm-new. The confirm field is checked in
the browser only and is never sent — the backend has no use for a field whose
only job is to catch a typo in another field. Submission is a fetch to the BFF
proxy; failure renders the coded message inline on the field it concerns, using
the existing inline error alert the three creation dialogs already use.

Where that surface lives, and what it is called, is `## Open questions` 3. Note
for whoever answers it: `CONTEXT.md` puts "account" on the _Avoid_ list twice —
under **Tenant** and under **Client** — so this surface must not be called
"Account". The glossary's word for the thing being changed is **Login**.

### Prefactoring

None. No debt sits in this feature's way. The one piece of debt in the area —
two login services each encoding a password — is named above and is
`manager-resets-a-password`'s to remove, not this feature's.

## Design direction

One new surface, one mode: **Operate** — the visitor completes a task and
leaves. It is not Persuade (nothing to decide), not Read (nothing to
understand), not Experience.

The visual world is already committed in `DESIGN.md` and nothing about it is
reopened here. This surface introduces **no new token, no new component and no
new interaction pattern**; it is assembled entirely from the shipped vocabulary,
pinned against the three existing creation dialogs:

- A single `Card` (12px radius, flat hairline border, no shadow — the
  Flat-At-Rest Rule; this is not an overlay) holding the form, on the standard
  page canvas inside the existing shell.
- Inputs at the shipped spec: 8px radius, `hairline-strong` border, `canvas`
  background, 36px height, border shifting to `primary` on `:focus-visible`.
- Exactly **one** pill-radius control on the screen — the "Change password"
  primary action — per the Pill-Is-Primary Rule. Any secondary action (Cancel,
  or a link away) stays at the 8px control radius.
- Failure uses the existing inline error alert component the creation dialogs
  use, in `danger`/`danger-bg`; success uses the `success` tone. No new alert
  style.
- Below `md` the rail is already a drawer and the card is full-width; the form
  reflows to one column, which it already is.

If `## Open questions` 3 is answered with a nav entry, then **`DESIGN.md`'s
per-role nav item list is updated in the same commit as the code** — frontend
rule 7 — and the item takes the identical rail styling and active-state
treatment as every existing item, per the Do's list. If it is answered with a
top-bar change, the viewer identity chip stops being a static `<span>` and that
*is* a new interaction, which is why the recommendation does not go there.

Visual goldens: one new surface × theme × breakpoint set, captured against the
existing stub-backend fixtures rather than a live database. No existing golden
should move; one that does is a finding, not a recapture.

## Constraints

- **No Flyway migration.** V55 is the highest on `main` and remains so. The
  single exception is `## Open questions` 2 being answered against its
  recommendation, which reserves **V56** for a `password_changed_at` column.
- Success is `204 No Content`. Every refusal is `400` with
  `{"code": "...", "message": "..."}`. **Never `401`, never `403`** — the
  frontend session layer reads `401` as an expired session.
- The password is hashed by the existing `PasswordEncoder` bean
  (`BCryptPasswordEncoder`, `security/SecurityConfig`). No second encoder, no
  cost-factor change, no algorithm change.
- `LoginRequest`'s password validation stays `@NotBlank` and nothing else,
  whatever is decided in `## Open questions` 1. A shape rule at sign-in would
  lock out every existing password shorter than it.
- The endpoint is reachable by `MANAGER`, `AGENT` and `TESTER` alike, and acts
  only on the caller's own `User` row. No request field may name a user, and no
  path variable may either.
- No password, hash, or password length is ever written to a log line, returned
  in a response body, or placed in a URL.
- Backend tests run under `IntegrationTest`: singleton Testcontainers Postgres,
  each method in a transaction that rolls back. Mocking a repository in an
  integration test is banned (coding standards Backend rule 5).
- **No test — integration or e2e — may change a seeded user's password.** The
  three seeded credentials (`manager@example.com` / `ChangeMe123!`,
  `agent@example.com` / `AgentDemo123!`, `tester@example.com` /
  `TesterDemo123!`) are relied on by `AuthLoginTest`, by `IntegrationTest`'s
  token helpers and by most e2e specs. A password-change test creates its own
  Login first and changes that one. In the backend suite the transaction rolls
  back; in e2e nothing rolls back at all, so this is not optional there.
- Maven runs with `JAVA_HOME=/opt/homebrew/opt/openjdk@21` (JDK 26 breaks
  Lombok). Checkstyle is part of `mvn verify` and stays clean.
- e2e and visual runs go against an isolated Postgres/backend/frontend on ports
  other than 3000/8080/5432, with `E2E_DATABASE_URL` pointed at that Postgres
  and a temporary uncommitted Playwright config. The user's docker-compose stack
  is never written to.
- JWT lifetime is `app.jwt.expiration-minutes` (default 60) and the session
  cookie's max-age matches it at 3600s. Neither is changed by this feature.
- New backend classes go in the existing `web` package — see
  `## Decisions taken`.

## Testing decisions

Tests assert external behaviour only: HTTP status, the response body's `code`,
and whether a subsequent sign-in succeeds. Never the hash in the database, never
which repository method ran, never a `PasswordEncoder` interaction.

- **One seam, and it already exists**: the HTTP API seam — `IntegrationTest` +
  MockMvc against a real Postgres. Every backend assertion in this feature is
  made there. No new seam is added, and none is needed: the feature is one
  endpoint whose entire observable effect is visible through another endpoint
  that is already tested.
- **The proof that the change is real is a sign-in, not a hash read.** The
  decisive assertions go through the existing `IntegrationTest.loginAs(username,
  password)` helper — i.e. through `POST /api/auth/login`, the very path
  `AuthLoginTest` covers: after a successful change, `loginAs(user, newPassword)`
  returns a token and `loginAs(user, oldPassword)` is 401. This is the highest
  seam available and it asserts the only thing a user actually cares about.
  Reading `password_hash` out of the database would assert an implementation
  detail and would pass even if sign-in were broken.
- **All three roles are covered at that seam**, using `IntegrationTest`'s
  `managerToken()`, `agentToken()` and `testerToken()` helpers to *reach* the
  endpoint — but each role's *change* is performed against a Login the test
  created, never a seeded one (see `## Constraints`). A Manager case is
  non-negotiable: it is the role whose `User` row links to neither an Agent nor
  a Tester, and it is the one a naive implementation resolving the caller
  through `CallerIdentityResolver` would break.
- **Prior art, named**: `AuthLoginTest` (the sign-in assertions this feature's
  proof reuses, including the 401-on-wrong-password shape) and `AgentLoginApiTest`
  (login creation, the coded-409 assertion shape `$.code`, and the fixture style
  for making a Login to act on).
- **Gotcha to respect** (`docs/agents/implementer-notes.md`): MockMvc requests
  inside one `@Transactional` test method share a Hibernate session, so a change
  and the sign-in proving it are both visible within the method — that is fine
  and is what makes the seam work. What is *not* fine is chaining a failing call
  and a succeeding retry in one method; a wrong-current-password case and a
  success case are separate test methods.
- **The wrong-current-password case asserts three things at once**: the status
  is `400` (not `401`), the body carries the expected `code`, and a subsequent
  `loginAs` with the *old* password still succeeds — i.e. a failed attempt left
  the password alone.
- **Frontend component tests** (Vitest + Testing Library) for the form, in the
  shape `create-agent-login-dialog`'s own tests already use: stub the BFF
  response with each `code`, assert the rendered message and the flagged field;
  assert the mismatch between new and confirm is caught before any request is
  made. The network is stubbed at the fetch boundary, never by reaching into an
  internal function.
- **One e2e spec**, under `frontend/tests/e2e/`, one file for this journey step:
  create a Login through the UI as a Manager, sign in as that person, change the
  password, then sign in again with the new password and be refused with the
  old. This is the only place the whole path is exercised in a browser, and it
  is what the acceptance walkthrough replays.
- **Visual goldens** for the new surface, per the frontend DoD — one per theme
  and breakpoint, against the stub backend, not a live database.
- **No unit test of the change-password service.** It has one branch and a
  Spring context behind it; a unit test with a mocked `PasswordEncoder` would
  assert the mock. Coding standards Backend rule 6 puts a test needing a context
  on the integration side, and that is where the whole of this feature's
  coverage belongs.

## Decisions taken

- **The endpoint is `POST /api/me/password`, in the caller's own `/api/me`
  namespace** — `MeController` already establishes `/api/me` as "the signed-in
  user", the row written is resolved from the principal and from nothing in the
  request, and a path with no id is the honest shape for an action with no
  target to authorize.
- **It gets its own controller beside `MeController` rather than a method
  inside it** — `MeController` is documented as the reference *read-only*
  protected endpoint, and a write path needs its own `@ExceptionHandler` to emit
  a `code` (Backend rule 7), which `MeController` has no other use for.
- **New classes go into the existing `web` package, not a new
  feature-shaped package** — coding standards Backend rule 13 makes
  package-by-feature the norm for new code but explicitly permits following the
  existing layered layout, and only cites a violation against a *new package*
  choosing the layered shape. Adding to `web` creates no new package, and the
  `package-by-feature` epic will move `web` wholesale; carving one
  `authentication` package out now would leave that epic with a half-migrated
  tree to reconcile.
- **No `SecurityConfig` change** — `/api/me/**` matches no rule in the chain and
  falls through to `.anyRequest().authenticated()`, which is precisely the
  intended access for an endpoint all three roles may call on themselves only.
- **Verification is `PasswordEncoder.matches`, not a round-trip through
  `AuthenticationManager`** — authenticating would mint a fresh `Authentication`
  and emit sign-in-shaped events, making a password change unreadable in the
  logs as anything other than a sign-in.
- **Success is `204` with no body** — there is nothing to return that the caller
  does not already know, and a response body around a password change is a
  place for a secret to leak into.
- **Every refusal is `400` with a `{code, message}` body, never `401`/`403`** —
  the frontend treats `401` as an expired session and would route the user to
  sign in, discarding a form they mistyped one field of. The `{code, message}`
  shape reuses `USERNAME_TAKEN`'s contract and the existing `readErrorCode`.
- **A new password identical to the current one is refused, with its own code**
  — a no-op change reported as success is a small lie, and someone changing
  their password because it was exposed would be told it worked when nothing
  happened. Cheap to undo if the human disagrees.
- **The confirm-new-password field exists only in the browser and is never
  sent** — its only job is catching a typo in a field the user cannot read back;
  the backend has no use for a second copy, and not sending it is one fewer
  place for the secret to travel.
- **No rate limiting, throttling or lockout** — the endpoint requires a valid
  JWT, so it is not an anonymous brute-force surface, and the product has no
  rate-limiting infrastructure to extend. Introducing one is its own decision.
- **The audit line writes both the subject and the actor even though they are
  always equal here** — `manager-resets-a-password` has the same event with the
  two differing, and one line shape for both keeps the trail readable.
- **A failed verification is not audited** — a mistyped password is ordinary,
  and per-user failure counting is the first half of a lockout feature this spec
  ruled out.
- **The two login services keep only `create`; no password-write prefactor
  happens here** — the epic assigns that prefactor to
  `manager-resets-a-password`, and doing it early would put this feature's diff
  mostly in code it does not call.
- **No Flyway migration** — the feature overwrites a `V1` column and stores
  nothing new. V55 stays the highest on `main`.
- **Testing: the existing HTTP seam is reused and no new seam is added**; the
  decisive assertion is a real sign-in through `IntegrationTest.loginAs`, the
  highest seam available, with `AuthLoginTest` and `AgentLoginApiTest` as prior
  art. A hash read would assert an implementation detail and would pass with
  sign-in broken.
- **No unit test of the change-password service** — with a mocked
  `PasswordEncoder` it would assert the mock; Backend rule 6 puts anything
  needing a context on the integration side.
- **Tests create their own Login rather than changing a seeded user's password**
  — the three seeded credentials are relied on by `IntegrationTest`'s token
  helpers and most e2e specs, and e2e state does not roll back.

## Open questions

Three. Each is about behaviour a user will see, none is settled by `PRODUCT.md`,
`docs/journeys.md` or the epic, and the first two are also hard to undo once
people's real passwords depend on them — cases 1 and 2 of
`docs/agents/escalation.md`. No ticket may be cut while they stand
(`docs/agents/issue-tracker.md` rule 1).

**1. Should a password rule be introduced, and if so does it apply everywhere a
password is set — or does this feature leave it at "not blank"?**

Today the only rule anywhere is `@NotBlank`, on `LoginRequest`,
`AgentCreateRequest` and `TesterCreateRequest`. No minimum length, no
complexity. So a user could change their password to `a`. But adding a minimum
*only* to changing a password would make this product stricter about the
password you choose for yourself than about the one a Manager assigns you —
which is backwards, and which this spec will not do silently.

*Recommendation:* **introduce one rule — a minimum of 8 characters, no
complexity classes — and apply it to all three places a password is set: this
feature's change, Agent creation, and Tester creation.** Reasons: one rule in
one sentence means no asymmetry a user can trip over; length beats composition
rules (which push people toward `Passw0rd!`); the widening is genuinely small,
being one annotation on two existing request DTOs plus a shared message; and it
costs no existing user anything, because the rule is checked only when a
password is *written* — nothing re-validates an existing password, and sign-in
keeps `@NotBlank` exactly as it is, so nobody is locked out. All three seeded
passwords already exceed 8 characters.
*Blocks:* the ticket cut, and story 12. *Meanwhile:* everything else in this
spec is independent of the answer.
*The alternatives, if you prefer one:* (a) no rule at all, leaving `a` a legal
password; (b) a rule on change-password only, accepting the asymmetry; (c) a
longer minimum, or a complexity requirement.

**2. What should changing a password do to sessions — this browser's, and any
other browser already signed in?**

The JWT is stateless and carries only an expiry, so today the answer is
"nothing": every token already issued keeps working until it expires, up to 60
minutes. Someone who changes their password *because* they believe it was seen
would reasonably expect the other person to be kicked out, and would not be.

*Recommendation:* **sign the user out of this browser on success — drop the
session cookie and send them to the sign-in page with a confirmation — and leave
any other session to expire on its own within the hour.** Reasons: signing out
here is free (the cookie is already dropped by an existing route), it makes the
user prove the new password immediately, and it is what the epic's own closing
proof describes ("changes their own password and signs in with the new one").
Ending *other* sessions is not free: a stateless JWT cannot be revoked, so it
needs a `password_changed_at` column and a check of it in the JWT filter on
every request — which is the same per-request account-state check
`deactivate-a-login` must build, and where the epic has already parked the
identical question ("whether deactivation takes effect immediately for a session
already holding a valid JWT"). Deciding both in one place, once, beats building
half the mechanism here.
*Blocks:* story 13, the walkthrough's sign-in step, and — if answered against
the recommendation — the "no migration" constraint, which would reserve V56.
*Meanwhile:* the endpoint and the form are unaffected either way.
*The alternatives:* (a) stay signed in here and show a confirmation in place
(fewest clicks, but the new password is never exercised); (b) end every session
everywhere immediately, paying for the column and the filter check now.

**3. Where does this screen live, and what is it called?**

This is the first account-shaped screen in the product and the navigation has
nowhere to put it. The three rails hold only work surfaces (Dashboard, Requests,
Fleet, Invoices…), there is no `/account`, `/profile`, `/settings` or `/me`
route anywhere, and the top bar's viewer identity chip is a static `<span>` next
to a sign-out button.

*Recommendation:* **a new last nav item, "My login", on all three rails, leading
to a per-console route that renders one shared form** — `/manager/…`,
`/agent/…`, `/client/…`, so each console keeps its own shell and its own correct
rail. Reasons: the rail is the product's only established way to reach a screen,
it is the one pattern all three consoles already share identically, and adding
an item is three one-line changes plus the matching line in `DESIGN.md`'s
per-role item list. The name is **not "Account"**: `CONTEXT.md` puts "account"
on the _Avoid_ list under both **Tenant** and **Client**, and the glossary's word
for the thing being changed is **Login**.
*Blocks:* story 14, the frontend ticket, and the `DESIGN.md` edit that must
travel in the same commit.
*Meanwhile:* the backend endpoint is entirely independent of the answer.
*The alternatives:* (a) turn the top bar's viewer chip into a dropdown menu
holding "Change password" and "Sign out" — the most conventional answer in
products generally, but it converts a static element into a new interactive
component with its own overlay, focus and keyboard behaviour, which is real work
and a genuinely new pattern for this design system; (b) a bare route reachable
only by typing the URL — cheapest, but it means shipping a capability nobody can
find, which fails the journey in practice; (c) a different name — "Password",
"My password", "Security".

## Acceptance walkthrough

1. [agent] Sign in through the API as a purpose-made Login (not a seeded one), `POST /api/me/password` with the correct current password and a valid new one, and show the response is `204` with an empty body. (stories: 1, 2, 15)
2. [agent] Immediately sign in with the new password and show a token comes back, then sign in with the old password and show `401`. (stories: 9, 10)
3. [agent] Repeat step 1's change for a Manager Login, an Agent Login and a Tester Login, and show all three succeed — the Manager one proving a Login linked to neither an Agent nor a Tester works. (stories: 6, 7, 8)
4. [agent] `POST /api/me/password` with a deliberately wrong current password and show the response is `400` (not `401`, not `403`) carrying the expected `code`, and that signing in with the unchanged old password still works. (stories: 3, 4)
5. [agent] Call `POST /api/me/password` with no JWT and show it is refused, then with a valid JWT of each of the three roles and show none of them is refused on role grounds. (stories: 6, 7, 8)
6. [agent] Submit the new password decided in `## Open questions` 1 as a value that breaks the rule, and show a `400` with its own distinct `code`. (stories: 12)
7. [agent] Grep the backend log output produced by a successful change and show one `PASSWORD_CHANGED` audit line naming the user, the actor and the tenant, and no line anywhere containing the password or a hash. (stories: 15, 16)
8. [agent] Show `git diff` touching no Flyway migration, no `SecurityConfig` matcher, and neither login service's `create`. (stories: 20)
9. [agent] Run `mvn verify`, the frontend vitest suite, typecheck, lint, the full e2e suite and the visual suite, all green, with no pre-existing golden recaptured. (stories: 20)
10. [agent] In a browser at the desktop breakpoint, reach the password screen the way `## Open questions` 3 was answered, change the password, and show the confirmation and the session behaviour decided in `## Open questions` 2. (stories: 11, 13, 14)
11. [agent] In the same browser, sign in with the new password and reach the console, then sign out and show the old password refused with the sign-in page's existing error. (stories: 9, 10)
12. [agent] Submit the form with the new and confirm fields differing and show it is caught in the browser with no request sent; then submit a wrong current password and show the inline message names that field and the form keeps what was typed. (stories: 3, 4, 5)
13. [agent] Load the screen at the mobile breakpoint with the rail drawer closed, and tab through the form from the top showing a visible focus ring on every control and a sane tab order. (stories: 18, 19)
14. [human] Open the screen in each of the three consoles and confirm the wording, layout and behaviour are identical, and that nothing on it offers anything but changing the password. (stories: 17)
15. [human] Read the screen's name and its nav entry and confirm it does not use the word "account", and that you could have found it without being told the URL. (stories: 14)
16. [human] Change your own password in the running app, then confirm by whatever the answer to `## Open questions` 2 was — that you were or were not signed out here — and that a second browser you left signed in behaves as that answer says. (stories: 13)

## Execution order

**Provisional — tickets are not cut while `## Open questions` is non-empty**
(`docs/agents/issue-tracker.md` rule 1). The shape below is what the answers
will be poured into; answer 1 may add a slice, answer 2 may add a migration to
the first, and answer 3 decides the third's surface.

1. `change-own-password-endpoint` — `POST /api/me/password`, the change-password service, the coded `400` contract, the audit line, and the integration tests proving the change through a real sign-in for all three roles. (stories: 1, 2, 3, 6, 7, 8, 9, 10, 15, 16, 20)
2. `password-rule` — the rule decided in `## Open questions` 1, applied to every path that sets a password, with its own code and message. Depends on `change-own-password-endpoint`. (stories: 12)
3. `change-password-surface` — the BFF proxy, the form, its place in the navigation, the session behaviour decided in `## Open questions` 2, the `DESIGN.md` nav-list edit in the same commit, component tests, one e2e spec and the visual goldens. Depends on `change-own-password-endpoint`. (stories: 4, 5, 11, 13, 14, 17, 18, 19)
