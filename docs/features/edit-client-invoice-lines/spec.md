---
feature: edit-client-invoice-lines
epic: invoice-correction-and-history
status: approved
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

The Agent fronts those carrier bills, so the Agent's own pay has to follow
the billed figure too. The human settled that the same day
(`docs/inbox/question-edit-lines-agent-pay.md` `## Answer`): "If an edit
occur before approval it should update the agent own monthly pay otherwise
its a carry-over." Today the Agent Invoice's Local Support Fees read the
Fleet and Fees directly and never a Client Invoice (ADR 0002), so an edit
would never reach the Agent's pay. This feature amends that.

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
- **Get the Agent paid for the month** (`exists`, stays `exists`, changed).
  Local Support Fees now follow the amounts billed on each Contract's Client
  Invoice, so an edit made before that Client Invoice is approved reaches the
  Agent's pay for the month.
- **Send an invoice back for correction** (`wanted`, stays `wanted`). Nothing
  of the send-back is built here. This feature makes "a sent-back draft keeps
  the numbers it was sent with, shows any Fee of its month logged since as a
  new pre-filled line, and the Agent edits them" possible without a second
  data model, and an edit made there reaches the Agent's pay by the same rule.
- **Work through what is waiting** (`exists`, stays `exists`). The Review
  Queue's totals come from the stored lines, so they show the edited total.

## Goals / Non-goals

**Goals**

- While a Client Invoice is a draft, its Agent can change the amount of every
  line: each Postpaid SIM's line in the base amount, and each Fee line.
- Lines start pre-filled from the computation. On a draft that has never been
  sent, lines the Agent has not touched keep following the Fleet and Fees, and
  a new Postpaid SIM or a new Fee appears as a new pre-filled line. A line the
  Agent edited keeps the Agent's amount until the Agent resets it.
- An edited line shows that it was edited and what the computation gave. The
  Agent can reset it.
- Sending freezes every line, edited or not, as the invoice's own stored
  lines. From then on every read serves those lines, and no stored line ever
  recalculates.
- If a sent invoice is ever a draft again (send-back), it shows its stored
  lines exactly as sent, plus a new pre-filled line for each Fee of its month
  that has no line yet; the next send stores those too.
- Every Client Invoice already sent or approved before this change reads
  exactly as it does today.
- The Agent and the Manager see which lines were edited, and by how much. The
  Tester and the PDF see only the billed amounts.
- The Agent's Local Support Fees follow each Client Invoice's billed amounts
  until that Client Invoice is approved. Approval makes them final for that
  month's pay. A sent Agent Invoice moves by an edit's difference until it is
  approved; after that the difference carries over.
- A Fee, or a Postpaid SIM, of the month that a sent Client Invoice does not
  bill still counts in that month's pay at its computed amount.
- For a month in which nobody edits a line, Local Support Fees read what they
  read today.

**Non-goals.** Each of these is something a reasonable agent would otherwise
build.

- **No send-back.** No `SENT → DRAFT` edge, no reason, no by-id Agent routes.
  That is `send-a-client-invoice-back`, which is being revised to build on
  this. This feature only makes its data model natural.
- **No carry-over.** A correction after a Client Invoice is approved, or after
  the Agent Invoice is approved, does not reach a later month's pay here. That
  is `invoice-adjustment`'s. This feature only guarantees that an approved
  invoice's figures do not move.
- **No editing a Fee.** The Fee keeps its amount, description, month and
  Request. The Fee list and the Request's view keep showing the Fee as logged.
  Only the invoice's own copy of the line changes.
- **No editing a line's description, Fee type or SIM.** Only the amount.
- **No adding a free-form line, and no deleting a line.** A new charge is
  logged as a Fee, so it still traces to a Request. A line that should bill
  nothing is set to zero.
- **No negative amount, no credit, no discount line.** Correcting an amount
  after sending or approval is the Manager's `invoice-adjustment`.
- **No Manager edit of a Client Invoice line.** The Manager reviews and
  approves. The Manager's Agent Invoice override (ADR 0003) is unchanged and
  still covers only Salary and the new-advance line.
- **No editing a sent or approved Client Invoice.** ADR 0001's freeze holds
  from `sent` onward.
- **No new line, column or breakdown on the Agent Invoice.** Local Support
  Fees stays one figure; only how it is computed changes.
- **No gate between the two invoices.** The Agent may still send the Agent
  Invoice before the Client Invoices, and the Manager may approve them in any
  order.
- **No reason or note on an edit** (the human's answer).
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
9. As an Agent, I want a line I edited to keep my amount when the Fleet or the Fees change afterwards, until I reset it, so that my correction is never silently overwritten.
10. As an Agent, I want sending to freeze exactly the lines and amounts I see, edited or not, and nothing to recalculate afterwards, so that the Manager reviews what I prepared.
11. As an Agent, I want no edit controls on a sent or approved invoice, and an edit from a stale page refused with a message telling me to refresh, so that a figure under review never moves.
12. As an Agent, I want to edit a line without having to write a reason, so that correcting a figure from the carrier's bill stays quick; the attached Carrier Invoice File is the evidence.
13. As an Agent, I want my edits to leave the Fee itself unchanged, in the Fee list and on its Request, so that the record of what I logged stays true.
14. As an Agent whose Agent Invoice for the month is still a draft, I want its Local Support Fees to follow the amounts I bill on each Client Invoice, edits included, and the Client Invoice draft to tell me so, so that I am reimbursed what the carrier really charged.
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
25. As an Agent, I want a sent invoice to keep its own lines, so that if it ever comes back to me as a draft I edit the numbers I sent, not a fresh computation, and see each Fee of its month logged since the send as a new pre-filled line I can edit and send, while no line it was sent with changes and no line is added for a Postpaid SIM added since.
26. As an Agent, I want sending my Agent Invoice to freeze Local Support Fees from the amounts billed on my Client Invoices at that moment, edits included, so that what I send is what I was really charged.
27. As an Agent whose Agent Invoice is already sent but not yet approved, I want a later edit to one of that month's Client Invoices to move its Local Support Fees by exactly the edit's difference, so that an edit made before approval still reaches my pay.
28. As an Agent, I want a Fee logged, or a Postpaid SIM added, after a Client Invoice was sent still to count in my Local Support Fees for that month at its computed amount, so that money I fronted is not left out of my pay.
29. As the company, I want an approved Client Invoice's billed amounts to be final for that month's Local Support Fees, and an approved or paid Agent Invoice never to move because of a Client Invoice, so that approved pay stays approved; any later correction is a carry-over for `invoice-adjustment`.
30. As the company, I want every change an edit makes to a sent Agent Invoice recorded as an audit line naming the Agent Invoice, the Client Invoice, and the old and new Local Support Fees, so that a moved pay figure can be traced.
31. As the company, I want an edit racing a send or an approval of the Agent Invoice to leave its Local Support Fees equal to what the rule gives, never losing or double-counting the edit, so that pay is right whatever the order.
32. As everyone already using the product, I want Local Support Fees for a month in which nobody edited a line to read as they do today, apart from the one edge named under Constraints, so that the pay change carries no surprise.

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

linesStored = true, status SENT or APPROVED
  lines  = the stored rows, exactly; nothing is computed

linesStored = true, status DRAFT   (only after a send-back)
  lines  = the stored rows, exactly, never recomputed
           + one pre-filled line per Fee of the invoice's Contract and billing
             month that has no FEE row: amount = computedAmount = the Fee's
             amount, edited false, not stored until edited or sent
           — no line is added for a Postpaid SIM; the base amount stays as sent
```

- **Editing a never-sent draft** writes or updates the stored row for that
  SIM or Fee. Resetting it (saving the computed amount) deletes the row, so
  the line follows the computation again.
- **Editing a draft with stored lines** updates the stored row. A pre-filled
  late-Fee line has no row yet, so its first edit inserts one, with
  `computedAmount` = the Fee's amount. A reset on such a draft saves the
  computed amount into the row and never deletes it: a row the invoice was
  sent with must stay, and a late-Fee row at its computed amount reads exactly
  as the pre-fill did, because a Fee's amount never changes.
- **Sending** stores every line the invoice shows, edited or not, and sets
  `linesStored`. It inserts a row for each shown line that has none. On a
  never-sent draft it also sets each override row's `computedAmount` to the
  computation at that moment. On a draft with stored lines it leaves every
  existing row exactly as it is, so the only rows a resend writes are those of
  pre-filled late-Fee lines. A stored row for a SIM that no longer bills that
  month is kept, with a computed amount of zero, so it still shows as edited.
- **From `sent` onward** the stored rows are the invoice (ADR 0001's freeze,
  with lines instead of membership). A Fee logged while the invoice is `SENT`
  or `APPROVED` does not appear on it.

**Why this makes send-back natural.** Returning an invoice to `DRAFT` changes
only its status. `linesStored` stays true, so the draft shows exactly the
lines it was sent with, editable, and no stored line refreshes, as the human
asked. A Fee of that month logged since the send appears beside them as a new
pre-filled line, as the human answered on 2026-10-02. The resend has nothing
to clear: the rows it was sent with already exist and are left alone, and it
inserts rows only for late-Fee lines, which by definition have none. No
membership table has to be emptied, and no unique constraint can trip.

The resolution above, "an invoice's lines", is one operation in
`ClientInvoiceService`, used by the read, the send, `editLine` and the pay
rule alike.

The totals: base amount = the sum of the `POSTPAID_SIM` and `BASE_AMOUNT`
lines; Fees total = the sum of the `FEE` lines; total = both. This total is
the invoice's **billed total**.

### Local Support Fees: the new rule (amends ADR 0002)

Local Support Fees stay a sum over the Agent's Contracts for the month. What
changes is each Contract's term:

```
contractTerm(contract, month):
  invoice = the Client Invoice for (contract, month), if one exists

  no invoice                          → computation (ContractAmountService, as today)
  invoice, linesStored = false        → its billed total
                                         (the computation with the Agent's edits;
                                          equal to the computation when nothing is edited)
  invoice, linesStored = true         → its billed total (the sum of its lines as resolved
                                           above, so on a sent-back draft including its
                                           pre-filled late-Fee lines)
                                         + the computed amount of every Fee of that month
                                           with no FEE line among those lines
                                         + unless it has a BASE_AMOUNT line, the computed
                                           amount of every Postpaid SIM billing that month
                                           with no POSTPAID_SIM line on it
```

In words: **every line the Client Invoice bills, at its billed amount, plus
anything of that month the Client Invoice does not bill, at its computed
amount.** The second part is the human's answer of 2026-10-02 (a Fee logged
after the Client Invoice was sent counts at its logged amount). It keeps what
ADR 0002 protects: a Fee the Agent fronted after the Client Invoice went out
still reaches their pay that month. A late Fee counts the same whether it is
unbilled (at its amount) or shown on a sent-back draft as a pre-filled line
(at the same amount), so a send-back that makes it appear moves no pay.

The status of the Client Invoice decides nothing extra. An approved invoice
cannot be edited, so its billed amounts are final, which is "approval makes
them final" by construction. A later correction is `invoice-adjustment`'s
carry-over.

The rule lives in one place: a new `ContractAmountService` operation, "a
Contract's payable amount for a month", which reuses the line resolution
`ClientInvoiceService` uses for reads, so the Client Invoice and the pay can
never resolve a line two ways. `AgentInvoiceService.computeLocalSupportFees`
calls it instead of `totalForMonth`. `totalForMonth` stays: it is still the
computation the Client Invoice pre-fills from.

### Local Support Fees across the Agent Invoice's lifecycle

| Agent Invoice | When a Client Invoice line of that month is edited |
|---|---|
| `DRAFT` | Nothing is written. The draft computes live, so it reads the new figure on its next read. |
| `SENT` | Its frozen Local Support Fees move by exactly the edit's difference (new billed amount − old billed amount), in the edit's transaction, with an audit line. The human's answer, 2026-10-02. |
| `APPROVED`, `PAID` | Nothing moves. The difference is a carry-over for `invoice-adjustment`. |

- **Sending the Agent Invoice** (ADR 0003's snapshot) freezes Local Support
  Fees from the new rule at that moment. Salary and the Rollout Advance lines
  are unchanged.
- **The difference, not a recomputation.** Moving a sent Agent Invoice by the
  edit's difference changes only what the edit changed. A Fee logged after the
  Agent Invoice was sent does not slip in, which keeps ADR 0003's freeze for
  everything but the edit. That holds for every line alike: an edit to the
  line of a Fee logged after the Agent Invoice's send moves it by the edit's
  difference, and the Fee's own amount still does not slip in.
- **The old billed amount of a pre-filled line** (a late-Fee line on a
  sent-back draft, or an untouched line on a never-sent draft) is its
  pre-filled amount, so the difference is measured from what the line showed.
- **Which Agent Invoice.** The one of the Contract's Agent for the Client
  Invoice's billing month, if it exists. None yet: nothing to do; it computes
  live when created.
- **The Manager's override** never touches Local Support Fees, so the two
  never write the same column.
- **A sent-back Client Invoice** (`send-a-client-invoice-back`) edited later
  follows the same table, because the edit goes through the same
  `editLine`.

**Locks, so no edit is lost or counted twice.** `AgentInvoiceRepository`
gains a locking finder. Send, approve, mark-paid and override of an Agent
Invoice re-read it under that lock. `editLine` takes the Client Invoice's row
lock first, writes the line, then takes the Agent Invoice's lock, always,
even when the Agent Invoice is `DRAFT`, and holds both to commit. So:

- edit before an Agent Invoice send: the send waits, then computes from the
  committed edit;
- send before the edit: the edit sees `SENT` and applies the difference;
- edit against an approve: the edit either applies the difference before the
  approval, or sees `APPROVED` and moves nothing; the approve's write cannot
  overwrite the moved figure with a stale one.

The lock order is always Client Invoice, then Agent Invoice, and no Agent
Invoice operation takes a Client Invoice lock, so the two cannot deadlock.

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

No Agent Invoice row is touched. A sent, approved or paid Agent Invoice keeps
its snapshot.

Nothing is deleted or rewritten. `snapshot_base_amount` and
`client_invoice_fee_snapshots` keep their data, and new code stops writing and
reading them. Because Fees never change, each backfilled invoice reads the same
lines, amounts and totals as before the migration (story 20). The only
difference is that its base amount has no per-SIM breakdown, which it never
had once sent.

What makes it hard to undo: after the first edit, the stored lines are the
only record of what an invoice billed, and Agent Invoices sent afterwards
freeze pay from them. Going back to the old model would lose every edited
amount. That is the feature itself, which the human asked for and approved.
Recorded in a new ADR, below.

### ADR

**A new ADR, "A Client Invoice holds its own lines, editable while draft, and
the Agent's pay follows them until approval"** (the next number at merge,
`0004` on `main` today; the send-back ADR takes the number after). It records:

- the lines model and `linesStored`, and that the computation only pre-fills;
- that ADR 0001's reason still holds: from `sent` onward no figure moves. The
  freeze now stores lines with amounts, not Fee membership, because an
  invoice's amount can now differ from its Fee's;
- that an edited line keeps the Agent's amount until reset, while untouched
  lines of a never-sent draft follow the computation;
- that a draft with stored lines (a sent-back invoice) never recomputes a
  stored line, and shows each Fee of its month with no line as a new
  pre-filled line, stored at the next send; no line is added for a Postpaid
  SIM (the human's answer of 2026-10-02);
- the Local Support Fees rule above, its lifecycle table, and the human's
  words it follows ("If an edit occur before approval it should update the
  agent own monthly pay otherwise its a carry-over"), with the 2026-10-02
  answer that a sent, not yet approved Agent Invoice moves by the edit's
  difference;
- why ADR 0002's reason survives: anything the Client Invoice does not bill
  still counts at its computed amount (the human's answer of 2026-10-02), so a
  fronted Fee is never dropped;
- the alternatives: keeping ADR 0002 as it was (rejected by the human);
  "billed total only" (drops Fees logged after send, the case ADR 0002 was
  written for); recomputing a sent Agent Invoice instead of moving it by the
  difference (would let later Fees in, breaking ADR 0003's freeze); blocking
  the Agent Invoice's send until the Client Invoices are approved (couples the
  two documents' timing; not asked for).

ADR 0001 gets a dated note: "Amended by ADR 0004: a draft's lines are
pre-filled by the computation and editable by the Agent; the snapshot at send
stores lines, not Fee membership; a stored line is never recomputed." ADR 0002 gets a dated note: "Amended by
ADR 0004: Local Support Fees now take each Contract's billed Client Invoice
amounts, plus anything that invoice does not bill at its computed amount; a
sent Agent Invoice moves by the difference of a later edit until it is
approved." ADR 0003 gets a dated note: "Amended by ADR 0004: a sent Agent
Invoice's Local Support Fees move by the difference of a Client Invoice edit
until the Agent Invoice is approved; nothing else of the freeze changes."

### Prefactoring: one send, in the service, in one transaction

Sending lives in `ClientInvoiceController` today (the transition check, the
snapshot, the status and `sentAt` writes) and is not one transaction. This
feature rewrites the snapshot, so the send moves into `ClientInvoiceService`
as one `@Transactional` operation taking an already-found invoice. It
re-reads the invoice under a row lock (a locking finder on
`ClientInvoiceRepository`), so a concurrent edit waits for it and then sees
`SENT`. This was the first ticket of `send-a-client-invoice-back`; it moves
here, and that spec drops it.

The Agent Invoice's send, approve, mark-paid and override become
`@Transactional` and re-read under the new Agent Invoice lock, in the pay
ticket.

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
3. finds the line among the invoice's current lines, as the resolution above
   gives them, or `404`;
4. writes the amount: on a never-sent draft, upserts the override row, or
   deletes it when the amount equals the computed amount; on a draft with
   stored lines, updates the row, or inserts one for a pre-filled late-Fee
   line, and never deletes a row;
5. sets `editedAt`/`editedBy` (cleared on a reset);
6. writes `AuditLog.clientInvoiceLineEdited(invoiceId, kind, sourceId,
   oldAmount, newAmount, actor, tenant)`, following
   `agentInvoiceOverridden`'s shape;
7. hands the difference to the Agent Invoice (added by the pay ticket):
   `AgentInvoiceService` locks the Agent's invoice for that month and, if it
   is `SENT`, adds the difference to its Local Support Fees and writes
   `AuditLog.agentInvoiceLocalSupportFeesFollowed(agentInvoiceId,
   clientInvoiceId, oldAmount, newAmount, actor, tenant)`.

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
  what was actually billed. Your Agent Invoice for this month follows these
  amounts, unless it is already approved."
- Sent and approved invoices show no Edit or Reset. They show the "Edited ·
  computed" line on edited lines, as the Manager sees them.
- **BFF:** one pass-through proxy for the new route, in the shape every other
  proxy has, using `backendFetch`. `lib/api/types.ts` gains the new fields.
- The Agent Invoice page is unchanged: it shows the Local Support Fees figure
  the backend gives it.

### Frontend: Manager

`ClientInvoiceDetailView` shows the per-SIM base lines when the invoice has
them, and the "Edited · computed {amount}" line under each edited line. The
Review Queue, the Pending approvals card and the Agent Invoice detail page
need no change: they render the totals the backend gives them.

### Tester

No frontend change. The Tester's page and the PDF show `baseAmount`,
`feeLines` and `totalAmount`, which are now the billed amounts. The API gives
a Tester no edit markers.

### Untouched, and why that is correct

- **Fees.** No Fee row is written. `FeeResponse` and the Fee list are as they
  were.
- **Get-or-create.** Creating a draft writes no lines.
- **Salary and the Rollout Advance lines** of the Agent Invoice, and the
  Manager's override of them.
- **The Agent Invoice's columns.** Local Support Fees keeps its one snapshot
  column; no migration touches `agent_invoices`.

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

- The Agent's Client Invoices draft gains Edit row actions and the new hint.
  If a golden of that page is captured, it moves in the frontend Agent ticket
  and is re-approved there, with the reason in the commit.
- No other golden may move. One that does is a finding.

## Constraints

- **One additive migration.** It creates a table and a column and backfills
  `SENT`/`APPROVED` invoices from data that already exists. It deletes and
  rewrites nothing, and touches no Agent Invoice. Forward-only (Backend
  rule 9).
- Every `SENT` or `APPROVED` Client Invoice from before the migration serves
  the same lines, amounts, totals, PDF and queue row as before (story 20).
- Every Agent Invoice sent, approved or paid before the release keeps its
  snapshot.
- From `sent` onward no Client Invoice line's amount changes. An edit is
  refused unless the invoice is `DRAFT`, checked under the same row lock the
  send takes.
- An approved or paid Agent Invoice's Local Support Fees never move.
- **The one edge where unedited pay can read differently from today**
  (story 32): an Agent Invoice still a draft, for a month whose Client Invoice
  was already sent, where the Fleet changed after that send. Today it counts
  the live Fleet; now it counts what the Client Invoice billed (a SIM removed
  after the send still counts; a legacy invoice's frozen base amount is used).
  This is the human's rule applied, and is accepted. Fees cannot move, so
  they never cause it.
- A line's amount is `>= 0`, at most two decimal places, in the invoice's
  currency. The database checks `amount >= 0` too.
- No Fee row is ever written by this feature.
- `computedAmount` and `edited` never reach a Tester, a log line or the PDF.
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
   - **the send-back shape (story 25):** a fixture sets a sent invoice, with
     one edited SIM line, back to `DRAFT`, as send-back will. A Fee of its
     month logged after the send, and a Postpaid SIM added after it, exist.
     Its read serves every line it was sent with exactly as sent (amounts and
     `computedAmount`s), plus the late Fee as a new line with `edited` false
     and its logged amount, and no line for the new SIM. An edit of a sent
     line succeeds; an edit of the late-Fee line succeeds and a reset of it
     reads `edited` false again. A second send succeeds with no constraint
     violation and stores the late-Fee line; after it, a Fee logged since
     does not appear. The same case on a backfilled invoice (`BASE_AMOUNT`
     line) shows the late Fee added and the base amount line untouched.
3. **Local Support Fees at the same seam (existing).** Prior art:
   `AgentInvoiceApiTest` (the Agent's draft and send) and
   `AgentInvoiceByIdApiTest` (Manager override, approve, mark paid). A new
   `LocalSupportFeesFollowClientInvoiceApiTest` covers, each through the
   Agent Invoice's own read:
   - no edits anywhere → Local Support Fees equal to today's computation, for
     a Contract with no Client Invoice, a never-sent draft, and a sent one;
   - an edit on a never-sent draft → a draft Agent Invoice reads the edited
     figure; a reset → back to the computation;
   - Agent Invoice sent after the edit → its snapshot includes the edit;
   - Agent Invoice sent, then an edit (a sent-back fixture for the Client
     Invoice, or a never-sent draft) → Local Support Fees move by exactly the
     difference; a Fee logged after the Agent Invoice's send does not slip in;
     one audit line;
   - Agent Invoice approved, then an edit → Local Support Fees unchanged;
     paid → unchanged;
   - a Fee logged after the Client Invoice's send → counted at its logged
     amount; the same Fee once a send-back fixture shows it as a pre-filled
     line → Local Support Fees unchanged; that line edited → moved by the
     edit's difference;
   - a Client Invoice approved → Local Support Fees equal to its billed total
     plus anything not billed;
   - a legacy invoice with a `BASE_AMOUNT` line → its base amount, with no
     per-SIM additions;
   - the Manager's override on a sent Agent Invoice still changes only Salary
     and new advance, and an edit afterwards leaves those as overridden.
4. **The races at the same seam, not with mocks.** Two committing tests,
   each cleaning up its own rows instead of using `IntegrationTest`'s
   rollback:
   - edit versus Client Invoice send: whichever wins, the final `SENT`
     invoice's lines equal what its send stored, and the edit either landed
     before the send or got `409`;
   - edit versus Agent Invoice send, and edit versus Agent Invoice approve:
     the final Local Support Fees equal the rule's value for the final
     states, with the edit counted exactly once or, after approval, not at
     all.
5. **The prefactors and the read switch are proven by existing suites
   passing unedited:** `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest`,
   `AgentInvoiceApiTest`, `AgentInvoiceByIdApiTest`,
   `client-invoice-generation.spec.ts`,
   `client-invoice-submission-and-visibility.spec.ts`,
   `agent-invoice-submission-and-approval.spec.ts`,
   `manager-invoice-review-queue.spec.ts`. One exception is allowed and must
   be named in the ticket: an assertion that a sent invoice's
   `basePostpaidSims` is null, which this feature deliberately changes. An
   Agent Invoice assertion that fails because a Fleet changed after a Client
   Invoice's send is the Constraints edge; it is changed only with the edge
   named in the commit.
6. **Frontend component tests (Vitest + Testing Library; existing).** Prior
   art: `agent-invoice-override-control.test.tsx`,
   `client-invoices-view.test.tsx`, `client-invoice-detail-view.test.tsx`.
   They cover the edit control (open, Save, Cancel, `400` inline with the
   input kept, `409` copy, generic failure), the edited marker and Reset, no
   Edit on a sent invoice, the hint line, and the Manager's per-SIM lines and
   markers.
7. **One e2e spec, `edit-client-invoice-lines.spec.ts` (existing seam).**
   Prior art: `client-invoice-generation.spec.ts`, which asserts $25.00 base,
   $45.00 Fee, $70.00 total. On a Contract it creates itself: the Agent edits
   the SIM line to $31.40 and the Fee line to $40.00, sees $71.40, opens the
   Agent Invoice and sees Local Support Fees $1.40 higher than before the
   edits, resets the Fee line, sends, and the Manager sees the edited SIM line
   with "computed $25.00" and a total of $76.40.
8. **No unit tests** of the line-resolution or pay rule beyond the HTTP seam,
   which proves each branch (Backend rule 6).

## Decisions taken

### Settled by the human (2026-10-01)

- **Every line is editable by the Agent while the invoice is a draft; the
  computation only pre-fills.** Epic `## Reworked`, and the inbox answer.
- **A sent invoice returned to draft refreshes nothing.** That is
  `send-a-client-invoice-back`'s to build; this feature's `linesStored`
  makes it the default.
- **The base amount is one editable line per Postpaid SIM**, not one line for
  the whole base amount. The draft already lists each SIM, the carrier bills
  per SIM, and the Manager can see which SIM went up
  (`question-edit-lines-base-per-sim`).
- **Before the first send, new SIMs and Fees appear as new pre-filled lines,
  untouched lines keep following the Fleet and Fees, and an edited line keeps
  the Agent's amount until Reset. Nothing recalculates after the first send**
  (`question-edit-lines-before-first-send`). A sent-back draft's late-Fee
  lines (2026-10-02, below) add lines; they recompute none.
- **No reason is required for an edit.** The Manager sees each edit with its
  computed amount and has the Carrier Invoice File
  (`question-edit-lines-reason`).
- **The Agent and the Manager see "Edited · computed $X"; the Tester and the
  PDF see only the billed amount** (`question-edit-lines-who-sees`).
- **The one-way migration is approved**: every invoice stores its own lines,
  old invoices are copied into lines that read exactly as today, and nothing
  is deleted (`question-edit-lines-migration`).
- **The Agent's pay follows edits made before approval; after approval a
  correction is a carry-over.** Not the recommendation: "If an edit occur
  before approval it should update the agent own monthly pay otherwise its a
  carry-over" (`question-edit-lines-agent-pay`). This amends ADR 0002. The
  carry-over itself is `invoice-adjustment`'s.

### Answered by the human (2026-10-02)

- **A sent but not yet approved Agent Invoice moves by the edit's
  difference; once it is approved, the difference carries over.** As
  recommended (`question-pay-after-agent-invoice-sent`). This amends ADR 0003's
  freeze for the edit only.
- **A Fee logged after the Client Invoice was sent counts in that month's pay
  at its logged amount.** As recommended
  (`question-pay-late-fee-after-client-invoice-sent`). The rule keeps its
  second and third terms.
- **When a Client Invoice is sent back, a Fee of that month logged after the
  first send appears on it as a new pre-filled line, and every line it was
  sent with stays exactly as sent** (`question-late-fee-on-sent-back-invoice`,
  raised by `send-a-client-invoice-back`). This changes this spec's model: a
  draft with stored lines resolves its stored rows plus one pre-filled line per
  Fee of its month with no line, and the next send stores them. Story 25 and
  its test are reversed accordingly.

### Taken alone

- **One `client_invoice_lines` table plus a `linesStored` flag, not a copy of
  the computation on every draft.** A never-sent draft stores only its edited
  lines and computes the rest, so an untouched draft behaves exactly as today
  with no writes on read. A sent invoice stores every line. One flag decides
  which read applies, and send-back needs only to change the status.
- **Send-back is natural by construction:** a draft with `linesStored` serves
  and edits its stored rows; the send inserts rows only for lines that have
  none, and never rewrites a stored row of an invoice already sent. The
  sibling spec drops its "clear the Fee snapshot rows" and "the base amount
  of a past month" rules: a stored line is never recomputed, whatever the
  month.
- **A late-Fee line on a sent-back draft is resolved, not stored, until it is
  edited or sent.** That is the never-sent draft's pattern reused, so a
  send-back still writes nothing, and a Fee logged while the invoice is back
  with the Agent shows up too.
- **A sent-back draft adds Fee lines only, never Postpaid SIM lines.** The
  answer names Fees; SIM membership has no dates, so a SIM on the Fleet today
  is not evidence it billed a past month, and the base amount the Manager
  reviewed stays as sent. An unbilled SIM still counts in pay by the rule's
  third term.
- **On a draft with stored lines, a reset saves the computed amount and keeps
  the row.** Deleting a row the invoice was sent with would drop the line;
  a late-Fee row at its computed amount reads the same as the pre-fill,
  because a Fee's amount never changes.
- **The invoice stores amounts, not Fee membership.** An edited amount cannot
  live on the Fee, which stays immutable and is read by the Fee list.
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
- **The audit lines follow `agentInvoiceOverridden`**: old and new amount, no
  free text.
- **The edit UI copies `AgentInvoiceOverrideControl`** inline, with no dialog,
  so `DialogShell`'s debt (tech-debt.md:16) stays out of this feature.
- **Local Support Fees are computed per Contract from the Client Invoice's
  lines, through the same line resolution the Client Invoice reads with.**
  One resolver means the bill and the pay cannot disagree about a line. It
  is a new `ContractAmountService` operation, so ADR 0002's single home for
  "a Contract's amount for a month" stays single.
- **A Contract with no Client Invoice, or an unedited never-sent draft, uses
  the computation.** That is the same number, and it needs no Client Invoice
  to exist, which ADR 0002 rejected requiring.
- **Approval needs no special case in the pay rule.** An approved Client
  Invoice cannot be edited, so "final at approval" holds by construction.
- **A sent Agent Invoice moves by the edit's difference, not by a
  recomputation** (the human's answer of 2026-10-02 says "by the edit's
  difference"). A recomputation would also pull in Fees logged since its
  send, which ADR 0003's freeze keeps out.
- **The difference is applied inside the edit's transaction, under a lock on
  the Agent Invoice that its send, approve, mark-paid and override also
  take, in the order Client Invoice then Agent Invoice.** Without it, an
  approve that read the invoice before the edit would save a stale figure
  over the moved one.
- **The Agent Invoice keeps one Local Support Fees column and gains no
  migration.** The audit line records each move; a breakdown per Contract is
  not asked for.
- **One ADR, 0004, covers the lines and the pay rule**, with dated notes on
  0001, 0002 and 0003, the shape ADR 0003's 2026-09-17 note set. The two
  decisions were asked for together and share one rule. The send-back ADR
  takes the number after.
- **The pay change is its own backend ticket**, after the edit ticket, so the
  edit's behaviour is proven before pay depends on it, and each ticket stays
  within three modules.
- **The hint line says the Agent Invoice follows the edits "unless it is
  already approved"**, rather than reading the Agent Invoice's status into
  the Client Invoice response. It is true in every state and needs no new
  field.
- **Testing: the existing migration, HTTP, component and e2e seams, plus
  committing race tests.** Prior art is named under `## Testing decisions`.

- **The read paths are transactional (after review, `agent-edits-a-client-invoice-line`).** `toResponse`, `pdf` and `latestSentOrApproved` are `@Transactional(readOnly = true)` and `approve` is `@Transactional`. Reason: `open-in-view` is off and stored lines hold lazy SIM and Fee references; outside a transaction they would fail in production, invisibly to `IntegrationTest`. Recorded as debt.
- **An override row for a SIM that no longer bills stays on a never-sent draft as an edited line with computed amount 0 (after review, `agent-edits-a-client-invoice-line`).** It follows this spec's taken-alone decision that such a line is kept; the previous ticket's read had dropped it. It can be edited or reset away.

- **The ticket cut's last critic failure was fixed by the orchestrator, not escalated (after review).** The second critique failed R3 on `serve-client-invoices-from-stored-lines` only: a criterion checked an `edited` field that `agent-edits-a-client-invoice-line` introduces. The critic's own rewording was applied verbatim (late Fee asserted in `feeLines` at its logged amount; "same Fee lines, base amount and totals"), plus its advisory that an edited SIM row shows its billed amount. Reason: a wording slip with the fix given, not an unclear spec, so escalation case 4 would ask the human nothing they could answer.

## Open questions

None

## Acceptance walkthrough

1. [agent] Create a Contract with two Postpaid SIMs (A at $25.00, B at $15.00) and log one $45.00 Fee this month. As its Agent, read the current-month Client Invoice and show one line per SIM and one Fee line, every `edited` false, base $40.00 and total $85.00, the same figures the computation gives today. Read the Agent's Agent Invoice draft and record its Local Support Fees. (stories: 1, 23, 32)
2. [agent] `PUT …/client-invoice/lines` SIM A to `31.40` and the Fee line to `40.00`. Show `200`, both lines `edited: true` with `computedAmount` 25.00 and 45.00, base $46.40, Fees $40.00, total $86.40. (stories: 2, 3, 4, 5)
3. [agent] Read the Fee from the Fee list and its Request, and show $45.00 unchanged. Read the Agent Invoice draft again and show Local Support Fees $1.40 higher than in step 1. (stories: 13, 14)
4. [agent] Send `-1`, a blank amount and `1.234` and show `400` each; send `0` for SIM B and show `200` with base $31.40. Send another month's Fee id and a retired SIM's id and show `404`. (stories: 7)
5. [agent] Add Postpaid SIM C at $10.00 and log a $10.00 Topup Fee. Read the draft and show both as new pre-filled lines, SIM A still $31.40, and the Fee line still $40.00. Reset the Fee line by saving `45.00` and show it `edited: false`. (stories: 6, 8, 9)
6. [agent] Call the edit route with a Manager's, a Tester's and another Agent's token and show `403`; with `OtherTenantFixture`'s Contract and show `404`. (stories: 15)
7. [agent] Send the Agent Invoice and show its frozen Local Support Fees include SIM A at $31.40 and SIM B at $0. Then edit SIM A on the still-draft Client Invoice to `35.00` and show the sent Agent Invoice's Local Support Fees up by exactly $3.60, with one `agentInvoiceLocalSupportFeesFollowed` audit line. Log another Fee and show the sent Agent Invoice unchanged. (stories: 26, 27, 30)
8. [agent] Send the Client Invoice. As the Manager, read it by id and show the same lines and amounts the Agent saw, per SIM, with `edited`/`computedAmount`. Show the Review Queue row's total equal to it. Log one more Fee and show the Client Invoice unchanged. Edit a line and show `409`. (stories: 10, 11, 16, 17, 18)
9. [agent] On a second Contract whose Client Invoice is already sent, log a $10.00 Fee for the month and show it counted in a draft Agent Invoice's Local Support Fees at $10.00. (stories: 28)
10. [agent] Approve the Agent Invoice as the Manager. On a third Contract's never-sent Client Invoice, edit a line and show the approved Agent Invoice's Local Support Fees unchanged, and no follow audit line. Approve a Client Invoice and show an edit to it is `409`. (stories: 29)
11. [agent] As a Tester of the Client, read the invoice and download its PDF. Show the billed amounts and totals, `computedAmount` and `edited` null, and no "computed" text in the PDF. (stories: 19)
12. [agent] Grep the backend log for steps 2 and 7 and show one `clientInvoiceLineEdited` audit line per edit, with the Agent as actor, the Tenant, the line and the old and new amounts, and the one follow line. (stories: 21, 30)
13. [agent] Run the race tests and show the sent lines always equal what the send stored, the edit having landed first or got `409`; and that the Agent Invoice's Local Support Fees count the edit exactly once against a send, and not at all after an approve. (stories: 22, 31)
14. [agent] Run `ClientInvoiceLinesMigrationTest` green. On the demo stack, before migrating, record the JSON and PDF of a past `APPROVED` Client Invoice, the Review Queue rows, and every sent and approved Agent Invoice; migrate, and show every amount, total, Fee line, queue row and Agent Invoice identical. (stories: 20)
15. [agent] Run the story-25 cases of `ClientInvoiceLineEditApiTest`: a sent invoice set back to `DRAFT` by a fixture serves every line as sent, shows a Fee logged after the send as a new pre-filled line and no line for a SIM added after it, accepts an edit of a sent line and of the late-Fee line, and resends without error with the late-Fee line stored; the backfilled variant keeps its base amount line untouched. Show the draft Agent Invoice's Local Support Fees unchanged by the late Fee's appearance. (stories: 25, 28)
16. [agent] In a browser as the Agent, open Client Invoices. Edit SIM A to 31.40 and show the marker "Edited · computed $25.00", the new totals and a Reset action. Type `-5` and show the inline error with `-5` still in the field. Show the hint that the Agent Invoice follows these amounts unless already approved, and that no reason is asked for. Open the Agent Invoice and show the Local Support Fees moved. Reset a line and show its marker gone. (stories: 2, 3, 4, 5, 6, 7, 12, 14)
17. [agent] Send the Client Invoice and show no Edit or Reset left, with the markers still shown. In a second tab still on the draft, try an edit and show the refresh message. (stories: 10, 11)
18. [agent] In a browser as the Manager, open the invoice from the Review Queue and show the per-SIM lines with the edited marker, and the same total in the queue and on the Dashboard's Pending approvals card. (stories: 16, 17, 18)
19. [agent] In a browser as the Tester, open the invoice and the PDF and show the billed amounts with no marker. (stories: 19)
20. [agent] Edit, reset and save by keyboard alone with a visible focus ring at each stop; repeat at the mobile breakpoint and show the row actions and the inline field usable within the viewport. Do the Manager's detail page the same way. (stories: 24)
21. [agent] Run `mvn verify`, the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green, with the suites named in `## Testing decisions` item 5 unedited apart from the named exceptions, and no golden moved except the Agent's draft page if captured. (stories: 20, 23, 32)
22. [human] Take a real carrier bill for a month where a Postpaid SIM's usage went over its plan. As the Agent, set that SIM's line to the billed figure, check the Agent Invoice's Local Support Fees went up by the same amount, and send both. As the Manager, review them. Confirm the "Edited · computed" line tells you enough to approve without a written reason, that the Agent Invoice figure is what you expect to reimburse, and that the Tester's statement looks right to send a Client. (stories: 2, 5, 12, 14, 16, 19, 26)
23. [human] Read the new ADR and the notes on ADR 0001, 0002 and 0003. Confirm they say what you settled: the computation only pre-fills, every line is the Agent's to edit while draft, nothing moves once sent, a sent invoice keeps its own lines, a sent-back draft adds a pre-filled line for each late Fee of its month and recomputes none, the Agent's pay follows edits until approval (a sent Agent Invoice by the edit's difference), after which a correction is a carry-over, and a Fee the Client Invoice does not bill still counts at its logged amount. (stories: 10, 14, 20, 25, 27, 28, 29)

## Execution order

Six tickets, each touching at most three `ARCHITECTURE.md` modules. They run
in order: each depends on the one before (ticket 6 on ticket 5, which already implies ticket 3).

1. `store-client-invoice-lines`. Labels: `enabler`, `backend`. Depends on
   nothing. Modules: `domain`, `repository`. (stories: —)
   - The `ClientInvoiceLine` entity, `ClientInvoice.linesStored`, the line
     repository, and the migration with its backfill.
   - `ClientInvoiceLinesMigrationTest`. No behaviour changes yet.
2. `serve-client-invoices-from-stored-lines`. Labels: `enabler`, `backend`.
   Depends on `store-client-invoice-lines`. Modules: `web`, `repository`,
   `demo`. (stories: 1, 10, 20, 23, 25)
   - The send moves into `ClientInvoiceService`, one transaction, under the
     row lock, inserting a row for every shown line that has none and setting
     `linesStored`; it stops writing `snapshotBaseAmount` and Fee snapshot
     rows.
   - The line resolution, including a stored-lines draft's pre-filled
     late-Fee lines, and `toResponse` following it; the response shape is
     unchanged.
   - `findQueueRows` sums the lines. `DemoDataLoader` seeds lines for its past
     invoices.
   - The new ADR (lines part), the note on ADR 0001, and the tech-debt entry
     for the unused snapshot structures.
   - Existing suites pass unedited; the story-25 fixture test's read and
     resend parts (lines as sent, the late Fee pre-filled, no new SIM line,
     the resend storing it).
3. `agent-edits-a-client-invoice-line`. Labels: `backend`. Depends on
   `serve-client-invoices-from-stored-lines`. Modules: `dto`, `web`,
   `logging`. (stories: 2, 3, 4, 5, 6, 7, 8, 9, 11, 13, 15, 17, 19, 21, 22)
   - The edit request DTO, the `PUT …/lines` route, `editLine` (steps 1–6),
     the audit method.
   - The additive response fields, per-SIM lines on every status, and their
     omission for Testers.
   - `editLine` on a stored-lines draft: updating a stored row, inserting one
     for a late-Fee line, a reset that keeps the row.
   - `ClientInvoiceLineEditApiTest`, including the story-25 edit parts, and
     the edit-versus-send race test.
4. `local-support-fees-follow-billed-client-invoice-lines`. Labels:
   `backend`. Depends on `agent-edits-a-client-invoice-line`. Modules: `web`,
   `repository`, `logging`. (stories: 14, 26, 27, 28, 29, 30, 31, 32)
   - The `ContractAmountService` payable-amount operation and
     `computeLocalSupportFees` calling it, in the draft read and the send
     snapshot.
   - The Agent Invoice locking finder; send, approve, mark-paid and override
     made transactional under it.
   - `editLine` step 7: the difference handed to a `SENT` Agent Invoice, and
     the `agentInvoiceLocalSupportFeesFollowed` audit method.
   - The new ADR's pay part, and the notes on ADR 0002 and 0003.
   - `LocalSupportFeesFollowClientInvoiceApiTest` and the Agent Invoice race
     tests; `AgentInvoiceApiTest` and `AgentInvoiceByIdApiTest` pass
     unedited.
   - Built as the human answered on 2026-10-02: a sent Agent Invoice moves
     by the edit's difference until approved, and a Fee the Client Invoice
     does not bill counts at its logged amount.
5. `agent-edits-lines-on-the-client-invoice-page`. Labels: `frontend`.
   Depends on `local-support-fees-follow-billed-client-invoice-lines`.
   Modules: `components/agent`, `app/api`, `lib/api`.
   (stories: 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 14, 24)
   - `EditClientInvoiceLineControl`, the marker, Reset, the hint, the BFF
     proxy and the types.
   - Component tests and `edit-client-invoice-lines.spec.ts`, including its
     Agent Invoice step.
6. `manager-sees-edited-client-invoice-lines`. Labels: `frontend`. Depends on
   `agent-edits-a-client-invoice-line` and
   `agent-edits-lines-on-the-client-invoice-page`. Modules:
   `components/manager`.
   (stories: 16, 17, 18, 24)
   - Per-SIM base lines and the edited marker on the Client Invoice detail
     page; component tests.
   - The Manager's closing steps of `edit-client-invoice-lines.spec.ts` (the
     edited SIM line with "computed $25.00", total $76.40). They extend the
     spec ticket 5 creates, which is why this ticket depends on ticket 5 too.
