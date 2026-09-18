---
id: topup-fee-from-option
title: An Agent logs a Topup Fee from a Topup Option
status: ready-for-agent
depends_on: [topup-options-and-postpaid-plans]
labels: [backend, frontend, invoicing]
---

## Context

A Topup Fee can reference the Topup Option it was bought from. The option
pre-fills the amount, and the Agent can adjust it. This implements `spec.md`
Solution (Fee changes) and user stories 20-23.

## Acceptance criteria

- [ ] For a Topup Fee, the log-Fee dialog shows an optional Topup Option picker. It lists the active Options of the active Carriers in the Contract's Country, labelled by Carrier and Option name.
- [ ] Picking an Option pre-fills the amount with its price. The amount stays editable and required.
- [ ] A Topup Fee can be logged with no Option, as it can today.
- [ ] A saved Fee keeps a reference to its Option, and its amount is the one the Agent submitted.
- [ ] An Option is refused in each of these cases:
  - on a Fee that isn't a Topup Fee
  - when it belongs to another Country's Carrier
  - when the Option, or its Carrier, is archived
- [ ] Editing an Option's price never changes any Fee that already exists.
- [ ] The seed data includes at least one Topup Fee linked to an Option.

## Tests

- **API seam:**
  - a Fee with an Option and an adjusted amount keeps both
  - a Fee with no Option still works
  - the refusal cases above
  - editing the Option's price leaves the Fee unchanged
- **Component seam:** Picking an Option pre-fills the amount, and the picker
  is hidden for Fee types other than Topup.
- **E2E:** An Agent logs a Topup Fee by picking an Option and adjusting the
  amount, and the Fee shows the adjusted amount.

## Regression

This puts Fee logging and the Client and Agent Invoice Fee totals at risk.
`FeeApiTest` and `fee-logging-and-provisioning.spec.ts` must pass unchanged.

## Observability

Add the Topup Option id, when there is one, to the existing Fee logged audit
event.
