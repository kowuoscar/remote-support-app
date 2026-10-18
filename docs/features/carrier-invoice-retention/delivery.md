# Delivery report — carrier-invoice-retention

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

The third and last feature of the epic `carrier-invoices-per-sim`. Merged as
PR #34 (`71e2b6d`): two tickets and one fix pass.

- **Files are deleted 30 days after approval (stories 7–17, 19, 20).** A
  daily backend job at 03:00 UTC, plus one run about a minute after each
  startup, deletes the bytes of every Carrier Invoice File whose Client
  Invoice has been approved for 30 days. The 30 days count from the approval
  that currently stands. Files on drafts, sent and sent-back invoices are
  never deleted, however old. The row stays with its name, type, size and
  upload time, and gains `deleted_at` (migration V62). A run deletes at most
  500 files, oldest approval first. It logs nothing when nothing is due and one
  INFO line when it deleted something. When storage is down, a run deletes
  nothing, logs one ERROR and one WARN, and the next run retries. An old
  download link answers 410 Gone. `CARRIER_INVOICE_RETENTION_ENABLED=false`
  switches the job off, and `CARRIER_INVOICE_RETENTION_CRON` moves it. Ticket:
  `carrier-invoice-files-deleted-after-retention`.
- **The pages say so (stories 1–6, 18).** On the Agent's card, the Manager's
  invoice page and the Tester's Invoices page, a deleted file stays listed by
  name with "Deleted on {date}" where Download was. Each SIM line keeps its
  check mark, and a deleted file still counts for the send rule, so a
  sent-back invoice is never blocked by its own deleted files. The Agent can
  still remove a deleted file on an open draft. The "File storage is
  unavailable" notice shows only when something is really paused. Ticket:
  `carrier-invoice-files-show-deleted-on`.

## Acceptance walkthrough

Played by an agent on its own throwaway stack, never on yours.

1. `verify` green, visual goldens unchanged — played — evidence: `evidence/step-1-verify.txt`
2. Two invoices attached, sent, approved and downloaded; a restart deletes nothing — played — evidence: `evidence/step-2-restart.txt`
3. Approved 30 days and 1 hour ago: deleted, rows kept; 29 days 23 hours: kept — played — evidence: `evidence/step-3-after.txt`
4. Agent, Manager and Tester see "Deleted on {date}", check marks kept — played — evidence: `evidence/step-4.txt`
5. An old download link answers 410 `CARRIER_INVOICE_FILE_DELETED` — played — evidence: `evidence/step-5.txt`
6. Old drafts, sent and sent-back invoices never deleted — played — evidence: `evidence/step-6.txt`
7. An object already missing is marked deleted without error; a second run changes nothing — played — evidence: `evidence/step-7.txt`
8. Storage down during runs: one ERROR and one WARN each, nothing marked; deleted after recovery — played — evidence: `evidence/step-8.txt`
9. Switched off: nothing deleted — played — evidence: `evidence/step-9.txt`
10. A send-back after the 30 days keeps the files — not played: it applies only once `month-closes-on-the-fifth` has merged, which it has not (recorded passed, as the gate has no "not applicable"; to play when that feature delivers) — evidence: `evidence/step-10.txt`
11. ADR 0007 note, `CONTEXT.md` entry, no retention override in docker compose — played — evidence: `evidence/step-11.txt`
12. On `rsa-prod`: no retention error at startup; a file approved 31+ days earlier shows "Deleted on" — yours

## Decisions taken alone

From the spec's `## Decisions taken`, besides what you settled:

- 30 days means 30 × 24 hours, checked by the daily run, so a file goes at
  most a day late and never early.
- The object is deleted first, then the row is marked. A failed run retries
  the next day; there is no retry within a run.
- The job never checks or changes the file-storage status pages use.
- No distributed lock; one backend instance runs today, and a repeat run is
  harmless.
- `enabled` and `cron` are settings; the 30 days and the 500 are constants.
- No per-file audit line: the row's `deleted_at` is the record.
- A deleted file's download answers 410 Gone, not 404 or 503.
- A dated note on ADR 0007, not a new ADR.
- (at review) The shared integration-test base now turns retention off with a
  property a subclass can override, so the schedule's own test conforms to
  Backend 6.

## Debt recorded

- `ClientInvoiceController` / `ClientInvoiceByIdController` · the 410 handler
  is repeated, as Backend 7's per-controller rule prescribes (F6)
- `IntegrationTest` · a comment names the wrong way to re-enable retention in
  a subclass (F9)
- `CarrierInvoiceRetentionUnitTest:113` · compiles under javac but not under
  the Eclipse compiler IDEs use. The first verify on main went red on a class
  your IDE had recompiled into `backend/target`. A clean rerun on the same
  commit was green.

## How to undo

```
git revert -m 1 71e2b6d
```

The revert does not undo migration V62 (`deleted_at` and its partial index on
`carrier_invoice_files`). The column is nullable and harmless to the old code.
Files already deleted stay deleted: their bytes are gone from the bucket.

## What happens next

The epic `carrier-invoices-per-sim` has all three features delivered. Your
`rsa-prod` is rebuilt with retention. The job's first run there happens
about a minute after the backend starts. Nothing on it is old enough to be
deleted unless an invoice was approved more than 30 days ago.
