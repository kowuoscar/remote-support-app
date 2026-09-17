---
id: component-test-harness
title: Stand up the frontend component test seam on the invoice action controls
status: in-progress
depends_on: []
labels: [frontend, testing]
---

## Context

Prefactor for this feature: spec.md's Testing decisions add a component-test seam (Vitest + jsdom + Testing Library) the repo doesn't have yet. It lands first, on controls that already exist, so every later ticket extends the seam rather than inventing it.

## Acceptance criteria

- [ ] `npm test` in the frontend runs Vitest with jsdom and Testing Library, and exits non-zero on a failing test
- [ ] Component tests exist for the existing Manager approve (Client Invoice and Agent Invoice), override and mark-paid controls: confirm step where the control has one, pending state while the request is in flight, inline error on a 409 and on a 500 response
- [ ] The tests mock `fetch` and Next.js navigation/refresh — no backend and no browser required
- [ ] The frontend README documents how to run component tests next to the existing visual and e2e suites
- [ ] Lint and type checks cover the test files

## Tests

The acceptance criteria are themselves tests at the component seam (spec.md Testing decisions, seam 3). Also verify a deliberately broken assertion fails `npm test`, then revert it.

## Regression

N/A — adds test tooling only; no shipped behaviour changes. `npm run lint`, `npm run build` and the visual suite must still pass with the new dev dependencies installed.

## Observability

N/A — test tooling, no runtime behaviour.
