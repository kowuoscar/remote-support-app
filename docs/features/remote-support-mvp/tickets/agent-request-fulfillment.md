---
id: agent-request-fulfillment
title: Agent progresses a Request and logs proactive Requests
status: done
depends_on: [tester-request-submission]
labels: [backend, frontend, requests]
---

## Context

Implements the Request status lifecycle and proactive logging from the spec's Solution and user stories 17-18.

## Acceptance criteria

- [ ] Agent can move a Request from Submitted to In Progress, and from In Progress to Completed
- [ ] Agent can cancel a Request (to Cancelled) with a reason
- [ ] Agent can log a Request directly (on a Tester's behalf) for one of their own Contracts, starting at Submitted or immediately at Completed
- [ ] Only the Contract's Agent can change that Request's status; a Tester cannot change status

## Tests

Backend HTTP API seam: valid status transitions; invalid transitions rejected (e.g. Completed → In Progress); Agent-authored Request creation; a Tester attempting a status change is rejected.

## Regression

Tester-visible Request list from `tester-request-submission` still shows Agent-authored Requests and reflects status changes.

## Observability

Status-transition events logged with Request id, old/new status, actor.
