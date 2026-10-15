---
id: fleet-corrections
title: Correct a Smartphone or SIM Card already in the Fleet
status: in-progress
journeys: [put-resources-in-the-field]
---

<!-- sdlc:template epic 1 -->

## Intent

Raised by the human on 2026-10-14 while testing a fresh, production-like stack
as a Manager and as a Tester. Today, once a Smartphone or SIM Card is added to
a Contract's Fleet, almost nothing about it can be changed by anyone: only a
Smartphone's serial and a SIM Card's "Installed in" can be. A mistyped SIM
number has no fix in the product.

What the human asked for:

- **SIM Card**: a Manager can correct a mistyped **number** and the
  **Postpaid plan**.
- **Smartphone**: a Manager can correct a mistyped **model** and the
  **owner**.
- **A Tester can do the same corrections.** This changes the Tester's role:
  `PRODUCT.md` today says a Tester only *views* their Client's Fleet and asks
  for changes through Requests. The spec updates that line.

Found at the same time, not asked for but on the same page: the backend
already lets a Manager change a unit's **status**
(`FleetAccessGuard.requireCanChangeStatus`), but the Manager's Contract page
shows no status control — only the Agent's Fleet page does
(`frontend/components/manager/contract-fleet-view.tsx`).

Written down as `proposed` while implementation was paused for hands-on
testing; placed first in the epic order by the human the same day.

## Questions for the spec gate

- A Postpaid plan change moves the SIM's monthly fee, which feeds the
  Contract's base amount on the Client Invoice. What happens to the current
  month's invoice once sent or approved — and to the Agent Invoice?
- Which Testers may correct: any Tester of the Client, or only the Primary
  Contact?
- Is a correction audit-logged with the old and new value, like the serial
  change is?
- Does the Manager's Contract page gain the status control too?
- Can the carrier, or Postpaid ↔ Prepaid, be corrected as well? Not asked
  for; the human named number and plan only.

## Journeys

- **Put resources in the field** → `exists`. This epic extends it: the Fleet
  can be corrected after it is built, not only built.

## Features

- [x] `correct-a-fleet-unit` — a Manager or a Tester corrects a SIM Card's number and Postpaid plan, and a Smartphone's model and owner.
- [x] `agent-corrects-the-fleet` — the corrections move from the Tester to the Agent: an Agent corrects the four things on their own Contracts' Fleet; a Tester only sees them (acceptance feedback, 2026-10-15).

## Reworked

The two feature lines first written (`correct-a-sim-card`,
`correct-a-smartphone`) are one feature: the same callers, the same guard and
the same Fleet tables, and the human wants one PR per fix (2026-10-14).
