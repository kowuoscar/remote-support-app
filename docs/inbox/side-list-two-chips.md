---
id: side-list-two-chips
type: question
status: open
blocks: [client-invoices-layout-fixes]
created: 2026-10-15
---

<!-- sdlc:template inbox-item 1 -->

## Question

When a side list row has both its month's status (e.g. "Awaiting approval") and the "Sent back" mark, where does the mark go? Both chips together are wider than the 256px column's row, so they cannot both sit on the name's line. See docs/features/client-invoices-layout-fixes/spec.md, Open questions 1.

## Recommendation

The status stays on the name's line, on the right; the "Sent back" mark goes on a second line, right-aligned under it. *Reason:* every row's status sits in the same place down the column, and the name keeps some room. Alternatives: the mark on line 1 and the status below; or an icon-only mark beside the status.

## Blocks

Ticket `side-list-row-one-line`.

## Meanwhile

The other questions of this spec gate; nothing is built until the specs are approved.

## Answer
