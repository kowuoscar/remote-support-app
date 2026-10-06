---
id: adjustment-carry-over-automatic
type: question
status: open
blocks: [invoice-adjustment]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

When an Agent edits a Client Invoice line **after** that month's Agent Invoice was approved (e.g. $30 → $45), is the +$15 recorded **automatically** as a pending adjustment to their next pay, or left for the **Manager** to record by hand?

**Recommendation:** Automatic, marked "Carry-over", pending on the Agent's next invoice, and withdrawable by the Manager before it's sent. Done by hand, it's easily forgotten.

Spec: `docs/features/invoice-adjustment/spec.md`, Open questions 5.

## Blocks

`invoice-adjustment` (no tickets until all six are answered).

## Meanwhile

The loop works on whatever else is unblocked.

## Answer

