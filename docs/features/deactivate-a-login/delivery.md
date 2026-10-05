# Delivery report — deactivate-a-login

<!-- sdlc:template delivery 1 -->

Merged to `main` as `8f7b99a` (PR #23) on 2026-10-05. The merge gate answered
`ok: true`: no blocking finding, harness passed, `verify` passed.

## What was built

A Manager can switch an Agent's or a Tester's login off and back on. Nothing
is deleted: only signing in stops.

- **Refused at once, everywhere** (`deactivated-login-refused`,
  `dead-session-lands-on-sign-in`, `deactivated-message-at-sign-in`).
  - **Sign-in:** a switched-off person who types the right password sees
    "This login has been deactivated. Ask your Manager if you need access
    again.". A wrong password still gets "Incorrect email or password.", so a
    stranger learns nothing.
  - **Open sessions:** a session already signed in is refused on its next
    click, and the page sends the person to the sign-in page.
  - **Own password:** they can't change their own password either.
- **The Manager's controls** (`deactivate-an-agents-login-api`,
  `deactivate-a-testers-login-api`, `deactivate-an-agents-login-ui`,
  `deactivate-a-testers-login-ui`).
  - **Where:** **Deactivate login** and **Reactivate login** sit beside
    **Reset password**, on the Agent's page and on each row of a Client's
    Testers table. Each asks for confirmation first.
  - **The tag:** a switched-off login shows a grey **Deactivated** tag. Its
    "since" date appears on hover and is read out by screen readers.
  - **Repeats and audit:** pressing either action twice changes nothing more.
    Each real change writes one audit line naming who, by whom and in which
    Tenant.
  - **Passwords:** switching a login back on keeps the old password. A reset
    still works while the login is off.
- **Nothing lost.** A switched-off Agent stays listed and pickable, keeps
  their invoices, requests and standing amounts, and keeps their one login
  slot.
- **Shared lookup** (`administered-login-lookup`): the password reset and the
  new actions find their target login, and check the Manager may act on it,
  in one place.

## Acceptance walkthrough

1–11 (API, refusals, idempotency, reset while off, Tester flow, other Tenant,
permissions, audit log) — played — evidence: `evidence/step-1.txt` … `evidence/step-11.txt`
12. Full suites and migration: 670 backend tests, 321 vitest, 83 e2e, 87
    visual; one migration (V58, one nullable column) — played after the fix —
    evidence: `evidence/step-12-replay-*`
13–20 (Agent page, dialogs, sign-in message, reactivation, Testers row,
error messages, keyboard and 375px) — played — evidence: `evidence/step-13-*` …
`evidence/step-20*`
21. On a phone: deactivate a real signed-in Agent, watch their next tap land on
    sign-in, read the message and the dialog wording — yours
22. Reactivate them and confirm the old password working again is what you
    want — yours
23. Open a deactivated Agent's page, invoices and a request, and a deactivated
    Tester's Client page: is everything still there? — yours

Full detail per step is in `acceptance.json`.

## Decisions taken alone

All are in the spec's `## Decisions taken`. Taken during build and review
**(after review)**:

- **A fix in the shared dialog.** The fix pass changed `DialogShell`, which
  every dialog in the app uses. Escape now closes a dialog immediately.
  Before, an Enter pressed right after Escape could fail to reopen it, which
  made a test fail. A fresh design check confirmed that the existing
  password-reset and create-login dialogs still behave correctly.
- **Error handling.** The two refusals now follow the coding rules: the
  sign-in refusal goes through the controller's own error handler, and the
  password-change refusal throws the app's own error type. Both still answer
  401 exactly as before.
- **The merge check's false alarm.** The check that guards existing tests
  flagged Spring's `DisabledException` in app code as a skipped test. I named
  that in the ticket and let the merge go ahead. The bug is in the sdlc
  plugin's guard, outside this repo.
- **A stalled build agent.** The agent first given the Tester screen stalled
  without committing anything, and a fresh agent took the ticket.
- **Ticket fixes.** The ticket critic's objections were fixed in one revision.
  One ticket was split, and a test file was renamed so the test runner picks
  it up.

## Debt recorded

Entries tagged `deactivate-a-login` in `docs/tech-debt.md`:
- **Sideways scroll on phones:** the Manager's Client page already did this
  before this feature. Its cause is now known: a hidden "Actions" header
  label escapes the table, and it's a one-line fix.
- **Focus on reset:** the reset dialog still loses keyboard focus while
  confirming. The shared button needs a busy mode that stays focusable.
- **Dates:** two date styles now coexist in the app, and the "since" date is
  only visible on hover.
- **Leftovers:** a copied dialog width, and the wrong-password 401 is still
  built in a try/catch.

## How to undo

```
git revert -m 1 8f7b99a
```

Migration V58 stays applied. Its one nullable column is then unused, and every
login can sign in again, including any switched off while this was live. The
shared dialog fix is reverted along with the rest.

## What happens next

`deactivate-a-login` was the last feature of `login-lifecycle`, so the epic
closes next. Then comes `send-a-client-invoice-back`, whose eight tickets are
cut.
