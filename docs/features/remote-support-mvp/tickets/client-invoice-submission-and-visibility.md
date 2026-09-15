---
id: client-invoice-submission-and-visibility
title: Send a Client Invoice, make it visible to the Client, and approve it
status: ready-for-agent
depends_on: [client-invoice-generation]
labels: [backend, frontend, invoicing]
---

## Context

Implements the Client Invoice's sent/approved lifecycle, Client-side visibility and on-demand PDF from the spec's Solution and user stories 7-8, 24, 34-35.

## Acceptance criteria

- [ ] Agent can send a draft Client Invoice, moving it to status sent; a sent invoice is no longer editable by the Agent
- [ ] Once sent, every Tester at that Contract's Client can view the Client Invoice read-only
- [ ] A Tester can generate a PDF of the Client Invoice on demand; no PDF is stored at rest
- [ ] Manager can review a sent Client Invoice, including its attached carrier files, and approve it, moving it to status approved
- [ ] A draft Client Invoice is never visible to a Tester
- [ ] Manager cannot approve a Client Invoice still in draft

## Tests

Backend HTTP API seam: send transition and its editability lock; Tester visibility gated on non-draft status; PDF generation from stored data; approve transition and its precondition; a Tester from a different Client cannot view it.

## Regression

Draft-building behaviour from `client-invoice-generation` (base amount, Fee lines, attachments) is unchanged before sending.

## Observability

Status-transition events (sent, approved) logged with Client Invoice id, actor, timestamp.
