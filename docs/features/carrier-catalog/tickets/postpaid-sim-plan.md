---
id: postpaid-sim-plan
title: A new Postpaid SIM's monthly fee comes from its Postpaid Plan
status: ready-for-agent
depends_on: [topup-options-and-postpaid-plans, sim-card-carrier]
labels: [backend, frontend, fleet, invoicing]
---

## Context

A Postpaid SIM names a Postpaid Plan, and its monthly fee is copied from the
Plan's price instead of being typed. It implements `spec.md` Solution (SIM
Card changes for the Plan part, and Why copy the price) and user stories 12,
17-19, 26 (Plan part) and 28.

## Acceptance criteria

- [ ] On every SIM-creation path, a Postpaid SIM requires a Postpaid Plan. The Plan must belong to the chosen Carrier and must not be archived. A Prepaid SIM that names a Plan is refused.
- [ ] The monthly fee is copied from the Plan's price when the SIM Card is created. A free-typed monthly fee is no longer accepted.
- [ ] In every SIM-creation form, a Plan picker appears only for a Postpaid SIM. It lists only active Plans of the chosen Carrier and shows, read-only, the monthly fee the chosen Plan sets.
- [ ] A SIM Card response carries the Plan's id and name, and a flag saying whether it is archived. The Fleet tables show the Plan's name.
- [ ] Changing a Plan's price never changes the monthly fee of a SIM Card already on it, and never changes any Client Invoice total.
- [ ] Existing Postpaid SIMs keep their monthly fee and have no Plan. Their Client Invoices are unchanged.
- [ ] The seeded Postpaid SIMs keep their fees. At least one new seeded Postpaid SIM is on a seeded Plan.

## Tests

- **API seam:**
  - a missing Plan on a Postpaid SIM is refused
  - a Plan on a Prepaid SIM is refused
  - a Plan from another Carrier is refused
  - an archived Plan is refused
  - the fee is copied from the Plan
  - editing the Plan's price leaves the created SIM Card's fee unchanged
  - a draft Client Invoice's base amount uses the SIM Card's copied fee
- **Component seam:** The Plan picker shows only for a Postpaid SIM, is
  filtered by the chosen Carrier, and displays the fee.
- **E2E:** An Agent completes a Provision SIM Request for a Postpaid SIM by
  picking a Carrier and a Plan, and the Fleet shows the Plan and the fee.

## Regression

This puts the Client Invoice base amount and its frozen snapshot at risk
(ADR 0001). The Client Invoice API tests must pass unchanged in their
assertions, and a sent invoice's total must not move when a Plan's price
changes.

## Observability

Add the Plan id and the copied monthly fee to the SIM Card provisioned audit
event.
