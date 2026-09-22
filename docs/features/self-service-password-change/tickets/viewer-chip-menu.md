---
id: viewer-chip-menu
title: Turn the top bar's viewer chip into an accessible actions menu, absorbing log out
status: done
depends_on: []
labels: [frontend, design]
stories: [15, 16, 19, 20, 21]
---

## Context

This ticket must run with the **`design` slot** engaged (`docs/agents/frontend.md`),
not built freehand against `DESIGN.md` alone — `spec.md` `## Design
direction`, "This feature needs the `design` slot": a menu is exactly the
component an agent left to its own devices produces the category default
for, and none of this repository's own code is prior art to lean on (see
below).

`spec.md` `## Design direction`: **this is the first interactive overlay
with real focus management this design system has ever shipped.**
`ContractSwitcher` is visual prior art *only* — pin the menu's appearance to
its open panel (`rounded-xl`, `canvas-overlay`, hairline border,
`shadow-elevated`; items at the 8px control radius, `ink-secondary` at rest,
`canvas-soft` on hover, no indigo fill) but reuse **none** of its code: it
has no focus management, its dismissal listeners are mounted unconditionally,
and copying it would inherit exactly the accessibility gaps this pattern must
not have. The behaviour owed, with nothing in the repository to copy:
`aria-haspopup="menu"`/`aria-expanded` on the trigger, a `role="menu"` panel
of `role="menuitem"` buttons, focus moving into the panel on open and
**back to the trigger** on close, arrow-key movement between items, and
dismissal on Escape and an outside pointer press, with listeners bound only
while the menu is open. `spec.md` `## Solution`: the chip (today a static
`<span>` in `TopBar`) becomes the trigger; the standalone log-out button is
removed and log out becomes the menu's other item, keeping its shipped
accessible name "Log out" so the shared e2e `logout(page)` helper needs only
one added step and no spec is edited. `lib/nav.tsx` is not touched — no
route or nav item is added anywhere. The chip is hidden below `sm` today;
the trigger must not inherit that (`## Constraints`).

**`DESIGN.md` gains four things in this same commit** (`## Design
direction`, "What `DESIGN.md` gains"): a new **Components → Menu** entry
(the pattern, its tokens, and its explicit distinction from
`ContractSwitcher` — a menu performs actions, the switcher scopes data);
a **named rule** that a menu returns focus to its trigger on close and is
dismissible by Escape and an outside press; the **Layout** line describing
the top bar, updated to say the chip is the menu trigger and log out lives
inside it; and the **Elevation** shadow-vocabulary list, which gains the
menu's open panel alongside "review dialogs, the Contract switcher's open
dropdown panel."

**This ticket lands both menu items, but only "Log out" is wired for real.**
"Change password" is rendered, keyboard-reachable and semantically a real
`menuitem` — satisfying the walkthrough's "menu opens with exactly two
items" — but activating it today only closes the menu; there is no dialog
yet for it to open. That is not a dead control: it behaves exactly like
Escape (no navigation, no error, no visible difference from any other
dismissal), so the product stays coherent the moment this ticket alone is
merged. `change-password-dialog` depends on this ticket specifically to
replace that close-only handler with the real one.

Kept deliberately over the alternative of rendering "Log out" alone here and
moving the two-item criterion to `change-password-dialog`: the spec itself
states the menu holds two items, this ticket's own walkthrough step (11)
asks for exactly two items opening from the chip, and the feature ships as
one PR, so no user ever meets the inert state in between. If a reviewer
prefers the smaller promise, the alternative is a same-sized change: drop
"Change password" from this ticket's acceptance criteria and menu, and add
it — inert until wired — to `change-password-dialog`'s own opening move.

## Acceptance criteria

- [ ] In each of the three consoles, clicking the viewer chip opens a menu (`role="menu"`) with exactly two `role="menuitem"` entries, "Change password" and "Log out" — the chip's old static `<span>` and the top bar's separate standalone log-out button are both gone.
- [ ] Activating "Log out" signs the user out and returns them to sign-in — the same behaviour the removed standalone button had, now reached through the menu.
- [ ] Activating "Change password" closes the menu without navigating or throwing; `change-password-dialog` swaps this handler for opening its dialog.
- [ ] Opening the menu moves focus to a menu item; closing it by any means (Escape, an outside pointer press, or activating an item) returns focus to the chip trigger; and with the menu closed, an outside press or Escape has no effect on the page.
- [ ] Arrow-key movement cycles focus between the two items while the menu is open, and both items are reachable and activatable by keyboard alone.
- [ ] The chip/trigger is visible and operable at every breakpoint, including below `sm` where it is hidden today.
- [ ] `DESIGN.md` gains, in this same commit, all four items named in `## Context`: the Menu component entry, the named focus/dismissal rule, the top-bar layout line, and the elevation list entry.

## Tests

Seam: Vitest + Testing Library component tests for the menu, asserting
**behaviour, not markup** — roles and `aria-expanded` through the
accessibility tree, never class names (spec `## Testing decisions`, "this is
the feature's genuinely new code and it has no prior art in the repository
to lean on"). Playwright visual goldens for the top bar with the menu open,
per theme × breakpoint, against the stub-backend fixtures (`## Design
direction`).

**The "menu open" goldens live in a new visual spec file, not in the
existing `frontend/tests/visual/surfaces.spec.ts`.** That file's
`gotoAndSettle` only navigates and waits for a `ready` marker — it has no
hook for interacting with the page (opening the menu) before capture — and
it is not touched by this ticket: none of its `surfaces[]` entries, its
loop, or its `gotoAndSettle` helper changes. Its 32 existing PNGs are
expected to **recapture**, because the top bar they all render changes (see
`## Regression`), but that is pixels moving under an unmodified spec, not a
spec-file edit. The new file adds its own captures — one surface's top bar,
menu open, per theme × breakpoint — on top of the stub-backend fixtures the
existing suite already uses.

- Case: clicking the chip opens the menu; `aria-expanded` toggles true/false with open state.
- Case: focus lands on a menu item on open; Escape closes the menu and returns focus to the chip.
- Case: a pointer press outside the panel closes the menu and returns focus to the chip.
- Case: arrow keys move focus between "Change password" and "Log out" without leaving the panel.
- Case: activating "Log out" triggers the existing sign-out flow; activating "Change password" closes the menu with no navigation and no error.
- Case: with the menu closed, an outside press or Escape has no effect on the page (proving the dismissal listeners are not mounted unconditionally, unlike `ContractSwitcher`).
- Visual goldens: top bar with the menu open, light/dark theme, desktop/mobile breakpoint, against the stub backend.

## Regression

- `frontend/tests/e2e/helpers.ts`'s shared `logout(page)` helper **gains one step** (open the menu before clicking "Log out" by its accessible name) — every existing spec that imports it keeps passing **unmodified**, because the item keeps the shipped accessible name "Log out." No other method in that file changes.
- **Five existing e2e specs are edited, by name, because each clicks the standalone log-out button directly instead of through the helper**, and each breaks the moment `LogoutButton` leaves the DOM:
  - `frontend/tests/e2e/login.spec.ts:52`
  - `frontend/tests/e2e/create-agent-with-login.spec.ts:45`
  - `frontend/tests/e2e/create-login-for-existing-agent.spec.ts:89`
  - `frontend/tests/e2e/agent-invoice-submission-and-approval.spec.ts:36`
  - `frontend/tests/e2e/agent-standing-amounts-and-invoice-generation.spec.ts:32`

  In each, the direct `await page.getByRole("button", { name: "Log out" }).click();` is replaced by the shared `logout(page)` call (importing it if the file does not already). That one line is the only change in each file; every other test method in each of the five keeps passing unmodified.
- Every existing Playwright visual golden that contains a top bar is **expected to move** in this same commit (all 32 in `frontend/tests/visual/__screenshots__`, since `SurfacePage` renders `TopBar` unconditionally — see `## Tests` for where the new "menu open" goldens live instead). Because "every surface has a top bar" makes that guard vacuous on its own, the real constraint is narrower and checkable: for every recaptured existing golden, the pixel diff against its prior version is confined to the top bar's right-hand control cluster (the chip/trigger and where the standalone log-out button used to sit) — no page-body pixel changes; `surfaces.spec.ts`'s golden count stays at exactly 32 (no surface added or removed); and this all travels in the same commit as the `DESIGN.md` edit, which is what makes it a decided evolution rather than drift (spec `## Design direction`). A recapture whose diff reaches outside that cluster, or a golden count that changes, is a finding, not a recapture.
- `LogoutButton` (`frontend/components/app-shell/logout-button.tsx`) is **deleted**, not merely modified — flagged here so the merger does not mistake this for an unreviewed regression. Its `aria-label="Log out"` moves onto the new menu item, so no user-visible or accessible-name change ships.
- `lib/nav.tsx` is untouched by this ticket — no console gains a route or a nav item.

## Observability

N/A — pure frontend UI change; no backend behaviour, and therefore no audit
trail, is added or affected by this ticket.
