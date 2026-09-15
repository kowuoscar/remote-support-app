---
id: agent-invoice-submission-and-approval
title: Agent sends their monthly invoice; Manager overrides, approves and pays
status: ready-for-agent
depends_on: [agent-standing-amounts-and-invoice-generation]
labels: [backend, frontend, invoicing, payroll]
---

## Context

Implements the Agent Invoice's sent/approved/paid lifecycle and the Manager's per-invoice override, from the spec's Solution and user stories 9-12, 27-28.

## Acceptance criteria

- [ ] Agent can send their draft Agent Invoice, moving it to status sent; it is no longer editable by the Agent
- [ ] Manager can view a sent Agent Invoice with all its lines (Local Support Fees, Salary, Rollout Advance repayment and new advance)
- [ ] Manager can override the Salary or Rollout Advance value on that one invoice at approval time, without changing the Agent's standing amount used by future invoices
- [ ] Manager can approve a sent Agent Invoice, moving it to status approved
- [ ] Manager can mark an approved Agent Invoice as paid, moving it to status paid; no payment is executed by the app
- [ ] A Client cannot view any Agent Invoice
- [ ] Agent can see the status history (draft/sent/approved/paid) of their own invoices

## Tests

Backend HTTP API seam: send transition and lock; per-invoice override changes only that invoice's total, not the Agent's standing amount or other invoices; approve and paid transitions and their preconditions (cannot approve a draft, cannot pay before approved); Client role rejected on any Agent Invoice endpoint.

## Regression

Standing-amount behaviour from `agent-standing-amounts-and-invoice-generation` (next-month-effective rule) is unaffected by a per-invoice override.

## Observability

Status-transition and override events logged with Agent Invoice id, actor, old/new value where applicable.
