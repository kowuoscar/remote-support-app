---
id: design-system
title: Commit the visual world and its guardrails
status: done
depends_on: []
labels: [frontend, design-system]
---

## Context

Implements the spec's `## Design direction`: pins the Stripe-inspired world and stands up the Next.js App Router app shell before any UI ticket is built. The first frontend ticket, always — every other frontend ticket depends on it.

## Acceptance criteria

- [ ] A Next.js (App Router) app exists under `/frontend`
- [ ] `PRODUCT.md` records durable product context (`impeccable init`)
- [ ] `DESIGN.md` and its sidecar commit the visual world, pinned against Stripe (`impeccable new-work`)
- [ ] A surface brief exists for each of the three surfaces (Manager Console, Agent Console, Client Portal), each naming its mode: Operate
- [ ] The design detector hook is on (`/impeccable hooks on`)
- [ ] A golden baseline is committed for each surface × theme × breakpoint
- [ ] Fonts are self-hosted

## Tests

`impeccable audit` passes. The golden baseline renders identically on a second run.

## Regression

N/A — nothing shipped yet.

## Observability

N/A — no runtime behaviour.
