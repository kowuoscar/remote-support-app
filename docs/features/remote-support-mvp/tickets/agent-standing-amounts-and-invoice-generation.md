---
id: agent-standing-amounts-and-invoice-generation
title: Manager sets standing salary/advance; Agent's monthly invoice assembles
status: in-progress
depends_on: [client-invoice-submission-and-visibility]
labels: [backend, frontend, invoicing, payroll]
---

## Context

Implements the Agent Invoice's Local Support Fees, Salary and Rollout Advance lines, and the next-month-effective rule for standing amounts, from the spec's Solution and user stories 5-6, 25-26.

## Acceptance criteria

- [ ] Manager can update an Agent's standing monthly salary; the change applies starting the invoice for the following month, not the current one
- [ ] Manager can update an Agent's standing Rollout Advance amount, with the same next-month-effective rule
- [ ] Agent's draft monthly invoice's Local Support Fees line equals the sum, across every one of the Agent's Contracts, of that Contract's Client Invoice base amount plus its Fee total for the month
- [ ] The draft invoice's Salary line auto-populates from the Agent's standing salary in effect for that month
- [ ] The draft invoice carries two Rollout Advance lines — repayment of the previous month's advance (negative) and the new advance for next month (positive) — computed from the standing amounts in effect; they net to zero except in the cycle right after a standing-amount change takes effect
- [ ] Only the invoice's own Agent can view/build their draft Agent Invoice

## Tests

Backend HTTP API seam: standing-amount update takes effect only from next month; Local Support Fees total across multiple Contracts including ones whose Client Invoice is still in draft or sent (not gated on approved); Rollout Advance nets to zero in a steady month and shows the one-month delta right after a change; a salary or advance change made mid-month does not affect the current month's invoice.

## Regression

Client Invoice base/Fee amounts from prior tickets are unchanged by this ticket; they are only read here.

## Observability

Standing-amount change events logged with Agent id, old/new value, effective month, actor (Manager).
