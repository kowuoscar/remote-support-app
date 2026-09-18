---
id: provision-request-details
title: Provision Requests carry what to provision, and complete from it
status: ready-for-agent
depends_on: [sim-installed-in-smartphone, reboot-and-topup-details]
labels: [backend, frontend, requests, fleet]
---

## Context

Implements `spec.md` Solution (Details at submission and Fleet changes on completion, for Provision Smartphone and Provision SIM; the legacy fallback) and user stories 8-10, 25, 27, 28 and 33.

## Acceptance criteria

- [ ] A Provision Smartphone Request requires a requested model
- [ ] A Provision SIM Request requires a flavor and an active Carrier of the Contract's Country, plus an active Postpaid Plan of that Carrier when postpaid; it may name a target Smartphone, Active and in the Contract
- [ ] A Provision Request no longer accepts a unit to replace; historical ones keep theirs
- [ ] Completing a Provision Smartphone needs no Agent input and adds a company-owned Smartphone with the requested model and no serial
- [ ] Completing a Provision SIM asks only for the SIM number; Carrier, flavor, Plan and monthly fee come from the Request, even if the Carrier or Plan was archived after submission
- [ ] The new SIM Card is installed in the target Smartphone when one was named and it has room; otherwise it is added uninstalled and the Agent is told
- [ ] A Provision Request created before this ticket completes through the previous full form
- [ ] An Agent logging one proactively gives the same details; one that starts at Completed also gives the SIM number

## Tests

- **API seam:** each rule on both paths; completion effects; archived-after-submission still completes; target Smartphone full → added uninstalled; legacy fallback; proactive at Completed.
- **Component seam:** the Provision SIM details section (Plan only for postpaid, follows the Carrier).
- **E2E:** extend `fee-logging-and-provisioning.spec.ts`: a Tester submits a Provision SIM with Carrier, Plan and target Smartphone; the Agent completes it with a number; the Fleet shows it installed with the Plan's fee.

## Regression

SIM creation rules and Client Invoice base amount. `SimCardCarrierApiTest`, `PostpaidSimPlanApiTest`, `ClientInvoiceApiTest` and the provisioning e2e must pass, changed only to supply details.

## Observability

The provisioned audit events carry the Request id (already) and, for a SIM Card, the Smartphone it was installed in.
