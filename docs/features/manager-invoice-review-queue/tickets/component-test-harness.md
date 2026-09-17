---
id: component-test-harness
title: Stand up the frontend component test seam on the invoice action controls
status: done
depends_on: []
labels: [frontend, testing]
---

## Context

Prefactor for this feature: spec.md's Testing decisions add a component-test seam (Vitest + jsdom + Testing Library) the repo doesn't have yet. It lands first, on controls that already exist, so every later ticket extends the seam rather than inventing it.

What the existing controls lack, recorded rather than added here (this ticket ships no UI behaviour; the review-page tickets add what they need):

- **No confirm step on any of the four.** Approve (Client Invoice and Agent Invoice) and Mark paid act on a single click by design (see each control's doc comment); an override submits its field's form directly. The tests pin the single-click behaviour instead.
- **No 409-specific copy.** Every control shows the same generic inline error for a 409 and a 500 ("Couldn't approve. Try again.", "Couldn't mark as paid. Try again.", "Couldn't save. Try again."); only the override distinguishes a 400. The tests assert that shared message for both statuses.

Pending state (button, and the override's amount field, disabled while the request is in flight) and the inline error both exist on all four and are covered.

## Acceptance criteria

- [x] `npm test` in the frontend runs Vitest with jsdom and Testing Library, and exits non-zero on a failing test
- [x] Component tests exist for the existing Manager approve (Client Invoice and Agent Invoice), override and mark-paid controls: confirm step where the control has one, pending state while the request is in flight, inline error on a 409 and on a 500 response
- [x] The tests mock `fetch` and Next.js navigation/refresh — no backend and no browser required
- [x] The frontend README documents how to run component tests next to the existing visual and e2e suites
- [x] Lint and type checks cover the test files

## Tests

The acceptance criteria are themselves tests at the component seam (spec.md Testing decisions, seam 3). Also verify a deliberately broken assertion fails `npm test`, then revert it.

## Regression

N/A — adds test tooling only; no shipped behaviour changes. `npm run lint`, `npm run build` and the visual suite must still pass with the new dev dependencies installed.

## Observability

N/A — test tooling, no runtime behaviour.
