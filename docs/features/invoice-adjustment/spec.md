---
feature: invoice-adjustment
epic: invoice-correction-and-history
status: draft
date: 2026-10-06
---

<!-- sdlc:template spec 1 -->

# Invoice adjustment

## Problem

A Company Manager who finds an amount wrong on a Client Invoice of a month
that has already closed has no way to correct it. Fees can never be edited,
voided or back-dated (`FeeController` puts a new Fee in the month it is
logged), and once a month is closed nobody may edit its invoices. So the
error stays on the record, and settling it happens outside the app, from
memory.

The human settled how the business works, in two steps:

- 2026-09-30 (epic `## Reworked`): a past month is never reopened; the
  **Manager** corrects it on a later month's invoice. There is no
  `correct-a-fee`.
- 2026-10-08 (epic `## Reworked`, inbox `adjustment-which-month`): **the 5th
  closes the month.** Until the 5th of the following month a month's Client
  Invoice and Agent Invoice stay editable, draft, sent or approved, and an
  Agent's edit moves that month's pay directly. From the 6th the month is
  closed for everyone; an invoice not yet approved by then stays open, is
  flagged Late, and closes on approval. That rule is the earlier feature
  `month-closes-on-the-fifth`, which this spec depends on and does not
  re-specify. Once a month is closed, **only an adjustment** corrects it: the
  Manager records it, it lands on the **current month's** Client Invoice, and
  it **moves the Agent's pay by the same amount**.

A correction can lower an amount, which nothing in the app can express
today: a Fee must be greater than zero (`FeeCreateRequest`), and a Client
Invoice line's amount is checked `>= 0`.

## Journeys

Advances `docs/roadmap/invoice-correction-and-history.md`, the fourth of its
features, after `edit-client-invoice-lines`, `send-a-client-invoice-back` and
`month-closes-on-the-fifth`.

- **Send an invoice back for correction** (`wanted`, stays `wanted`). This
  feature delivers the epic's proof sentence "a Manager who finds an error
  after approval records an adjustment, a credit or a charge, that lands on
  the next month's invoice". The journey reaches `exists` only when
  `send-an-agent-invoice-back` lands too, so `docs/journeys.md` is not edited
  here.
- **Bill the Client for the month** (`exists`, stays `exists`, extended). A
  Client Invoice can carry adjustment lines below its lines.
- **Get the Agent paid for the month** (`exists`, stays `exists`, extended).
  An adjustment landing on a Contract's Client Invoice moves the Agent's
  Local Support Fees for that month by the same amount, through the payable
  amount. The Agent Invoice gains no new figure.
- **Work through what is waiting** (`exists`, stays `exists`). Review Queue
  totals include the adjustments an invoice carries. Membership is unchanged.
- **Look back at finished invoices** (`wanted`): untouched. `invoice-history`
  will display the adjustments this feature adds.

## Goals / Non-goals

**Goals**

- From a Client Invoice whose month is **closed** (as `month-closes-on-the-fifth`
  defines it), a Manager records an **adjustment**: a credit (lowers the bill)
  or a charge (raises it), an amount in the Contract's currency, and a
  required reason. There is one kind of adjustment (2026-10-08).
- The adjustment **lands on the current month's Client Invoice of the same
  Contract** at the moment it is recorded. The Manager never picks the month
  (2026-10-08).
- It **moves the Agent's pay by the same amount**: the Contract's payable
  amount for the receiving month includes it, so the Agent's Local Support
  Fees for that month follow (2026-10-08). The one exception is a charge for
  something the Agent was already paid for (open question 1).
- It shows on the receiving invoice, on every surface that shows that
  invoice's figures: the detail pages, the Agent's card, the Review Queue
  total, the Tester's view and the PDF, each with the month it corrects and
  its reason. The Agent sees it read-only (2026-10-08); the Client sees the
  reason (2026-10-08).
- A Manager may withdraw an adjustment until its receiving invoice is sent
  (2026-10-08).
- The invoice an adjustment corrects lists it, pending, on a sent invoice
  (which one) or withdrawn, so the Manager can see the correction was made.

**Non-goals.** Each of these is something a reasonable agent would otherwise
build.

- **No closing rule here.** When a month closes, the Late flag, and what an
  edit to a sent or approved open-month invoice does to its review are
  `month-closes-on-the-fifth`'s. This spec only reads "is this invoice's month
  closed".
- **No adjustment on an open month.** Before the month closes the invoice is
  edited or sent back instead; the record action is not offered.
- **No reopening a closed month.** No adjustment ever lands on the invoice it
  corrects or on any closed month.
- **No Agent Invoice adjustment, and no adjustment to the Agent's pay alone**
  (2026-10-08, the two-kinds design declined). An adjustment always sits on
  the Client's bill. A Salary or Rollout Advance error is corrected before
  the month closes, by the Manager's existing override.
- **No automatic carry-over** (2026-10-08). No adjustment is ever generated
  from an edit: until the 5th an Agent's edit moves that month's pay directly,
  and after it the Agent cannot edit. `ClientInvoiceService.editLine` is not
  touched.
- **No rule for a credit larger than the invoice it lands on** (2026-10-08;
  epic `## Later`). Nothing is split, carried on or refused; the receiving
  invoice's total is the plain sum of its lines and adjustments.
- **No choosing the receiving month.** It is always the current billing month.
- **No editing, voiding or back-dating a Fee.** An adjustment is not a Fee,
  has no Request, and never changes a Fee row. There is no `correct-a-fee`.
- **No negative Fee and no negative Client Invoice line.** `FeeCreateRequest`'s
  `> 0` rule and the line's `amount >= 0` check stay. Only an adjustment can
  be negative.
- **No Agent-made adjustment.** Only a Manager records or withdraws one.
- **No Agent refusal, acknowledgement or dispute flow** (2026-10-08). The
  Agent sees it and settles any disagreement with the Manager outside the app.
- **No editing an adjustment.** A wrong pending one is withdrawn and recorded
  again; one already on a sent invoice is corrected by another adjustment.
- **No separate adjustment figure on the Agent Invoice.** Its Local Support
  Fees include the adjustment the way they include every billed line.
- **No notification** of any kind. The line on the draft is the signal.
- **No list of all adjustments across the Tenant.** They are seen on the
  invoice they correct and on the invoice that receives them.
  `invoice-history` may add one.
- **No settling outside an invoice.** An adjustment whose receiving invoice is
  never sent stays pending on it.
- **No Agent Invoice send-back.** That is `send-an-agent-invoice-back`.
- **No dropping of the dead snapshot structures** (`snapshotBaseAmount`,
  `ClientInvoiceFeeSnapshot`). Still a later contract step.

## User stories

1. As a Company Manager, I want to record a credit on a closed Client Invoice I find was overbilled, with a reason, so that the Client is refunded on the current month's invoice without reopening the closed one.
2. As a Company Manager, I want to record a charge on a closed Client Invoice I find was underbilled, with a reason, so that the Client pays what was missed on the current month's invoice.
3. As a Company Manager, I want an adjustment to move the Agent's pay for the current month by the same amount, so that the Agent is paid on what the Client is really billed without my recording anything twice.
4. As a Company Manager, I want to enter the amount as a positive figure and choose credit or charge, so that I never have to reason about signs.
5. As a Company Manager, I want to be refused, inline and with my input kept, an empty reason, a reason over 1000 characters, a zero, negative, blank or over-precise amount, so that I fix the input rather than start again.
6. As a Company Manager, I want no adjustment action on an invoice whose month is still open (draft, sent, approved before the 6th, or Late and not yet approved), so that an error caught in time is edited or sent back instead.
7. As a Company Manager, I want the reason field labelled "Shown to the Client on the invoice", so that I write it for the Client.
8. As a Company Manager, I want an adjustment to land on the current month's Client Invoice of the same Contract, without choosing a month, so that it can never be aimed at a closed month.
9. As a Company Manager, I want the invoice I corrected to list every adjustment recorded from it, its amount, reason, and whether it is pending, on a sent invoice (which one) or withdrawn, so that I can see the correction was made and where it went.
10. As a Company Manager, I want to withdraw an adjustment while its receiving invoice has not been sent, so that a wrong correction never reaches a sent invoice.
11. As a Company Manager, I want an adjustment whose receiving invoice was sent to have no withdraw action, so that a figure already sent is corrected only by another adjustment.
12. As a Company Manager reviewing a sent invoice, I want its adjustments shown as their own lines, with the month they correct and the reason, and included in the total, so that I approve the real amount knowing why it differs.
13. As a Company Manager, I want the Review Queue totals of the Client Invoice and of the Agent's Agent Invoice to include the adjustments, so that the queue and the invoices agree.
14. As a Company Manager whose page is stale (the receiving invoice was sent, or the adjustment was already withdrawn), I want a clear message telling me to refresh, so that I do not assume my action worked.
15. As an Agent, I want my Client Invoice to show each adjustment it receives as a read-only line, with the month it corrects and the Manager's reason, and its total to include it, so that I send what will actually be billed.
16. As an Agent, I want an adjustment line to have no edit or reset control, so that it is clear the correction is the Manager's.
17. As an Agent, I want my Local Support Fees for the month to move by each adjustment my Contract's Client Invoice receives, so that my pay follows the corrected bill.
18. As an Agent, I want an adjustment recorded after I sent this month's Client Invoice to appear on it and move my pay, since the month is still open, so that a correction is never deferred to a month later than the current one.
19. As an Agent, I want a sent-back invoice to keep the adjustments it receives, so that correcting it never drops a correction.
20. As the company, I want a charge for something the Agent was already paid for to leave the Agent's pay unchanged, so that the Agent is never paid twice (open question 1).
21. As a Tester, I want a sent or approved Client Invoice to show its adjustment lines, each labelled with the month it corrects and the reason, and its total to include them, so that I understand why this month's bill differs from the work done.
22. As a Tester, I want the PDF to show the same adjustment lines and total, so that the document and the page agree.
23. As the company, I want every adjustment recorded and withdrawn written to the audit log (who, which, amount, without the reason's text), so that every correction can be traced.
24. As the company, I want an adjustment to always name the invoice it corrects, so that an amount with no Request still traces back to a reviewed record.
25. As an Agent or a Tester, I want to be unable to record or withdraw an adjustment, so that it stays the Manager's power.
26. As a Manager of one Tenant, I want every adjustment route on another Tenant's invoice refused as if it did not exist, so that the Tenant boundary holds.
27. As an Agent, I want to see adjustments only on my own Contracts' invoices, so that I never see another Agent's.
28. As the company, I want a record or a withdraw racing a send of the receiving Client Invoice or of the Agent's Agent Invoice to leave the adjustment counted exactly once on the bill and once in the pay, so that no money is lost or doubled.
29. As everyone already using the product, I want invoices with no adjustment to look, total and behave exactly as before, so that this change carries no release risk.
30. As a Manager or an Agent working by keyboard or on a phone, I want the adjustment form, the adjustment list and the adjustment lines usable without a mouse and at the mobile breakpoint, so that the action is available wherever I work.

## Solution

### Builds on, unchanged

- **`month-closes-on-the-fifth`** (must be merged first). This spec needs one
  thing from it: a predicate saying whether a Client Invoice's month is
  closed (approved, and past the 5th of the following month; a Late invoice
  closes on approval). It also relies on that feature's rule that an edit to
  an open month's Client Invoice, whatever its status, moves that month's
  Agent Invoice's Local Support Fees (the human's 2026-10-08 words). If that
  feature leaves a status in which an open-month edit does not move pay, the
  adjustment's pay effect has a hole there, and the ticket must stop and
  raise it.
- **The line model and the one resolution** (`edit-client-invoice-lines`,
  ADR 0004): `ClientInvoiceService.lines(invoice)` stays the only place that
  decides an invoice's lines. Adjustments are **not** Client Invoice lines
  (see Decisions taken); they are resolved beside the lines by one new
  operation, and the response, the total, the PDF, the queue sums and the
  payable amount add them.
- **Send-back** (ADR 0005): a send-back leaves a receiving invoice's
  adjustments on it.
- **The row locks and their order**: Client Invoice, then Agent, then Agent
  Invoice. Every new write takes the locks it needs in that order.

### The model: an adjustment

One table:

```
invoice_adjustments
  id, tenant_id
  contract_id
  corrects_invoice_id → client_invoices     (closed when recorded)
  receiving_month     date, first of month  (the billing month current when recorded)
  amount   numeric(12,2), <> 0, signed      (credit negative, charge positive)
  currency (copied from the Contract)
  reason   varchar(1000), not blank
  recorded_at, recorded_by
  withdrawn_at, withdrawn_by null
  -- per open question 1's recommendation only:
  already_paid_fee_id → fees null, already_paid_sim_card_id → sim_cards null
```

The receiving invoice is addressed by `(contract_id, receiving_month)`, not by
an id: the current month's Client Invoice may not exist as a row yet (it is
get-or-created on first view), and the pair names it either way. The
receiving month is always open, because the current month closes only on the
6th of the next, so an adjustment can never land on a closed month or on the
invoice it corrects.

Its state is derived, never stored:

```
WITHDRAWN  withdrawn_at set                                          (final)
PENDING    not withdrawn; receiving invoice absent or DRAFT          (withdrawable)
SENT       not withdrawn; receiving invoice SENT or APPROVED         (not withdrawable)
```

A send-back of the receiving invoice returns its adjustments to `PENDING`
(the invoice is a draft again, "until the invoice is sent" holds again). Once
the receiving month closes, `SENT` is final.

### What a Manager does

- **Record.** `POST /api/client-invoices/{id}/adjustments`, Manager only,
  body `{ "kind": "CREDIT" | "CHARGE", "amount": "12.50", "reason": "…" }`.
  `amount` is positive, at most two decimals, within the amount bounds
  already used for lines. `201` with the adjustment; `400` invalid body;
  `404` not in the caller's Tenant; `409` coded `INVOICE_MONTH_NOT_CLOSED`
  when `{id}`'s month is open; `403` for any other role. It sets
  `receiving_month` to the current billing month, `currency` from the
  Contract. In one transaction it locks the receiving Client Invoice if it
  exists, then the Agent Invoice of the receiving month if it exists, writes
  the row, and moves that Agent Invoice's Local Support Fees by the amount
  exactly as a line edit of that month would (see below).
- **Withdraw.** `POST /api/invoice-adjustments/{adjustmentId}/withdraw`,
  Manager only. `200`; `409` coded `ADJUSTMENT_NOT_PENDING` when it is
  `SENT` or `WITHDRAWN`, carried by a `Reason` enum as the other coded
  conflicts are. It takes the same locks as a record, re-reads the
  adjustment, and moves pay back by the amount.
- **See.** The corrected invoice's by-id response gains
  `adjustmentsRecorded`: every adjustment recorded from it, with `state`, and
  the receiving month (and invoice id, when it exists).

### What a Client Invoice carries

`ClientInvoiceResponse` gains:

- `adjustments`: every non-withdrawn adjustment with this invoice's Contract
  and `receiving_month = billingMonth`, each
  `{ id, amount, correctsBillingMonth, reason, pending }`;
- `adjustmentsTotal`;
- `totalAmount` now includes `adjustmentsTotal`. Lines and the base amount
  are unchanged.

Because the receiving month is fixed at record time, nothing is stamped at
send: the invoice's adjustments are its rows, whatever its status. One
recorded while the invoice is `SENT` or `APPROVED` appears on it at once;
that is the human's rule (the current month's invoice, whatever its status)
and, for the invoice's review state, a Manager edit of an open-month invoice
under `month-closes-on-the-fifth`.

- **Tester.** On a Client Invoice the Tester already sees (`SENT`, `APPROVED`),
  each adjustment shows its amount, "Adjustment to the {Month YYYY} invoice"
  and its reason (2026-10-08). A Tester is never shown `adjustmentsRecorded`.
- **PDF.** The Client Invoice PDF renders an "Adjustments" block after the
  lines, same label and reason, and the total including them.
- **Review Queue.** `ClientInvoiceQueueRow` adds the invoice's adjustments to
  its total. The Agent Invoice queue row needs nothing: its Local Support
  Fees already include them.

### The pay: same amount, through the payable amount

`ContractAmountService.payableAmountForMonth(contract, month)` adds the
`adjustmentsTotal` of `(contract, month)` in every branch (no Client Invoice,
lines not stored, lines stored), minus any adjustment that open question 1
excludes. So:

- a `DRAFT` Agent Invoice of the receiving month reads it live;
- a `SENT` one moves by the amount in the record's (or withdraw's)
  transaction, with the existing `agentInvoiceLocalSupportFeesFollowed`
  audit line, as ADR 0004's table does for a line edit;
- an `APPROVED` or `PAID` one of an open month moves exactly as
  `month-closes-on-the-fifth` makes a line edit move it.

This is why one kind can serve both sides: pay already follows what the
Client Invoice bills (ADR 0004), so a credit for an overbilled SIM line
removes pay the Agent got from that line, and a charge for an underbilled
line pays what the Agent did not get. The only case it gets wrong is
something of the closed month that pay counted **without** it being billed:
ADR 0004 pays a Fee logged after the Client Invoice's send (and a Postpaid
SIM added after it) at its computed amount even though the invoice never
billed it. A charge for such a forgotten Topup would pay the Agent a second
time. A Topup never logged in the app was never paid, and its charge
correctly pays it once.

**Open question 1, as recommended:** on a charge, the form offers the closed
invoice's paid-but-unbilled items (each Fee and Postpaid SIM of that month
the invoice has no line for, with its computed amount). Picking one fills
the amount, stores the item's id, and that adjustment is left out of the
payable amount: it bills the Client and moves no pay, and its line reads
"Already in the Agent's pay" to the Agent and the Manager (never to the
Tester). The same item cannot be charged twice while a non-withdrawn
adjustment names it.

### ADR

**A new ADR 0006, "A closed month is corrected forward by an adjustment on the
current month's Client Invoice"** (numbered after 0005; if numbers shift at
merge, the next free one). It records:

- an adjustment is the only signed amount in the billing model; Fees and
  Client Invoice lines stay non-negative;
- an adjustment traces to the invoice it corrects, not to a Request. This
  narrows PRODUCT.md's principle "every billable amount traces back to a
  logged Request" for adjustments only, and the ADR says so;
- it exists only for a closed month and lands on the current month's Client
  Invoice, never on a closed one;
- it enters the Contract's payable amount for the receiving month, so the
  Agent's pay moves by the same amount (amending ADR 0004's payable amount),
  with open question 1's exception;
- no adjustment is generated automatically; ADR 0004's "carry-over for
  `invoice-adjustment`" row is not realised here (2026-10-08).

ADR 0004 gets a dated "Amended by ADR 0006" note. PRODUCT.md's principle 1
gets the same narrowing in the ADR's commit, a harness change the implementer
declares and the merger applies.

### Schema and contract

- **One additive migration**, `invoice_adjustments`, at the next free Flyway
  version at merge. No existing row rewritten, no existing check changed.
- `ClientInvoiceResponse` gains `adjustments`, `adjustmentsTotal` and
  (Manager by-id only) `adjustmentsRecorded`; `totalAmount` includes the
  adjustments. `AgentInvoiceResponse` changes shape not at all; its Local
  Support Fees value includes them. For an invoice with none, every existing
  field keeps its value.
- New routes as above; `SecurityConfig` gets Manager-only matchers for the
  record and withdraw routes, before the broader invoice matchers.
- Audit: two new `AuditLog` methods, `invoiceAdjustmentRecorded` and
  `invoiceAdjustmentWithdrawn` (ids, signed amount, receiving month, actor,
  Tenant), never the reason's text. A pay move reuses
  `agentInvoiceLocalSupportFeesFollowed`.

### Frontend

- **Manager, Client Invoice detail page.** A **Record adjustment** secondary
  control, shown only when the invoice's month is closed, expanding an inline
  form in `SendBackClientInvoiceControl`'s shape: a Credit / Charge segmented
  choice with a one-line hint each ("Lowers this month's invoice and the
  Agent's pay" / "Raises this month's invoice and the Agent's pay"), an
  amount field with the currency, a required reason textarea labelled
  "Shown to the Client on the invoice" (2026-10-08), and **Record** and
  **Back**. With Charge, the paid-but-unbilled picker of open question 1.
  Below the invoice's own figures, an "Adjustments recorded from this
  invoice" list: signed amount (credit with a minus), reason, state badge
  (Pending on {Month}, On the {Month} invoice with a link, Withdrawn), and
  **Withdraw** on pending rows.
- **Every Client Invoice view** (the Agent's card, the Manager's detail page,
  the Tester's invoice): an "Adjustments" block after the lines, one row per
  adjustment with "Adjustment to the {Month} invoice", the reason, the signed
  amount, a **Pending** badge while the invoice is a draft (not for a
  Tester), and "Already in the Agent's pay" where open question 1 applies
  (not for a Tester). The block is not rendered when there is none.
- **Line editor.** Adjustment rows never get the line editor's controls.
- **Agent Invoice pages**: unchanged; Local Support Fees carry the amount.
- **BFF.** Pass-through proxies for the new routes with `backendFetch`.

## Design direction

**Operate**, built from shipped patterns; `DESIGN.md` does not change.

- The record form copies the send-back form: 8px controls, `danger`-toned
  inline error, the page's single pill unchanged (Pill-Is-Primary Rule). The
  Credit / Charge choice uses the existing segmented control.
- Amounts use the tabular figures the invoice pages already use. A credit is
  shown with a leading minus in the body colour, not in red: it is not an
  error.
- The **Pending** badge is the `warning` tone ("awaiting" in DESIGN.md); the
  sent-on state and "Already in the Agent's pay" are `neutral`; **Withdrawn**
  is `neutral` with the row's text muted.

Visual goldens: **no new goldens**. Every adjustments block renders nothing
when empty, so no existing golden should move; any golden that moves is a
finding.

## Constraints

- Lands after `month-closes-on-the-fifth` (merged). No dependency on
  `send-an-agent-invoice-back`.
- Additive migration only, at the next free version at merge. Flyway runs
  with `outOfOrder=false`.
- An adjustment's amount is non-zero, at most two decimals, signed only in
  storage; the API takes a kind and a positive amount.
- An adjustment is recorded only from a closed invoice and lands only on the
  current billing month's invoice of the same Contract; its receiving month
  never changes; a withdrawn one never changes again.
- `payableAmountForMonth` includes every non-withdrawn adjustment of its
  `(contract, month)`, except those open question 1 excludes.
- Locks: record and withdraw take the receiving Client Invoice's lock, then
  the receiving month's Agent Invoice's, as `editLine` does; withdraw re-reads
  the adjustment under them. A send of either invoice and a record or withdraw
  serialize on those locks.
- The reason never appears in a log line or an audit line.
- Backend tests run under `IntegrationTest`, rolled back per method; races
  commit and clean up. No repository mocks (Backend rule 5).
- e2e and visual runs use the isolated stack; an e2e failure is judged
  against a controlled comparison on an idle machine, never waved off as a
  flake.
- Maven runs under JDK 21; Checkstyle stays clean.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, the rendered
accessibility tree, what a user sees. Never repository calls or component
internals.

1. **Backend: the HTTP API seam (primary, existing).** Prior art:
   `ClientInvoiceSendBackApiTest` (by-id Manager action, coded `409`, Tenant
   `404`, role `403`), `ClientInvoiceLineEditApiTest` (line resolution and
   totals), `LocalSupportFeesFollowClientInvoiceApiTest` (pay read through the
   Agent Invoice's own response), `ClientInvoiceByIdApiTest`. A new
   `InvoiceAdjustmentApiTest` covers: record on an approved invoice two months
   back (closed whatever today's date) and `409 INVOICE_MONTH_NOT_CLOSED` on a
   current-month invoice in each status; `400` cases; the adjustment on the
   current month's draft and its total, and on a current-month invoice already
   `SENT`; withdraw while pending, `409 ADJUSTMENT_NOT_PENDING` once the
   receiving invoice is sent, and pending again after its send-back; Tester
   view and `403`/`404` matrices; Review Queue totals. New cases in
   `LocalSupportFeesFollowClientInvoiceApiTest` cover the pay: a `DRAFT`
   Agent Invoice reads it, a `SENT` one moves by the amount on record and back
   on withdraw, and (open question 1) a charge naming an unbilled Fee moves
   none. The closed-month predicate itself is `month-closes-on-the-fifth`'s to
   test; this suite uses only months that are closed or open on every day.
2. **Races at the same seam, committing** (prior art
   `ClientInvoiceSendBackRaceTest`): record vs the receiving Client Invoice's
   send, withdraw vs that send, record vs the Agent Invoice's send; the
   adjustment ends counted once in the bill and once in the pay.
3. **Migration test** under the existing migration test package: the table
   applies on a database at the previous version with data, and its check
   refuses a zero amount.
4. **PDF**: an existing PDF test seam is extended to assert the adjustments
   block and total by extracted text, as the line-edit feature's PDF checks
   do.
5. **Frontend component tests** (Vitest + Testing Library). Prior art:
   `send-back-client-invoice-control.test.tsx`,
   `client-invoice-detail-view.test.tsx`,
   `edit-client-invoice-line-control.test.tsx`. Cover the record form (kind,
   validation, the Client-facing label, `409`, input kept), the control's
   absence on an open-month invoice, the recorded list and withdraw, the
   adjustments block's empty/pending/sent states, and no editor control on
   an adjustment row.
6. **One e2e spec, `invoice-adjustment.spec.ts`**, on `DemoDataLoader`'s
   approved Client Invoice of two months back (always closed): the Manager
   records a $20.00 credit, sees it Pending on this month in the list, the
   Agent sees it on the current month's card with the total down $20.00, and
   the Manager withdraws it. e2e state does not roll back, so the spec ends
   with the adjustment withdrawn.
7. **Existing suites pass unedited**: every invoice API test, the invoice
   e2e specs and the component tests, since an invoice with no adjustment is
   unchanged. Any forced edit is named in its ticket's `## Regression`.
8. **No unit tests** of the landing or pay rule beyond the HTTP seam
   (Backend rule 6).

## Decisions taken

### Settled by the human

- **Corrections carry forward, made by the Manager; no `correct-a-fee`**
  (2026-09-30).
- **The 5th closes the month; an adjustment exists only for a closed month,
  lands on the current month's invoice, and the Manager never picks the
  month** (2026-10-08, inbox `adjustment-which-month`). The closing rule is
  `month-closes-on-the-fifth`'s.
- **One kind of adjustment: it sits on the Client's bill and moves the
  Agent's pay by the same amount** (2026-10-08, inbox
  `adjustment-agent-pay-or-client-bill`; the two-kinds recommendation was
  declined). How a charge for something already paid avoids double pay is
  open question 1.
- **The Agent sees an adjustment with its reason, read-only; the Manager can
  withdraw it until the invoice is sent** (2026-10-08, inbox
  `adjustment-agent-sees-it`).
- **The Client sees the reason; the field is labelled "Shown to the Client on
  the invoice"** (2026-10-08, inbox `adjustment-client-sees-reason`).
- **No automatic carry-over** (2026-10-08, inbox
  `adjustment-carry-over-automatic`).
- **A credit larger than the receiving invoice is out of scope** (2026-10-08,
  inbox `adjustment-credit-bigger-than-invoice`; epic `## Later`).

### Taken alone

- **Adjustments are their own rows, not a new Client Invoice line kind.** A
  line kind would need the `amount >= 0` check relaxed and would be editable
  by the Agent's line rule; a separate table keeps every existing line rule
  true.
- **The receiving invoice is `(contract, receiving_month)`, fixed at record.**
  The human's rule names the month; the pair names the invoice even before
  its row exists, and nothing has to be stamped at send.
- **One recorded onto a sent or approved current-month invoice appears on it
  at once.** The human said "the current month's invoice" without exception,
  and the month is still open; what that does to the invoice's review is
  `month-closes-on-the-fifth`'s rule for any Manager edit.
- **"Pending" means the receiving invoice is a draft; a send-back makes it
  pending again.** That is the literal reading of "until the invoice is
  sent", and a sent-back invoice is a draft.
- **State derived, no status column.** Same shape as "sent back".
- **The pay moves through `payableAmountForMonth`, not a separate write.** The
  bill and the pay keep resolving through one place (ADR 0004), and a `SENT`
  Agent Invoice moves by the difference as it already does for a line edit.
- **No separate adjustment figure on the Agent Invoice.** Local Support Fees
  have never broken down per billed line; the Agent sees the adjustment on
  their Client Invoice.
- **A credit larger than the invoice totals to a plain, possibly negative
  sum.** Building nothing is cheapest to undo when the epic's Later item is
  taken up.
- **The API takes `CREDIT`/`CHARGE` and a positive amount; storage is signed.**
- **No editing an adjustment.** Withdraw and record again, one audit line
  each.
- **Record and withdraw take the Client Invoice then the Agent Invoice lock.**
  They move pay, so they serialize with both sends in the existing order.
- **The corrected invoice lists its adjustments (`adjustmentsRecorded`); no
  Tenant-wide list.** A list is `invoice-history`'s to add.
- **A new ADR 0006 with a note on 0004, and the narrowing of PRODUCT.md
  principle 1.** A signed, non-Request-traced billed amount is surprising
  without context and costly to reverse once invoices carry it.
- **Two new audit methods, without reason text.** Same rule as send-back.
- **Credits shown with a minus in the body colour.** A credit is not an error.
- **Testing: the existing HTTP, component, PDF and e2e seams, plus committing
  race tests; only months closed or open on every day.** The closing
  predicate's day boundary is the dependency's to test.

## Open questions

1. **When the Manager charges the Client for something the Agent was already
   paid for, how is the Agent kept from being paid twice?** Example: a $15
   Topup was logged in September after September's Client Invoice was sent;
   the app paid the Agent $15 for it but never billed the Client. In
   November the Manager charges the Client $15 for it; by the one-kind rule
   the Agent's November pay would also rise $15, a second time.
   *Recommendation:* when recording a charge, the Manager can pick that
   Topup (or any Fee or Postpaid SIM the closed invoice left off) from a
   list; such a charge bills the Client and does not move the Agent's pay,
   and the Agent sees it marked "Already in the Agent's pay". Every other
   adjustment moves pay by the same amount. *Reason:* the app knows exactly
   which items were paid but not billed, so the Manager cannot mark the
   wrong one; a Topup never logged in the app was never paid, and its
   ordinary charge pays the Agent once, correctly.

## Acceptance walkthrough

1. [agent] As the Manager, on the approved Client Invoice of two months back (demo data) for Contract C, `POST …/adjustments` a `CREDIT` of $20.00 with a reason; show `201`, amount `-20.00`, the Contract's currency, receiving month the current month, and the invoice's `adjustmentsRecorded` listing it `PENDING`. Record a `CHARGE` of $5.00 and show `201`. (stories: 1, 2, 4, 8, 9, 24)
2. [agent] Send a zero, a negative, a three-decimal and a blank amount, an empty and a 1001-character reason, and show `400` each; record on a current-month invoice as draft, sent and approved and show `409 INVOICE_MONTH_NOT_CLOSED` each; with the Agent's and a Tester's token show `403`; on `OtherTenantFixture`'s invoice show `404`. (stories: 5, 6, 25, 26)
3. [agent] As C's Agent, read the current month's Client Invoice draft and show the credit and the charge as `adjustments` with `pending: true`, `correctsBillingMonth`, the reason, and `totalAmount` equal to lines − 20.00 + 5.00; try to edit an adjustment through the line edit route and show it refused. Read the Agent's current Agent Invoice draft and show Local Support Fees down 15.00 from before step 1. (stories: 3, 15, 16, 17)
4. [agent] Withdraw the $5.00 charge; show it `WITHDRAWN`, gone from the draft, and Local Support Fees up 5.00. As the Agent, send the Agent Invoice, then record a $10.00 credit from the closed invoice; show the sent Agent Invoice's Local Support Fees down 10.00 and an `agentInvoiceLocalSupportFeesFollowed` audit line. (stories: 10, 17, 28)
5. [agent] As the Agent, send the current Client Invoice; show the adjustments on the sent response with `pending: false`, `adjustmentsRecorded` showing them on this invoice, the Review Queue totals (Client Invoice and Agent Invoice) including them, and withdraw returning `409 ADJUSTMENT_NOT_PENDING`. Record another $3.00 credit and show it on the sent invoice at once. Send the invoice back, show the adjustments still on it and withdraw now accepted; resend. (stories: 11, 12, 13, 18, 19)
6. [agent] As a Tester of C's Client, read the sent invoice and show each adjustment line labelled with the corrected month and its reason, no `adjustmentsRecorded`; download the PDF and show the Adjustments block and the total. (stories: 21, 22)
7. [agent] As another Agent, read C's invoices and show `404`, and show none of C's adjustments on their own invoices. (stories: 27)
8. [agent] (Per open question 1's answer.) On a closed fixture invoice with a Fee logged after its send, record a charge picking that Fee; show it on the current month's Client Invoice and Local Support Fees unchanged; try to charge the same Fee again and show it refused. (stories: 20)
9. [agent] Run the committing race tests: record vs Client Invoice send, withdraw vs Client Invoice send, record vs Agent Invoice send; show each adjustment counted once in the bill and once in the pay. (stories: 28, 14)
10. [agent] Grep the backend log for steps 1, 4 and 5 and show one audit line for each record and withdraw, with actor, Tenant, ids and signed amount, and no reason text. (stories: 23, 24)
11. [agent] In a browser as the Manager, open a current-month invoice and show no **Record adjustment** control; open the closed invoice, expand it, show the reason field's "Shown to the Client on the invoice" label and the pill unchanged, submit empty and show the inline refusal with input kept, record a $20.00 credit and show it listed Pending; withdraw it; record again. In a second tab send the receiving invoice as the Agent, then try to withdraw in the first tab and show the refresh message. (stories: 1, 5, 6, 7, 9, 10, 14)
12. [agent] As the Agent in a browser, show the credit on the current Client Invoice card with a Pending badge, its reason and no edit control. As the Tester, show the adjustment line on the sent invoice page. Show that an invoice with no adjustment renders no Adjustments block. (stories: 15, 16, 21, 29)
13. [agent] Run `mvn verify`, the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green, with existing suites unedited except as named in tickets' `## Regression`, and no golden moved. (stories: 29)
14. [agent] Do the record, withdraw and the Agent's view by keyboard alone with a visible focus ring at each stop, and repeat at the mobile breakpoint with every form and list usable in the viewport. (stories: 30)
15. [human] Take a real error from a closed month's Client Invoice and record the credit or charge you would really record, with the reason you would really write. Read the current month's invoice as the Agent and as the Client would see it, the PDF, and the Agent's Local Support Fees. Confirm the wording, the amounts and the pay move are what you want. (stories: 1, 2, 3, 7, 15, 17, 21, 22)
16. [human] Take a real Topup that was paid to the Agent but never billed, charge it to the Client, and confirm the Agent is not paid for it twice. (stories: 20)
17. [human] Read ADR 0006 and the notes on ADR 0004 and PRODUCT.md, and confirm they say what you settled: a closed month is corrected only by an adjustment on the current month's invoice; only adjustments are negative; an adjustment moves the Agent's pay by the same amount; nothing is carried over automatically. (stories: 3, 8, 24)

## Execution order

**Depends on: `month-closes-on-the-fifth`, merged.** Each slice is a vertical
path through migration, service, route, BFF and UI. No ticket is cut until
open question 1 is answered.

1. `manager-records-an-invoice-adjustment`. Labels: `backend`, `frontend`.
   The `invoice_adjustments` migration, record and withdraw routes with the
   closed-month gate, `adjustmentsRecorded`, the audit methods, ADR 0006, the
   Manager's form and list. No blocker within this feature.
2. `adjustment-shows-on-the-current-month-client-invoice`. Labels: `backend`,
   `frontend`. `adjustments` and totals on the receiving invoice, pending vs
   sent, Review Queue total, Tester view, PDF, the Agent's card block.
   Depends on 1.
3. `adjustment-moves-the-agents-pay`. Labels: `backend`. The payable amount,
   the `SENT` Agent Invoice move on record and withdraw, the lock order, the
   race tests. Depends on 1.
4. `charge-for-something-already-paid`. Labels: `backend`, `frontend`. Open
   question 1 as answered. Depends on 2 and 3.
5. `invoice-adjustment-e2e`. Labels: `frontend`. The e2e spec of Testing
   decisions item 6. Depends on 2 and 3.
