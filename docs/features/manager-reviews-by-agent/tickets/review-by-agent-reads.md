---
id: review-by-agent-reads
title: Read the Agents with their waiting counts and an Agent's month for review
status: ready-for-agent
depends_on: []
labels: [backend]
stories: [31]
---

## Context

`spec.md` `### The model: derived, no entity`, `### Reads: ReviewByAgentController`, `## Constraints`, `## Testing decisions` item 1 (`ReviewByAgentApiTest`). Prefactoring note: the new reads are projection queries with totals summed in the query (as `findQueueRows`), not entity reads. No migration; new classes go in the layer packages `web`, `dto`, `repository` (ADR 0006).

Demoable through the API alone: three Manager-only reads under `/api/review-queue/**` (the Agents with `waiting`, an Agent's months, an Agent's month with `reviewStatus`). Includes the widened `/api/review-queue/**` matcher in `SecurityConfig` and the repository methods `countSentByAgent`, `findReviewMonthRows`, `findReviewRows`, `countWaitingByAgent` and the non-DRAFT Agent Invoice month lookup. It does not touch `ClientInvoiceService`.

Enables `agents-side-list-on-invoices`. Runs in parallel with `approve-a-month-api`.

Harness declarations for the result (the merger applies them): `ARCHITECTURE.md` entries for `ReviewByAgentController`, the new DTO records and repository methods, and the ADR 0006 frozen-list entries for the new classes.

## Acceptance criteria

- [ ] As a Manager, `GET /api/review-queue/agents` lists every Agent of the tenant in `lower(name)` then id order with `waiting` = their `SENT` Client Invoices plus their `SENT`/`APPROVED` Agent Invoice (0 for none); another tenant's Agents are absent and the sum of `waiting` equals the length of `GET /api/review-queue`.
- [ ] `GET /api/review-queue/agents/{agentId}/months` lists months newest first with `waiting` per month, including a month holding only a non-DRAFT Agent Invoice and excluding a month holding only never-sent drafts.
- [ ] `GET /api/review-queue/agents/{agentId}/months/{YYYY-MM}` serves `monthSentAt`, the Client Invoices that reached the Manager ordered by `lower(clientName)` then id with `reviewStatus` `TO_REVIEW` (SENT), `APPROVED`, `SENT_BACK` (a sent-back DRAFT), `totalAmount` from the stored lines, a late never-sent draft absent, and `agentInvoice` null while the Agent Invoice is DRAFT and `{id, status, currency, totalAmount}` once sent.
- [ ] The three reads answer 404 for an unknown or other-tenant Agent, 400 for a malformed month (`2026-13`), 403 for an Agent and for a Tester, and create or change no invoice (the `client_invoices` row count is unchanged after them).

## Tests

Seam per spec `## Testing decisions` item 1 (HTTP seam with real Postgres through `IntegrationTest`, a fresh Agent per case with `ClientInvoiceMonthTestSupport.sendTheMonth`).

- **`ReviewByAgentApiTest` (new, `IntegrationTest`):** the Agents list (order, `waiting` counting SENT Client Invoices and a SENT/APPROVED Agent Invoice, 0 for an Agent with nothing, another tenant absent, sum equals the queue length); months (newest first, `waiting`, Agent-Invoice-only month listed, never-sent draft not); the month (Client name order, `reviewStatus` for SENT, APPROVED and a sent-back draft, totals from stored lines, late draft absent, `monthSentAt`, `agentInvoice` null then present); 404 unknown and other-tenant Agent; 400 `2026-13`; 403 for an Agent and a Tester on all three; no invoice created by any read.

## Regression

At risk: `GET /api/review-queue` and its type filter (the matcher widens to `/api/review-queue/**`), Manager-only access for the existing route. `ReviewQueueApiTest` protects these and is expected to pass unmodified. No existing test file changes.

## Observability

N/A: read-only endpoints that write and log nothing.
