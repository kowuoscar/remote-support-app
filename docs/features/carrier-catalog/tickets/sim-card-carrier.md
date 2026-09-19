---
id: sim-card-carrier
title: Every new SIM Card names a Carrier from the catalog
status: done
depends_on: [agent-maintains-carriers]
labels: [backend, frontend, fleet]
---

## Context

This replaces a SIM Card's free-text carrier with a reference to a Carrier,
on every SIM-creation path, and migrates existing SIM Cards. It implements
`spec.md` Solution (SIM Card changes for the Carrier part, and Migration) and
user stories 17 (Carrier part), 23, 26 (Carrier part), 27, 29 and 31.

## Acceptance criteria

- [x] Creating a SIM Card requires a Carrier. The Carrier must belong to the Contract's Agent's Country and must not be archived. This holds on every path:
  - the Manager's create-SIM dialog on the Contract Fleet page
  - completing a Provision SIM Request
  - an Agent logging a proactive Provision SIM Request that starts at Completed
  - an Agent logging a proactive Provision SIM Fee
- [x] Each of those forms shows a Carrier picker, listing active Carriers only, in place of the free-text carrier input.
- [x] A SIM Card response carries the Carrier's id and name, and a flag saying whether it is archived.
- [x] Every Fleet table (Agent, Manager, Tester) shows the Carrier's name, marks an archived Carrier as archived, and shows "—" for a SIM Card with no Carrier.
- [x] The migration handles existing SIM Cards:
  - For each Country, it creates one Carrier per distinct free-text carrier name, grouped case-insensitively after trimming and keeping the first spelling found. A SIM Card's Country is its Contract's Agent's Country.
  - It links every SIM Card to its Carrier.
  - SIM Cards with no carrier stay unlinked.
  - If an active Carrier with that name (ignoring case, after trimming) already exists in the Country, it is reused instead of a new one being created. For example, the seeded US "Verizon" must be reused for the V17 demo SIM Card's "Verizon".
  - It then drops the free-text carrier column.
- [x] The migration changes no SIM Card's monthly fee and no Client Invoice total.
- [x] The seeded SIM Cards are linked to the seeded Carriers.

## Tests

- **API seam:**
  - each creation path refuses a missing Carrier, another Country's Carrier
    and an archived Carrier, and accepts a valid one
  - the response carries the Carrier's name
  - the migration's grouping and linking, run against rows that differ only
    in case or whitespace, rows with an empty carrier, and two Countries using
    the same carrier name
- **E2E:** Extend `fee-logging-and-provisioning.spec.ts`: an Agent completes a
  Provision SIM Request by picking a Carrier, and the Fleet shows its name.
  The Manager creates a SIM Card from the Carrier picker.

## Regression

This puts SIM provisioning and the Client Invoice base amount at risk.
`RequestApiTest`, `FeeApiTest`, the provisioning e2e and the Client Invoice API
tests must pass, updated only to supply a Carrier. Totals must be identical
before and after the migration.

## Observability

Add the Carrier id to the existing SIM Card provisioned audit event. The
migration logs how many Carriers it created and how many SIM Cards it linked.
