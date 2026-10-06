---
feature: invoice-adjustment
epic: invoice-correction-and-history
status: draft
date: 2026-10-06
---

<!-- sdlc:template spec 1 -->

# Invoice adjustment

## Problem

A Company Manager who finds an amount wrong on an invoice that has already
been sent or approved has no way to correct it. An approved Client Invoice is
final (ADR 0001, ADR 0005), an Agent Invoice only moves forward (ADR 0003),
and a Fee can never be edited, voided or back-dated (`FeeController` puts a
new Fee in the month it is logged). So the error stays on the record, and
settling it happens outside the app, from memory.

The human settled how the business works (2026-09-30, epic `## Reworked`):
when an invoice is wrong after sending or approval, **the Manager** corrects
it on the **following month's** invoice. A past month is never reopened.
Send-back stays for errors caught before approval; there is no
`correct-a-fee`.

There is a second, already-promised gap. Since `edit-client-invoice-lines`, the
Agent's pay follows each Client Invoice's billed lines until the Agent Invoice
of that month is approved (the human's answers of 2026-10-01 and 2026-10-02,
ADR 0004). An edit made after that approval, typically on a last-month
Client Invoice the Manager sent back, moves nothing: ADR 0004's table calls
the difference "a carry-over for `invoice-adjustment`". Today nothing records
that carry-over. It is described, and then lost.

A correction can lower an amount, which nothing in the app can express
today: a Fee must be greater than zero (`FeeCreateRequest`), and a Client
Invoice line's amount is checked `>= 0`.

## Journeys

Advances `docs/roadmap/invoice-correction-and-history.md`, the third of its
features, after `edit-client-invoice-lines` and `send-a-client-invoice-back`.

- **Send an invoice back for correction** (`wanted`, stays `wanted`). This
  feature delivers the epic's proof sentence "a Manager who finds an error
  after approval records an adjustment, a credit or a charge, that lands on
  the next month's invoice". The journey reaches `exists` only when
  `send-an-agent-invoice-back` lands too, so `docs/journeys.md` is not edited
  here.
- **Bill the Client for the month** (`exists`, stays `exists`, extended). A
  Client Invoice can carry adjustment lines below its base amount and Fees.
- **Get the Agent paid for the month** (`exists`, stays `exists`, extended).
  An Agent Invoice can carry adjustments as a fifth figure beside its four,
  and an edit made after the Agent Invoice's approval reaches a later month's
  pay as a carry-over.
- **Work through what is waiting** (`exists`, stays `exists`). Review Queue
  totals include the adjustments an invoice carries. Membership is unchanged.
- **Look back at finished invoices** (`wanted`): untouched. `invoice-history`
  will display the adjustments this feature adds.

## Goals / Non-goals

**Goals**

- From a Client Invoice that is `sent` or `approved`, a Manager records a
  **Client adjustment**: a credit (lowers the bill) or a charge (raises it), an
  amount in the Contract's currency, and a required reason.
- From an Agent Invoice that is `sent`, `approved` or `paid`, a Manager
  records an **Agent adjustment** the same way, in the Agent Invoice's
  currency: a credit lowers the Agent's pay, a charge raises it.
  *(Open question 1.)*
- An adjustment is **pending** until it **lands**: it lands on the next
  invoice of the same Contract (Client adjustment) or the same Agent (Agent
  adjustment) that is sent for the first time, for the month it was recorded
  in or later. Until then it shows on that invoice's draft as a pre-filled,
  read-only line. *(Open question 4.)*
- Once landed, an adjustment is part of that invoice's frozen figures, on
  every surface that shows them: the detail pages, the Review Queue total,
  and, for a Client adjustment, the Tester's view and the PDF.
- An edit to a Client Invoice line, made while that month's Agent Invoice is
  `approved` or `paid`, records a pending Agent adjustment of exactly the
  edit's difference, automatically. *(Open question 5.)*
- A Manager may withdraw an adjustment while it is pending, never after it
  has landed.
- The invoice an adjustment corrects lists it, pending, landed (and where) or
  withdrawn, so the Manager can see the correction was made.

**Non-goals.** Each of these is something a reasonable agent would otherwise
build.

- **No reopening a past month.** No adjustment ever lands on the invoice it
  corrects, or on any invoice of an earlier month than the one it was
  recorded in. An approved Client Invoice and an approved or paid Agent
  Invoice stay final (ADR 0001, ADR 0003, ADR 0005).
- **No editing, voiding or back-dating a Fee.** An adjustment is not a Fee,
  has no Request, and never changes a Fee row. There is no `correct-a-fee`.
- **No negative Fee and no negative Client Invoice line.** `FeeCreateRequest`'s
  `> 0` rule and the line's `amount >= 0` check stay. Only an adjustment can
  be negative.
- **No Agent-made adjustment.** Only a Manager records or withdraws one. The
  Agent's only path to one is the automatic carry-over of their own edit.
- **No editing an adjustment.** A wrong pending adjustment is withdrawn and
  recorded again. A landed one is corrected by another adjustment.
- **No choosing the receiving month.** The Manager does not pick where an
  adjustment lands (open question 4's recommendation).
- **No adjustment on a resend.** A sent-back invoice's resend takes no
  adjustment; only a first send lands them. A sent-back invoice keeps the
  adjustments it landed at its first send, as sent.
- **No splitting a credit across months.** A credit larger than the receiving
  invoice lands whole (open question 6).
- **No Client adjustment moving the Agent's pay, and no Agent adjustment
  moving the Client's bill** (open question 1's recommendation). When both
  are wrong, the Manager records one of each.
- **No Agent refusal, acknowledgement or dispute flow** (open question 2's
  recommendation).
- **No notification** of any kind. The pending line on the draft is the
  signal, as the sent-back list was for send-back.
- **No list of all pending adjustments across the Tenant.** They are seen on
  the invoice they correct and on the draft that will receive them.
  `invoice-history` may add one.
- **No change to the Manager's Agent Invoice override** (ADR 0003). It still
  covers only Salary and the new-advance line of a `sent` invoice.
- **No settling outside an invoice.** An adjustment for a Contract or an
  Agent that never sends another invoice stays pending, visible on the
  invoice it corrects.
- **No Agent Invoice send-back.** That is `send-an-agent-invoice-back`.
- **No dropping of the dead snapshot structures** (`snapshotBaseAmount`,
  `ClientInvoiceFeeSnapshot`). Still a later contract step.

## User stories

1. As a Company Manager, I want to record a credit on a sent or approved Client Invoice I find was overbilled, with a reason, so that the Client is refunded on next month's invoice without reopening this one.
2. As a Company Manager, I want to record a charge on a sent or approved Client Invoice I find was underbilled, with a reason, so that the Client pays what was missed on next month's invoice.
3. As a Company Manager, I want to record a credit or a charge on a sent, approved or paid Agent Invoice I find wrong, with a reason, so that the Agent's pay is put right on their next Agent Invoice.
4. As a Company Manager, I want to enter the amount as a positive figure and choose credit or charge, so that I never have to reason about signs.
5. As a Company Manager, I want to be refused, inline and with my input kept, an empty reason, a reason over 1000 characters, a zero, negative, blank or over-precise amount, so that I fix the input rather than start again.
6. As a Company Manager, I want no adjustment action on a draft invoice, so that an error caught before sending is fixed by the Agent or by a send-back instead.
7. As a Company Manager, I want the invoice I corrected to list every adjustment recorded from it, its amount, reason, whether it is pending, landed (on which invoice) or withdrawn, so that I can see the correction was made and where it went.
8. As a Company Manager, I want to withdraw a pending adjustment I recorded by mistake, so that a wrong correction never reaches an invoice.
9. As a Company Manager, I want a landed adjustment to have no withdraw action, so that a figure already sent never moves.
10. As a Company Manager, I want an adjustment to land on the Contract's (or the Agent's) next invoice to be sent, without choosing a month, so that it cannot be aimed at a month already closed.
11. As a Company Manager reviewing a sent invoice, I want its landed adjustments shown as their own lines, with the month they correct and the reason, and included in the total, so that I approve the real amount knowing why it differs.
12. As a Company Manager, I want the Review Queue total of an invoice to include its landed adjustments, so that the queue and the invoice agree.
13. As a Company Manager whose page is stale (the invoice was sent back, or the adjustment already landed or was withdrawn), I want a clear message telling me to refresh, so that I do not assume my action worked.
14. As an Agent, I want my Client Invoice draft to show each pending Client adjustment for its Contract as a read-only line, with the month it corrects and the Manager's reason, and its total to include it, so that I send what will actually be billed.
15. As an Agent, I want my Agent Invoice draft to show each pending Agent adjustment as a read-only line, with the month it corrects and the reason, and its total to include it, so that I see my pay as it will be.
16. As an Agent, I want an adjustment line to have no edit or reset control, so that it is clear the correction is the Manager's.
17. As an Agent, I want sending my invoice to land exactly the adjustments it showed, and those to stay on it as sent, so that what I sent is what is reviewed.
18. As an Agent, I want an adjustment recorded after I sent this month's invoice to wait for next month's, so that a sent invoice never changes under me.
19. As an Agent, I want a sent-back invoice to keep the adjustments it landed at its first send, and to take no new one on its resend, so that correcting it never pulls in an unrelated correction.
20. As an Agent who edits a Client Invoice line after that month's Agent Invoice was approved or paid, I want the difference recorded automatically as a pending Agent adjustment, so that my pay is corrected next month instead of the difference being lost.
21. As an Agent, I want that automatic carry-over to say which Client Invoice, month and line it came from, and by how much, so that I can check it.
22. As a Company Manager, I want an automatic carry-over to appear on the corrected Agent Invoice like any adjustment, marked as a carry-over, and to be withdrawable while pending, so that I keep control of what is paid.
23. As an Agent, I want an edit made while my Agent Invoice is a draft or sent to move my pay exactly as it does today, and to record no carry-over, so that nothing is counted twice.
24. As an Agent, I want a Client adjustment on my Contract's invoice to leave my Local Support Fees unchanged, so that a correction to the Client's bill does not silently change my pay.
25. As a Tester, I want a sent or approved Client Invoice to show its adjustment lines, each labelled with the month it corrects (and the reason, per open question 3), and its total to include them, so that I understand why this month's bill differs from the work done.
26. As a Tester, I want the PDF to show the same adjustment lines and total, so that the document and the page agree.
27. As a Tester, I want to see no pending adjustment and no Agent adjustment, so that I see only the Client's own statement once sent.
28. As the company, I want an invoice whose credit exceeds its other lines to show its true, negative total, so that no credit is lost or invented (open question 6).
29. As the company, I want every adjustment recorded, withdrawn and landed written to the audit log (who, which, amount, without the reason's text), so that every correction can be traced.
30. As the company, I want an adjustment to always name the invoice it corrects, so that an amount with no Request still traces back to a reviewed record.
31. As an Agent or a Tester, I want to be unable to record or withdraw an adjustment, so that it stays the Manager's power.
32. As a Manager of one Tenant, I want every adjustment route on another Tenant's invoice refused as if it did not exist, so that the Tenant boundary holds.
33. As an Agent, I want to see adjustments only on my own Contracts' and my own invoices, so that I never see another Agent's.
34. As the company, I want a first send and an adjustment recorded or withdrawn at the same moment to leave the adjustment either landed on that invoice or still pending, never both or neither, so that every adjustment lands exactly once.
35. As everyone already using the product, I want invoices with no adjustment to look, total and behave exactly as before, so that this change carries no release risk.
36. As a Manager or an Agent working by keyboard or on a phone, I want the adjustment form, the adjustment list and the adjustment lines usable without a mouse and at the mobile breakpoint, so that the action is available wherever I work.

## Solution

### Builds on the delivered siblings, unchanged

- **The line model and the one resolution** (`edit-client-invoice-lines`,
  ADR 0004): `ClientInvoiceService.lines(invoice)` stays the only place that
  decides an invoice's lines, and keeps resolving `POSTPAID_SIM`, `FEE` and
  `BASE_AMOUNT` lines exactly as today. Adjustments are **not** Client Invoice
  lines (see Decisions taken); they are resolved beside the lines, by one
  new operation, and the response, the total, the PDF and the queue sums add
  them.
- **The pay rule** (ADR 0004): `ContractAmountService.payableAmountForMonth`
  is unchanged and never reads a Client adjustment (open question 1). The
  lifecycle table gains its carry-over row's effect, below.
- **Send-back** (ADR 0005): a send-back leaves landed adjustments as they are;
  a resend lands none.
- **The row locks and their order**: Client Invoice, then Agent, then Agent
  Invoice. Every new write here takes the locks it needs in that order.

### The model: an adjustment

Two tables, one per invoice type, because the two have different owners and
different correcting/receiving invoices:

```
client_invoice_adjustments                 agent_invoice_adjustments
  id, tenant_id                              id, tenant_id
  contract_id         (receiver's owner)     agent_id            (receiver's owner)
  corrects_invoice_id → client_invoices      corrects_invoice_id → agent_invoices
  amount   numeric(12,2), <> 0, signed       amount   numeric(12,2), <> 0, signed
  currency (copied from the Contract)        currency (copied from the Agent Invoice)
  reason   varchar(1000), not blank          reason   varchar(1000), not blank
                                             origin   MANAGER | CARRY_OVER
                                             carry_over_line_id → client_invoice_lines (CARRY_OVER only)
  recorded_month date (first of month, UTC)  recorded_month
  recorded_at, recorded_by                   recorded_at, recorded_by
  landed_invoice_id → client_invoices null   landed_invoice_id → agent_invoices null
  landed_at null                             landed_at null
  withdrawn_at, withdrawn_by null            withdrawn_at, withdrawn_by null
  check: not (landed and withdrawn)          check: not (landed and withdrawn)
```

A credit is stored negative, a charge positive. An adjustment is:

```
PENDING   ──first send of a receiving invoice──▶ LANDED   (final)
   │
   └──withdraw (Manager)──▶ WITHDRAWN (final)
```

`PENDING` = neither landed nor withdrawn. No status column: the state is
derived from the two timestamps, as "sent back" is from `sentBackAt`.

**Which invoice receives it.** An invoice of month M (Client Invoice of the
adjustment's Contract, or Agent Invoice of its Agent) receives every pending
adjustment with `recorded_month <= M`, **at its first send only**:

- while that invoice is a draft never sent, its read shows those pending
  adjustments as read-only lines, and its total includes them;
- its first send, under the invoice's row lock, stamps `landed_invoice_id` and
  `landed_at` on every pending adjustment it showed or that is pending at that
  moment, in the same transaction;
- from `sent` onward the invoice's adjustments are exactly the rows stamped
  with its id. Nothing else ever joins it: a resend after a send-back lands
  none, and a sent-back draft shows its landed adjustments, read-only, and no
  pending one.

So an adjustment recorded from September's invoice in early October lands on
October's invoice when it is first sent. One recorded after October's was sent
waits for November's. It can never land on the month it corrects or an
earlier one, because the invoice it corrects is already past its first send,
and every earlier month's invoice either was too or can no longer be sent
(an invoice is first-sent only for the current month).

### What a Manager does

- **Record.** `POST /api/client-invoices/{id}/adjustments` and
  `POST /api/agent-invoices/{id}/adjustments`, Manager only, body
  `{ "kind": "CREDIT" | "CHARGE", "amount": "12.50", "reason": "…" }`.
  `amount` is positive, at most two decimals, within the invoice amount
  bounds already used for lines. `201` with the adjustment; `400` invalid
  body; `404` not in the caller's Tenant; `409` when the corrected invoice is
  a `DRAFT` (Client: `SENT` or `APPROVED` accepted; Agent: `SENT`, `APPROVED`
  or `PAID`); `403` for any other role. It sets `recorded_month` to the
  current billing month (UTC) and `currency` from the corrected invoice.
- **Withdraw.** `POST /api/{client|agent}-invoice-adjustments/{adjustmentId}/withdraw`,
  Manager only. `200`; `409` when it has landed or was already withdrawn
  (coded body `ADJUSTMENT_NOT_PENDING`, carried by a `Reason` enum as the other
  coded conflicts do). It re-reads the adjustment `FOR UPDATE`, so a withdraw
  and a first send that race each other leave it in exactly one final state.
- **See.** The corrected invoice's by-id response gains
  `adjustmentsRecorded`: every adjustment recorded from it, with `state`
  (`PENDING`, `LANDED`, `WITHDRAWN`), and for a landed one the receiving
  invoice's id and billing month.

### What an invoice carries

`ClientInvoiceResponse` and `AgentInvoiceResponse` each gain:

- `adjustments`: the adjustments this invoice carries (pending ones on a never-sent draft,
  landed ones from `sent` onward and on a sent-back draft), each
  `{ id, amount, correctsBillingMonth, reason, origin, pending }`;
- `adjustmentsTotal`;
- `totalAmount` now includes `adjustmentsTotal`.

For a Client Invoice, `baseAmount` and the Fee lines are unchanged, and the
total is lines plus adjustments. For an Agent Invoice the total is its four
figures plus `adjustmentsTotal`; the four snapshot columns and ADR 0003's
freeze are untouched, and the Manager's override still writes only Salary and
new advance.

- **Tester.** On a Client Invoice the Tester already sees (`SENT`, `APPROVED`),
  each adjustment shows its amount and "Adjustment to the {Month YYYY}
  invoice", with the reason per open question 3, and never `origin`. A Tester
  is never shown `adjustmentsRecorded`. Agent adjustments are never in any
  Tester-facing response.
- **PDF.** The Client Invoice PDF renders an "Adjustments" block after the
  Fees, with the same label as the Tester's, and the total including them.
  It renders only what the response carries.
- **Review Queue.** `ClientInvoiceQueueRow` and the Agent Invoice queue row
  add the landed adjustments to their totals.

### The carry-over, recorded automatically

ADR 0004's lifecycle table, with only its last row changed:

| Agent Invoice of the edited month | Effect of the edit |
|---|---|
| none yet, or `DRAFT` | Nothing written; the draft reads the new figure live. |
| `SENT` | Its Local Support Fees move by exactly the edit's difference, with an audit line. |
| `APPROVED`, `PAID` | Nothing moves on it. A pending Agent adjustment of exactly the difference is recorded, `origin = CARRY_OVER`, correcting that Agent Invoice, `carry_over_line_id` the edited line. |

It is written inside `ClientInvoiceService.editLine`'s transaction, which
already holds the Client Invoice lock and then the Agent Invoice lock, so no
new lock order appears. Its reason is generated, not typed:
"Carry-over: {Client} {Month YYYY} Client Invoice, {line description} edited
from {old} to {new}". `recorded_by` is the editing Agent. An edit whose
difference is zero records nothing. Each edit records its own adjustment, so
an edit and its reset leave two that net to zero, both visible (see
Decisions taken).

Because a Client adjustment does not move pay (open question 1), the
carry-over is the only path by which an invoice correction reaches pay
automatically. A Manager correcting an error that hurt both the Client's bill
and the Agent's pay records one of each.

### ADR

**A new ADR 0006, "Corrections after sending settle forward as adjustments"**
(numbered after 0005; if numbers shift at merge, the next free one). It
records:

- an adjustment is the only signed amount in the billing model; Fees and
  Client Invoice lines stay non-negative;
- an adjustment traces to the invoice it corrects, not to a Request. This
  narrows PRODUCT.md's principle "every billable amount traces back to a
  logged Request" for adjustments only, and the ADR says so;
- it lands at a receiving invoice's first send and is then frozen with it,
  so ADR 0001's and ADR 0003's reason (a reviewed figure never moves) holds;
- ADR 0004's carry-over row is now realised as an automatic pending Agent
  adjustment;
- a Client adjustment does not enter the payable amount.

ADR 0001, ADR 0003 and ADR 0004 each get a dated "Amended by ADR 0006" note.
PRODUCT.md's principle 1 gets the same narrowing in the ADR's commit — a
harness change the implementer declares and the merger applies.

### Schema and contract

- **Two additive migrations**, one per slice: `client_invoice_adjustments` at
  the next free Flyway version at merge (V60 today), and
  `agent_invoice_adjustments` at the one after. No existing row rewritten, no
  existing check changed.
- `ClientInvoiceResponse` and `AgentInvoiceResponse` gain `adjustments`,
  `adjustmentsTotal` and (Manager by-id only) `adjustmentsRecorded`;
  `totalAmount` includes the adjustments. For an invoice with none, every
  existing field keeps its value.
- New routes as above; `SecurityConfig` gets Manager-only matchers for the
  record and withdraw routes, before the broader invoice matchers.
- Audit: three new `AuditLog` methods, `invoiceAdjustmentRecorded`,
  `invoiceAdjustmentWithdrawn` and `invoiceAdjustmentsLanded` (type, ids,
  signed amount, origin, actor, Tenant), never the reason's text.

### Frontend

- **Manager, Client Invoice and Agent Invoice detail pages.** A
  **Record adjustment** secondary control, shown while the invoice is past
  `draft`, expanding an inline form in `SendBackClientInvoiceControl`'s shape:
  a Credit / Charge segmented choice with a one-line hint each ("Lowers the
  next invoice" / "Raises the next invoice"), an amount field with the
  currency, a required reason textarea (labelled per open question 3), and
  **Record** and **Back**. Below the invoice's own figures, an
  "Adjustments recorded from this invoice" list: amount (signed, credit shown
  with a minus), reason, state badge (Pending, Landed on {Month} with a link,
  Withdrawn), and **Withdraw** on pending rows. The control takes its endpoint
  URL, so both pages mount the same control.
- **Every invoice view** (the Agent's Client Invoice card, the Agent's Agent
  Invoice, the Manager's detail pages, the Tester's invoice): an
  "Adjustments" block after the Fees (Client) or after the four figures
  (Agent), one row per adjustment with "Adjustment to the {Month} invoice",
  the reason where shown, the signed amount, a **Pending** badge on a draft's
  pending ones, and **Carry-over** on a carry-over's row (not for a Tester).
  The block is not rendered when the invoice has none.
- **Line editor.** Adjustment rows never get the line editor's edit or
  reset controls.
- **BFF.** Pass-through proxies for the new routes with `backendFetch`.

## Design direction

**Operate**, built from shipped patterns; `DESIGN.md` does not change.

- The record form copies the send-back form: 8px controls, `danger`-toned
  inline error, the page's single pill (Approve, or Mark paid) unchanged
  (Pill-Is-Primary Rule). The Credit / Charge choice uses the existing
  segmented control.
- Amounts use the tabular figures the invoice pages already use. A credit is
  shown with a leading minus in the body colour, not in red: it is not an
  error.
- The **Pending** badge is the `warning` tone ("awaiting" in DESIGN.md);
  **Landed** and **Carry-over** are `neutral`; **Withdrawn** is `neutral`
  with the row's text muted.

Visual goldens: **no new goldens**. Every adjustments block renders nothing
when empty, so no existing golden should move; any golden that moves is a
finding.

## Constraints

- Lands after `send-a-client-invoice-back` (merged). No dependency on
  `send-an-agent-invoice-back`; when that lands, its resend follows this
  spec's "first send only" rule.
- Additive migrations only, at the next free version at merge. Flyway runs
  with `outOfOrder=false`.
- An adjustment's amount is non-zero, at most two decimals, signed only in
  storage; the API takes a kind and a positive amount.
- An adjustment never lands on the invoice it corrects nor on any month
  before its `recorded_month`; only a first send lands one; a landed or
  withdrawn adjustment never changes again.
- From `sent` onward an invoice's adjustments are fixed (ADR 0001, ADR 0003).
- `payableAmountForMonth` never reads a Client adjustment (open question 1).
- Locks: first send takes the invoice's row lock and stamps pending
  adjustments in its transaction; withdraw locks the adjustment row; the
  carry-over is written inside `editLine`'s existing lock order. A record
  needs no invoice lock: a record committed after a send's stamp stays
  pending for the next month.
- The reason never appears in a log line or an audit line; to a Tester only
  per open question 3; an Agent adjustment never reaches a Tester.
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
   Agent Invoice's own response), `ClientInvoiceByIdApiTest`. New
   `ClientInvoiceAdjustmentApiTest` and `AgentInvoiceAdjustmentApiTest`
   cover: record on each allowed status and `409` on a draft; `400` cases;
   the pending line on the next draft and its total; landing at first send;
   a record after the send waiting for the next month (a past-month fixture
   in `DemoDataLoader`'s shape plays the "next month" without a clock); no
   landing on a resend after a send-back, and the landed ones kept on the
   sent-back draft; withdraw while pending, `409 ADJUSTMENT_NOT_PENDING`
   after; Tester view and `403`/`404` matrices; Review Queue totals; a Client
   adjustment leaving Local Support Fees unchanged. A new
   `ClientInvoiceLineEditCarryOverApiTest` (or new cases in
   `LocalSupportFeesFollowClientInvoiceApiTest`) covers the carry-over row:
   `APPROVED` and `PAID` record one of exactly the difference, `DRAFT` and
   `SENT` record none, zero difference records none.
2. **Races at the same seam, committing** (prior art
   `ClientInvoiceSendBackRaceTest`): first send vs withdraw, and first send
   vs record; each adjustment ends landed exactly once or pending/withdrawn.
3. **Migration tests** under the existing migration test package: both
   tables apply on a database at the previous version with data, and their
   checks refuse a zero amount and a landed-and-withdrawn row.
4. **PDF**: an existing PDF test seam is extended to assert the adjustments
   block and total by extracted text, as the line-edit feature's PDF checks
   do.
5. **Frontend component tests** (Vitest + Testing Library). Prior art:
   `send-back-client-invoice-control.test.tsx`,
   `client-invoice-detail-view.test.tsx`,
   `edit-client-invoice-line-control.test.tsx`. Cover the record form
   (kind, validation, `409`, input kept), the recorded list and withdraw,
   the adjustments block's empty/pending/landed/carry-over states, and no
   editor control on an adjustment row.
6. **One e2e spec, `invoice-adjustment.spec.ts`.** e2e state does not roll
   back and cannot reach a later month, so it plays only what the current
   month allows, on a Contract it creates: the Agent sends; the Manager
   approves, records a $20.00 credit from that invoice, sees it Pending in
   the list, and the sent invoice is unchanged; the Manager withdraws it.
   Landing on a following month's draft is proven at the API seam with a
   past-month fixture (item 1) and by walkthrough steps 3–5 on demo data.
7. **Existing suites pass unedited**: every invoice API test, the three
   invoice e2e specs and the component tests, since an invoice with no
   adjustment is unchanged. Any forced edit is named in its ticket's
   `## Regression`.
8. **No unit tests** of the landing rule beyond the HTTP seam (Backend
   rule 6).

## Decisions taken

### Settled by the human

- **Corrections after sending or approval carry forward to the next month,
  made by the Manager; a past month is never reopened; no `correct-a-fee`**
  (2026-09-30).
- **Every Client Invoice line is editable by the Agent while draft** (2026-10-01).
  Adjustment rows are not lines and are not editable (open question 2).
- **An edit before the Agent Invoice's approval reaches that month's pay;
  after approval it is a carry-over** (2026-10-01/02). Built here as the
  carry-over row.
- **Send-back is for errors caught before approval** (2026-09-30). The record
  action is nonetheless offered on a `sent` invoice too, as the epic's line
  says "after sending or approval".

### Taken alone

- **Adjustments are their own rows, not a new Client Invoice line kind.** A
  line kind would need the `amount >= 0` check relaxed, would flow into the
  payable amount through the one resolution, and would be editable by the
  Agent's line rule; a separate table keeps every existing line rule true.
- **Two tables, one per invoice type.** Owner, corrected and receiving
  invoices differ by type; one table would need nullable pairs and checks.
- **No fixed receiving month; an adjustment lands at the next first send of
  a month at or after the one it was recorded in.** A fixed target can point
  at an invoice sent a moment later (an Agent's send is not serialized with a
  Manager's record), leaving it stranded; stamping at send cannot.
- **Only a first send lands adjustments.** A resend after a send-back would
  otherwise add a line the Manager's reviewed figures never had, and break
  ADR 0005's "lines as sent".
- **No status column; state derived from `landed_at` / `withdrawn_at`.** Same
  shape as "sent back"; one check forbids both.
- **The API takes `CREDIT`/`CHARGE` and a positive amount; storage is signed.**
  The Manager never types a sign; sums stay plain.
- **The record action is offered on `sent` as well as later statuses.** The
  epic says "after sending or approval"; send-back remains the better tool
  for a `sent` Client Invoice and the form does not hide that.
- **A Manager may withdraw a pending adjustment.** Without it a typo needs a
  counter-adjustment on a real invoice; withdrawing touches no sent figure.
- **No editing an adjustment.** Withdraw and record again covers it, with one
  audit line each.
- **The carry-over records one adjustment per edit, and nothing for a zero
  difference.** Each edit already writes its own audit line; netting edits
  in place would make a pending row change under the Manager. Cheap to
  change to per-line netting.
- **The carry-over's reason is generated and names the Client, month, line
  and old/new amounts.** The Agent and Manager need to check it without the
  audit log.
- **`recorded_by` on a carry-over is the editing Agent.** That is who caused
  it; `origin` says it was automatic.
- **The corrected invoice lists its adjustments (`adjustmentsRecorded`); there
  is no Tenant-wide list.** The Manager records from that page and checks
  there; a list is `invoice-history`'s to add.
- **A record takes no invoice lock.** Landing is decided by the send under its
  own lock; a record committed after it simply waits a month. Withdraw locks
  the adjustment row against the send's stamp.
- **A new ADR 0006 with notes on 0001, 0003, 0004, and the narrowing of
  PRODUCT.md principle 1.** Signed amounts and a non-Request-traced billed
  amount are surprising without context and costly to reverse once invoices
  carry them.
- **Three new audit methods, without reason text.** Same rule as send-back:
  no audit line carries free text.
- **Credits shown with a minus in the body colour.** A credit is not an error.
- **Testing: the existing HTTP, component, PDF and e2e seams, plus committing
  race tests.** Prior art named above; the month boundary is proven with a
  past-month fixture, not a clock.

## Open questions

1. **Can an adjustment correct the Agent's pay as well as the Client's bill,
   and does correcting one move the other?** Example: September's Client
   Invoice billed a SIM at $50 instead of $30 and was approved; the Agent was
   paid on $50. Recording a $20 credit for the Client — should the Agent's
   October pay also drop $20 automatically? But if instead the Agent forgot to
   bill a $15 Topup (already paid to them, since logged Fees always count in
   pay), a $15 charge to the Client must not pay the Agent $15 again.
   *Recommendation:* both kinds exist, and each moves only its own side: a
   Client adjustment changes only what the Client is billed, an Agent
   adjustment only what the Agent is paid; when both are wrong the Manager
   records one of each. *Reason:* the two examples need opposite automatic
   behaviour, so any automatic link is wrong half the time.
2. **Does the Agent see an adjustment, and can they refuse or change it?**
   Example: the Manager records a $20 credit on the Client's bill; the Agent's
   October draft shows "Adjustment to the September invoice −$20.00" with the
   Manager's reason. *Recommendation:* the Agent sees every adjustment on
   their own Client Invoices and Agent Invoice, with the reason, and cannot
   edit, remove or refuse it; disagreements are settled with the Manager
   outside the app, and the Manager withdraws it if needed. *Reason:* the
   Manager owns corrections (the human's 2026-09-30 answer) and approves
   money; a refusal flow is a feature of its own.
3. **Does the Client's Tester (and the PDF) see the adjustment's reason, or
   only an "Adjustment to the September 2026 invoice −$20.00" line?**
   *Recommendation:* show the reason too, and label the Manager's reason
   field "Shown to the Client on the invoice". *Reason:* an unexplained credit
   or charge on a statement invites questions, and unlike a send-back note it
   is part of what the Client is billed. (If you prefer it hidden, the line
   shows only the month it corrects.)
4. **Where does an adjustment land: always the next invoice still being
   prepared, or a month the Manager picks?** Example: on 3 October the Manager
   finds September's invoice overbilled $20; the credit goes on October's
   invoice. Had October's already been sent, it would go on November's.
   *Recommendation:* always automatic, the next invoice sent for that Contract
   (or Agent). *Reason:* a picked month can be one already sent or closed, and
   "next month" is how the business already said it works.
5. **When an Agent edits a Client Invoice line after their Agent Invoice for
   that month was approved, should the difference be recorded automatically as
   a pending adjustment to their pay, or left for the Manager to record?**
   Example: September's Agent Invoice is approved; the Manager sends
   September's Client Invoice back; the Agent corrects a SIM line from $30 to
   $45. *Recommendation:* automatic — a +$15 Agent adjustment, marked
   "Carry-over", appears pending on the Agent's October Agent Invoice, and the
   Manager can withdraw it before it is sent. *Reason:* "otherwise it's a
   carry-over" (2026-10-01) means it happens; by hand it is easily forgotten,
   and the Manager still reviews it on the October invoice.
6. **If a credit is larger than the invoice it lands on, what does the
   invoice show?** Example: a $300 credit lands on an October Client Invoice
   whose lines total $250. *Recommendation:* the invoice shows a total of
   −$50.00 (the Client is owed $50), and nothing carries on to November.
   *Reason:* rare, honest, and splitting a credit across months is a rule
   that can be added later without touching stored data.

## Acceptance walkthrough

1. [agent] As the Manager, on an approved Client Invoice of last month (demo data) for Contract C, `POST …/adjustments` a `CREDIT` of $20.00 with a reason; show `201`, amount `-20.00`, the Contract's currency, and the invoice's `adjustmentsRecorded` listing it `PENDING`. Record a `CHARGE` of $5.00 on a `SENT` invoice and show `201`. (stories: 1, 2, 4, 7)
2. [agent] Send a zero, a negative, a three-decimal and a blank amount, an empty and a 1001-character reason, and show `400` each; record on a draft and show `409`; with the Agent's and a Tester's token show `403`; on `OtherTenantFixture`'s invoice show `404`. (stories: 5, 6, 31, 32)
3. [agent] As C's Agent, read the current month's Client Invoice draft and show the $20.00 credit and the $5.00 charge as `adjustments` with `pending: true`, `correctsBillingMonth`, the reason, and `totalAmount` equal to lines − 20.00 + 5.00; try to edit an adjustment through `PUT …/lines` and show it refused. Show the Agent's Agent Invoice Local Support Fees unchanged by them. (stories: 14, 16, 24)
4. [agent] Withdraw the $5.00 charge and show it `WITHDRAWN` and gone from the draft. As the Agent, send the draft; show the credit landed (`pending: false`) in the sent response, `adjustmentsRecorded` on last month's invoice showing `LANDED` on this invoice, and the Review Queue total for it including −20.00. Withdraw the landed credit and show `409 ADJUSTMENT_NOT_PENDING`. (stories: 8, 9, 11, 12, 17)
5. [agent] Record another credit from last month's invoice after the send; show it pending, absent from the sent invoice, and shown on a next-month draft (fixture) instead. Send the current invoice back, resend it, and show the first credit still on it and the new one still pending. (stories: 10, 18, 19)
6. [agent] As a Tester of C's Client, read the sent invoice and show the adjustment line labelled with the corrected month (and reason per open question 3), no `origin` and no `adjustmentsRecorded`; download the PDF and show the Adjustments block and the total. Show no pending adjustment ever appears to the Tester. (stories: 25, 26, 27)
7. [agent] As the Manager, on an approved Agent Invoice record a $40.00 charge; as that Agent, show it pending on their current Agent Invoice draft with `totalAmount` up $40.00 and the four figures unchanged; send it and show it landed and in the Review Queue total. As another Agent, show it is not visible. (stories: 3, 15, 17, 33)
8. [agent] With the Agent's September Agent Invoice `APPROVED`, have the Manager send September's Client Invoice back; as the Agent edit a SIM line from $30.00 to $45.00 by id; show a pending Agent adjustment of +$15.00, origin `CARRY_OVER`, its generated reason naming the Client, month, line and both amounts, correcting the September Agent Invoice, and the September Agent Invoice unchanged. Reset the line and show a second, −$15.00, carry-over. Repeat with the Agent Invoice `SENT` and with it `DRAFT`, and show no carry-over recorded and pay moved as before. (stories: 20, 21, 22, 23)
9. [agent] Run the committing race tests: first send vs withdraw, first send vs record; show every adjustment landed exactly once or still pending/withdrawn. (stories: 34, 13)
10. [agent] Grep the backend log for steps 1, 4 and 8 and show one audit line for each record, withdraw and landing, with actor, Tenant, ids and signed amount, and no reason text. (stories: 29, 30)
11. [agent] Land a $300.00 credit on a fixture invoice whose lines total $250.00 and show `totalAmount` −50.00 on the response, the Review Queue and the PDF (per open question 6). (stories: 28)
12. [agent] In a browser as the Manager, open an approved Client Invoice, expand **Record adjustment**, show Approve/pill unchanged, submit empty and show the inline refusal with input kept, record a $20.00 credit and show it listed as Pending; withdraw it; record again. In a second tab let the invoice's adjustment land (send as the Agent), then try to withdraw in the first tab and show the refresh message. Repeat the record on an Agent Invoice page. (stories: 1, 3, 5, 7, 8, 13)
13. [agent] As the Agent in a browser, show the pending credit on the Client Invoice card with a Pending badge and no edit control, and on the Agent Invoice page a pending carry-over row marked Carry-over. As the Tester, show the landed adjustment line on the invoice page. Show that an invoice with no adjustment renders no Adjustments block. (stories: 14, 15, 16, 21, 25, 35)
14. [agent] Run `mvn verify`, the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green, with existing suites unedited except as named in tickets' `## Regression`, and no golden moved. (stories: 35)
15. [agent] Do the record, withdraw and the Agent's view by keyboard alone with a visible focus ring at each stop, and repeat at the mobile breakpoint with every form and list usable in the viewport. (stories: 36)
16. [human] Take a real error from a past month's approved Client Invoice and record the credit or charge you would really record, with the reason you would really write. Read next month's invoice as the Agent and as the Client would see it, and the PDF. Confirm the wording and amounts are what you want the Client to receive. (stories: 1, 2, 14, 25, 26)
17. [human] Take a real case where the Agent's approved pay was wrong, record an Agent adjustment, and read the Agent's next Agent Invoice. Then send back a last-month Client Invoice whose Agent Invoice is approved, correct a line as the Agent, and confirm the automatic carry-over is what you expect on next month's pay. (stories: 3, 15, 20, 21, 22)
18. [human] Read ADR 0006 and the notes on ADR 0001, 0003, 0004 and PRODUCT.md, and confirm they say what you settled: corrections settle forward on the next invoice sent, never on a past month; only adjustments are negative; a Client adjustment does not move pay (if so answered); an edit after approval becomes a carry-over adjustment. (stories: 10, 24, 30)

## Execution order

**Depends on: `send-a-client-invoice-back`, merged.** Each slice is a
vertical path through migration, service, route, BFF and UI.

1. `manager-records-a-client-invoice-adjustment`. Labels: `backend`,
   `frontend`. The `client_invoice_adjustments` migration (next free version
   at merge, V60 today), record and withdraw routes, `adjustmentsRecorded`,
   the audit methods, ADR 0006, the Manager's form and list on the Client
   Invoice page. No blocker.
2. `client-invoice-adjustment-lands-on-the-next-client-invoice`. Labels:
   `backend`, `frontend`. Pending lines on a never-sent draft, landing at
   first send (none at resend), response totals, Review Queue total, Tester
   view, PDF, the Agent's card block, the send-vs-withdraw and send-vs-record
   race tests. Depends on 1.
3. `manager-records-an-agent-invoice-adjustment`. Labels: `backend`,
   `frontend`. The `agent_invoice_adjustments` migration (the version after
   1's), record/withdraw on the Agent Invoice, landing at the Agent Invoice's
   first send, totals and queue, the Manager and Agent views. Depends on 1
   (shared control and audit methods).
4. `client-invoice-edit-after-approval-carries-over`. Labels: `backend`,
   `frontend`. The carry-over row in `editLine`, its generated reason, the
   Carry-over marker. Depends on 3.
5. `invoice-adjustment-e2e`. Labels: `frontend`. The e2e spec of Testing
   decisions item 6 across 2–4. Depends on 2 and 4.
