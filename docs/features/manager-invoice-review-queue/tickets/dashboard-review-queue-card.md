---
id: dashboard-review-queue-card
title: Show the real Review Queue on the Manager Dashboard
status: ready-for-agent
depends_on: [client-invoice-review-page]
labels: [frontend, invoicing]
---

## Context

Replaces the Dashboard's sample "Pending approvals" data with the Review Queue. Implements spec.md Solution (Dashboard), user story 25, and the Constraints entry on the card's size and unavailable state.

## Acceptance criteria

- [ ] The Pending approvals card and its count stat show the real Review Queue size
- [ ] The card lists at most the 4 longest-waiting items, each linking to its detail page
- [ ] When nothing is waiting, the card says so
- [ ] If the Review Queue can't be loaded, only the card shows an unavailable state; the rest of the Dashboard renders
- [ ] The Dashboard's other figures are unchanged
- [ ] The card region is masked in the manager visual goldens, re-baselined in the same commit, and the visual suite still runs without a backend

## Tests

- **Component seam:** card renders up to 4 items with correct links, the empty message, and the unavailable state.
- **E2E:** after an Agent sends an invoice, a Manager clicks its row on the Dashboard card and lands on its detail page.
- **Visual:** manager goldens pass with the card masked.

## Regression

Manager Dashboard goldens (desktop/mobile × light/dark) — protected by `tests/visual/surfaces.spec.ts`; any change other than the masked card region is a failure.

## Observability

A failed Review Queue load on the Dashboard is logged server-side with the response status.
