---
id: adjustment-agent-pay-or-client-bill
type: question
status: open
blocks: [invoice-adjustment]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

An adjustment corrects a past invoice on next month's invoice. Can it correct the **Agent's pay** as well as the **Client's bill**, and does correcting one move the other?

Example: a SIM was billed to the Client at $50 instead of $30. A $20 credit to the Client: should the Agent's pay drop $20 too? But a $15 charge for a Topup the Agent forgot to bill must not pay the Agent twice, since they were already paid for it.

**Recommendation:** Both kinds exist and each moves only its own side. When both are wrong, the Manager records one of each.

Spec: `docs/features/invoice-adjustment/spec.md`, Open questions 1.

## Blocks

`invoice-adjustment` (no tickets until all six are answered).

## Meanwhile

The loop works on whatever else is unblocked.

## Answer

