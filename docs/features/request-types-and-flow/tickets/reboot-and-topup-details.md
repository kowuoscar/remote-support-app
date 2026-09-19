---
id: reboot-and-topup-details
title: Reboot and Topup Requests name their unit, and a Topup its Topup Option
status: done
depends_on: [other-replaces-repair]
labels: [backend, frontend, requests]
---

## Context

First per-type details slice; it also sets the structure the later ones reuse. Implements `spec.md` Solution (Details at submission for Reboot and Topup; Catalog for Testers; the Topup part of Fees and approval) and user stories 1-5, 15, 19, 21 and 32.

## Acceptance criteria

- [x] A Reboot Request requires an Active Smartphone of its Contract; a Topup Request requires an Active SIM Card of its Contract
- [x] A Topup Request requires a Topup Option of that SIM Card's Carrier when the Carrier has an active one; otherwise it requires a description
- [x] The same rules apply when an Agent logs the Request proactively
- [x] Anyone who can view a Contract can read the active Carrier catalog of that Contract's Country through a Contract-scoped read; the Country-scoped routes stay Agent and Manager only
- [x] The Tester's submit dialog and the Agent's log-Request dialog show a details section that changes with the type, built as one component per type plus shared unit pickers
- [x] The Tester's and the Agent's Requests lists summarise the details on each row
- [x] Completing a Topup Request pre-fills the Fee amount from its Topup Option and links the Fee to it; the amount stays editable
- [x] One module validates type-specific details for both the Tester and the Agent path
- [x] A Request created before this ticket, with no details, still lists and completes as before

## Tests

- **API seam:** each rule on both paths; unit of another Contract, retired unit, Option of another Carrier, archived Option refused; Carrier without Options needs a description; the Contract-scoped catalog read per role and tenant; the Topup Fee carries the Request's Option; a legacy Request completes.
- **Component seam:** the details section per type; the Option picker follows the chosen SIM Card's Carrier.
- **E2E:** a Tester submits a Topup with an Option; the Agent completes it and the Fee shows the Option's amount.

## Regression

Request submission and completion, Topup Fee from Option. `RequestApiTest`, `TopupFeeFromOptionApiTest`, `tester-request-submission.spec.ts` and `agent-request-fulfillment.spec.ts` must pass, changed only to supply the new required details.

## Observability

The Request submitted and logged audit events carry the target unit id and the Topup Option id.
