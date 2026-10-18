---
id: approve-carrier-invoice-retention
type: approval
status: open
blocks: [carrier-invoice-retention]
created: 2026-10-18
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for deleting carrier invoice files 30 days after approval?
`docs/features/carrier-invoice-retention/spec.md` (user stories, non-goals,
decisions taken, walkthrough).

In short:

- Each Carrier Invoice File's bytes are deleted from the object store 30 days
  after its Client Invoice's approval, by a daily job at 03:00 UTC plus one
  run about a minute after startup. Files on invoices that aren't approved
  are never deleted, however old.
- The row stays: the file is still listed by name with "Deleted on {date}"
  where Download was, for the Agent, the Manager and the Tester. Each SIM
  line keeps its check mark. An old download link answers 410 Gone.
- A store outage during a run deletes nothing, logs one ERROR, and the next
  run retries. The job can be switched off or moved through the environment.
- Decisions to veto if you disagree: the 30 days count from the approval
  that currently stands (a send-back stops the count, the next approval
  restarts it); no "Kept until" (your answer); no restore, no Manager
  override, no per-Client period; two tickets (backend job, then the
  frontend's deleted row).

## Recommendation

Approve.

## Blocks

`carrier-invoice-retention`.

## Meanwhile

Nothing else in this epic is waiting; `carrier-invoice-per-sim` awaits your
walkthrough step on `rsa-prod`.

## Answer
