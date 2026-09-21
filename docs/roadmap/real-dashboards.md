---
id: real-dashboards
title: See where things stand on arrival
status: planned
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

## Reworked

## Later

- Any dashboard figure that turns out to need a new aggregate beyond the
  Manager's billed-this-month and payout-this-month.

