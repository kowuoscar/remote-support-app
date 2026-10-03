---
id: approve-deactivate-a-login
type: approval
status: answered
blocks: [deactivate-a-login]
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for `deactivate-a-login`?
`docs/features/deactivate-a-login/spec.md`.

In plain terms: a Manager can switch an Agent's or a Tester's login off, and
back on. Nothing is deleted: the Agent stays listed, gets work and is invoiced
as usual; their Requests and history stay. Only signing in stops. Your two
answers are in:
- **At once:** someone switched off while using the app lands on the sign-in
  page at their next click.
- **The message:** with the right password they read "This login has been
  deactivated. Ask your Manager if you need access again."; a wrong password
  still gets the usual error.

Where it shows: a **Deactivate login** button beside **Reset password** on the
Agent's page and on each Tester's row; a neutral **Deactivated** tag while it's
off (hover shows "Deactivated since 2 Oct 2026"), and **Reactivate login** in
place of the button; a confirm step before either action.

Choices made without you (approving accepts them):

1. **Switching back on keeps the old password.** Example: you switch Ana off by
   mistake and back on a minute later; she signs in as before. If you want her
   to have a new one, **Reset password** sits right beside it.
2. **You can reset a switched-off login's password.** It stays off until you
   switch it on, so you can prepare a returning person's password in advance.
3. **A switched-off person can't change their own password** either (this
   closes the gap the epic warned about).
4. **The tag shows the date only**, not which Manager did it; the audit log
   records who.
5. **A Manager's own login is not covered** here: that is the admin's job, in
   `tenant-administration`, as you said.

## Recommendation

Approve. Seven tickets, one small additive database change (a "deactivated
since" date on each login; every existing login stays active).

## Blocks

`deactivate-a-login` stays a `draft`.

## Meanwhile

The client dashboard gets its last one-line fix and merges; then tickets for
the two approved invoice features are cut.

## Answer

Approved (2026-10-03), including the five choices taken alone.
