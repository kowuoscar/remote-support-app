---
id: type-scale-tokens
title: Add the 12px and 13px label sizes as type-scale tokens in the theme
status: ready-for-agent
depends_on: []
labels: [enabler, frontend]
stories: []
---

## Context

Enables Frontend rule 8 (as amended) for `client-dashboard-identity`, `client-dashboard-fleet-and-requests` and `client-dashboard-latest-invoices`, whose new lines use these tokens instead of `text-[12px]` / `text-[13px]` (spec `## Solution`, "Small type sizes"; `## Decisions taken`, last two entries). `frontend/app/globals.css`'s `@theme` gains `--text-label-sm` (12px) and `--text-label` (13px), with no line-height of their own, so `text-label-sm` renders exactly as `text-[12px]` does. No existing use is migrated.

## Acceptance criteria

- [ ] `@theme` in `frontend/app/globals.css` declares `--text-label-sm: 12px` and `--text-label: 13px` and no paired `--line-height` for either.
- [ ] An element given `text-label-sm` computes `font-size: 12px` with the same line-height it has under `text-[12px]`, and one given `text-label` computes `13px` likewise (shown in the production build).
- [ ] No existing `text-[12px]` / `text-[13px]` use is changed, and no visual golden moves.

## Tests

- **Visual suite (spec Testing decisions 4):** the full existing suite passes with every golden unmodified. This is the case that proves "no golden moves".
- **Build check:** `next build` succeeds, and a throwaway element using each class (not committed) shows the computed size and line-height in the built CSS. No new test file; the spec adopts no seam for CSS.

## Regression

- At risk: every surface (a theme-wide file), and Tailwind's handling of the existing arbitrary text sizes.
- Existing tests expected to change: none. Every golden in `frontend/tests/visual/__screenshots__/` must be unchanged.

## Observability

N/A — a design token, no runtime behaviour.
