---
feature: carrier-invoice-per-sim
epic: carrier-invoices-per-sim
status: approved
date: 2026-10-17
---

<!-- sdlc:template spec 1 -->

# One Carrier Invoice File per Postpaid SIM line, required before send

## Problem

The human, on 2026-10-16: "Make sure agent can't send a client invoice to the
manager without the related carrier invoices. Example: A contract with a
client who have 3 postpaid and 1 prepaid. The bare minimum before sending the
invoice is that 3 carrier invoices are attached. I would expect when
attaching an invoice to link it to the related sim number, carrier and then
store it with a computed name (client - month year - carrier - sim number),
the action button should handle opening a form to do that."

Today, after `carrier-invoice-object-storage`:

- A **Carrier Invoice File** belongs to a Client Invoice only. Its row has no
  link to a SIM Card. "Attach carrier invoice" opens the file picker straight
  away, several files at once, and keeps each uploaded file's own name.
- **Send checks nothing about files.** A Client Invoice with three Postpaid
  SIM lines can go to the Manager with no carrier invoice at all.
- Nothing on a Postpaid SIM line says whether its carrier invoice is there.
  The Manager has to match file names to SIM numbers by eye.
- **No file can be removed**, by anyone. A wrong file attached to a draft
  stays on the invoice for good.
- When a send is refused, the Agent sees only "Couldn't send. Try again.",
  whatever the reason.

Settled with the human (epic, 2026-10-17), and not reopened here:

- One file per Postpaid SIM. A file never covers several SIMs.
- Every SIM has a Carrier.
- No backfill: no Carrier Invoice File exists on the human's stack.
- After a send-back the Agent can remove a file and attach its replacement.
- The link exists for the check mark: "it's really to have in the row an icon
  (check mark) showing that for the sim number a file were uploaded."
- The computed name is stored in the database and given on download. Object
  keys stay ids. Objects carry no metadata.
- Only the Agent attaches. The Manager's invoice page has no Attach control.
- While file storage is unavailable, the files UI pauses. Availability is
  checked on demand, when an invoice page opens.
- Retention (`carrier-invoice-retention`, specced in parallel) deletes the
  bytes 30 days after approval and keeps the row, its name and the SIM line's
  check mark.

## Journeys

Second feature of `docs/roadmap/carrier-invoices-per-sim.md`.

- **Bill the Client for the month** (`exists`, stays `exists`). Attaching
  becomes one file per Postpaid SIM line, through a form. Send requires every
  line's file. Each line shows a check mark once its file is attached. The
  journey's proof, `client-invoice-submission-and-visibility.spec.ts`, attaches
  a file for its Postpaid SIM before sending and asserts the check mark and
  the send gate.

## Goals / Non-goals

Goals:

- "Attach carrier invoice" opens a form. The Agent picks one of the invoice's
  Postpaid SIM lines (shown by number and Carrier) and one file.
- The file is stored as "{Client} - {Month Year} - {Carrier} - {SIM
  number}{.ext}". The name is computed by the backend when the file is
  attached, kept on the row, and given on download.
- One file per (Client Invoice, SIM Card), enforced in the database.
- A check mark on every Postpaid SIM line that has its file: on the Agent's
  card and, read-only, on the Manager's invoice detail.
- The Agent cannot send while any Postpaid SIM line lacks its file. The page
  shows this before the click, and the server refuses it with a coded 409.
- The Agent can remove a file from a draft that is open to them, and attach a
  replacement. The object is deleted from the store too.
- The demo data, the backend tests and the e2e suite follow the new rule.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No retention, no "Deleted on", no deletion job.** Those belong to
  `carrier-invoice-retention`. This feature keeps that feature possible: the
  row and its SIM link are never deleted except by the Agent's Remove on a
  draft.
- **No file covering several SIMs, and no several files for one SIM** (the
  human). Replacing a file means removing it, then attaching the new one.
  There is no "replace" action.
- **No multi-file picker any more.** One file per attach. Attaching a file
  without naming its SIM is refused.
- **No restriction on file type or size beyond today's**: any type, up to 20MB.
- **No preview of the computed name in the form.** The name appears in the
  file list once the file is attached. The backend is the only place that
  computes it.
- **No renaming of an attached file**, and no renaming when a SIM number is
  corrected later. Its name is fixed at attach.
- **No check mark, SIM lines or Remove on the Tester's Invoices page.** It
  shows no SIM lines today. It keeps listing the files, now under their
  computed names.
- **The Manager neither attaches nor removes.** The Manager's view gets the
  read-only check mark only. The backend's existing permission for a Manager
  to attach through the API is left as it is. Removing is the Contract's own
  Agent's only.
- **No change to approve or send-back.** Neither looks at files. Send is the
  only gate.
- **No gate on invoices without per-SIM lines**: an invoice with no Postpaid
  SIM line, or a legacy one holding a single `BASE_AMOUNT` line.
- **No change to the object key, the bucket, the availability check or the
  paused-state mechanics** (ADR 0007). The paused notice gets one more word.
- **No backfill or migration of file data**, beyond the nullable column.
- **No new status endpoint and no per-SIM upload route.** Everything rides on
  the existing files routes and the Client Invoice response.

## User stories

Attaching:

1. As an Agent, I want "Attach carrier invoice" on my draft to open a form where I pick one of the invoice's Postpaid SIM lines, shown as its number and Carrier, and one file, so that each carrier invoice is tied to the SIM it bills.
2. As an Agent, I want the form to offer only the Postpaid SIM lines that do not have their file yet, with the line preselected when only one is left, so that I never attach a second file to a SIM by mistake.
3. As an Agent, I want the file stored and listed under the computed name "{Client} - {Month Year} - {Carrier} - {SIM number}" plus the uploaded file's extension (e.g. `Acme Corp - October 2026 - Verizon - +1-202-555-0102.pdf`), so that every carrier invoice is named the same way, whatever the carrier called it.
4. As an Agent, a Manager or a Tester, I want a download to save under that computed name, so that the file on my disk says what it is.
5. As an Agent, I want a Client, Carrier or SIM number that holds a character a file name cannot carry (`/ \ : * ? " < > |` or a control character) to still give a valid name, with each such character replaced by `_`, so that the download opens on any computer.
6. As an Agent, I want the name fixed when I attach, so that a later correction of the SIM number does not rename a file already attached. On a draft, I can remove it and attach it again.
7. As an Agent, I want each Postpaid SIM line on my invoice to show a check mark once its carrier invoice is attached, so that I can see at a glance which SIMs are covered.
8. As an Agent, I want an attach that is refused or fails to say why inside the form ("This SIM already has its carrier invoice…", "File storage is unavailable right now…", or "Couldn't attach the file. Try again."), with the form kept open, so that I can act on it.
9. As an Agent, I want "Attach carrier invoice" disabled once every Postpaid SIM line has its file, and absent on an invoice with no Postpaid SIM line, so that I am not offered an action with nothing to do.
10. As an Agent on a Contract with 3 Postpaid SIMs and 1 Prepaid SIM, I want exactly 3 carrier invoices asked for, one per Postpaid SIM line, and none for the Prepaid SIM, which has no line, so that the rule matches the human's example.

Sending:

11. As an Agent, I want "Send Client Invoice" disabled while any Postpaid SIM line lacks its carrier invoice, with a note under it saying how many are missing ("2 Postpaid SIMs still need their carrier invoice."), so that I know what stands between me and sending.
12. As the business, I want the server to refuse a send with a 409 coded `CARRIER_INVOICES_MISSING` while any Postpaid SIM line the invoice shows lacks its file, whatever the page showed, so that no Client Invoice reaches the Manager without them.
13. As an Agent whose send is refused for that reason (a SIM began billing after I loaded the page), I want the message "A Postpaid SIM still needs its carrier invoice. Reload to see which.", not "Couldn't send. Try again.", so that I know what to do.
14. As an Agent whose Contract has no Postpaid SIM billing this month, I want to send as today, with no file asked for.
15. As an Agent resending after a send-back, I want the same rule on the invoice's stored Postpaid SIM lines, so that a resend cannot drop a file either.
16. As an Agent, I want a file whose SIM no longer appears among the invoice's lines (it left the Fleet before the month began) to stay listed and removable, without counting for any line or blocking the send, so that nothing disappears silently.
17. As an Agent whose every Postpaid SIM line has its file, I want to send even while file storage is unavailable, because the check reads the attached files the app records and never the store. While a file is still missing and the store is down, I cannot attach it, so the send waits for the store.

Removing:

18. As an Agent, on a draft open to me (sent back, or of the current month and never sent), I want a Remove action on each attached file, confirmed in a second step, so that I can take off a wrong file and attach the right one.
19. As an Agent, I want a removal to take the check mark off its SIM line, put that line back in the attach form, and count it again in the send note, so that the page tells the truth at once.
20. As the human, I want a removed file's bytes deleted from the object store as well as its row, so that the store keeps nothing that no invoice lists.
21. As an Agent, I want no Remove on a sent or approved invoice, or on a past month's draft that was never sent, and the server to refuse it (409), so that what the Manager reviews cannot change under them.
22. As a Manager or a Tester, I have no Remove action, and the server refuses one from me (403), so that only the Agent who built the invoice changes its files.
23. As an Agent, while file storage is unavailable, I want Remove disabled like Attach and Download, and the notice to read "File storage is unavailable right now. Attaching, removing and downloading are paused.", so that I am not offered an action that can only fail. The server answers 503.

Reviewing and reading:

24. As a Manager reviewing an invoice, I want each Postpaid SIM line to show the same check mark, read-only, so that I can see every SIM's carrier invoice is there before I approve.
25. As a Tester, I want my Client's sent invoice to list its carrier invoice files under their computed names, downloadable as today, so that I can find each SIM's bill.

The record and the team:

26. As the team, I want each Carrier Invoice File row linked to its SIM Card, unique per (Client Invoice, SIM Card), so that the one-file-per-SIM rule holds in the database even under a race.
27. As the team, I want the row and its SIM link to stay until the Agent removes it from a draft, and never be deleted when the bytes go, so that `carrier-invoice-retention` can keep the check mark after the bytes are deleted.
28. As the team, I want the demo's sent Client Invoice to carry one file per Postpaid SIM line, and the demo to start even when the store is down, so that the demo shows the rule kept and never blocks startup.
29. As the team, I want the backend tests and the e2e suite to attach a file per Postpaid SIM line before every send, through one helper each, so that the suite proves the journey under the new rule.
30. As the team, I want `CONTEXT.md`'s Carrier Invoice File entry to say one per Postpaid SIM line, with a computed name, removable by its Agent from an open draft, so that the glossary matches the code.

## Solution

### Prefactoring: debt paid on the way

- **One handler for the coded 409.** Today only `ClientInvoiceByIdController`
  renders `ClientInvoiceConflictException` as `{code, message}`. The
  current-month `ClientInvoiceController` lets it fall through to the plain
  conflict body. The handler moves to one advice that covers both controllers
  (or is repeated identically in the second one), so that every Client Invoice
  route answers `{code, message}` for a coded 409. The send gate and the
  already-attached refusal both need this.
- **The stale header comment of V12** ("bytes live on the filesystem") gets
  nothing new, because migrations are never edited (Backend 9). The V61
  comment states the current truth.

### Schema: V61

The next free Flyway number at implementation. It is V61 as of `main` e960281.
The orchestrator reserves the numbers, because `carrier-invoice-retention` is
specced in parallel.

```sql
ALTER TABLE carrier_invoice_files ADD COLUMN sim_card_id UUID REFERENCES sim_cards (id);
ALTER TABLE carrier_invoice_files
  ADD CONSTRAINT uq_carrier_invoice_files_invoice_sim UNIQUE (client_invoice_id, sim_card_id);
```

- The column is nullable. A row from before this feature (dev and test
  databases only, none on the human's stack) keeps `NULL`. Postgres lets any
  number of `NULL`s pass the unique constraint. Every row written from now on
  has a SIM.
- Nothing is rewritten or dropped. `filename` stays `VARCHAR(255)`, and the
  computed name is cut to fit (below).
- `CarrierInvoiceFile` gains a `LAZY` `simCard` reference (Backend 11).

### The computed name: `CarrierInvoiceFileName`

A small pure component in `web` (the Client Invoices cluster has not moved,
ADR 0006). Its whole surface:

```java
static String of(String clientName, LocalDate billingMonth, String carrierName, String simNumber, String uploadedFilename);
```

- **Parts.** The Client's name, the billing month as English month name and
  year (`October 2026`, `Locale.ENGLISH`, the same text as the frontend's
  `formatBillingMonth`), the SIM's Carrier's current name (archived or not),
  and the SIM's current number. They are joined by `" - "`.
- **A SIM with no Carrier.** The human settled that every SIM has one.
  `CONTEXT.md` still allows a pre-catalog SIM with none, so the code must not
  fail on it. Such a SIM's part is left out:
  `{Client} - {Month Year} - {SIM number}`.
- **Unsafe characters.** In each part, `/ \ : * ? " < > |` and control
  characters become `_`. Runs of whitespace collapse to one space, and each
  part is trimmed.
- **Extension.** The uploaded file name's last `.xxx` is kept, lowercased,
  when it is 1 to 10 ASCII letters or digits. Otherwise there is none: an
  upload named `invoice` or `scan.final version` gets no extension.
- **Length.** When the name would pass 255 characters, the Client part is
  shortened until it fits. The month, the Carrier, the number and the
  extension are never cut.
- **Computed once, at attach,** under the invoice's row lock, from the
  entities as they are then. It is stored in `filename` and never recomputed
  (story 6). The upload's own name is used only for the extension and is not
  stored.

### Attaching: the contract

`POST /api/client-invoices/{invoiceId}/files` and its current-month twin
`POST /api/contracts/{contractId}/client-invoice/files` take a multipart body
with two parts:

- `file`, as today;
- `simCardId`, now required.

`CarrierInvoiceFileService.attach(invoice, file, simCardId, principal)`
keeps its order: availability gate (503), upload outside the lock, then
`ClientInvoiceService.recordAttachedFile(found, fileId, key, file, simCardId,
principal)`, and an undo that deletes the object on any failure. Under the
row lock, `recordAttachedFile`:

1. requires `DRAFT` (409), and open-to-Agent for an Agent (409
   `PAST_MONTH_DRAFT_NOT_SENDABLE`). Both checks are unchanged.
2. finds `simCardId` among `lines(invoice)` of kind `POSTPAID_SIM`. A missing,
   unknown, Prepaid, other-Contract or non-billing SIM is a **400** with the
   message "Not a Postpaid SIM line of this Client Invoice".
3. refuses a SIM that already has a file on this invoice with a **409** coded
   `CARRIER_INVOICE_ALREADY_ATTACHED`. The unique constraint backs it up. A
   constraint violation at commit maps to the same coded 409.
4. computes the name with `CarrierInvoiceFileName`, from the invoice's
   Contract's Client, the invoice's billing month, and the SIM's Carrier and
   number. It saves the row with `sim_card_id`, then logs `AuditLog.created`
   as today.

A request without `simCardId` is a 400 at the controller, before any upload.

### Reading: what the response carries

- `CarrierInvoiceFileResponse` gains `simCardId` (nullable UUID). It is `null`
  only for a row from before this feature.
- `ClientInvoiceBaseSimLineResponse` gains `carrierInvoiceFileId` (nullable
  UUID, always serialised): the id of the file on this invoice linked to the
  line's SIM, or `null`. `toResponse` reads the invoice's files once and maps
  them by SIM. The check mark is this field being non-null. The Tester's
  response carries it too, and the Tester's page does not show SIM lines.
- A file whose SIM has no line on the invoice is listed in `files` as usual.
  It sets no line's `carrierInvoiceFileId` (story 16).

### The send gate

In `ClientInvoiceService.send`, under the row lock, after the status check
and `requireOpenToAgent`, and before any line row is written:

- `required` = the SIM ids of every line of kind `POSTPAID_SIM` in
  `lines(invoice)`, the same resolution the send then stores. On a never-sent
  draft, these are the billing Postpaid SIMs plus any override row still
  shown. On a sent-back draft, they are the stored rows.
- `attached` = the `sim_card_id`s of the invoice's Carrier Invoice File rows.
- When `required` minus `attached` is not empty, throw
  `ClientInvoiceConflictException(Reason.CARRIER_INVOICES_MISSING, "…")`. The
  message names the count and the SIM numbers, for logs and API callers. The
  invoice stays `DRAFT` with nothing written.
- No `POSTPAID_SIM` line (no Postpaid SIM, or a legacy `BASE_AMOUNT` invoice)
  means nothing is required (stories 14 and 10).
- **The gate never touches the object store.** It reads rows, so it is
  unaffected by `filesAvailable` (story 17). The row lock already serialises
  it against attach and remove.

`ClientInvoiceConflictException.Reason` gains `CARRIER_INVOICES_MISSING` and
`CARRIER_INVOICE_ALREADY_ATTACHED`.

### Removing

A new route, `DELETE /api/client-invoices/{invoiceId}/files/{fileId}`,
answers **204**. It is by id only, because the frontend reaches every invoice
by id. Access: `ClientInvoiceAccessGuard.requireCanSend`, which allows the
Contract's own Agent only. A Manager, a Tester or another Agent gets 403. The
order mirrors attach, the other way round. The row goes first, so a reader
never sees a row without its object:

1. `CarrierInvoiceFileService.remove(invoice, fileId, principal)`: if
   `isAvailable()` is false, answer 503 at once (story 23).
2. `ClientInvoiceService.removeFile(found, fileId, principal)`, a
   `@Transactional` method. It re-reads the invoice under the row lock,
   requires `DRAFT` (409) and open-to-Agent (409
   `PAST_MONTH_DRAFT_NOT_SENDABLE`), finds the row by id and invoice (404),
   deletes it, logs a new `AuditLog.carrierInvoiceFileRemoved(invoiceId,
   fileId, simCardId, actor, tenant)`, and returns the row's key.
3. After the commit, outside the lock, `storage.delete(key)`. If that fails,
   it logs WARN "stray object, no row refers to it" with the key and still
   answers 204: the file is gone from the invoice, as the Agent asked.
   `CarrierInvoiceFileStorage` marks the store unavailable, as it does on any
   failure.

### Demo data

`DemoDataLoader` sends contract US-1's current-month invoice through the
controller, so the gate would now refuse it. Before that send, the loader
attaches one small generated PDF per Postpaid SIM line, through
`CarrierInvoiceFileService.attach`. The loader runs before the startup probe
has marked the store available, so it uses the storage `put` and
`recordAttachedFile` directly, with the same undo. If the store refuses, the
loader logs one WARN and leaves that invoice as a draft, so the demo always
starts. The approved past invoices written directly by the loader stay
without files. They read as invoices from before this rule.

### Frontend

**Types.** `CarrierInvoiceFile` gains `simCardId: string | null`.
`ClientInvoiceBaseSimLine` gains `carrierInvoiceFileId: string | null`. One
helper, `missingCarrierInvoiceLines(invoice)`, returns the Postpaid SIM lines
whose `carrierInvoiceFileId` is null. The Attach control, the send note and
the tests all use it.

**The attach form.** `AttachCarrierInvoiceFileControl` becomes a button that
opens `AttachCarrierInvoiceDialog`. It follows the dialog prior art:
`DialogShell`, `DialogErrorAlert`, and `PickerSelect` as `LogFeeDialog` uses
it.

- **Title**: "Attach carrier invoice".
- **"Postpaid SIM" field**, required. One option per line from
  `missingCarrierInvoiceLines`, labelled `{number} · {Carrier}` (or the bare
  number for a SIM with no Carrier). Preselected when exactly one is left.
- **"File" field**, required: one native file input, no `multiple`, any type.
- **Buttons**: Cancel, and Attach (primary).
- **The request.** It posts `file` and `simCardId` to the by-id route through
  the existing BFF route, unchanged, which forwards the multipart body as is.
- **On success**, the dialog closes and the page refreshes (`refreshPage`).
- **Errors** stay in the dialog, which stays open:
  - 409 `CARRIER_INVOICE_ALREADY_ATTACHED`: "This SIM already has its carrier
    invoice. Remove it first to replace it."
  - 503: "File storage is unavailable right now. Try again later."
  - any other failure: "Couldn't attach the file. Try again."
  - network: the existing "Couldn't reach the server…" line.
- **The button.** It is shown on an editable draft that has at least one
  Postpaid SIM line. It is disabled while `filesAvailable` is false or when no
  line is missing its file. It is absent when there are no Postpaid SIM lines
  (story 9).

**The check mark.** On the Agent's card (`AgentClientInvoiceCard`) and the
Manager's detail (`ClientInvoiceDetailView`), a Postpaid SIM line whose
`carrierInvoiceFileId` is set shows `IconCheckCircle` after the
"{Carrier} · Postpaid" label. It carries an accessible name, "Carrier invoice
attached", and is not interactive. A line without its file shows nothing
extra. Both views render it through one small shared component,
`CarrierInvoiceCheck`, so the two copies of the layout (debt :17) do not
drift further.

**The send gate on the page.** `SendClientInvoiceControl` takes a
`missingCarrierInvoices` count.

- **While the count is above 0**, "Send Client Invoice" is disabled. Under it
  sits one `text-label-sm` `ink-mute` line, linked by `aria-describedby`:
  "1 Postpaid SIM still needs its carrier invoice." or "{n} Postpaid SIMs
  still need their carrier invoice."
- **The send's error body is read.** A 409 with code
  `CARRIER_INVOICES_MISSING` shows "A Postpaid SIM still needs its carrier
  invoice. Reload to see which." Any other failure keeps "Couldn't send. Try
  again."

**Remove.** `CarrierInvoiceFilesSection` takes an optional per-file action
slot. The Agent's card passes a `RemoveCarrierInvoiceFileControl` for each
file on an editable draft.

- **The trigger**: a quiet "Remove" text button beside Download, with the
  accessible name "Remove {filename}".
- **The confirm**: an inline second step, following the Send control's focus
  handling: the line "Remove this file?", then "Cancel" and a danger-toned
  "Remove".
- **The request**: a `DELETE` through a new BFF `DELETE` handler on the
  existing `files/[fileId]` route, with `forwardJson` (Frontend 4, 13).
- **On success**, the page refreshes.
- **On failure**: "Couldn't remove the file. Try again."
- **While paused** (`filesAvailable` false), the trigger is disabled like
  Attach. With Remove offered, the section's notice reads "File storage is
  unavailable right now. Attaching, removing and downloading are paused." The
  Manager's and the Tester's wording does not change.

**The visual stub backend.** It serves `carrierInvoiceFileId` on its SIM
lines: the first line has a file and the second does not. Its `files` lists
that one file under a computed name. The Agent's draft golden then shows both
states of a line, plus the disabled Send with its note.

## Design direction

Design slot: `impeccable`. `DESIGN.md` is the world. The feature adds no
token, no colour and no new component style, only uses of existing ones.

- **The Agent's Client Invoice card**: Operate. The check mark reads as a
  quiet confirmation: `IconCheckCircle`, `h-3.5 w-3.5`, `text-success`, inline
  after the muted Carrier label. It is no badge and no extra column, and the
  row layout and the `xl` area layout do not move. A missing file is the
  absence of the mark. The Send note says the count, so the rows need no red
  "missing" marker. No danger tone appears on a draft that is simply
  unfinished.
- **The disabled Send**: the primary button's existing disabled look (40%
  opacity), with the note under it, right-aligned like the confirm text.
  There is one plain sentence and no blame.
- **The attach dialog**: Operate, using the existing dialog surface, field
  labels and `PickerSelect`. Two fields and two buttons. Primary is the only
  pill.
- **Remove**: a quiet `text-label-sm` `ink-mute` text button, beside Download
  in the file row. Its confirm step uses the danger tone, the system's tone
  for destructive actions. It is inline, with no modal for one file.
- **The Manager's invoice detail**: Read. The same check mark and nothing to
  act on.
- **The Tester's Invoices page**: unchanged apart from the file names.
- **`DESIGN.md`** gains one line under Components: "A Postpaid SIM line whose
  carrier invoice is attached carries `IconCheckCircle` (`h-3.5`, `success`)
  after its Carrier label, non-interactive, named 'Carrier invoice attached'.
  A file row's Remove is a quiet `ink-mute` text action whose inline confirm
  is danger-toned." The paused-section line gains "removing".

## Constraints

- Inherits every constraint of `carrier-invoice-object-storage` (ADR 0007),
  `send-a-client-invoice-back` and `edit-client-invoice-lines`, except where
  this spec says otherwise.
- **One Carrier Invoice File per (Client Invoice, SIM Card)**: the
  `uq_carrier_invoice_files_invoice_sim` constraint. A new row always has a
  `sim_card_id`.
- **The computed name.** Exactly
  `{Client} - {Month Year} - {Carrier} - {SIM number}{.ext}`, English month
  name, the parts separated by space, hyphen, space. At most 255 characters,
  with only the Client part shortened to fit. `/ \ : * ? " < > |` and control
  characters become `_`. It is computed only by the backend, at attach, and
  never recomputed.
- **HTTP contract changes. Nothing else changes.**
  - Attach requires the multipart part `simCardId`:
    - 400 when it is missing or is not a Postpaid SIM line of the invoice;
    - 409 `CARRIER_INVOICE_ALREADY_ATTACHED` when that SIM already has its
      file.
  - Send can answer 409 `CARRIER_INVOICES_MISSING`.
  - New `DELETE /api/client-invoices/{invoiceId}/files/{fileId}`:
    - 204 on success;
    - 403 for anyone but the Contract's Agent;
    - 404 for an unknown file;
    - 409 when the invoice is not a draft open to the Agent;
    - 503 while the store is unavailable.
  - `CarrierInvoiceFileResponse.simCardId` and
    `ClientInvoiceBaseSimLineResponse.carrierInvoiceFileId` are added.
  - Every coded 409 body is `{code, message}` on both the by-id and the
    current-month routes.
- **The send gate reads only database rows**, under the row lock send already
  takes, and never calls the store.
- **No store call inside a transaction that holds a Client Invoice row lock**
  (Backend 8). Remove deletes the object after the commit.
- **Migration**: one forward-only Flyway script (Backend 9), at the number the
  orchestrator reserves (V61 at the time of writing). It adds a column and a
  constraint and rewrites nothing.
- **No new dependency**, in either main or test scope.
- Upload limit unchanged: one file per request, 20MB.
- Every isolated run (tests, e2e, acceptance) uses its own throwaway Postgres
  and SeaweedFS. Nothing touches the human's compose project `rsa-prod`, its
  ports 3000/8080/5432 or its volumes. The e2e suite runs on an idle machine,
  and a failure is never called a flake without a controlled comparison.
  Maven runs with JDK 21.

## Testing decisions

Tests assert external behaviour: HTTP status and body, the rows a request
leaves, what is in the bucket, and what a user sees and can do on the page.

1. **The HTTP seam, against real Postgres and SeaweedFS (`IntegrationTest`,
   `ObjectStoreTestSupport`).** New cases go in `ClientInvoiceByIdApiTest` and
   `ClientInvoiceApiTest`, next to the existing attach and download cases.
   Attaching:
   - attach with `simCardId` gives 201, `simCardId` in the body, the
     computed name, and the SIM line's `carrierInvoiceFileId` set;
   - attach without `simCardId` gives 400 and no object under the invoice's
     prefix;
   - attach naming a Prepaid SIM, a SIM of another Contract, or a SIM not
     billing this month gives 400;
   - a second attach for the same SIM gives 409
     `CARRIER_INVOICE_ALREADY_ATTACHED`, leaving one row and one object.

   Sending:
   - send with a missing line gives 409 `CARRIER_INVOICES_MISSING`; the
     invoice is still `DRAFT`, `linesStored` is false and no line row is
     written;
   - on both routes, the body is `{code, message}`;
   - send with every line covered gives 200;
   - send with no Postpaid SIM gives 200;
   - send after a send-back is gated on the stored lines;
   - a file whose SIM stopped billing neither counts nor blocks;
   - send succeeds while `ObjectStoreAvailability` reports unavailable when
     every row exists (made unavailable through its `markUnavailable()`).

   Removing:
   - the Agent gets 204 on a current-month draft and on a sent-back draft,
     the row is gone, and `keysUnder` no longer lists the key;
   - removing from a sent invoice gives 409, and from a past-month draft
     never sent gives 409 `PAST_MONTH_DRAFT_NOT_SENDABLE`;
   - a Manager, a Tester and another Agent each get 403;
   - an unknown file gives 404;
   - while unavailable, the answer is 503 and the row stays.

   Prior art: `ClientInvoiceApiTest`'s attach/list/download cases and
   `ClientInvoiceSendBackApiTest`'s coded-409 assertion.
2. **Concurrency.** `CarrierInvoiceFileAttachConcurrentSendTest` gains a
   case: an attach and a send racing on an invoice missing only that SIM's
   file. Either the attach wins and the send succeeds, or the send gets the
   coded 409 and the attach succeeds. Never a sent invoice with a line
   missing its file.
3. **Unit tests (JUnit 5 + Mockito, Backend 6 and 12).**
   - `CarrierInvoiceFileName`, table-driven: the plain case; an archived
     Carrier; no Carrier; each unsafe character; whitespace runs; extension
     kept, lowercased, absent or rejected (`scan.final version`); a 255-cut
     that shortens only the Client part; a non-ASCII Client name kept.
   - `CarrierInvoiceFileService` adds:
     - remove deletes the object only after `removeFile` returns;
     - a failing delete logs WARN and does not throw;
     - an exception from `removeFile` deletes nothing;
     - while unavailable, remove never calls the storage.
4. **The existing backend tests that send an invoice with a Postpaid SIM**
   (the Client Invoice, send-back, line-edit, Agent Invoice, Review Queue and
   dashboard API tests, among others) attach a file per Postpaid SIM line
   first. They go through one new test-support helper beside
   `ObjectStoreTestSupport`, for example
   `attachCarrierInvoicesForEveryPostpaidLine(invoiceId, agentToken)`, which
   posts a tiny file per line through the real endpoint. Nothing they assert
   changes. `DemoDataLoaderApiTest` asserts that the demo's sent invoice has
   one file per Postpaid SIM line.
5. **Frontend component tests (Vitest + Testing Library)**, at the highest
   seam that renders each piece.
   - The attach dialog, in a new test beside it:
     - it lists only the missing lines, labelled `{number} · {Carrier}`;
     - with one line left it is preselected;
     - it posts `file` and `simCardId`;
     - each error code shows its text, and the dialog stays open.
   - `client-invoices-view.test.tsx` (the Agent's card, through the view):
     - a covered line shows "Carrier invoice attached" and an uncovered one
       does not;
     - Send is disabled with the count note, and enabled with none missing
       or no Postpaid lines;
     - Attach is disabled when nothing is missing and absent with no
       Postpaid lines;
     - a send refused with `CARRIER_INVOICES_MISSING` shows its text;
     - Remove opens its confirm and sends a `DELETE`;
     - Remove is disabled while paused, with the new notice wording.
   - `client-invoice-detail-view.test.tsx` (the Manager): the check mark
     shows, and there is no Remove and no Attach.
   - `carrier-invoice-files-section.test.tsx`: the action slot renders, and
     the paused wording names removing only when Remove is offered.
   - Fixtures gain the two new fields.
6. **End to end, on the isolated stack.**
   - `helpers.ts` gains `attachCarrierInvoice(page, simNumber)`, which goes
     through the form, and `attachCarrierInvoicesForEveryPostpaidSim(page)`.
   - Every spec that sends a Client Invoice with a Postpaid SIM calls the
     second helper before "Send Client Invoice":
     - `client-invoice-submission-and-visibility`
     - `client-invoice-generation`
     - `edit-client-invoice-lines`
     - `send-a-client-invoice-back`
     - `client-invoices-side-list`
     - `manager-dashboard`
     - `client-dashboard`
     - `manager-invoice-review-queue`
     - and any other the run reveals.
   - `client-invoice-generation.spec.ts` replaces its hidden-input attach with
     the form, and asserts:
     - the computed name;
     - the check mark;
     - Send disabled until every line is covered.
   - `send-a-client-invoice-back.spec.ts` adds removing a file after the
     send-back, attaching its replacement and resending.
   - The journey proof `client-invoice-submission-and-visibility.spec.ts`
     asserts the Manager's check mark.
   - Prior art: `client-invoice-generation.spec.ts:26-88`.
7. **Visual goldens.** The four `agent-client-invoices-{desktop,mobile}-{light,dark}.png`
   goldens change, in the same commit as the stub and the code that move
   them:
   - one line with its check mark and one without;
   - the disabled Send with its note;
   - the file listed under a computed name.

   No other golden should move. One that does is a defect. No surface is
   added. The dialog and the Remove confirm are covered by component tests
   and the walkthrough.
8. **`verify`** stays the command in `docs/agents/sdlc.json`.

## Decisions taken

- **Spec approved by the human, 2026-10-17** (`docs/inbox/approve-carrier-invoice-per-sim.md`, "ok"), including the four decisions it listed (remove on any open draft; send during a store outage when every file is present; name fixed at attach, extension kept, unsafe characters to `_`; remove confirmed by a second click).

- **The gate counts every `POSTPAID_SIM` line the invoice shows, the same `lines(invoice)` the send stores.** Reason: the check marks and the gate then follow one rule, and a line's file is required exactly when the line is billed.
- **A file whose SIM has no line stays listed, removable, and neither counts nor blocks.** Reason: deleting it silently would lose the Agent's upload. Flagging it would add UI for a rare case: a SIM that left the Fleet before the month began, or an override reset to zero. Its name already carries its SIM number.
- **Removal is allowed on any draft open to its Agent: a current-month draft never sent, as well as one sent back.** The human said "after a send-back"; this extends it to the never-sent draft. Reason: one file per SIM means a wrong file attached before the first send could otherwise never be replaced. "Open to its Agent" is already the one gate of every Agent write.
- **The send gate reads rows only, so file storage being unavailable does not block a send when every line has its file, and does block one when a file is missing** (the open point left by `carrier-invoice-object-storage`). Reason: the human's rule is "can't send without the related carrier invoices", with no exception. The rows are the truth about what is attached, and the bytes of an attached file are already in the store.
- **The name is computed at attach and stored, never recomputed.** Reason: the human asked to "store it with a computed name", and the epic says the name lives in the database. A carrier's bill names the SIM as it was. On a draft, the Agent can remove and re-attach to get a corrected number.
- **Month as `October 2026` (English, `Locale.ENGLISH`).** Reason: it is the text every invoice page already shows for the billing month (`formatBillingMonth`).
- **Keep the uploaded file's extension (1 to 10 ASCII letters or digits, lowercased), or none.** Reason: the downloaded file must open in the right application. Content-type mapping would guess wrong for less common types.
- **Unsafe characters become `_`; the Client part is cut to fit 255.** Reason: `filename` is `VARCHAR(255)`, and operating systems refuse those characters. The Client is the part best known from context.
- **A SIM with no Carrier leaves the Carrier part out.** Reason: the human says it cannot happen, but the schema allows it. Failing an attach over it would block a send.
- **No restriction on file type.** Reason: the human asked for none. Carriers send PDFs and sometimes images, and today's upload accepts any type.
- **No preview of the computed name in the form.** Reason: the frontend would need a second copy of the naming rule. The list shows the real name a moment later.
- **Attach is disabled when nothing is missing, and absent when there is no Postpaid SIM line.** Reason: there is nothing to attach in either case. A disabled button still says where attaching lives.
- **Send is disabled with a count note, and the server also refuses with a coded 409.** Reason: the Agent learns the rule before clicking. The server stays authoritative for a stale page. The note gives a count, and the rows' check marks show which SIMs are missing.
- **The check mark appears on the Manager's detail too, read-only.** Reason: the Manager approves, and the human wanted the row to show that the SIM's file was uploaded. That holds for whoever looks at the row.
- **Nothing is added to the Tester's page.** Reason: it shows no SIM lines today, and a check mark has no row to sit on there.
- **Remove is the Contract's own Agent's only (`requireCanSend`); Manager attach through the API is left as is.** Reason: the human settled that only the Agent attaches in the UI. Narrowing the Manager's API permission is out of scope and no screen offers it.
- **Remove deletes the row under the lock, then the object after the commit; a failed object delete is a WARN, and the answer is still 204.** Reason: a reader never sees a row without its object. A stray object is invisible and reportable. Deleting the object first could leave a row whose download 404s if the transaction then refused.
- **Remove is disabled and answers 503 while the store is unavailable.** Reason: Attach is paused too, so the replacement could not follow. One paused state for all file actions, and no stray objects from an outage.
- **The paused notice reads "Attaching, removing and downloading are paused." where Remove is offered.** Reason: the notice names exactly what the section offers, the rule its first wording followed.
- **Remove's confirm is inline, not a dialog.** Reason: it is one file and one sentence. It follows the Send control's inline two-step and its focus handling.
- **`carrierInvoiceFileId` on the SIM line, not a boolean.** Reason: it says the same thing, and a later change (retention's "Deleted on", a jump to the file) can use the id without changing the contract again.
- **`sim_card_id` nullable, with a plain unique constraint.** Reason: pre-feature dev and test rows keep working. Postgres's handling of `NULL` lets them through. No backfill exists to make it `NOT NULL`, and a later contract migration can tighten it.
- **The demo loader attaches through storage `put` plus `recordAttachedFile`, and on a store failure leaves the invoice a draft with a WARN.** Reason: the demo must start whatever the store's state (ADR 0007), and its sent invoice must obey the rule it demonstrates.
- **One shared `CarrierInvoiceCheck` for both views.** Reason: the Agent and Manager invoice layouts are already duplicated (debt :17). The new mark must not become a third copy.
- **The coded-409 handler covers the current-month controller too.** Reason: a refused send on that route would otherwise lose its `code`.
- **Test seams: the existing HTTP seam with real Postgres and SeaweedFS, component tests at the view, e2e through the real form; no new seam.** Reason: those seams already prove attach, send and send-back. One helper per suite keeps the many sending tests unchanged in what they assert.
- **Five tickets, one PR (below).** Reason: the per-SIM attach is the foundation, the computed name and the check mark are their own slices, and the gate and Remove each build on the check mark and are each demoable alone.
- **The ticket cut's second-round critic gaps (R2 split, two R5 cases, two R6 files) were closed by the orchestrator**, all mechanical; no behaviour changed.

## Open questions

None

## Acceptance walkthrough

The `[agent]` steps run against an isolated stack the agent starts and tears
down itself. It never touches the human's compose project `rsa-prod`, ports
3000/8080/5432 or its volumes. The stack is:

- a throwaway Postgres and a throwaway SeaweedFS (`docker run --rm`, exact
  names, free ports, the pinned tag, dev-only credentials);
- the backend on the `demo` profile on a free port;
- a production build of the frontend on a free port.

Logins are in the root `README.md`; Jordan is the demo Agent. "List the
bucket" means an S3 API listing against that SeaweedFS.

1. [agent] (stories: 29) Run `verify` on an idle machine. Show:
   - it is green;
   - only the four `agent-client-invoices-*` goldens changed, and each shows a check mark on one SIM line, the disabled Send with its note, and a computed file name;
   - no throwaway container is left behind.

2. [agent] (stories: 10, 11) Set up the human's example. As the Manager, on one of Jordan's Contracts with no Postpaid SIM yet, add three Postpaid SIMs (two on Verizon, one on AT&T) and one Prepaid SIM. As Jordan, open that Contract's current-month draft. Show:
   - exactly three Postpaid SIM lines, each "{number}" with "{Carrier} · Postpaid", no check mark;
   - no line for the Prepaid SIM;
   - "Send Client Invoice" disabled, with "3 Postpaid SIMs still need their carrier invoice."

3. [agent] (stories: 1, 3, 4, 7) Click "Attach carrier invoice". Show the form lists the three lines as `{number} · {Carrier}`. Pick the first, choose `scan.PDF`, and attach. Show:
   - the dialog closes;
   - the file is listed as `{Client} - {Month Year} - Verizon - {number}.pdf`;
   - that line shows the "Carrier invoice attached" check mark;
   - the note reads "2 Postpaid SIMs still need their carrier invoice."

   Download it and show the saved name equals the listed name and the SHA-256 equals the upload's.

4. [agent] (stories: 2, 8, 12) Through the API as Jordan, show:
   - attach without `simCardId` is 400;
   - attach with the Prepaid SIM's id is 400;
   - attach again for the first SIM is 409 `{code: CARRIER_INVOICE_ALREADY_ATTACHED}`;
   - `POST /send` is 409 `{code: CARRIER_INVOICES_MISSING, message}` naming the two missing numbers, and the invoice is still a draft;
   - the same send through the current-month route also carries `code`.

   In the browser, with the dialog open on a SIM that was attached meanwhile through the API, attach. Show the "already has its carrier invoice" text, with the dialog still open.

5. [agent] (stories: 1, 2, 9, 26) Reopen the form. Show it offers only the two remaining lines. Attach one; the form preselects the last line; attach it. Show:
   - three check marks;
   - "Attach carrier invoice" disabled;
   - Send enabled.

   Send. Then:
   - list the bucket and show exactly three objects under `carrier-invoices/{tenant}/{invoice}/`, keyed by file ids, with no `x-amz-meta-*`;
   - query `carrier_invoice_files` and show each row's `sim_card_id` and computed `filename`.

6. [agent] (stories: 12, 13) With the page loaded on a fully covered draft of another Contract, add a fourth Postpaid SIM as the Manager. As Jordan, without reloading, confirm send. Show "A Postpaid SIM still needs its carrier invoice. Reload to see which." After a reload, show the new line has no check mark and Send is disabled.

7. [agent] (stories: 4, 21, 22, 24, 25) As the Manager, open the sent invoice's `/manager/invoices/client/{id}`. Show:
   - each Postpaid SIM line with the check mark;
   - no Attach and no Remove.

   Through the API, `DELETE` a file as the Manager: 403. As the Tester of that Client, show the Invoices page lists the three files under their computed names, and one downloads. Through the API as the Tester: 403. As Jordan on the sent invoice: no Remove, and the API answers 409.

8. [agent] (stories: 15, 18, 19, 20) As the Manager, send the invoice back. As Jordan:
   - Remove the AT&T file and confirm. Show the file gone, that line's check mark gone, Send disabled with "1 Postpaid SIM still needs its carrier invoice.", and the form offering that line again. List the bucket: its object is gone.
   - Attach a replacement. Show the check mark back, then resend.

9. [agent] (stories: 18, 21) On another current-month draft never sent, as Jordan, attach a file and remove it, and show it works. Through the API, on a past month's draft never sent, `DELETE` a file: 409 `PAST_MONTH_DRAFT_NOT_SENDABLE`.

10. [agent] (stories: 5, 6) Correct the number of a SIM whose file is attached, through Fleet's correction. Show:
    - the line shows the new number with its check mark still there;
    - the file keeps its original name.

    Create a Client named `Acme/Labs "Test": North` with a Contract and a Postpaid SIM, and attach a file to its draft. Show the name reads `Acme_Labs _Test__ North - …` and downloads under that name.

11. [agent] (stories: 9, 14) On a Contract whose month has only a Prepaid SIM, show no "Attach carrier invoice", Send enabled, and the send succeeds.

12. [agent] (stories: 17, 23) On a draft where every line has its file, stop the throwaway SeaweedFS (started without `--rm` for this step) and reload twice, the first reload at least 30 seconds after the last invoice page load (the availability check is cached for 30 seconds, as the object-storage feature settled). Show:
    - the files section paused, with "Attach carrier invoice", each Download and each Remove disabled;
    - the notice "File storage is unavailable right now. Attaching, removing and downloading are paused.";
    - Send enabled, and the send succeeds;
    - through the API, `DELETE` is 503 at once.

    On another draft missing a file, show Send disabled with its note. Start SeaweedFS again.

13. [agent] (stories: 16) Make a SIM leave a never-sent draft's lines after its file was attached. Use a Return kept in Agent Stock, or whichever path the implementer found that takes it out of the Contract's billing Postpaid SIMs for the month, and name it in the evidence. Show:
    - its file still listed and removable;
    - no line for it;
    - it neither counts in the note nor blocks Send.

14. [agent] (stories: 27, 28, 30) Restart the backend on a fresh demo database. Show:
    - contract US-1's current-month invoice is sent, with one file per Postpaid SIM line and a check mark on each;
    - the demo with SeaweedFS stopped at startup still starts, logs one WARN, and leaves that invoice a draft.

    Show the declared `CONTEXT.md` entry for Carrier Invoice File and the `DESIGN.md` line.

15. [human] (stories: 1, 3, 7, 11, 18, 24) On your own stack (`rsa-prod`), rebuild with your usual `up -d --build`. On a Contract with Postpaid SIMs:
    - open this month's Client Invoice and check Send is disabled with the count note;
    - attach a real carrier PDF for each Postpaid SIM through the form;
    - check each name reads `{Client} - {Month Year} - {Carrier} - {SIM number}.pdf` and each line shows its check mark;
    - send;
    - as the Manager, check the marks and send it back;
    - as the Agent, remove one file, attach it again, and resend.

    Confirm it reads and behaves as you expected.

## Execution order

Five tickets, one PR. The computed name and the check mark each depend on the
per-SIM attach; the gate and Remove each depend on the check mark, and not on
the computed name (they read rows and SIM links, and work with the placeholder
name).

1. `attach-carrier-invoice-per-sim`: V61 (`sim_card_id` and the unique
   constraint), the required `simCardId` on attach (400 and the coded 409),
   `simCardId` in `CarrierInvoiceFileResponse`, the shared coded-409 handler,
   the stored name a placeholder (the uploaded name). On the frontend: the
   attach dialog, the `missingCarrierInvoiceLines` helper (reading
   `files[].simCardId`), the types, the e2e `attachCarrierInvoice` helper with
   `client-invoice-generation.spec.ts` moved to the form. Labels: backend,
   frontend. No dependency. (stories: 1, 2, 8, 9, 10, 16, 26, 27)
2. `carrier-invoice-computed-name`: `CarrierInvoiceFileName` and its unit
   test, the stored and downloaded computed name, the number-correction case,
   the Tester's names, the `CONTEXT.md` declaration. Labels: backend. Depends
   on `attach-carrier-invoice-per-sim`. (stories: 3, 4, 5, 6, 25, 30)
3. `carrier-invoice-check-mark`: `carrierInvoiceFileId` on the SIM line
   response, the shared `CarrierInvoiceCheck` on the Agent's card and the
   Manager's detail, the stub, the four `agent-client-invoices-*` goldens and
   the `DESIGN.md` check-mark line. Labels: backend, frontend. Depends on
   `attach-carrier-invoice-per-sim`. (stories: 7, 24)
4. `send-requires-every-carrier-invoice`: the `CARRIER_INVOICES_MISSING`
   gate in `send`, the disabled Send with its count note and the coded error
   text, the backend test helper and every sending test using it, the demo
   loader's attach before send, every sending e2e spec calling the helper,
   and the four goldens recaptured for the note. Labels: backend, frontend.
   Depends on `carrier-invoice-check-mark` (the shared goldens and stub).
   (stories: 11, 12, 13, 14, 15, 17, 28, 29)
5. `remove-carrier-invoice-file`: the `DELETE` route (row under the lock,
   object after commit, 503 while unavailable), `AuditLog`'s removal event,
   the BFF `DELETE`, the Remove control with its inline confirm and paused
   state, the notice wording, the `send-a-client-invoice-back.spec.ts`
   replacement flow, and the `DESIGN.md` Remove line. Labels: backend,
   frontend. Depends on `carrier-invoice-check-mark`. (stories: 18, 19, 20,
   21, 22, 23)
