---
id: frontend-cleanup
title: Pay down the frontend debt review keeps catching
status: planned
journeys: []
---

<!-- sdlc:template epic 1 -->

## Intent

From the retro on `login-lifecycle` and the invoice features built alongside
it, accepted by the human on 2026-10-08. The debt log is over its threshold
in `frontend`, and the same few defects cost a review fix pass on feature
after feature:

- Hand-written text sizes (`text-[12px]`/`text-[13px]`) were flagged in review
  on edit-client-invoice-lines F2, deactivate-a-login F3 and
  send-a-client-invoice-back F2. A lint rule catches them at `verify` instead.
- Three features in a row lost keyboard focus to the top of the page while a
  button was saving, and each was fixed locally in a different way. The
  shared button gets one focusable busy mode, used everywhere.
- The remaining frontend debt: the duplicated invoice layouts, the dead
  routes, the two date styles and the three spinner styles.

Placed by the human right after `package-by-feature`, which pays much of the
backend share of the debt.

## Journeys

No journey moves: this is behaviour-preserving work, plus a keyboard-focus
fix on screens that already exist. The proof that closes it: on `main`,
`verify` fails on a branch that adds `text-[13px]`, every saving button keeps
keyboard focus, and the `frontend` section of `docs/tech-debt.md` is under
its threshold.

## Features

- [ ] `text-size-lint-rule` — an ESLint rule fails `verify` on `text-[12px]`/`text-[13px]` in the files a branch touches, so new lines use `text-label-sm`/`text-label`.
- [ ] `busy-button-keeps-focus` — the shared button gets one focusable busy mode (`aria-disabled` + `aria-busy` + spinner), used by every saving button, the reset and login dialogs included.
- [ ] `one-invoice-layout` — the duplicated invoice layouts become one.
- [ ] `remove-dead-routes` — routes nothing links to are removed.
- [ ] `one-date-and-spinner-style` — one date style and one spinner style across the app.

## Reworked

## Later
