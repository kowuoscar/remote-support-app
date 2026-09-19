---
id: other-replaces-repair
title: Other replaces Repair, and every Request can carry a description
status: done
depends_on: []
labels: [backend, frontend, requests, invoicing]
---

## Context

First slice of the type set. Implements `spec.md` Solution (Types; the description part of Details at submission) and user stories 13, 14, 24 and 45. Replace types arrive in `replace-requests`.

## Acceptance criteria

- [x] The Request types offered to Testers and Agents no longer include Repair and include Other
- [x] Every Request accepts an optional description; an Other Request is refused without one
- [x] The description shows on the Request in the Tester's and the Agent's Requests lists
- [x] A Fee can be logged for Other, against a Request or proactively; Repair is no longer a Fee type
- [x] A migration turns every Repair Request and Repair Fee into Other, setting the description "Repair" on a Request that had none
- [x] The migration changes no Fee amount, billing month or Client or Agent Invoice total, draft or frozen
- [x] Seed data holds at least one Other Request with a description

## Tests

- **API seam:** Other without a description refused on the Tester and the Agent path; description round-trips on every type; Other Fee proactive and against a Request; Repair rejected as a type; the migration run against a database holding Repair Requests, Repair Fees and a sent Client Invoice, with totals identical before and after (follow `SimCardCarrierMigrationTest`).
- **Component seam:** the description field is required only for Other.
- **E2E:** a Tester submits an Other Request with a description and the Agent sees it.

## Regression

Request submission, Fee logging and both invoice totals. `RequestApiTest`, `FeeApiTest`, `ClientInvoiceApiTest` and `AgentInvoiceApiTest` must pass, changed only where they named Repair.

## Observability

The Request submitted and logged audit events carry whether a description was given (never its text).
