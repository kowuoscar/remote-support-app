# Delivery report — self-service-password-change

<!-- sdlc:template delivery 1 -->

## What was built

Until this feature, a password could be set once and never changed by anyone.
A signed-in user of any role can now change their own, proving the current one
first — the first feature of the `login-lifecycle` epic, which still owes
Manager-initiated resets and deactivation.

- **Stories 1–3, 6–10, 17, 18, 22** — `POST /api/me/password`, the first
  endpoint in this product that *writes* on behalf of the signed-in caller
  (`MeController` was read-only). `204` on success, a coded `400` on every
  refusal, one audit line naming subject, actor and tenant and never the
  password. Ticket: `change-own-password-endpoint`.
- **Stories 12, 13** — an 8-character minimum on **every** path that writes a
  password. `LoginRequest` keeps `@NotBlank` alone, so nobody holding a
  shorter existing password is locked out. Tickets: `password-minimum-length`,
  plus the fix pass (see below).
- **Stories 15, 16, 19–21** — the viewer chip became an accessible actions
  menu: the first interactive overlay here with real focus management.
  `DESIGN.md` gained a Menu component, a named focus-return rule, a layout
  line and an elevation entry. Log out moved inside it and the standalone
  button was deleted. Ticket: `viewer-chip-menu`.
- **Stories 4, 5, 11, 14** — the dialog itself on the existing `DialogShell`,
  branching on the coded refusals so each points at the right field, signing
  the user out of this browser on success and landing them on sign-in with a
  confirmation. Ticket: `change-password-dialog`.

The Tester path also gained error codes it never had: it threw a codeless
conflict, so the dialog could not tell "email already taken" from "this Client
already has a primary contact".

## Acceptance walkthrough

Eighteen `[agent]` steps played against a real browser on an isolated stack,
evidence under `evidence/`. Five steps are **yours** — steps 19 to 23: wording
parity across the three consoles, sign-off that log out moving into the menu is
accepted, that neither the menu item nor the dialog says "account", your own
live password change plus the second-browser check that the other session
survives, and the `DESIGN.md` diff read alongside the code.

Highlights from what was played: a mismatched confirmation is caught in the
browser with **no network request fired**, proved from the request log; a
wrong current password shows inline and keeps every typed value; the menu
panel's bounding box measured inside a 390px viewport; and a solid 2px
focus-visible outline confirmed by computed style at every keyboard stop.

## Decisions taken alone

Thirty entries in the spec. **Four were the human's**, taken at the spec gate,
and none of them is protected by a test — which is why each reviewer was asked
to check them against the code rather than the tests:

- an 8-character minimum on every write path, sign-in excluded;
- success signs the user out of **this** browser only, so no
  `password_changed_at` column and no per-request check;
- the surface hangs off the viewer chip rather than a nav item;
- log out moves into that menu and the standalone button goes.

**(after review)** This branch also carries the loop's own bookkeeping — the
`package-by-feature` epic, a roadmap edit, a closed inbox proposal and a
`ticket-critic` amendment. Raised as `out-of-scope` and kept, by choice rather
than necessity; the full account, including a false justification the
orchestrator wrote and a re-reviewer caught from the reflog, is in the spec's
`## Decisions taken`.

## Debt recorded

- `dialog-shell.tsx` renders children unconditionally — the general form of the regression this feature paid for once. Cured in the consumer only (F3).
- Two stale Javadoc counts left by the fix pass (F10).
- The e2e suite sheds unrelated specs under load: 0, 1, 2 then 3 failures across four runs on one unchanged tree, the last taking 33.6 minutes against a normal 2.4.
- `run-e2e-isolated.sh` drops its arguments, so naming a single spec silently runs all 59.

Seven smells found in changed lines were **fixed** in the one fix pass.

## How to undo

```
git revert -m 1 ae12292
```

Reverts code only — this feature added **no migration**, so unlike
`globally-unique-usernames` the revert is symmetrical and leaves nothing
behind in the database.

## What happens next

`manager-resets-a-password`, the epic's second feature, which also prefactors
the password write into one place — deliberately left alone here.

**Read before specifying `deactivate-a-login`**, the third: `login-lifecycle.md`
records that `ChangePasswordService` verifies the current password with
`PasswordEncoder.matches` directly, which is equivalent to sign-in *only*
because every `UserDetails` account-status flag is still default `true`. Add an
enabled flag and this call site diverges silently — a deactivated login could
still change its own password, and no test today could catch it.
