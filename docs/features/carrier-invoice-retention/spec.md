---
feature: carrier-invoice-retention
epic: carrier-invoices-per-sim
status: draft
date: 2026-10-17
---

<!-- sdlc:template spec 1 -->

# Carrier Invoice Files deleted 30 days after approval

## Problem

The human asked, on 2026-10-16, for a "file retention policy: 30 days after
being approved". A carrier's invoice is supporting evidence for one month's
Client Invoice. Once the Manager has approved that invoice, the file has done
its job, and the business does not want to keep it for ever.

Today nothing is ever deleted. A Carrier Invoice File's bytes stay in the
**Object store** for as long as the row exists, and rows are never removed.
The code has no scheduled job of any kind: no `@EnableScheduling`, no
`@Scheduled`, no cron, no job lock. `CarrierInvoiceFileStorage.delete` exists.
It is idempotent, and it is used today only to undo a failed attach.

Settled with the human on 2026-10-17 (epic, "Settled with the human"), and not
reopened here:

- **The record is kept (option A).** After the 30 days the bytes are deleted.
  The invoice still lists the file by name, with "Deleted on {date}" in place
  of Download. The SIM line keeps its check mark.
- **The app deletes, through a daily job that counts from approval.** A bucket
  lifecycle rule is not used, because it cannot count from approval (ADR 0007).
- **The backend never refuses to start over the store**, and file storage
  availability is checked only on demand, when invoice pages are opened. There
  is no background availability check (ADR 0007). The daily deletion job is a
  separate, settled thing. It must stay cheap and quiet, and it must behave
  well while the store is down.
- **Production is Hetzner Object Storage** (`DeleteObject` is already checked
  against Hetzner's supported actions in ADR 0007). The human's `rsa-prod`
  stack and every test use SeaweedFS.

## Journeys

Third and last feature of `docs/roadmap/carrier-invoices-per-sim.md`. It ships
after `carrier-invoice-per-sim`, whose check mark on each Postpaid SIM line it
must keep.

- **Bill the Client for the month** (`exists`, stays `exists`). A new last
  step: 30 days after the Manager approves, the invoice's carrier invoice files
  are deleted, and the invoice says so where each file was. At delivery, the
  journey's sentence in `docs/journeys.md` gains "its carrier invoice files are
  kept for 30 days after approval". Its proof spec,
  `client-invoice-submission-and-visibility.spec.ts`, keeps passing unedited.

## Goals / Non-goals

Goals:

- 30 days after a Client Invoice is approved, each of its Carrier Invoice
  Files' objects is deleted from the Object store by the backend, with no human
  action.
- The row stays. It gains the moment its bytes were deleted, and every screen
  that lists the file shows "Deleted on {date}" in place of Download.
- A deleted file still counts as the SIM line's file: the check mark stays.
- The job runs once a day, and once shortly after the backend starts. A missed
  day is caught up by the next run.
- While the store is down, a run deletes nothing, marks nothing, logs one
  ERROR, and leaves the work to the next run. The rest of the app is
  untouched.
- The job is cheap: one indexed query and a bounded batch per run. It writes
  no log line when nothing is due.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No retention by bucket lifecycle rule**, object expiry, or object tag
  (the human, 2026-10-17; ADR 0007).
- **No deleting the row**, the file name, its type, its size or its object key.
  Only the bytes go.
- **No "Kept until {date}" or countdown before the deletion**, and no
  notification, email or banner about an upcoming deletion. This is the open
  question below. The draft follows its recommendation.
- **No restoring a deleted file**, no bucket versioning, no trash or grace
  period, no backup before deletion.
- **No Manager action to delete now, keep longer, or exempt a file**, and no
  per-Tenant or per-Client retention period. It is 30 days for every file.
- **No retention for anything other than Carrier Invoice Files.** Agent
  Invoices, PDFs (rendered on demand, never stored) and the database rows are
  untouched.
- **No new endpoint to trigger, inspect or report on the job**, and no admin
  screen. The log and each row's `deleted_at` are the whole record.
- **No background availability check.** The job never calls
  `ObjectStoreAvailability.probe()` and never sets file storage available
  (ADR 0007's on-demand rule stands).
- **No distributed job lock** (ShedLock or similar). One backend instance runs
  today, and the job is idempotent, so two instances running it at once do no
  harm (Decisions taken).
- **No change to send, send-back, approve, attach, the PDF or the paused
  state.** The per-SIM form, the send gate, the check mark and removing a file
  after a send-back are `carrier-invoice-per-sim`'s. This feature only says how
  they treat a deleted file.
- **No new `Clock` bean.** Tests and the walkthrough backdate `approved_at`
  (Decisions taken).

## User stories

Seeing a deleted file:

1. As an Agent, I want a Carrier Invoice File on my approved Client Invoice to stay downloadable for 30 days after the Manager approved it, so that I can still fetch it while the month is fresh.
2. As an Agent, I want a file deleted by retention to stay listed on my Client Invoice under its name, with "Deleted on {date}" in place of Download, so that I can see what was attached and when it went.
3. As a Company Manager, I want the same on the invoice's detail view (name listed, "Deleted on {date}" in place of Download), so that the invoice I approved still shows what supported it.
4. As a Tester, I want my Client's invoice on the Invoices page to show a deleted file the same way, so that I know the file existed and is gone on purpose, not broken.
5. As an Agent or a Manager, I want each Postpaid SIM line to keep its check mark once its file is deleted, so that the invoice still shows that every SIM had its carrier invoice.
6. As an Agent, a Manager or a Tester, I want a deleted file shown as deleted whether file storage is up or down, and the "File storage is unavailable" notice shown only when something on the section is really paused, so that a deleted file is never mistaken for an outage, and an outage never hides a deletion.
7. As anyone holding an old download link to a deleted file, I want a clear "deleted" answer (410 Gone), not an error about the store, so that the link explains itself.

How deletion behaves:

8. As the human, I want each file's bytes deleted from the Object store 30 days after its Client Invoice's approval, with no one doing anything, so that the retention rule holds by itself.
9. As the human, I want the 30 days counted from the approval, never from the upload or the billing month, and a file on an invoice that is not approved (draft, sent, sent back) never deleted, however old it is, so that nothing is deleted before the Manager has signed off.
10. As the human, I want a deletion that was due while the backend was stopped done by the first run after it starts, and a run every day after that, so that a stack switched off at night still keeps the rule.
11. As the human, I want a run that meets a store that is down to delete nothing, mark nothing as deleted, log one ERROR for the whole run (not one per file), and leave everything for the next run, so that an outage neither loses track of a file nor floods the log.
12. As the human, I want the job to cost one query and a bounded batch per run, and to log nothing when nothing is due and one INFO line when it deleted something, so that it stays invisible in normal use.
13. As the human, I want each deleted file's row kept with its name, type, size, upload time and deletion time, so that the record of what supported an invoice outlives the bytes.
14. As the human, I want to switch the daily deletion off, or move its time of day, through the environment without a code change, so that I can stop it at once if something looks wrong.
15. As the team, I want a file whose object is already missing marked deleted without an error, and a second run (or a second backend instance) over the same file to change nothing, so that the job is safe to repeat.
16. As the team, I want a row marked deleted only once its object's deletion succeeded, so that a row never says "deleted" while its bytes are still in the store.

Living with the other features:

17. As the human, should a Client Invoice ever leave `approved` before its files are deleted (`month-closes-on-the-fifth` adds a send-back of an approved invoice), I want its files kept, and the 30 days counted again from its next approval, so that "30 days after being approved" always means the approval that stands.
18. As an Agent, should an invoice whose files were deleted come back to me as a draft (the same send-back), I want each deleted file still to count as its SIM's file, and to be able to remove it and attach a replacement, so that I am never blocked by a file I cannot download.
19. As everyone, I want attach, download, send, send-back, approve, the PDF and the paused state to behave exactly as before for every file not yet deleted, so that retention carries no release risk.
20. As the team, I want ADR 0007 to note that retention runs as a daily job without checking availability, and `CONTEXT.md` to say a Carrier Invoice File's bytes are kept 30 days after approval, so that the next reader finds the rule without this spec.

## Solution

### Schema

One additive Flyway migration, numbered at the next free version when this
feature merges (V61 on today's `main`; `carrier-invoice-per-sim` may take it
first):

- `carrier_invoice_files.deleted_at timestamptz NULL`. It is null while the
  bytes are kept. It is set once, when retention deleted them, and never
  cleared.
- A partial index on `carrier_invoice_files (client_invoice_id) WHERE
  deleted_at IS NULL`. It serves the job's join, and it shrinks as files are
  deleted.

No data is rewritten. `storage_path` keeps the object key after the deletion,
for the record. `CarrierInvoiceFile` gains `deletedAt`.

### The rule

```
due(file, now) = file.deletedAt IS NULL
             AND file.clientInvoice.status = APPROVED
             AND file.clientInvoice.approvedAt <= now - 30 days    (exactly 30 × 24h, instants in UTC)
```

The rule is evaluated on every run against the invoice's **current** status
and `approvedAt`. Nothing about the deadline is stored. So:

- today, `APPROVED` is terminal, and the count runs from the one approval;
- if `month-closes-on-the-fifth` lands, a send-back of an approved invoice
  clears `approvedAt`. Its files are no longer due, and a later approval
  starts the 30 days again (story 17). No code here reads that feature's
  open/closed rule.

A file uploaded after approval (only possible under
`month-closes-on-the-fifth`) is due on the same day as its invoice's other
files. The rule is per invoice, not per file.

### The job: `CarrierInvoiceRetention`

A new `@Component` in `web`, beside `CarrierInvoiceFileService` (the Client
Invoices cluster is unmoved, ADR 0006). It is not transactional itself. Its
public surface is one method:

```java
RetentionRun runOnce();   // record RetentionRun(int due, int deleted, boolean stoppedByStore)
```

A run:

1. Reads `now` once. In one short read-only transaction, it selects up to
   **500** due files (`id`, `storage_path`), oldest approval first. The query is
   on the repository, joined to `client_invoices`. It holds no lock.
2. For each file, in order:
   1. `CarrierInvoiceFileStorage.delete(key)`. This is outside any
      transaction, and no row lock is held (Backend 8). A missing key
      succeeds, as today.
   2. On success: one short transaction runs `UPDATE carrier_invoice_files
      SET deleted_at = now WHERE id = ? AND deleted_at IS NULL`. Zero rows
      updated (another run got there first) is fine.
   3. On `ObjectStoreUnavailableException`: the run **stops**. The file and
      every file after it stay unmarked. The storage has already logged its
      one ERROR and marked file storage unavailable, as on any failed
      transfer. The job adds one WARN: "Carrier invoice retention stopped:
      object store unavailable; {deleted} deleted, {remaining} still due; next
      run retries".
3. The run logs one INFO line, "Carrier invoice retention deleted {n} files",
   only when n > 0. A run with nothing due logs nothing. Each file deleted is
   a DEBUG line with its id and key.

A failing run therefore writes at most two lines (one ERROR, one WARN), and
every later failing run writes the same two. A batch cut at 500 leaves the
rest to the next run, so a backlog clears in days, never in one long run.

**Triggers**, both on Spring's scheduling thread, never on the startup path:

- a cron, `0 0 3 * * *` in zone UTC by default (03:00 UTC daily);
- one run about one minute after `ApplicationReadyEvent`, so a stack that is
  off at 03:00 still catches up the day it starts (story 10).

`@EnableScheduling` is added once, on a small configuration class that is
switched off together with the triggers. This is the app's first scheduled
task. Nothing else is scheduled.

**Configuration**: a new `@ConfigurationProperties("app.carrier-invoice-retention")`
record, `CarrierInvoiceRetentionProperties` (Backend 14):

```java
record CarrierInvoiceRetentionProperties(
    boolean enabled,   // CARRIER_INVOICE_RETENTION_ENABLED, default true; false registers neither trigger
    String cron)       // CARRIER_INVOICE_RETENTION_CRON, default "0 0 3 * * *", always zone UTC
```

The 30 days and the batch of 500 are constants in the code, not settings.
Thirty days is the human's rule, and a setting would invite changing it per
environment. `runOnce()` works whatever `enabled` says. The flag only governs
the triggers. Tests set `enabled=false` and call `runOnce()` directly.

### Download, list and response

- `CarrierInvoiceFileResponse` gains `deletedAt` (an instant or null). Every
  role gets it, the Tester included. `ClientInvoiceResponse.files` carries it
  wherever files are listed today.
- **Download of a deleted file** answers `410 Gone` with
  `{ code: "CARRIER_INVOICE_FILE_DELETED" }`, through a new domain exception,
  `CarrierInvoiceFileDeletedException`, carrying `@ResponseStatus(GONE)`
  (Backend 7). `CarrierInvoiceFileService.download` checks `deletedAt` right
  after it finds the row, **before** the availability gate and before any
  store call. So a deleted file answers 410 even while the store is down
  (story 7). The BFF's binary forwarder already passes the status through.
- `filesAvailable` is unchanged. It stays a property of the store, not of a
  file.

### Frontend: the deleted file in `CarrierInvoiceFilesSection`

`CarrierInvoiceFileListItem` in `lib/api/types.ts` gains `deletedAt: string |
null`. The shared section takes it per file. The three screens that render it
(the Agent's Client Invoice card, the Manager's invoice detail, the Tester's
Invoices page) need no change of their own beyond passing the field through.

- **A deleted file** keeps its row, its paperclip and its name. In the
  Download slot it shows the plain text `Deleted on {date}`. It is not a
  link, not a button and not focusable, and it has no `aria-disabled`, since
  nothing is disabled: the file is gone. `{date}` is `deletedAt` formatted by
  the same `formatDate` call the page already uses for `approvedAt`, so the
  section reads in the page's one date style.
- **Paused and deleted together.** A deleted file looks the same whether
  `filesAvailable` is true or false. The "File storage is unavailable" notice
  shows only when something on the section is paused: the attach control is
  offered, or at least one file is **not** deleted. A section whose files are
  all deleted shows no notice during an outage.
- **The check mark** on each Postpaid SIM line (`carrier-invoice-per-sim`)
  keys on the file row existing, never on `deletedAt`. This feature adds a
  test that pins it (story 5).
- **After a send-back** (only under `month-closes-on-the-fifth`), a deleted
  file still satisfies `carrier-invoice-per-sim`'s send gate for its SIM. The
  Agent's remove control works on it as on any file: removal deletes the row,
  and the storage `delete` of an already-deleted key succeeds (story 18).

### ADR 0007, dated note

ADR 0007 gains one dated line under Decision: "Amended 2026-10-17
(`carrier-invoice-retention`): retention runs as a daily scheduled job, plus
once after startup. It deletes the objects of files whose Client Invoice was
approved at least 30 days earlier, keeps the row with `deleted_at`, and never
probes or sets availability. A failed run stops, and the next run retries."
No new ADR: a daily job is easy to reverse, and the hard-to-reverse choice
(the app deletes, not a lifecycle rule) is already ADR 0007's.

## Design direction

Design slot: `impeccable`. `DESIGN.md` is the world. This is a quiet terminal
state of an existing row, not a new pattern: no new token, colour or
component.

- **Agent Client Invoice card, Manager invoice detail, Tester Invoices page:
  Read.** The viewer understands that the file existed and was removed on
  purpose.
- The row keeps its shape, its paperclip and its name in `ink`, because what
  was attached is still true. "Deleted on Oct 2, 2026" sits where Download
  was, in `text-label-sm` `ink-mute`, with no icon and no link colour. It is a
  fact, not a control and not a warning.
- No warning or danger tone. Nothing failed and nothing waits.
- `DESIGN.md`'s paused-files-section line under Components gains one clause
  for the deleted row, so the next file control follows it.
- In every existing state, every pixel is unchanged. The visual stub serves
  `files: []`, so no golden moves.

## Constraints

- Ships after `carrier-invoice-per-sim` is merged. Inherits every constraint of
  `carrier-invoice-object-storage` and of ADR 0007, except where this spec
  says otherwise.
- **The deletion rule is exactly `due(file, now)` above**: status `APPROVED`,
  `approvedAt <= now - 30 days` (30 × 24 hours, UTC instants), `deleted_at`
  null. It is evaluated at run time from the invoice's current status and
  `approvedAt`. No deadline is stored.
- **Retention deletes objects only.** No row, column value or object of a
  file that is not due is ever deleted or rewritten. `deleted_at` is set once
  and never cleared.
- **A row is marked deleted only after its object's `DeleteObject` succeeded**
  (or found no object).
- **No store call inside a transaction**, and no Client Invoice row lock is
  taken by the job (Backend 8).
- **At most 500 files per run.** Triggers: the cron (default `0 0 3 * * *`,
  zone UTC) and one run about one minute after startup, both on the scheduling
  thread, neither able to delay or fail startup.
- **Logging per run**: nothing when nothing is due. One INFO when n > 0. On a
  store failure, the storage's one ERROR plus one WARN from the job, never a
  line per file above DEBUG. Never the store's credentials.
- **No availability probe from the job**, and the job never sets file storage
  available. A failed delete marks it unavailable through the storage's
  existing path, as any failed transfer does.
- **Download of a deleted file is `410` with code
  `CARRIER_INVOICE_FILE_DELETED`**, answered without calling the store and
  regardless of availability.
- **HTTP contract: additions only.** `deletedAt` on `CarrierInvoiceFileResponse`,
  and the 410. No new route and no new BFF route.
- **S3 operations unchanged**: `DeleteObject` is the only one retention uses,
  and it is already among the five checked against Hetzner.
- One additive migration (the next free number at merge). No data rewritten.
- Configuration through `app.carrier-invoice-retention` only (Backend 14). The
  30 days are not configurable.
- Every isolated run (tests, e2e, acceptance) uses its own throwaway Postgres
  and SeaweedFS. No agent touches the human's `rsa-prod` stack, its ports
  3000/8080/5432, its volumes, or any Hetzner bucket. The e2e suite runs on an
  idle machine, and a failure is never called a flake without a controlled
  comparison.
- Maven runs under JDK 21. Checkstyle stays clean.

## Testing decisions

Tests assert external behaviour: what the API answers, and what is left in the
bucket, read through `ObjectStoreTestSupport`. They never assert repository or
SDK calls in an integration test (Backend 5).

1. **The main seam: `runOnce()` on the real bean, under `IntegrationTest`,
   against the shared SeaweedFS.** A new `CarrierInvoiceRetentionTest`. Each
   case builds its data through the API (attach, send, approve, as
   `ClientInvoiceApiTest` does), then backdates `approvedAt` through the
   repository (prior art: `ClientInvoiceByIdApiTest.moveToPastMonth`). It
   calls `runOnce()`, then reads the result back through the API and the
   bucket. This is the highest seam that reaches the job without a trigger.
   It needs no clock: an `approvedAt` of `now - 30 days - 1 minute` against
   `now - 30 days + 1 minute` plays the boundary with the real time. The test
   context sets `app.carrier-invoice-retention.enabled=false`, so no trigger
   fires during the suite. It covers:
   - a due file: its object is gone (`keysUnder` the invoice prefix is
     empty), its `deletedAt` is set in `GET /api/client-invoices/{id}`, and
     its download is `410 CARRIER_INVOICE_FILE_DELETED`. The name, type and
     size are unchanged, and `runOnce()` reports `deleted = 1`;
   - one minute short of 30 days: kept and downloadable;
   - a draft, a sent and a sent-back invoice whose `uploadedAt` and
     `billingMonth` are moved back months: never deleted;
   - an object already removed through the test helper: marked deleted, no
     error;
   - a second `runOnce()`: changes nothing, `deleted = 0`;
   - more than 500 due files is **not** played here; the cap is a unit case.
2. **Unit tests (JUnit 5 + Mockito, Backend 6)** on `CarrierInvoiceRetention`
   over a mocked storage and repository, for what a real store cannot show on
   demand:
   - `ObjectStoreUnavailableException` on the second of three files: the
     first is marked, the second and third are not, the run reports
     `stoppedByStore`, the third is never attempted, and exactly one WARN is
     logged (prior art: the log-capture in `ObjectStoreAvailabilityTest`);
   - nothing due: no store call, no log line above DEBUG;
   - the query is asked for at most 500;
   - the job never calls `ObjectStoreAvailability.probe()`.
3. **The 410 at the HTTP seam**: one case in `ClientInvoiceApiTest` for a row
   with `deletedAt` set through the repository: `410` with the code. One case
   with the store marked unavailable: still `410`, not `503`.
4. **Triggers**: one context test that `enabled=false` registers no scheduled
   task and `enabled=true` registers the cron in UTC (read from Spring's
   `ScheduledTaskHolder`). The startup run is shown in the walkthrough, not
   waited on in a test.
5. **Frontend component tests (Vitest + Testing Library)**, in the existing
   `CarrierInvoiceFilesSection` test:
   - a deleted file lists its name and "Deleted on {date}", with no link;
   - with `filesAvailable` false and only deleted files, no notice;
   - one deleted and one live file, paused: the notice shows, and the live
     file's Download is paused;
   - the per-SIM check mark stays for a SIM whose file is deleted, in
     `carrier-invoice-per-sim`'s own component test, extended by one case.
   Fixtures gain `deletedAt: null`.
6. **No new e2e spec.** Retention needs time to pass or a database write, and
   the job's trigger is not reachable from a browser. The existing specs run
   unedited. The walkthrough plays the real job on a real stack.
7. **Visual goldens**: unchanged, none added. A golden that moves is a defect.
8. **`verify`** stays the command in `docs/agents/sdlc.json`, unchanged.

## Decisions taken

- **No "Kept until {date}" before the 30 days are up** (settled by the human, 2026-10-18, `retention-kept-until`: "ok" to the recommendation). Reason: the human asked for "Deleted on" once the file is gone, not a warning before it; it can be added later.
- **The 30 days are evaluated each run from the invoice's current status and
  `approvedAt`, and nothing is stored.** Reason: it is the literal reading of
  "30 days after being approved". It needs no write at approval. It handles
  `month-closes-on-the-fifth` with no code of its own: a send-back clears
  `approvedAt` and stops the count, and the next approval restarts it. Veto
  if you would rather count from the first approval, or wait for the month to
  close.
- **No extra "month closed" condition, should `month-closes-on-the-fifth`
  land.** Reason: an invoice is open for at most about 35 days after its
  month starts, and approval comes at the earliest on the month's first day.
  So a file is due while the month is still open only when the invoice was
  approved in the first five days of its own billing month. That case is rare,
  and a send-back then still finds the row and its check mark (story 18).
- **"30 days" means 30 × 24 hours between instants, run by a job at 03:00
  UTC, so a file goes at the first run after its deadline.** Reason: one
  unambiguous comparison. Files are never deleted early, and at most a day
  late.
- **Delete the object first, then mark the row in its own short
  transaction.** Reason: a row never claims "deleted" while the bytes remain
  (the retention promise). The reverse failure (bytes gone, row not yet
  marked) heals on the next run, since a missing key deletes fine. A
  send-back racing the few milliseconds between the two (only under
  `month-closes-on-the-fifth`) still records the truth: the bytes are gone.
- **A store failure stops the run at the first file. Nothing is retried
  within a run, and the next run retries.** Reason: one ERROR per run, not
  one per file (the human's ask). A store that failed once is likely down for
  the next 499 files, and the daily cadence is the retry.
- **The job tries the store whatever the cached availability says, and never
  probes.** Reason: skipping on a stale "unavailable" could stall retention
  while nobody opens a page. Probing would be the background availability
  check the human turned down. A failed delete already marks unavailable
  through the storage's existing path.
- **Spring `@Scheduled` with a cron, plus one run a minute after startup.**
  Reason: the epic settled a daily job, and Spring scheduling is already on
  the classpath with nothing new to add. The startup run covers a stack that
  is off at 03:00, such as a laptop. A minute's delay keeps it off the
  startup path and away from the startup bucket check.
- **No distributed lock.** Reason: one instance runs today. Two concurrent
  runs only repeat an idempotent `DeleteObject` and a conditional `UPDATE`.
  ShedLock would add a dependency and a table for a case that does not exist.
- **Batch of 500 per run, oldest approval first.** Reason: it bounds the time
  and memory of one run. A month's files number in the tens, so a backlog
  still clears in a day or two.
- **`enabled` and `cron` are settings, and the 30 days and 500 are
  constants.** Reason: a switch off and a time of day are the operator's
  (story 14). The period is the human's rule, not an environment's choice.
- **No per-file audit line. `deleted_at` on the row is the durable record,
  plus one INFO per run.** Reason: `AuditLog` has no system actor and every
  call site names a user. Adding one for an automatic, rule-driven deletion
  adds noise, while the row already says what went and when.
- **No new `Clock` bean. Tests and the walkthrough backdate `approved_at`.**
  Reason: backdating plays every case with the real time (prior art
  `moveToPastMonth`). `month-closes-on-the-fifth` plans the app's `Clock` bean
  as its first ticket, and adding one here would collide with it. If that
  bean exists when this is built, the job reads it.
- **Download of a deleted file is `410 Gone` with code
  `CARRIER_INVOICE_FILE_DELETED`, checked before the availability gate.**
  Reason: a 404 would read as "never existed" and a 503 as "try later".
  Neither is true. No screen links a deleted file, so this only answers old
  links and API callers.
- **"Deleted on {date}" uses the page's existing `formatDate`, in the
  viewer's zone.** Reason: it sits beside the approval date on the same card,
  and two date styles in one card is the debt already recorded.
- **A deleted file is plain text in the Download slot, not `aria-disabled`.**
  Reason: nothing is disabled. The file is gone, and a disabled control would
  promise it comes back.
- **The paused notice counts only files not deleted.** Reason: it follows the
  existing rule that no notice shows where nothing is paused.
- **The check mark and the send gate treat a deleted file as attached.**
  Reason: the human said the SIM line keeps its check mark. The send gate
  follows the same row, so an approved-then-sent-back invoice is never
  blocked by its own deleted files.
- **A partial index on `client_invoice_id WHERE deleted_at IS NULL`.**
  Reason: the job's only query joins on it, and the index shrinks as files
  are deleted.
- **A dated note on ADR 0007, not a new ADR.** Reason: the irreversible choice
  (the app deletes, keyed by ids, no lifecycle rule) is already ADR 0007's. A
  scheduled job is easy to remove.
- **No e2e spec. The walkthrough plays the job.** Reason: the trigger is
  time, not a browser action. The API seam plus the component tests cover
  each half.
- **Two tickets: the backend job with its 410 and response field, then the
  frontend's deleted row.** Reason: each is a complete slice. The backend one
  is demoable through the API and the bucket, and the frontend one is a
  single shared component.
- **`track` is `full`.** Reason: it adds the app's first scheduled task, a
  migration, and an irreversible deletion of user files.

## Open questions

None.

## Acceptance walkthrough

The `[agent]` steps run on an isolated stack the agent starts and tears down
itself. They never touch the human's `rsa-prod` stack, ports 3000/8080/5432,
its volumes, or any Hetzner bucket. The stack:

- a throwaway Postgres and a throwaway SeaweedFS (`docker run`, exact names,
  free ports, the pinned `chrislusf/seaweedfs` tag, dev-only credentials;
  SeaweedFS started without `--rm` so it keeps its data across a stop, and
  removed by name at the end);
- the backend on the `demo` profile on a free port, with `OBJECT_STORE_*`
  pointing at that SeaweedFS and `CARRIER_INVOICE_RETENTION_ENABLED=true`;
- a production build of the frontend on a free port.

"Backdate" means a SQL `UPDATE client_invoices SET approved_at = …` on that
throwaway Postgres only. "List the bucket" means an S3 API listing against the
throwaway SeaweedFS. "The startup run" is the one about one minute after the
backend starts. Logins are in the root `README.md`. Jordan is the demo Agent.

1. [agent] Run `verify`. Show it green and the visual goldens unchanged. (stories: 19)
2. [agent] As Jordan, on two Contracts' current-month drafts (A and B), attach each Postpaid SIM line's carrier invoice through the per-SIM form, and send both. As the Manager, approve both. Download every file once and show the bytes match. Restart the backend and wait past the startup run. Show every object still in the bucket, every Download still working, and no retention line in the log. (stories: 1, 9, 12)
3. [agent] Backdate A's `approved_at` to now minus 30 days and 1 hour, and B's to now minus 29 days and 23 hours. Restart the backend and wait past the startup run. List the bucket: A's objects are gone and B's are there. Query `carrier_invoice_files`: A's rows are all present, with `deleted_at` set and `filename`, `content_type`, `size_bytes`, `uploaded_at` and `storage_path` unchanged; B's `deleted_at` is null. Show exactly one INFO line, "Carrier invoice retention deleted {n} files", with n equal to A's file count. (stories: 8, 9, 10, 12, 13, 16)
4. [agent] In a browser, open A as Jordan, as the Manager on `/manager/invoices/client/{id}`, and as A's Client's Tester on the Invoices page. Show each file listed by name with "Deleted on {today's date}" where Download was, not a link and not focusable, and every Postpaid SIM line still checked (Jordan, Manager). Open B and show its Downloads working. Screenshot each. (stories: 2, 3, 4, 5)
5. [agent] Through the API, download one of A's files as Jordan: `410` with `CARRIER_INVOICE_FILE_DELETED`. (stories: 7)
6. [agent] Leave a third invoice C as a draft with files attached, send a fourth, D, and have the Manager send back a fifth, E (each with files). Move their `billing_month` and `uploaded_at` back three months by SQL. Restart the backend and wait past the startup run. Show all of C's, D's and E's objects in the bucket and their `deleted_at` null. (stories: 9)
7. [agent] Delete one of B's objects directly through the S3 API, then backdate B to now minus 31 days. Restart and wait past the startup run. Show every one of B's rows marked deleted (the missing one included), no ERROR or WARN in the log, and one INFO. Restart again and show no retention line at all: the second run found nothing. (stories: 12, 15)
8. [agent] The store goes down during a run (stories: 6, 11, 14, 16):
   - Approve a sixth invoice F with three files, and backdate it 31 days.
   - Restart the backend with `CARRIER_INVOICE_RETENTION_CRON="0 * * * * *"` (every minute).
   - `docker stop` SeaweedFS before the startup run. For the next three runs, show exactly one ERROR (the storage's `DeleteObject`, no credentials) and one WARN ("retention stopped … next run retries") per run, and none of F's rows marked deleted.
   - Leave no page open during that time, and show no other store-related line in the log between runs.
   - Open A and F as the Manager. Show A's files still "Deleted on" (no notice, since nothing on A is paused) and F's Downloads paused with the notice.
   - Download one of A's files through the API: still `410`, not `503`.
   - `docker start` SeaweedFS. At the next run, show F's objects gone, its rows marked, and one INFO.
   - Reload F twice as the Manager (the last availability check at least 30 seconds old) and show "Deleted on" with no notice.
9. [agent] Restart the backend with `CARRIER_INVOICE_RETENTION_ENABLED=false`, after backdating a seventh approved invoice G (with files) by 31 days. Wait three minutes. Show G's objects still in the bucket and no retention line in the log. (stories: 14)
10. [agent] Only if `month-closes-on-the-fifth` has merged by then: approve an invoice H with files, backdate its `approved_at` by 31 days, and before any run, send it back as the Manager while its month is still open (fix the demo clock inside its open window). Wait past the startup run. Show H's objects kept. As Jordan, show the invoice a draft with its files downloadable. Resend it, approve it again, and show its new `approved_at` and its files kept by the next run. If that feature has not merged, record this step as not applicable. (stories: 17, 18)
11. [agent] Show ADR 0007's dated note on retention and the declared `CONTEXT.md` entries. Run `docker compose -f docker-compose.yml config` (read-only) and show no `CARRIER_INVOICE_RETENTION_*` override, so `rsa-prod` gets the defaults. (stories: 20)
12. [human] On your own stack (`rsa-prod`), rebuild with your usual `up -d --build`. About a minute after the backend starts, `docker compose logs backend` shows no retention error. Approve a Client Invoice that has carrier invoice files and download one: it works. On or after the 31st day after that approval, open the invoice and confirm each file shows "Deleted on {date}" where Download was, each SIM line keeps its check mark, and you are happy with how that reads. (stories: 1, 2, 5, 8, 10)

## Execution order

Depends on `carrier-invoice-per-sim`, which must be merged first (the check
mark and the per-SIM form). Two tickets, one PR:

1. `carrier-invoice-files-deleted-after-retention`: the migration
   (`deleted_at` and the partial index), `CarrierInvoiceRetention` with
   `runOnce()`, the batch and the stop-on-store-failure rule,
   `CarrierInvoiceRetentionProperties`, `@EnableScheduling` with the cron and
   the startup run, `deletedAt` on `CarrierInvoiceFileResponse`, the `410
   CARRIER_INVOICE_FILE_DELETED` on download, ADR 0007's dated note, and the
   declared `CONTEXT.md` and `ARCHITECTURE.md` entries. Labels: backend. No
   dependency within the feature. (stories: 7, 8, 9, 10, 11, 12, 13, 14, 15,
   16, 17, 19, 20)
2. `carrier-invoice-files-show-deleted-on`: `deletedAt` in `lib/api/types.ts`
   and the fixtures, the deleted row in `CarrierInvoiceFilesSection`, the
   notice counting only files not deleted, the check-mark and send-gate cases
   for a deleted file, and the one-clause `DESIGN.md` line. Labels: frontend.
   Depends on `carrier-invoice-files-deleted-after-retention`. (stories: 1, 2,
   3, 4, 5, 6, 18)
