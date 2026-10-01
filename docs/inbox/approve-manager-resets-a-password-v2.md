---
id: approve-manager-resets-a-password-v2
type: approval
status: open
blocks: [manager-resets-a-password]
created: 2026-09-30
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the **revised** spec for `manager-resets-a-password`?
`docs/features/manager-resets-a-password/spec.md`. Your objections of
2026-09-30 are applied:

- **Passwords are generated, never typed**, for resets and for creating a
  login (Add agent, Create login, Add tester). The backend generates them.
- **A reset password never equals the old one.** Each candidate is checked
  against the current hash and drawn again on a match.
- **A Manager's login can't be reset here.** That's the SuperAdmin's job, in
  `tenant-administration`.

New choices you're most likely to want to veto:

1. **Format:** `k7mq-x3vh-p9te`. Three groups of four, lowercase letters and
   digits, without the look-alikes `l`, `o`, `0` and `1`. The hyphens are part
   of the password. It's easy to read aloud or type.
2. **Shown once:** after creating or resetting, the dialog shows the password
   selected, with a **Copy password** button and a warning that it won't be
   shown again. Closing the dialog removes it for good. It's never logged and
   never in a URL, storage or audit line.
3. **Reset asks for confirmation first** ("Generate a new password for …?"),
   because there's no field, and one click would otherwise replace someone's
   password.
4. **The demo logins keep their documented passwords.** The demo loader sets
   them directly, so manual testing still works.

## Recommendation

Approve. There's no migration. It's wider than the first draft: login
creation changes too, and many existing tests move from a typed password to
reading the generated one. It's five tickets, and the creation change lands
as a single ticket. Three walkthrough steps are yours.

## Blocks

`manager-resets-a-password` stays a `draft`, and no tickets are cut until you
answer.

## Meanwhile

`real-agent-dashboard` is being built.

## Answer
