---
id: adjustment-which-month
type: question
status: open
blocks: [invoice-adjustment]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

Does an adjustment always land on the **next invoice still being prepared**, or does the Manager **pick the month**?

Example: an error found on 3 October lands on October's invoice, or on November's if October's was already sent.

**Recommendation:** Always automatic: the next invoice of that Contract (or Agent) that's sent for the first time. A month the Manager picks could already be closed.

Spec: `docs/features/invoice-adjustment/spec.md`, Open questions 4.

## Blocks

`invoice-adjustment` (no tickets until all six are answered).

## Meanwhile

The loop works on whatever else is unblocked.

## Answer

