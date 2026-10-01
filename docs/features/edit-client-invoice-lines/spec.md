---
feature: edit-client-invoice-lines
epic: invoice-correction-and-history
status: draft
date: 2026-10-01
---

<!-- sdlc:template spec 1 -->

# Edit a Client Invoice's lines

## Problem

A Client Invoice is a base amount plus that month's Fees. Today nobody can
change a figure on it. While it is a draft, every number is computed live
from the Contract's Fleet and Fees (ADR 0001). When the Agent sends it, those
computed numbers freeze.

The computation is not what the Client is really billed. A Postpaid SIM's
monthly fee is fixed when the SIM is provisioned, but the carrier's real bill
for that SIM goes up with the Client's usage. The Agent holds the carrier's
invoice and knows the real figure, but has no way to put it on the Client
Invoice. So the invoice either goes out wrong, or the Agent works around it.

The human settled this on 2026-10-01 (epic `## Reworked`, and
`docs/inbox/approve-send-a-client-invoice-back.md` `## Answer`): "Client
invoice is base amount plus the fees, consider everything editable … the
agent can edit each line as even if postpaid is defined monthly, depending on
client usage it can go up and the agent needs to edit it." The computation
only **pre-fills** the lines; the Agent enters what was actually billed.

There is a second reason to build this now. `send-a-client-invoice-back`
returns a sent invoice to draft, and the human said "if an invoice is sent
back, nothing refreshes but its just editables". That works only if a sent
invoice **holds its own lines**. Today it holds a base amount and a list of
which Fees counted, and reads each Fee's amount from the Fee itself, which can
never be edited. This feature gives the invoice its own lines, and send-back
builds on them.

## Journeys

Advances `docs/roadmap/invoice-correction-and-history.md`. It is now the
**first** of its features: `send-a-client-invoice-back` builds on it.

- **Bill the Client for the month** (`exists`, stays `exists`). The draft
  still drafts itself from the base amount and the month's Fees. The Agent can
  now adjust any line before sending. The numbers still freeze at send.
- **Send an invoice back for correction** (`wanted`, stays `wanted`). Nothing
  of the send-back is built here. This feature makes "a sent-back draft keeps
  the numbers it was sent with, and the Agent edits them" possible without a
  second data model.
- **Work through what is waiting** (`exists`, stays `exists`). The Review
  Queue's totals come from the stored lines, so they show the edited total.
- **Get the Agent paid for the month** (`exists`, untouched, if open question
  1 is answered as recommended).

## Goals / Non-goals

**Goals**

- While a Client Invoice is a draft, its Agent can change the amount of every
  line: each Postpaid SIM's line in the base amount, and each Fee line.
- Lines start pre-filled from the computation. On a draft that has never been
  sent, lines the Agent has not touched keep following the Fleet and Fees, and
  a new Postpaid SIM or a new Fee appears as a new pre-filled line. A line the
  Agent edited keeps the Agent's amount.
- An edited line shows that it was edited and what the computation gave. The
  Agent can reset it.
- Sending freezes every line, edited or not, as the invoice's own stored
  lines. From then on every read serves those lines.
- Every Client Invoice already sent or approved before this change reads
  exactly as it does today.
- The Manager sees which lines were edited, and by how much, before
  approving. The Tester and the PDF see only the billed amounts.

**Non-goals.** Each of these is something a reasonable agent would otherwise
build.

- **No send-back.** No `SENT → DRAFT` edge, no reason, no by-id Agent routes.
  That is `send-a-client-invoice-back`, which is being revised to build on
  this. This feature only makes its data model natural.
- **No editing a Fee.** The Fee keeps its amount, description, month and
  Request. The Fee list, the Request's view and the Agent Invoice keep showing
  the Fee as logged. Only the invoice's own copy of the line changes.
- **No editing a line's description, Fee type or SIM.** Only the amount.
- **No adding a free-form line, and no deleting a line.** A new charge is
  logged as a Fee, so it still traces to a Request. A line that should bill
  nothing is set to zero.
- **No negative amount, no credit, no discount line.** Correcting an amount
  after sending or approval is the Manager's `invoice-adjustment`.
- **No Manager edit.** The Manager reviews and approves. The Agent Invoice
  override (ADR 0003) is unrelated and unchanged.
- **No editing a sent or approved invoice.** ADR 0001's freeze holds from
  `sent` onward.
- **No change to the Agent Invoice** and its Local Support Fees (ADR 0002),
  if open question 1 is answered as recommended.
- **No reason or note on an edit**, if open question 4 is answered as
  recommended.
- **No history of a line's edits** beyond the audit log. A line keeps its
  current amount and its computed amount.
- **No change to the Tester's page layout or the PDF layout.** Both show the
  billed amounts in the shape they use today.
- **No dropping of the old snapshot structures** (`snapshotBaseAmount`,
  `ClientInvoiceFeeSnapshot`). They stop being written and read, and stay in
  the schema. Dropping them is a later contract step.

## User stories

1. As an Agent, I want my Client Invoice draft to open with every line pre-filled, one per Postpaid SIM billing that month and one per Fee of that month, exactly as the draft is computed today, so that I start from the computed figures.
2. As an Agent, I want to change the amount of any Postpaid SIM's line on a draft, so that the invoice bills what the carrier really charged for that SIM, usage included.
3. As an Agent, I want to change the amount of any Fee line on a draft, so that the invoice bills what was really spent.
4. As an Agent, I want the base amount, the Fees total and the invoice total to recompute from the lines as soon as I save an edit, so that I always see the total I am about to send.
5. As an Agent, I want an edited line marked as edited, with the computed amount shown beside it, so that I can see what I changed and from what.
6. As an Agent, I want to reset an edited line to its computed amount, so that I can undo a mistake in one step.
7. As an Agent, I want to be able to set a line to zero, and to be refused a negative, blank or over-precise amount with an inline message that keeps what I typed, so that I fix the input rather than start again.
8. As an Agent working on a draft not yet sent, I want a Postpaid SIM added or a Fee logged after my edits to appear as a new pre-filled line, and the lines I have not touched to keep following the Fleet and Fees, so that nothing is left off.
9. As an Agent, I want a line I edited to keep my amount when the Fleet or the Fees change afterwards, so that my correction is never silently overwritten.
10. As an Agent, I want sending to freeze exactly the lines and amounts I see, edited or not, so that the Manager reviews what I prepared.
11. As an Agent, I want no edit controls on a sent or approved invoice, and an edit from a stale page refused with a message telling me to refresh, so that a figure under review never moves.
12. As an Agent, I want to edit a line without having to write a reason, so that correcting a figure from the carrier's bill stays quick; the attached Carrier Invoice File is the evidence.
13. As an Agent, I want my edits to leave the Fee itself unchanged, in the Fee list and on its Request, so that the record of what I logged stays true.
14. As an Agent, I want my Agent Invoice's Local Support Fees to be unaffected by my edits, and the draft to tell me so, so that I am not surprised that my own pay follows the Fleet and Fees, not the Client Invoice.
15. As the company, I want only the Contract's own Agent able to edit its Client Invoice's lines, with a Manager, a Tester or another Agent refused, and another Tenant's invoice treated as not existing, so that the invoice stays the Agent's to prepare and the Tenant boundary holds.
16. As a Company Manager, I want a sent invoice's detail page to show each line's billed amount and, for an edited line, that it was edited and the computed amount, so that I can judge the edit before approving.
17. As a Company Manager, I want a sent invoice's base amount broken down per Postpaid SIM, so that I can see which SIM's bill went up.
18. As a Company Manager, I want the Review Queue and the Dashboard's Pending approvals card to show the invoice's edited total, so that every total I see agrees with the invoice.
19. As a Tester, I want a sent invoice and its PDF to show the billed amounts and totals, with no mark of what was edited and no computed amount, so that I see one clean statement.
20. As the company, I want every Client Invoice sent or approved before this change to read exactly as it does today, in its lines, amounts, totals, PDF and Review Queue row, so that no figure already communicated moves.
21. As the company, I want every edit recorded as an audit line naming who edited, which invoice and line, and the old and new amount, so that a changed figure can be traced.
22. As the company, I want an edit and a send of the same invoice that race each other to leave the sent lines exactly as frozen, so that no sent figure ever moves after send.
23. As everyone already using the product, I want an invoice whose lines nobody edited to behave exactly as today, in the draft, the send, the totals, the Review Queue and the PDF, so that this change carries no release risk.
24. As an Agent or a Manager working by keyboard or on a phone, I want editing, resetting and reading edited lines usable without a mouse and at the mobile breakpoint, so that the work is possible wherever I am.
25. As an Agent, I want a sent invoice to keep its own lines, so that if it ever comes back to me as a draft I edit the numbers I sent, not a fresh computation.

## Solution

### The model: an invoice holds its own lines

A new entity, **`ClientInvoiceLine`**, one row per line, in a new table
`client_invoice_lines`:

```
ClientInvoiceLine
  id
  clientInvoice        → ClientInvoice (not null)
  kind                 POSTPAID_SIM | BASE_AMOUNT | FEE
  simCard              → SimCard, set only for POSTPAID_SIM
  fee                  → Fee,     set only for FEE
  computedAmount       numeric, the computation's amount for this line (see below)
  amount               numeric, the billed amount, >= 0
  editedAt, editedBy   set by the Agent's last edit, null if never edited
  unique (clientInvoice, simCard), unique (clientInvoice, fee)
  check: exactly the reference its kind needs
```

- `POSTPAID_SIM` is one line of the base amount. Its label (SIM number) and
  cancellation date are read from the SIM Card, as the draft breakdown does
  today.
- `FEE` is one Fee line. Its description, Fee type and date are read from the
  Fee, which never changes.
- `BASE_AMOUNT` exists only for invoices sent before this change: their base
  amount was frozen as one number, with no record of which SIMs it counted.
  It is never created by new code.
- A line is **edited** when `amount ≠ computedAmount`. This is what every
  view shows.

`ClientInvoice` gains one column, **`linesStored`** (boolean, not null,
default false). It is the single rule the read path follows:

```
linesStored = false   (a draft never sent)
  lines  = the computation (Postpaid SIMs billing that month, Fees of that month)
           with any stored row for the same SIM or Fee overriding its amount
           — stored rows exist only for the lines the Agent edited
  computedAmount shown = the live computation

linesStored = true    (set at the first send, never cleared)
  lines  = the stored rows, exactly; nothing is computed
```

- **Editing a never-sent draft** writes or updates the stored row for that
  SIM or Fee. Resetting it (saving the computed amount) deletes the row, so
  the line follows the computation again.
- **Sending** writes a row for every line the invoice shows, edited or not,
  with `computedAmount` = the computation at that moment, and sets
  `linesStored`. A stored row for a SIM that no longer bills that month is
  kept, with a computed amount of zero, so it still shows as edited.
- **From `sent` onward** the stored rows are the invoice (ADR 0001's freeze,
  with lines instead of membership).

**Why this makes send-back natural.** Returning an invoice to `DRAFT` changes
only its status. `linesStored` stays true, so the draft shows exactly the
lines it was sent with, editable, and nothing refreshes, as the human asked.
The resend has nothing to clear: its rows already exist, and the send only
writes rows for an invoice whose `linesStored` is false. No membership table
has to be emptied, and no unique constraint can trip.

The totals: base amount = the sum of the `POSTPAID_SIM` and `BASE_AMOUNT`
lines; Fees total = the sum of the `FEE` lines; total = both.

### Migration (hard to undo; stated precisely)

One Flyway migration, the next free version at merge (`V56` on `main` today;
`send-a-client-invoice-back` takes the one after). It only adds:

1. creates `client_invoice_lines` with its foreign keys, the two unique
   constraints, the kind check and an `amount >= 0` check;
2. adds `client_invoices.lines_stored boolean not null default false`;
3. **backfills every `SENT` and `APPROVED` invoice**:
   - one `BASE_AMOUNT` line, with `amount = computedAmount = snapshot_base_amount`;
   - one `FEE` line per `client_invoice_fee_snapshots` row, with
     `amount = computedAmount =` that Fee's amount;
   - `lines_stored = true`.
4. Existing `DRAFT` invoices get no rows. They keep computing live, exactly as
   now, because nobody has edited them yet.

Nothing is deleted or rewritten. `snapshot_base_amount` and
`client_invoice_fee_snapshots` keep their data, and new code stops writing and
reading them. Because Fees never change, each backfilled invoice reads the same
lines, amounts and totals as before the migration (story 20). The only
difference is that its base amount has no per-SIM breakdown, which it never
had once sent.

What makes it hard to undo: after the first edit, the stored lines are the
only record of what an invoice billed. Going back to the old model would lose
every edited amount. That is the feature itself, which the human asked for.
Recorded in a new ADR, below.

### ADR

**A new ADR, "A Client Invoice holds its own lines, editable while draft"**
(the next number at merge, `0004` on `main` today; the send-back ADR takes
the number after). It records:

- the lines model and `linesStored`, and that the computation only pre-fills;
- that ADR 0001's reason still holds: from `sent` onward no figure moves. The
  freeze now stores lines with amounts, not Fee membership, because an
  invoice's amount can now differ from its Fee's;
- that an edited line keeps the Agent's amount, while untouched lines of a
  never-sent draft follow the computation;
- that the Agent Invoice still reads the computation (ADR 0002), so the two can
  differ by an edit, by design.

ADR 0001 gets a dated note: "Amended by ADR 0004: a draft's lines are
pre-filled by the computation and editable by the Agent; the snapshot at send
stores lines, not Fee membership." ADR 0002 gets a dated note: "Client Invoice
lines are editable (ADR 0004). Local Support Fees still read the Fleet and
Fees, so an edit to a Client Invoice does not change the Agent's pay."

### Prefactoring: one send, in the service, in one transaction

Sending lives in `ClientInvoiceController` today (the transition check, the
snapshot, the status and `sentAt` writes) and is not one transaction. This
feature rewrites the snapshot, so the send moves into `ClientInvoiceService`
as one `@Transactional` operation taking an already-found invoice. It
re-reads the invoice under a row lock (a locking finder on
`ClientInvoiceRepository`), so a concurrent edit waits for it and then sees
`SENT`. This was the first ticket of `send-a-client-invoice-back`; it moves
here, and that spec drops it.

### Backend: reading lines

`ClientInvoiceService.toResponse` follows the rule above instead of
`status != DRAFT`. The response changes additively:

- `basePostpaidSims`: each element gains `amount` (billed), `computedAmount`
  and `edited`. `monthlyFeeAmount` stays, and still means the SIM's monthly
  fee. The list is now served for every status whose lines are per SIM, not
  only for drafts. It is null for an invoice with a `BASE_AMOUNT` line
  (sent before this change).
- `feeLines`: each element becomes a `ClientInvoiceFeeLineResponse` with every
  field `FeeResponse` has today, where `amount` is now the billed amount, plus
  `computedAmount` and `edited`. `FeeResponse` itself is unchanged, so the Fee
  list is untouched.
- `baseAmount`, `totalAmount`: sums of the lines.
- For a **Tester caller**, `computedAmount` and `edited` are null everywhere.
  The PDF never prints them.

`ClientInvoiceRepository.findQueueRows` sums the stored lines by kind instead
of `snapshotBaseAmount` and the Fee snapshot join. Every invoice in the queue
is `SENT`, so it always has stored lines.

### Backend: editing a line

`PUT /api/contracts/{contractId}/client-invoice/lines`, on the Agent's
current-month route (the only way an Agent reaches an invoice today). Body:

```json
{ "kind": "POSTPAID_SIM" | "FEE" | "BASE_AMOUNT", "sourceId": "<simCardId | feeId | null>", "amount": "31.40" }
```

```
200 ClientInvoiceResponse — the whole invoice, recomputed
400 — amount missing, negative, more than two decimals, or beyond the column's precision
404 — no such line on this invoice (a SIM not billing this month, a Fee of another month or Contract)
409 — the invoice is not DRAFT
403 — not the Contract's own Agent (a Manager, a Tester, another Agent)
```

`ClientInvoiceService.editLine(invoice, kind, sourceId, amount, principal)`,
`@Transactional`, takes an already-found invoice so that
`send-a-client-invoice-back` can expose it on its by-id route unchanged. It:

1. re-reads the invoice under the same row lock as the send;
2. refuses anything but `DRAFT` with `409`;
3. finds the line among the invoice's current lines, or `404`;
4. writes the amount: on a never-sent draft, upserts the override row, or
   deletes it when the amount equals the computed amount; on a draft with
   stored lines, updates the row;
5. sets `editedAt`/`editedBy` (cleared on a reset);
6. writes `AuditLog.clientInvoiceLineEdited(invoiceId, kind, sourceId,
   oldAmount, newAmount, actor, tenant)`, following
   `agentInvoiceOverridden`'s shape.

Access goes through `ClientInvoiceAccessGuard`, the check the current-month
send already uses. An unknown Contract or another Tenant's is `404`, as today.

### Frontend: Agent

- On the draft card in `AgentClientInvoicesView`, each Postpaid SIM row and
  each Fee row gets an **Edit** row action. It opens an inline amount field in
  the row, with **Save** and **Cancel**, copied from
  `AgentInvoiceOverrideControl`. A new `EditClientInvoiceLineControl` takes
  the line's kind and source id.
- An edited line shows its billed amount, with a quiet line beneath: "Edited ·
  computed {amount}" and a **Reset** row action, which saves the computed
  amount.
- After a save or a reset the page refreshes, and the base amount, Fees total
  and total follow.
- A `400` shows the server's message inline and keeps the input. A `409`
  shows "This invoice was sent. Refresh to see it." Anything else shows a
  generic retry message.
- One hint line on the draft, beneath the totals: "You can adjust any line to
  what was actually billed. Your Agent Invoice is still computed from your
  Fleet and Fees."
- Sent and approved invoices show no Edit or Reset. They show the "Edited ·
  computed" line on edited lines, as the Manager sees them.
- **BFF:** one pass-through proxy for the new route, in the shape every other
  proxy has, using `backendFetch`. `lib/api/types.ts` gains the new fields.

### Frontend: Manager

`ClientInvoiceDetailView` shows the per-SIM base lines when the invoice has
them, and the "Edited · computed {amount}" line under each edited line. The
Review Queue and the Pending approvals card need no change: they render the
totals the backend gives them.

### Tester

No frontend change. The Tester's page and the PDF show `baseAmount`,
`feeLines` and `totalAmount`, which are now the billed amounts. The API gives
a Tester no edit markers.

### Untouched, and why that is correct

- **Agent Invoice.** `ContractAmountService.totalForMonth` reads `sim_cards`
  and `fees`, never a Client Invoice (ADR 0002). An edit moves nothing there.
- **Fees.** No Fee row is written. `FeeResponse` and the Fee list are as they
  were.
- **Get-or-create.** Creating a draft writes no lines.

## Design direction

One surface, **Operate**, built from shipped patterns. The `design` slot is
not needed, and `DESIGN.md` does not change.

- The inline amount edit copies the Manager's Agent Invoice override control:
  28px `sm` row-action buttons, an 8px-radius input, and a `danger`-toned
  inline error. Save is not a pill: the card's one pill stays **Send**
  (Pill-Is-Primary Rule).
- Every amount, billed or computed, uses `Money` with `.tnum` (the Tabular
  Everywhere Rule), so the edited and computed figures align.
- "Edited · computed {amount}" is muted secondary text at the existing small
  type size, not a badge or a colour. It is information for review, not a
  warning.
- The hint line uses the muted text the card already uses for its notes.

Visual goldens:

- The Agent's Client Invoices draft gains Edit row actions. If a golden of
  that page is captured, it moves in the frontend Agent ticket and is
  re-approved there, with the reason in the commit.
- No other golden may move. One that does is a finding.

## Constraints

- **One additive migration.** It creates a table and a column and backfills
  `SENT`/`APPROVED` invoices from data that already exists. It deletes and
  rewrites nothing. Forward-only (Backend rule 9).
- Every `SENT` or `APPROVED` invoice from before the migration serves the same
  lines, amounts, totals, PDF and queue row as before (story 20).
- From `sent` onward no line's amount changes. An edit is refused unless the
  invoice is `DRAFT`, checked under the same row lock the send takes.
- A line's amount is `>= 0`, at most two decimal places, in the invoice's
  currency. The database checks `amount >= 0` too.
- No Fee row is ever written by this feature.
- `computedAmount` and `edited` never reach a Tester, a log line or the PDF.
- The Agent Invoice's computation is not touched (pending open question 1).
- Backend tests run under `IntegrationTest`: singleton Testcontainers
  Postgres, each method rolled back. No repository mocks (Backend rule 5).
- e2e and visual runs use the isolated stack (`docs/agents/implementer-notes.md`).
  An e2e failure is judged against a controlled comparison on an idle
  machine, never waved off as a flake.
- Maven runs under JDK 21, and Checkstyle stays clean.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, the rendered
accessibility tree, what a user sees. Never repository calls or component
internals.

1. **Migration seam (existing).** Prior art: the tests under
   `backend/src/test/.../migration`, which migrate a throwaway database to a
   fixed version. A new `ClientInvoiceLinesMigrationTest` migrates to the
   version before, writes a `SENT` and an `APPROVED` invoice with a base
   amount and Fee snapshot rows, plus a `DRAFT`, then migrates and asserts:
   one `BASE_AMOUNT` line equal to the old base amount, one `FEE` line per
   snapshot row equal to its Fee's amount, `lines_stored` true on both, and
   nothing on the draft.
2. **HTTP API seam (primary; existing).** Prior art: `ClientInvoiceApiTest`
   (draft computation, send, freeze) and `ClientInvoiceByIdApiTest` (Manager
   read and approve, `OtherTenantFixture`). A new
   `ClientInvoiceLineEditApiTest` covers:
   - edit a Postpaid SIM line and a Fee line → totals, `edited`,
     `computedAmount`;
   - the Fee's own read and the Fee list unchanged after the edit;
   - a SIM added and a Fee logged after an edit appear as new lines; the
     edited line keeps its amount; an untouched line follows the change;
   - reset by saving the computed amount → `edited` false, and the line
     follows the computation again;
   - zero accepted; negative, blank and three-decimal amounts → `400`;
     another month's Fee, or a SIM not billing → `404`;
   - send → the Manager's by-id read serves the same lines, per SIM; a Fee
     logged after send changes nothing; an edit after send → `409`;
   - a Manager, a Tester and another Agent → `403`; another Tenant → `404`;
   - a Tester's read gives null `computedAmount`/`edited`;
   - the Review Queue row's total equals the edited total;
   - the Agent Invoice's Local Support Fees read the same before and after
     an edit;
   - **the send-back shape (story 25):** a fixture sets a sent invoice back to
     `DRAFT`, as send-back will. Its read serves the lines as sent, a Fee
     logged after the send does not appear, an edit succeeds, and a second
     send succeeds with no constraint violation.
3. **The race at the same seam, not with mocks.** Two threads edit and send
   the same invoice. Whichever order wins, the final `SENT` invoice's lines
   equal what its send stored, and the edit either landed before the send or
   got `409`. This test commits real transactions, so it cleans up its own
   rows instead of using `IntegrationTest`'s rollback.
4. **The prefactor and the read switch are proven by existing suites passing
   unedited:** `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest`,
   `client-invoice-generation.spec.ts`,
   `client-invoice-submission-and-visibility.spec.ts`,
   `manager-invoice-review-queue.spec.ts`. One exception is allowed and must
   be named in the ticket: an assertion that a sent invoice's
   `basePostpaidSims` is null, which this feature deliberately changes.
5. **Frontend component tests (Vitest + Testing Library; existing).** Prior
   art: `agent-invoice-override-control.test.tsx`,
   `client-invoices-view.test.tsx`, `client-invoice-detail-view.test.tsx`.
   They cover the edit control (open, Save, Cancel, `400` inline with the
   input kept, `409` copy, generic failure), the edited marker and Reset, no
   Edit on a sent invoice, the hint line, and the Manager's per-SIM lines and
   markers.
6. **One e2e spec, `edit-client-invoice-lines.spec.ts` (existing seam).**
   Prior art: `client-invoice-generation.spec.ts`, which asserts $25.00 base,
   $45.00 Fee, $70.00 total. On a Contract it creates itself: the Agent edits
   the SIM line to $31.40 and the Fee line to $40.00, sees $71.40, resets the
   Fee line, sends, and the Manager sees the edited SIM line with "computed
   $25.00" and a total of $76.40.
7. **No unit tests** of the line-resolution rule beyond the HTTP seam, which
   proves each branch (Backend rule 6).

## Decisions taken

### Settled by the human (2026-10-01)

- **Every line is editable by the Agent while the invoice is a draft; the
  computation only pre-fills.** Epic `## Reworked`, and the inbox answer.
- **A sent invoice returned to draft refreshes nothing.** That is
  `send-a-client-invoice-back`'s to build; this feature's `linesStored`
  makes it the default.

### Taken alone

- **One `client_invoice_lines` table plus a `linesStored` flag, not a copy of
  the computation on every draft.** A never-sent draft stores only its edited
  lines and computes the rest, so an untouched draft behaves exactly as today
  with no writes on read. A sent invoice stores every line. One flag decides
  which read applies, and send-back needs only to change the status.
- **Send-back is natural by construction:** a draft with `linesStored` serves
  and edits its stored rows; the send writes rows only when `linesStored` is
  false. The sibling spec drops its "clear the Fee snapshot rows" and "the
  base amount of a past month" rules: a stored line is never recomputed,
  whatever the month.
- **The invoice stores amounts, not Fee membership.** An edited amount cannot
  live on the Fee, which stays immutable and is read by the Agent Invoice and
  the Fee list.
- **"Edited" is derived (`amount ≠ computedAmount`), not a stored flag.** An
  Agent who types the computed amount back has not changed the invoice, and
  no flag can disagree with the amounts.
- **Reset is saving the computed amount, not a separate endpoint.** One
  endpoint, one audit event, and on a never-sent draft the override row is
  removed so the line follows the computation again.
- **Lines are addressed by kind and source id (SIM or Fee), not by a line id.**
  A never-sent draft's untouched lines have no row, so a row id cannot
  address them.
- **A stored line whose SIM stops billing is kept, with a computed amount of
  zero.** Dropping it would silently discard the Agent's figure.
- **Backfilled old invoices get one `BASE_AMOUNT` line, not per-SIM lines.**
  Which SIMs a past base amount counted was never recorded, and SIM
  membership has no dates, so per-SIM lines would be invented.
- **The old snapshot structures stay in the schema, unused.** Dropping data
  is the irreversible part. A later contract migration removes them, and a
  `docs/tech-debt.md` entry records it.
- **The send moves into `ClientInvoiceService`, in one transaction, under a
  row lock.** This feature rewrites the snapshot. A lock shared by send and
  edit is the simplest way to keep an edit from landing on a sent invoice.
  It is the prefactor `send-a-client-invoice-back` had planned, moved here.
- **The edit endpoint is on the current-month route, and the service takes a
  found invoice.** That is the Agent's only way in today. The sibling's by-id
  route reuses the same service call.
- **Only the amount is editable, zero is allowed, negatives are not.** The
  human asked to adjust billed amounts. A credit is `invoice-adjustment`'s,
  and zero is how a line bills nothing without a delete.
- **The response changes additively.** `feeLines` gets its own response type,
  so `FeeResponse` and the Fee list are untouched.
- **The audit line follows `agentInvoiceOverridden`**: old and new amount, no
  free text.
- **The edit UI copies `AgentInvoiceOverrideControl`** inline, with no dialog,
  so `DialogShell`'s debt (tech-debt.md:16) stays out of this feature.
- **The ADR is new, with dated notes on 0001 and 0002**, the shape ADR 0003's
  2026-09-17 note set. The new ADR takes the next number. The send-back ADR
  takes the one after.
- **Testing: the existing migration, HTTP, component and e2e seams, plus one
  committing race test.** Prior art is named under `## Testing decisions`.

## Open questions

1. **Does the Agent's own pay follow the edited Client Invoice?** Example: SIM
   A's plan is $25. The carrier's real bill was $31.40 because of extra data,
   so the Agent edits the Client Invoice line to $31.40. Today the Agent's own
   invoice (Local Support Fees) is computed from the Fleet and Fees, so it
   still reimburses $25. **Recommendation: no, not in this feature.** The
   Agent Invoice keeps reading the Fleet and Fees, and the draft says so.
   Reason: the Agent Invoice is often sent before the Client Invoice is
   finished, and its numbers freeze then. Making it follow Client Invoice
   edits ties the Agent's pay to a separate document's timing, the problem ADR
   0002 was written to avoid. It also lets an Agent raise their own pay by
   editing the Client's invoice. If you want the Agent reimbursed the edited
   amount, say so, and it becomes its own feature that amends ADR 0002.
   (Case 1.)
2. **Is the base amount one editable line, or one line per Postpaid SIM?**
   Example: a Contract has three Postpaid SIMs at $25, $25 and $15. The base
   amount is $65. With one line, the Agent changes $65 to $72.30. With one
   line per SIM, the Agent changes the second SIM from $25 to $32.30, and the
   base amount becomes $72.30. **Recommendation: one line per Postpaid SIM.**
   Reason: the draft already lists each SIM with its fee, the carrier bills
   per SIM, and the Manager can then see which SIM went up. The Tester and the
   PDF still show the base amount as one total. (Case 1.)
3. **Before the first send, does an edited line stop following the Fleet and
   Fees, and do new Fees appear as new lines?** Example: the Agent edits SIM
   A from $25 to $31.40. Later that month a Topup Fee of $10 is logged and a
   new Postpaid SIM D at $15 is added. **Recommendation:** the $10 Fee and SIM
   D appear as new pre-filled lines; SIM A stays at $31.40; every line the
   Agent did not touch keeps following the Fleet and Fees until the first
   send, and Reset puts SIM A back to following them. Reason: nothing logged
   that month is left off the invoice, and nothing the Agent typed is
   overwritten. After the first send nothing is recomputed, as you said for
   send-back. (Case 1.)
4. **Must the Agent give a reason when editing a line?** Example: the Agent
   changes SIM A from $25 to $31.40. Should they have to type "extra data
   usage, see carrier bill"? **Recommendation: no reason.** Reason: the
   Manager sees each edit with its computed amount ("Edited · computed
   $25.00") and has the attached Carrier Invoice File, and `send-a-client-
   invoice-back` lets them return it with a question. A required reason on
   every line slows the Agent's routine monthly work. (Case 1.)
5. **Who sees that a line was edited?** Example: SIM A billed $31.40 instead
   of the computed $25. **Recommendation:** the Agent and the Manager see
   "Edited · computed $25.00" under the line; the Tester and the PDF show only
   $31.40, with no mark. Reason: the Manager needs the difference to approve
   with confidence, while the Client receives a statement of what is billed,
   and "computed" is an internal figure the Client was never promised.
   (Case 1.)
6. **Approve the one-way data change?** Every invoice will store its own lines.
   Each invoice already sent or approved is copied into lines from what it
   shows today, so it reads exactly as now; for example, last month's approved
   invoice of $25 base plus a $45 Fee becomes a $25 base line and a $45 Fee
   line, total $70, as before. The old structures are kept, not deleted. Once Agents start editing, the stored lines are the only
   record of what was billed, so going back to the old model would lose those
   edits. **Recommendation: approve.** It is what makes editing, and a
   send-back that refreshes nothing, possible, and nothing existing is
   deleted or changed. (Case 2.)

## Acceptance walkthrough

1. [agent] Create a Contract with two Postpaid SIMs (A at $25.00, B at $15.00) and log one $45.00 Fee this month. As its Agent, read the current-month Client Invoice and show one line per SIM and one Fee line, every `edited` false, base $40.00 and total $85.00, the same figures the computation gives today. (stories: 1, 23)
2. [agent] `PUT …/client-invoice/lines` SIM A to `31.40` and the Fee line to `40.00`. Show `200`, both lines `edited: true` with `computedAmount` 25.00 and 45.00, base $46.40, Fees $40.00, total $86.40. (stories: 2, 3, 4, 5)
3. [agent] Read the Fee from the Fee list and its Request, and show $45.00 unchanged. Read the Agent's current Agent Invoice before step 2 and after, and show Local Support Fees identical. (stories: 13, 14)
4. [agent] Send `-1`, a blank amount and `1.234` and show `400` each; send `0` for SIM B and show `200` with base $31.40. Send another month's Fee id and a retired SIM's id and show `404`. (stories: 7)
5. [agent] Add Postpaid SIM C at $10.00 and log a $10.00 Topup Fee. Read the draft and show both as new pre-filled lines, SIM A still $31.40, and the Fee line still $40.00. Reset the Fee line by saving `45.00` and show it `edited: false`. (stories: 6, 8, 9)
6. [agent] Call the edit route with a Manager's, a Tester's and another Agent's token and show `403`; with `OtherTenantFixture`'s Contract and show `404`. (stories: 15)
7. [agent] Send the invoice. As the Manager, read it by id and show the same lines and amounts the Agent saw, per SIM, with `edited`/`computedAmount`. Show the Review Queue row's total equal to it. Log one more Fee and show the invoice unchanged. Edit a line and show `409`. (stories: 10, 11, 16, 17, 18)
8. [agent] As a Tester of the Client, read the invoice and download its PDF. Show the billed amounts and totals, `computedAmount` and `edited` null, and no "computed" text in the PDF. (stories: 19)
9. [agent] Grep the backend log for step 2 and show one `clientInvoiceLineEdited` audit line per edit, with the Agent as actor, the Tenant, the line and the old and new amounts. (stories: 21)
10. [agent] Run the edit-versus-send race test and show the sent lines always equal what the send stored, the edit having landed first or got `409`. (stories: 22)
11. [agent] Run `ClientInvoiceLinesMigrationTest` green. On the demo stack, before migrating, record the JSON and PDF of a past `APPROVED` Client Invoice and the Review Queue rows; migrate, and show every amount, total, Fee line and queue row identical. (stories: 20)
12. [agent] Run the story-25 case of `ClientInvoiceLineEditApiTest`: a sent invoice set back to `DRAFT` by a fixture serves its lines as sent, omits a Fee logged after the send, accepts an edit, and resends without error. (stories: 25)
13. [agent] In a browser as the Agent, open Client Invoices. Edit SIM A to 31.40 and show the marker "Edited · computed $25.00", the new totals and a Reset action. Type `-5` and show the inline error with `-5` still in the field. Show the hint that the Agent Invoice is still computed from the Fleet and Fees, and that no reason is asked for. Reset a line and show its marker gone. (stories: 2, 3, 4, 5, 6, 7, 12, 14)
14. [agent] Send the invoice and show no Edit or Reset left, with the markers still shown. In a second tab still on the draft, try an edit and show the refresh message. (stories: 10, 11)
15. [agent] In a browser as the Manager, open the invoice from the Review Queue and show the per-SIM lines with the edited marker, and the same total in the queue and on the Dashboard's Pending approvals card. (stories: 16, 17, 18)
16. [agent] In a browser as the Tester, open the invoice and the PDF and show the billed amounts with no marker. (stories: 19)
17. [agent] Edit, reset and save by keyboard alone with a visible focus ring at each stop; repeat at the mobile breakpoint and show the row actions and the inline field usable within the viewport. Do the Manager's detail page the same way. (stories: 24)
18. [agent] Run `mvn verify`, the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green, with the suites named in `## Testing decisions` item 4 unedited apart from the one named assertion, and no golden moved except the Agent's draft page if captured. (stories: 20, 23)
19. [human] Take a real carrier bill for a month where a Postpaid SIM's usage went over its plan. As the Agent, set that SIM's line to the billed figure and send. As the Manager, review it. Confirm the "Edited · computed" line tells you enough to approve without a written reason, and that the Tester's statement looks right to send a Client. (stories: 2, 5, 12, 16, 19)
20. [human] Read the new ADR and the notes on ADR 0001 and ADR 0002. Confirm they say what you settled: the computation only pre-fills, every line is the Agent's to edit while draft, nothing moves once sent, a sent invoice keeps its own lines, and the Agent's own pay is (or is not, per your answer) separate. (stories: 10, 14, 20, 25)

## Execution order

Five tickets, each touching at most three `ARCHITECTURE.md` modules. They run
in order: each depends on the one before (ticket 5 on tickets 3 and 4).

1. `store-client-invoice-lines`. Labels: `enabler`, `backend`. Depends on
   nothing. Modules: `domain`, `repository`. (stories: —)
   - The `ClientInvoiceLine` entity, `ClientInvoice.linesStored`, the line
     repository, and the migration with its backfill.
   - `ClientInvoiceLinesMigrationTest`. No behaviour changes yet.
2. `serve-client-invoices-from-stored-lines`. Labels: `enabler`, `backend`.
   Depends on `store-client-invoice-lines`. Modules: `web`, `repository`,
   `demo`. (stories: 10, 20, 23, 25)
   - The send moves into `ClientInvoiceService`, one transaction, under the
     row lock, writing every line and setting `linesStored`; it stops writing
     `snapshotBaseAmount` and Fee snapshot rows.
   - `toResponse` follows the `linesStored` rule; the response shape is
     unchanged.
   - `findQueueRows` sums the lines. `DemoDataLoader` seeds lines for its past
     invoices.
   - The new ADR, the note on ADR 0001, and the tech-debt entry for the unused
     snapshot structures.
   - Existing suites pass unedited; the story-25 fixture test.
3. `agent-edits-a-client-invoice-line`. Labels: `backend`. Depends on
   `serve-client-invoices-from-stored-lines`. Modules: `dto`, `web`,
   `logging`. (stories: 2, 3, 4, 5, 6, 7, 8, 9, 11, 13, 14, 15, 17, 19, 21, 22)
   - The edit request DTO, the `PUT …/lines` route, `editLine`, the audit
     method.
   - The additive response fields, per-SIM lines on every status, and their
     omission for Testers.
   - The note on ADR 0002.
   - `ClientInvoiceLineEditApiTest` and the race test.
4. `agent-edits-lines-on-the-client-invoice-page`. Labels: `frontend`.
   Depends on `agent-edits-a-client-invoice-line`. Modules:
   `components/agent`, `app/api`, `lib/api`. (stories: 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 14, 24)
   - `EditClientInvoiceLineControl`, the marker, Reset, the hint, the BFF
     proxy and the types.
   - Component tests and `edit-client-invoice-lines.spec.ts`.
5. `manager-sees-edited-client-invoice-lines`. Labels: `frontend`. Depends on
   `agent-edits-a-client-invoice-line` and
   `agent-edits-lines-on-the-client-invoice-page`. Modules:
   `components/manager`.
   (stories: 16, 17, 18, 24)
   - Per-SIM base lines and the edited marker on the Client Invoice detail
     page; component tests.
   - The Manager's closing steps of `edit-client-invoice-lines.spec.ts` (the
     edited SIM line with "computed $25.00", total $76.40). They extend the
     spec ticket 4 creates, which is why this ticket depends on ticket 4 too.
