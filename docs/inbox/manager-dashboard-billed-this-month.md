---
id: manager-dashboard-billed-this-month
type: question
status: open
blocks: [real-manager-dashboard]
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

What should the Manager's **"Billed this month"** card count?

- **Which month:** the current billing month, or the last completed one?
- **Which Client Invoices:** sent, approved, or both? Do drafts count, including ones sent back for correction?

On the demo data today, "sent or approved this month" reads **$135.99** (the one invoice Solstice's US Contract has sent).

**Recommendation:** This month's Client Invoices that are **sent or approved**. Drafts never count, because a draft's total is still a live estimate, and sending is when the Client is billed.

Spec: `docs/features/real-manager-dashboard/spec.md`, Open questions 1.

## Blocks

`real-manager-dashboard`.

## Meanwhile

The loop works on whatever else is unblocked.

## Answer

