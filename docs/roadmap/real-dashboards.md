---
id: real-dashboards
title: See where things stand on arrival
status: in-progress
journeys: [see-where-things-stand-on-arrival]
---

<!-- sdlc:template epic 1 -->

## Intent

Each role's home page is the first thing they see, and today it shows them
fabricated data. The Agent's and the Client's dashboards come entirely from
`frontend/lib/demo`, including the persona in the page header — a real Agent
is shown another person's name, salary and Rollout Advance. The Manager's
dashboard mixes a real Review Queue and a real pending-request count with
invented money (`billedThisMonthUSD: 41280`, `payoutThisMonthUSD: 22940`).

Decided with the human at init: wire all three to real data and delete the
`frontend/lib/demo` fixtures. Scenario data for manual testing comes from the
`demo` profile loader that `demo-data-story` already built, which seeds a
real database — not from literals compiled into a page.

It runs last of the four because it corrects a surface rather than adding a
capability, and nothing else depends on it.

## Journeys

- **See where things stand on arrival** → `exists`.

The proof that closes this epic: on `main`, against the demo profile's
database, each of the three roles signs in and their dashboard shows their
own name and their own numbers, every figure traceable to a record in the
database.

## Features

- [x] `real-agent-dashboard` — an Agent's home page shows their own name, standing salary and Rollout Advance, this month's Local Support Fees, their open and recent Requests and their own invoice's status, all from the database; `frontend/lib/demo/agent.ts` is deleted.
- [x] `real-client-dashboard` — a Tester's home page shows their Client's name, the signed-in Tester, the Client's active Fleet, open Requests and the latest sent or approved Client Invoice per Contract; `frontend/lib/demo/client.ts` is deleted.
- [ ] `real-manager-dashboard` — the Manager's home page shows the signed-in Manager, real Client, Agent and Contract counts, and real billed-this-month and payout-this-month totals from a new Tenant-wide aggregate; the rest of `frontend/lib/demo` is deleted.

## Reworked

The exploration (2026-09-30) cut the epic by role, and ordered it by harm and
by cost rather than by how close each page already is to real.

**The Agent's page goes first because it is the one that is wrong about a
person.** Every figure on it, header included, comes from
`frontend/lib/demo/agent.ts`, so a real Agent is shown another person's name,
salary and Rollout Advance. Almost everything it needs is already served:
`/api/me` carries the `agentId`, and
`AgentStandingAmountController.java:53-62` already returns the standing salary
and advance. The rest are sums and filters over existing per-Contract routes.

**The Client's page second**, for the same reason and at a similar cost. Its
"current Tester" is a demo literal (`frontend/lib/demo/client.ts:7`); on a
real page it is simply the signed-in Tester, so it raises no question.

**The Manager's page goes last although it is the closest to real.** Its
Review Queue and pending-request counts are already live
(`frontend/app/manager/page.tsx:17-40`), and its counts are one list call each.
But its two money figures, `billedThisMonthUSD` and `payoutThisMonthUSD`
(`frontend/lib/demo/manager.ts:6-7`), have no endpoint and no definition yet:
whether "billed" means frozen Client Invoice totals or Fees logged, which
month, and which statuses count. That is a *what* for that feature's spec,
not for this cut. It also depends on the invoice lifecycle, which
`invoice-correction-and-history` is changing, so putting it last lets it see
that epic's answer.

**Each feature deletes its own fixture file.** The last one deletes
`frontend/lib/demo/types.ts`, whose invoice-status types are still imported
by `frontend/lib/status.ts` and `frontend/components/agent/dashboard-stats.tsx`.
Moving those types is a prefactor inside `real-manager-dashboard`.

**The visual goldens are in the way of every feature.** `tests/visual/surfaces.spec.ts`
renders the dashboards without a backend, masking the Manager's live regions
(`:95-102`). Once a page reads real data, it needs a stubbed backend or wider
masks. Two recorded debts bite here: relative request ages drift against the
wall clock (`docs/tech-debt.md:22`), and `maxDiffPixelRatio` hides small real
changes (`docs/tech-debt.md:23`). `real-agent-dashboard` pays the clock debt,
because its Recent Requests card is where the drift shows.

## Later

- Any dashboard figure that turns out to need a new aggregate beyond the
  Manager's billed-this-month and payout-this-month.

