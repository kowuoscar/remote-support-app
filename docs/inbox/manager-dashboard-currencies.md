---
id: manager-dashboard-currencies
type: question
status: open
blocks: [real-manager-dashboard]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

Contracts are in **different currencies**: each follows its Agent's country (the demo has USD and GBP). The app holds no exchange rates. How should the money cards show this?

**Recommendation:** **One total per currency** on the same card, for example $135.99 above £20.00, with no conversion. Converting would need a live exchange-rate service, which would be its own feature.

Spec: `docs/features/real-manager-dashboard/spec.md`, Open questions 3.

## Blocks

`real-manager-dashboard`.

## Meanwhile

The loop works on whatever else is unblocked.

## Answer

