---
id: manager-approves-requests
title: Provision and Replace Requests wait for the Company Manager's approval
status: ready-for-agent
depends_on: [replace-requests, sim-swap-moves]
labels: [backend, frontend, requests]
---

## Context

Implements `spec.md` Solution (Lifecycle; Fees and approval; Manager approval) and user stories 16, 17, 20, 22, 23 and 36-42, 44.

## Acceptance criteria

- [ ] A Provision Smartphone, Provision SIM, Replace Smartphone or Replace SIM Request starts at Pending Approval, whether a Tester submitted it or an Agent logged it; an Agent can no longer start one at Submitted or Completed
- [ ] Only the Manager approves (to Submitted) or rejects (to Rejected, reason required); the decision records who and when
- [ ] The Agent sees a Pending Approval Request on their Contract but can only cancel it, with a reason; Rejected is terminal
- [ ] A proactive Fee for one of the four types is refused with a message telling the Agent to log the Request; Topup and Other proactive Fees still work
- [ ] A Fee can't be logged against a Pending Approval, Rejected or Cancelled Request
- [ ] A Manager-only Pending Requests page lists every Pending Approval Request, longest-waiting first, with type, Client, Tester, Agent, requested details (for a Replace, the unit to be retired) and age, and approve and reject controls
- [ ] The Manager's dashboard shows the pending count, linking to the page; the Review Queue is unchanged
- [ ] Tester and Agent Requests lists show Pending Approval and Rejected in the status filter, and a Rejected Request's reason
- [ ] No Request that exists when this ships changes status
- [ ] Seed data holds a Pending Approval and a Rejected Request

## Tests

- **API seam:** start status per type and path; approve, reject without reason (400), reject; Agent and Tester 403 on approve, reject and the pending list; unknown or other-tenant 404; approving a non-pending Request 409; Agent start of a pending Request refused; cancel from pending; proactive Fee refusal; Fee against pending or rejected refused; list ordering.
- **Component seam:** approve and reject controls (reason required, 409 copy, final state), the dashboard card (follow `pending-approvals-card.test.tsx`).
- **E2E:** a Tester submits a Provision SIM → it shows Pending Approval → the Manager approves from Pending Requests → the Agent completes it. A Manager rejects another and the Tester sees the reason.
- **Visual:** goldens for the Pending Requests page per theme × breakpoint; the Manager navigation change is recorded in `DESIGN.md` in the same commit as the dashboard goldens it moves.

## Regression

Every flow that completed a Provision Request directly: the provisioning e2e and API tests now go through approval first. Proactive Provision Fees in existing tests move to the Request path. Review Queue tests must pass unchanged.

## Observability

Audit events for Request approved and rejected: Request id, actor, tenant, and that a reason was given.
