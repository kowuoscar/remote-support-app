---
id: change-password-dialog
title: Wire "Change password" to a dialog that changes the password and signs out here
status: in-progress
depends_on: [password-minimum-length, viewer-chip-menu]
labels: [frontend]
stories: [4, 5, 11, 14, 19, 20, 21]
---

## Context

`spec.md` `## Solution`, "Frontend: a menu on the viewer chip, opening a
dialog": the form is a modal dialog, not a route, built on the existing
`DialogShell` (native `<dialog>` via `showModal()`, so the focus trap,
Escape, backdrop and page inertness come from the platform). Three password
inputs — current, new, confirm-new — following `LoginCredentialFields`'
field shape (label above an `Input`, `autoComplete="current-password"` /
`"new-password"`) without reusing that component (`## Decisions taken`: it
pairs an email with one password; this form has three passwords and no
email). Failures render through the existing `DialogErrorAlert`, branching
on the response `code` via `readErrorCode`, exactly as
`create-agent-login-dialog` does. Behind it sits a plain pass-through BFF
proxy in `/api/me/**`, calling the backend through the shared `backendFetch`
helper. The confirm-new field is checked in the browser only and is never
sent (`## Decisions taken`). On success the dialog does not just close: it
drops the session through the existing sign-out route and sends the user to
sign-in carrying a confirmation, per the human's session decision (`##
Decisions taken`, "Changing a password signs the user out of this browser").

`## Design direction`, "What is pinned against what": the dialog is
assembled from the three creation dialogs' own vocabulary — `DialogShell`'s
12px radius, `canvas-overlay`, `shadow-elevated-strong`, `bg-ink/40`
backdrop; inputs at 8px radius, `hairline-strong` border, 36px height, border
to `primary` on `:focus-visible`; exactly one pill-radius control (the
"Change password" commit action), Cancel at the 8px control radius. This is
assembly, not a new pattern — no `design` slot needed here (unlike
`viewer-chip-menu`). Naming: neither the menu item nor the dialog uses the
word "account" (`CONTEXT.md`'s _Avoid_ list, under Tenant and Client).

This ticket **replaces** `viewer-chip-menu`'s close-only "Change password"
handler with the real one — the menu component itself is not otherwise
changed.

**Depends on `password-minimum-length`, not directly on
`change-own-password-endpoint`.** One of this ticket's own acceptance
criteria stubs and asserts the "new password shorter than 8 characters"
refusal — that refusal, its distinct `code`, and the `204` boundary at 8
characters are exactly what `password-minimum-length` adds to the
change-password endpoint; without it merged first, the backend returns
`204` for a 3-character password and there is no `code` for this ticket to
branch on. `password-minimum-length` itself depends on
`change-own-password-endpoint`, so that endpoint's existence is still a real
transitive requirement — naming it again directly here would be a
redundant edge, not an additional gate. Execution order already runs
`password-minimum-length` second, so this changes no parallelism:
`viewer-chip-menu` still runs free alongside both.

## Acceptance criteria

- [ ] Activating "Change password" from the menu, in any console, opens a modal dialog built on `DialogShell` with three password fields (current, new, confirm-new) and the field shape's `autoComplete` hints — replacing `viewer-chip-menu`'s close-only placeholder handler.
- [ ] Submitting with new and confirm-new differing is caught in the browser before any request is sent, with an inline message and the rest of the form left as typed.
- [ ] Submitting a request the backend refuses — wrong current password, new identical to current, or new shorter than 8 characters — shows `DialogErrorAlert` naming the field for that request's distinct `code`, keeps the typed values, and neither signs the user out nor navigates away.
- [ ] Submitting a valid change signs the user out through the existing sign-out route and lands on the sign-in page carrying a confirmation that the password changed; signing in there with the new password succeeds, and with the old password is refused with the sign-in page's existing error.
- [ ] The dialog and its trigger are usable at the mobile breakpoint, and the whole path — chip, menu items, every dialog field, Cancel, Change password — is reachable by keyboard alone with a visible focus ring at each stop.
- [ ] One e2e spec exercises the whole journey: create a Login through the UI as a Manager, sign in as that person, open the chip menu, change the password, land back on sign-in with the confirmation, sign in with the new password, and get refused with the old.

## Tests

Seam: Vitest + Testing Library component tests for the dialog, in
`create-agent-login-dialog`'s own shape — the network stubbed at the fetch
boundary, never by reaching into an internal function (coding standards
frontend rule 10). One Playwright e2e spec under `frontend/tests/e2e/` (spec
`## Testing decisions`, "one file for this journey step"). Visual goldens
for the dialog open, per theme × breakpoint, against the stub backend.

- Case: new/confirm mismatch blocks submission client-side; no fetch call is made.
- Case: a stubbed `400` for a wrong current password renders on the current-password field.
- Case: a stubbed `400` for "new identical to current" renders on the new-password field.
- Case: a stubbed `400` for "new shorter than 8 characters" renders on the new-password field.
- Case: a stubbed `204` success triggers the sign-out-and-redirect path.
- e2e case: the full journey named in the acceptance criteria above, using the updated `logout(page)` helper wherever the spec logs out.
- Visual goldens: dialog open, light/dark theme, desktop/mobile breakpoint, against the stub backend.

## Regression

- `viewer-chip-menu`'s "Change password" item's close-only handler is replaced by this ticket with the real one — expected, and exactly what that ticket's own acceptance criteria named as this ticket's job; the menu's markup, focus and dismissal behaviour are otherwise untouched.
- No existing dialog component (`create-agent-login-dialog`, `create-agent-dialog`, `create-tester-dialog`, `create-client-dialog`, and the other `DialogShell` consumers) is modified — this ticket adds a new dialog and a new BFF route beside them.
- `LoginCredentialFields` is not reused or modified (`## Decisions taken`).
- No existing test file is modified by this ticket; its component tests, e2e spec and visual goldens are all new files.

## Observability

N/A on the frontend — no new client-side logging is added. The observable
audit trail for a successful change is backend-only and already covered by
`change-own-password-endpoint`.
