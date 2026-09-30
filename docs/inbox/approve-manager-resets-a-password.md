---
id: approve-manager-resets-a-password
type: approval
status: answered
blocks: [manager-resets-a-password]
created: 2026-09-30
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for `manager-resets-a-password`?
`docs/features/manager-resets-a-password/spec.md`. Read `## User stories`,
`## Goals / Non-goals`, `## Decisions taken` and `## Acceptance walkthrough`
(you can skip `## Solution`).

In one line: a Manager sets a new password for an Agent (from the Agent's
page, beside the sign-in email) or a Tester (a row action on the Client's
Testers table) in their own Tenant, without knowing the old one, and hands it
over out of band.

The choices you are most likely to want to veto:

1. **The target's existing sessions survive the reset** until their token
   expires, within the hour. This follows your 2026-09-22 answer for
   self-service change. Revoking them needs the per-request check that
   `deactivate-a-login` builds.
2. **A Manager's Login cannot be reset here.** The feature line names only
   Agents and Testers, so a locked-out Manager still needs the database.
3. **The Manager types the password.** Nothing is generated. One masked field
   and no confirm field, matching the creation dialogs.
4. **The password may equal the current one.** Refusing it would tell the
   Manager what someone else's password is.
5. **Two prefactors ride along.** All four password writes go through one
   component. `DialogShell` mounts its form only while open, which pays debt
   `docs/tech-debt.md:16`, the regression the last feature hit.

## Recommendation

Approve. Nothing is irreversible: there is no migration, and the two new
endpoints serve only this app's frontend. Every choice above is cheap to
change. The walkthrough leaves you three steps (21–23): a real reset and
handover, the second-browser session check, and the Manager exclusion.

## Blocks

`manager-resets-a-password` stays a `draft`, and no tickets are cut until you
answer.

## Meanwhile

The loop moves on to the next epic in order, `invoice-correction-and-history`,
starting with the spec for `send-a-client-invoice-back`. `deactivate-a-login`
waits behind this feature in its own epic.

## Answer

Not approved as written — revise (human, 2026-09-30). Objections, verbatim:

- "One manager can't reset another manager's password -> true, it's the role
  of the admin/superadmin"
- "password are generated, not typed"
- "The new password should not be equal to the old one being replaced"

Follow-ups answered in the same session:
- Generated passwords apply to **reset and to login creation** (Agent and
  Tester) — this feature switches both.
- The admin/superadmin who resets a Manager's password belongs to the
  `tenant-administration` epic.
