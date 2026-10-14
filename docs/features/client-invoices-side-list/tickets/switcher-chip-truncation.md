---
id: switcher-chip-truncation
title: A long Client name in the single-Contract chip truncates
status: in-progress
depends_on: []
labels: [frontend]
stories: [25]
---

## Context

Debt paid from `docs/tech-debt.md` ("Single-contract label can overflow",
`contract-switcher.tsx`), per `spec.md` `### The Contract Switcher's chip`. It
needs nothing from the side list: the chip is the phone layout's and the fix is
the same on every page that shows it. Satisfies the frontend DoD in
`docs/agents/frontend.md`.

## Acceptance criteria

- [ ] An Agent with one Contract whose Client name is 60 characters sees the Contract chip truncated with an ellipsis at 390px, and the page has no sideways scroll (`document.documentElement.scrollWidth` equals the window width).
- [ ] The chip of a short name looks as it did, and the multi-Contract dropdown button, its focus handling and `aria-haspopup="listbox"` are unchanged.

## Tests

- **End to end (Playwright, real backend):** an Agent with one Contract whose Client is renamed to 60 characters, at 390px, shows the chip truncated and `scrollWidth` equal to the window width. Prior art: the 390px steps of `send-a-client-invoice-back.spec.ts`. This case lives in a new spec file, `contract-switcher-chip.spec.ts`. No class-asserting component test is added (the seam is not in the spec's testing decisions).
- **Visual:** existing goldens are the guard for short names.

## Regression

At risk: every page that shows the single-Contract chip (Requests, Fleet, invoices, Tester pages) and their visual goldens, which must not change for short names (`tests/visual/surfaces.spec.ts`). No existing test is expected to change; the e2e case goes in the new `contract-switcher-chip.spec.ts`.

## Observability

N/A — a CSS-only change with no data or log effect.
