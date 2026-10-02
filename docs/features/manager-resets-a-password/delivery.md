# Delivery report — manager-resets-a-password

<!-- sdlc:template delivery 1 -->

Merged to `main` as `6800285` (PR #20) on 2026-10-02. The merge gate answered
`ok: true`: no blocking finding, harness passed, `verify` passed. `verify` on
`main` after the merge also passed: 74 e2e tests.

## What was built

A Manager can now reset the password of an Agent or a Tester in their own
Tenant, and **the product generates every password**: on a reset and when a
login is created. No Manager types one any more.

- **Generated passwords** (`one-password-write`, `generated-password-generator`).
  Every password write goes through one component, `PasswordWrite`. Passwords
  look like `k7mq-x3vh-p9te`: three groups of four, with no l, o, 0 or 1. A
  reset never produces the current password.
- **Shown once** (`one-time-password-reveal`, `dialog-shell-mounts-when-open`).
  After creating or resetting a login, the dialog shows the password once:
  selected, with **Copy password** and a "won't be shown again" warning.
  Closing the dialog removes it for good. It is never logged or cached, and
  never put in a URL, browser storage or an audit line.
- **Login creation** (`new-logins-generate-password-api`,
  `creation-dialogs-reveal-generated-password`,
  `creation-takes-no-typed-password`). Add agent, Create login and Add tester
  have no password field. A typed password sent to the API is ignored. The
  demo logins keep their documented passwords.
- **Reset** (`reset-an-agents-password-api`, `reset-a-testers-password-api`,
  `reset-an-agents-password-ui`, `reset-a-testers-password-ui`).
  - **Agent:** **Reset password** beside an Agent's sign-in email.
  - **Tester:** a right-aligned row action on the Client's Testers table.
  - **Flow:** confirm first, then the reveal.
  - **Who:** only a Manager, only for an Agent or Tester in their own Tenant.
    Another Tenant answers "not found". A Manager's own login can't be reset
    here; that is the SuperAdmin's job, in `tenant-administration`.

## Acceptance walkthrough

1–14 (API, security and backend steps) — played — evidence: `evidence/step-1.txt` … `evidence/step-14.txt`
15. `verify` and the visual suite green — played — evidence: `evidence/step-15.txt`
16–25 (UI steps: dialogs, reveal, keyboard, mobile) — played — evidence: `evidence/step-16.txt` … `evidence/step-25.txt`
26. Your own real reset and handover: does the wording make clear the handover is yours? — yours
27. Second browser: a reset person's open session keeps working until it expires (within the hour) — yours
28. A Manager's login can't be reset here — yours

Full detail per step is in `acceptance.json`. Two step texts in the spec turned
out to be worded wrongly, and the code is right in both cases:

- **Step 15** expected existing Agent-page and Client-page goldens to move,
  but none existed.
- **Step 17** said "only one dialog element". The page has the existing Add
  tester dialog plus the one shared reset dialog.

## Decisions taken alone

All are in the spec's `## Decisions taken`. Taken during build and review
**(after review)**:

- I ran at most two implementers at once, because the e2e suite sheds specs
  when the machine is loaded.
- `DialogShell` gained an optional `onClosed` callback, so Create login swaps
  only after the reveal is dismissed.
- The reset dialog goldens live in their own visual specs, because the dialog
  has to be opened before capture.
- The reset is announced through each page's single status region. A small
  `useAnnouncement` hook makes a second reset of the same person announce
  again.
- The fix pass dropped `font-mono` from the reveal (Inter, per DESIGN.md), gave
  every dialog step an accessible name, and added `forwardSecretJson` for the
  five password-carrying routes.
- **You authorised one extra fix pass** (2026-10-02) to recapture four
  Testers-table goldens that the right-align fix had left stale.
- A merger's merge was once swept into my own status commit (`413c7ee`). The
  content is correct and the message is wrong. I left it rather than rewrite
  shared history.

## Debt recorded

- `create-*-dialog.tsx` · duplicated form-or-reveal state (F12)
- `testers-view.tsx` · `aria-label` on the actions cell, kept for test selectors (F14)
- `ui/button.tsx` · focus lost while a dialog submits (F16)
- `dialog-shell.tsx` · a second Escape can close mid-request; no overscroll containment (F26, F27)
- `testers-view.tsx` · row action needs a horizontal scroll on mobile (F21)

**Paid by this feature and removed:** `DialogShell` rendering closed dialogs'
children, and `PasswordMinimumLengthApiTest`'s stale Javadoc counts.

Wording suggestions from the design audit that the spec had decided (F18–F20)
were left as they are.

## How to undo

```
git revert -m 1 6800285
```

This reverts code only; there is no migration. Logins created while it was
live keep their generated passwords, which still work.

## What happens next

`deactivate-a-login` is next in `login-lifecycle`. The loop also continues
with `real-client-dashboard`, whose tickets are cut. The invoice epic waits
on your two pay questions.
