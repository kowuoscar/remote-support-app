---
feature: send-a-client-invoice-back
epic: invoice-correction-and-history
status: draft
date: 2026-10-02
---

<!-- sdlc:template spec 1 -->

# Send a Client Invoice back

## Problem

A Company Manager who opens a sent Client Invoice from the Review Queue can do
only two things with it: approve it, or leave it. If something is wrong or
missing, such as a Postpaid SIM line that does not match what the carrier
actually billed, a Fee line at the wrong amount, or a missing Carrier Invoice
File, there is no way to hand it back to the Agent and say so. The wrong
invoice gets approved and becomes the final record for the month, or it sits
in the Review Queue and nobody is told why.

Send-back is for errors caught **before approval**. An amount found wrong
after approval is corrected by the Manager on the following month's invoice
(`invoice-adjustment`); an approved invoice is never reopened.

The Client Invoice lifecycle only moves forward today
(`ClientInvoiceStatus.canTransitionTo`: `DRAFT → SENT → APPROVED`). ADR 0001
freezes the numbers at send so that a figure a Manager has reviewed can never
move quietly. `manager-invoice-review-queue` left "send back to Agent" out of
scope on purpose (its Non-goals).

The human settled on 2026-10-01 that a Client Invoice is a base amount plus
Fees, and that **every line is editable by the Agent while the invoice is a
draft**: the computation only pre-fills the lines, because real usage, postpaid
included, varies month to month. `edit-client-invoice-lines`
(`docs/features/edit-client-invoice-lines/spec.md`) builds that editing and
lands first. It gives every sent invoice its own stored lines, so a sent-back
invoice is a draft again carrying exactly the lines it was sent with, and
**nothing is recomputed**. The Agent edits what is wrong and resends.

There is a second gap. An Agent reaches a Client Invoice only as "this
Contract's invoice for the current month". A Manager usually reviews last
month's invoice this month. So an invoice the Manager sent back would have no
page where its Agent could find it.

## Journeys

Advances `docs/roadmap/invoice-correction-and-history.md`, the second of its
features in order, after `edit-client-invoice-lines`.

- **Send an invoice back for correction** (`wanted`, stays `wanted`). This
  feature delivers the Client Invoice half of the epic's closing proof: a
  Manager sends a Client Invoice back with a reason; the Agent sees the reason
  and the invoice as a draft again, corrects it (edits lines, attaches a
  file), resends it with a fresh snapshot, and the Manager approves it. The
  proof's "adds a late Fee of that month" depends on open question 1. The
  journey reaches `exists` only when `send-an-agent-invoice-back` and
  `invoice-adjustment` land too, so `docs/journeys.md` is not edited here.
- **Get the Agent paid for the month** (`exists`, stays `exists`). A
  send-back and a resend move nothing on the Agent Invoice. An edit the Agent
  makes on a sent-back invoice reaches their pay by the rule
  `edit-client-invoice-lines` sets, unchanged here.
- **Work through what is waiting** (`exists`, stays `exists`). A sent-back
  invoice leaves the Review Queue, and a resent one comes back. The queue
  itself does not change.
- **Look back at finished invoices** (`wanted`): untouched. `invoice-history`
  will display the fields this feature adds.

## Goals / Non-goals

**Goals**

- A Manager sends a `sent` Client Invoice of any billing month back to its
  Agent from its detail page. The Manager must give a reason.
- The invoice goes back to `draft`. It leaves the Review Queue and the
  Dashboard's Pending approvals card. Its lines are exactly the stored lines
  it was sent with. Nothing is recomputed, whatever its billing month.
- The Agent sees every Client Invoice sent back to them, from any billing
  month, together with the reason and when it was sent back. The Agent opens
  such an invoice by its own identity, edits its lines with the editor
  `edit-client-invoice-lines` provides, attaches Carrier Invoice Files to it
  and resends it.
- A resend freezes the lines the invoice shows at that moment, edited or not.
  The invoice goes back into the Review Queue, and the Manager approves it or
  sends it back again.
- A new ADR, 0005, adds the one backward edge, `SENT → DRAFT`, says why the
  freeze still protects every figure a Manager is looking at, and states that
  a send-back recomputes nothing.

**Non-goals.** Each of these is something a reasonable agent would otherwise
build.

- **No line editing built here.** The line model, `editLine`, its validation,
  its display, its audit and its effect on the Agent's pay are all
  `edit-client-invoice-lines`'. This feature only returns an invoice to the
  state in which that editor applies, exposes `editLine` on a by-id route, and
  hosts the editor on the Agent's by-id page.
- **No recomputing and no clearing on send-back.** A send-back writes no line
  row, never clears `linesStored`, never refreshes a line from the Fleet or a
  Fee, for the current month or a past one, and offers no "recompute" or
  "reset to computed" action beyond the per-line Reset the editor already has.
- **No change to the Local Support Fees rule.** How an edit reaches the
  Agent's pay, including the two timing questions still open on it, is
  `edit-client-invoice-lines`'. A send-back and a resend themselves move no pay.
- **No Agent Invoice send-back.** `send-an-agent-invoice-back` is its own
  feature. Whether an *approved* Agent Invoice may be sent back is still
  undecided. ADR 0003 is not edited here. The pieces this feature builds so
  that the sibling can reuse them are named under `## Decisions taken`.
- **No sending back an approved Client Invoice.** `approved` stays final and
  immutable (ADR 0001). Only `sent → draft` is added.
- **No editing, voiding or back-dating a Fee itself.** Editing a Client
  Invoice's Fee line changes what the invoice bills, never the Fee row, its
  amount or its `billingMonth`. A Fee stays immutable.
- **No adjustment, credit or negative amount.** A wrong amount found after
  approval is `invoice-adjustment`.
- **No diff between the sent and the resent numbers.** The Manager reads the
  resent invoice as it stands, with each edited line's "Edited · computed"
  marker from `edit-client-invoice-lines`, beside the reason they gave.
- **No removing a Carrier Invoice File.** No such action exists anywhere today,
  and this feature does not add one.
- **No notification** of any kind: no email, no badge in the nav, no count on
  the Agent's dashboard. The sent-back invoice appearing on the Agent's Client
  Invoices page is the whole signal. The epic's `## Later` keeps other channels.
- **No Manager list of sent-back invoices.** After a send-back, the Manager can
  still reach the invoice by its detail-page URL and, for the current month,
  from the Contract page's summary. `invoice-history` covers finished invoices
  only.
- **No Agent-side "withdraw" or "un-send".** Only a Manager moves an invoice
  backward.
- **No history of past send-backs.** The invoice keeps the most recent reason
  and time only. Each send-back writes an audit line.
- **No change to the Tester's view,** beyond what already follows from the
  existing rule that a Tester never sees a draft.
- **No removal of the current-month send, file-attach and line-edit routes,**
  even though the Agent's UI stops calling them (see `## Decisions taken`).
- **No change to how the Review Queue selects, orders or totals invoices.**

## User stories

1. As a Company Manager, I want to send a sent Client Invoice back to its Agent from the invoice's detail page, so that a wrong or incomplete invoice is corrected instead of approved or left waiting.
2. As a Company Manager, I want to be required to give a reason when I send an invoice back, so that the Agent knows what is wrong and what to change.
3. As a Company Manager who submits an empty reason, I want to be told inline and to keep the form open, so that I fix it rather than start again.
4. As a Company Manager, I want the detail page to show the invoice as a draft right after I send it back, with my reason, the numbers it was sent with and no actions left, so that I can see the send-back took effect.
5. As a Company Manager, I want a sent-back invoice to leave the Review Queue and the Dashboard's Pending approvals card, so that the queue shows only what is waiting on me.
6. As a Company Manager, I want to send back a Client Invoice of any billing month, so that last month's invoice can be corrected before approval just like this month's.
7. As a Company Manager, I want no send-back action on a draft or an approved invoice, so that an approved invoice stays the final record.
8. As a Company Manager whose page is stale (another Manager already approved or sent it back), I want a clear message telling me to refresh, so that I do not assume my action worked.
9. As a Company Manager, I want a resent invoice to come back into the Review Queue, waiting since it was resent, so that I review the corrected version.
10. As a Company Manager reviewing a resent invoice, I want to see that it was sent back before, when, and the reason I gave, so that I can check the correction against my request.
11. As a Company Manager, I want to approve a resent invoice exactly as I approve any sent one, so that a send-back ends in the normal final record.
12. As a Company Manager, I want to send the same invoice back again if it is still wrong, so that there is no limit on getting it right.
13. As an Agent, I want every Client Invoice sent back to me, from any Contract and any billing month, listed on my Client Invoices page, so that I never miss one.
14. As an Agent, I want each sent-back invoice to show the Manager's reason and when it was sent back, so that I know what is wrong and what to change.
15. As an Agent, I want to open a sent-back invoice of a past billing month on its own page, so that I can work on it even though it is not this month's.
16. As an Agent, I want a sent-back invoice to come back with exactly the lines it was sent with, each Postpaid SIM line (or an older invoice's single base amount line) and each Fee line, whatever its billing month, so that I start from what the Manager reviewed.
17. As an Agent, I want to edit every line of a sent-back invoice on its page, so that I correct what the Manager pointed out.
18. As the company, I want a sent-back invoice to show only the lines it was sent with, with no line added for a Fee logged or a Postpaid SIM added after the send, so that nothing on it refreshes (pending open question 1).
19. As an Agent, I want to attach more Carrier Invoice Files to a sent-back invoice, so that a missing carrier bill can be supplied.
20. As an Agent, I want to resend a sent-back invoice, freezing what I now see, so that the Manager reviews the corrected invoice.
21. As an Agent, I want the send confirmation to tell me the truth, which is that only a Manager can send an invoice back, instead of "this can't be undone", so that I am not misled.
22. As an Agent, I want a resent invoice to leave my sent-back list and show as sent, so that my list shows only what still needs me.
23. As an Agent, I want my current month's invoice card to show the reason when that invoice was sent back, so that I see it where I build the invoice.
24. As an Agent, I want to reach only my own Contracts' invoices by id, so that I cannot read, edit or send another Agent's invoice.
25. As the company, I want a past month's draft that was never sent to stay unsendable and uneditable by id, so that a month is never first billed late from figures pre-filled from today's Fleet.
26. As a Tester, I want a sent-back invoice to disappear from my Client's view until it is resent, and never to see the Manager's reason, so that I see only statements the Agent stands behind.
27. As a Tester, I want the resent invoice's new numbers and PDF once it is sent again, so that what I see matches what the Manager reviews.
28. As the company, I want a resend to freeze exactly the lines the invoice shows at that moment, none duplicated and none left over, with no failure, so that the frozen figures are the ones the Agent saw.
29. As the company, I want a send-back and an approval that race each other to leave the invoice in one consistent state, never approved and editable at once, so that the final record is never changed.
30. As the company, I want every send-back recorded as an audit line (who, which invoice, from sent to draft), without the reason's text, so that the event can be traced.
31. As an Agent or a Tester, I want to be unable to send an invoice back, so that it stays the Manager's power.
32. As a Manager of one Tenant, I want every send-back or by-id read of another Tenant's invoice refused as if it did not exist, so that the Tenant boundary holds.
33. As an Agent, I want a send-back and a resend themselves to leave my Agent Invoice unchanged, and an edit I make on a sent-back invoice to reach my Agent Invoice exactly as any draft edit does, so that my pay follows what I bill until approval and never moves for paperwork alone.
34. As everyone already using the product, I want sending, approving, editing, the Review Queue, the Tester's view and the PDF to behave exactly as before for invoices never sent back, so that this change carries no release risk.
35. As a Manager or an Agent working by keyboard or on a phone, I want the send-back form, the sent-back list and the by-id page usable without a mouse and at the mobile breakpoint, so that the action is available wherever I work.
36. As the company, I want nothing on a sent-back invoice recomputed, not by a change to the Fleet, nor by its month ending, nor by the resend, so that every number on it is one the Agent sent or typed.

## Solution

### Builds on `edit-client-invoice-lines`

That spec defines the model this feature uses as is (its `## Solution`, "The
model: an invoice holds its own lines"):

- a `client_invoice_lines` table, one `ClientInvoiceLine` per Postpaid SIM
  (`POSTPAID_SIM`) and per Fee (`FEE`), plus a single `BASE_AMOUNT` line on
  invoices sent before that change. Each line carries a `computedAmount` and a
  billed `amount`; a line is edited when the two differ;
- a `linesStored` flag on the Client Invoice, **set at the first send and
  never cleared**. With it set, the invoice's lines are its stored rows
  exactly, and nothing is computed;
- a send that stores every line and sets `linesStored`, and that writes rows
  only for an invoice whose `linesStored` is false;
- the send moved into `ClientInvoiceService` as one transaction under a row
  lock on the Client Invoice (its ticket 2), and `editLine(invoice, kind,
  sourceId, amount, principal)` taking an already-found invoice, under the
  same lock (its ticket 3);
- the Local Support Fees rule and its lifecycle table (its ticket 4).

So a send-back changes the status and nothing else about the numbers. Because
`linesStored` stays true, the draft serves the stored rows it was sent with,
`editLine` updates them in place, and the resend writes no row. Nothing is
cleared, so `client_invoice_lines`' unique constraints can never trip, and
nothing is recomputed, for any month. `edit-client-invoice-lines` already
proves this shape with a fixture (its story 25, testing item 2); this feature
replaces the fixture with the real transition.

### The lifecycle, amended

```
DRAFT ──send (Agent)──▶ SENT ──approve (Manager)──▶ APPROVED
  ▲                       │
  └──send back (Manager)──┘   reason required; lines and linesStored untouched
```

`ClientInvoiceStatus.canTransitionTo` gains `SENT → DRAFT`. `APPROVED` stays
terminal. An invoice is **sent back** when it is `DRAFT` and carries a
`sentBackAt`. That can only be true after a send-back, because a
get-or-created draft never has one. (Every sent-back invoice also has
`linesStored`; `sentBackAt` is what the UI and the access rule read, because
it also carries when.)

The reads need no rule of their own. A sent-back invoice is a draft with
`linesStored`, and `toResponse` serves it by `edit-client-invoice-lines`'
rule: its stored lines exactly, with `edited` and `computedAmount` for the
Agent and the Manager. A line's `computedAmount` stays the one stored at the
first send, so an edit on a sent-back invoice shows against that figure. A
Fee logged or a Postpaid SIM added after the send does not appear on the
invoice (story 18, open question 1).

### What a send-back does to the Agent's pay

The Local Support Fees rule is `edit-client-invoice-lines`' and is not
changed. Applied to a send-back:

- **The send-back and the resend move nothing.** Neither writes a line, and a
  Contract's term in the rule is the invoice's billed total plus anything it
  does not bill, which is the same before and after either.
- **An edit on a sent-back line goes through the same `editLine`**, so it
  follows that spec's lifecycle table for the Agent's Agent Invoice of the
  invoice's billing month:

  | Agent Invoice | Effect of the edit |
  |---|---|
  | none yet, or `DRAFT` | Nothing written; the draft reads the new figure live. |
  | `SENT` | Its Local Support Fees move by exactly the edit's difference, with an audit line. This is the behaviour `edit-client-invoice-lines` recommends in its open question 1 (`docs/inbox/question-pay-after-agent-invoice-sent.md`); if the human answers otherwise, this row follows that answer, with no change here. |
  | `APPROVED`, `PAID` | Nothing moves; the difference is a carry-over for `invoice-adjustment`. |

- The Client Invoice returning to draft does not reopen "before approval" for
  an Agent Invoice already approved: the rule reads the Agent Invoice's
  status, not the Client Invoice's. A send-back of a past month's invoice will
  usually meet an approved or paid Agent Invoice, so its edits are
  carry-overs.
- A Fee logged after the send counts in the Agent's pay at its computed
  amount, or not, as `edit-client-invoice-lines`' open question 2
  (`docs/inbox/question-pay-late-fee-after-client-invoice-sent.md`) is
  answered. Nothing here depends on that answer beyond what that spec builds.

No question is raised here on either point; both are already in the inbox.

### ADR

**A new ADR 0005, "A Manager may send a Client Invoice back to draft"**
(`edit-client-invoice-lines` records 0004; if numbers shift at merge, this one
stays the one after it). It records the backward edge, and why ADR 0001's
reason still holds: the freeze protects every figure while a Manager is
reviewing it (`sent`) or has approved it (`approved`). A send-back is the
Manager deliberately handing that review back, and the resend is a new review
of new frozen numbers. It states precisely what a send-back does to the
numbers:

- nothing is recomputed and nothing is cleared. The invoice returns to draft
  carrying its stored lines and `linesStored`, and the Agent edits them as on
  any draft (ADR 0004);
- no line is added after the first send (as answered to open question 1);
- a send-back and a resend move no pay; an edit on a sent-back invoice reaches
  the Agent's pay by ADR 0004's rule;
- `approved` stays terminal: an amount found wrong after approval is corrected
  by the Manager on the following month's invoice (`invoice-adjustment`).

It names the Client Invoice as implemented now and leaves the Agent Invoice to
`send-an-agent-invoice-back`, which will add its half and put the matching
note on ADR 0003. ADR 0001 gets a second dated note, in the shape ADR 0003's
2026-09-17 note set: "Amended by ADR 0005: `sent → draft` by a Manager's
send-back; the stored lines it was sent with become the draft's editable
lines, nothing recomputed; `approved` stays terminal."

### Backend: send back

`POST /api/client-invoices/{invoiceId}/send-back`, Manager only (the existing
`/api/client-invoices/**` matcher), body `{ "reason": "…" }`.

```
200 ClientInvoiceResponse   — now DRAFT, the lines it was sent with, sentBackAt/sentBackReason set
400                          — reason blank, or longer than 1000 characters
404                          — no such invoice in the caller's Tenant
409                          — invoice is not SENT (draft, or already approved)
403                          — caller is not a Manager
```

`ClientInvoiceService.sendBack(invoice, reason, principal)`, `@Transactional`,
does the following:

1. Re-reads the invoice under the row lock `edit-client-invoice-lines` adds
   (the locking finder on `ClientInvoiceRepository`). Approve is made to take
   the same lock here, so an approval and a send-back that race each other run
   one after the other, and the second gets `409`. Send and `editLine`
   already take it.
2. Checks `canTransitionTo(DRAFT)`, or `409`.
3. Touches no line and leaves `linesStored` set.
4. Sets `status = DRAFT`, clears `sentAt`, and sets `sentBackAt = now` and
   `sentBackReason`.
5. Writes `AuditLog.statusChanged("ClientInvoice", id, "SENT", "DRAFT", actor, tenant)`.
   This reuses the existing generic event, with no new audit method. The
   reason's text is never logged, following `requestRejected`'s rule.

The resend is the ordinary send of `edit-client-invoice-lines`. With
`linesStored` already true it writes no line, so it can never store a second
line for a SIM or a Fee; it sets `status = SENT` and a new `sentAt`.

### Backend: the Agent reaches an invoice by its id

The by-id routes open to the Contract's own Agent. Each check goes through
`ClientInvoiceAccessGuard` against the invoice's Contract, after the
Tenant-scoped lookup. One predicate in `ClientInvoiceService`, **open to its
Agent**, is true for a `DRAFT` that is sent back or of the current billing
month (UTC), and gates every Agent write by id:

| Route | Manager | Contract's own Agent | Other Agent / Tester |
|---|---|---|---|
| `GET /api/client-invoices/{id}` | yes | yes (any status) | 403 |
| `GET …/files`, `GET …/files/{fileId}` | yes | yes | 403 |
| `POST …/files` (new) | yes | yes, open to its Agent only (409 otherwise) | 403 |
| `PUT …/lines` (new) | 403 | yes, open to its Agent only (409 otherwise) | 403 |
| `POST …/send` (new) | 403 | yes, open to its Agent only (409 otherwise) | 403 |
| `POST …/approve`, `POST …/send-back` | yes | 403 | 403 |
| `GET …/pdf` | yes | 403 (unchanged; the Agent's PDF stays on the current-month route) | 403 |

`PUT /api/client-invoices/{id}/lines` takes the body and gives the responses
of `edit-client-invoice-lines`' current-month `PUT …/client-invoice/lines`,
and calls the same `editLine`, so validation, audit and the pay effect are
identical. `POST …/send` calls the same service send. `POST …/files` calls a
file-attach operation moved from `ClientInvoiceController` into
`ClientInvoiceService`, taking an already-found invoice, which the
current-month attach route also calls.

`SecurityConfig`'s `/api/client-invoices/**` matcher narrows from
Manager-only to Manager-or-Agent for exactly the `GET` read, the files routes,
`lines` and `send`. Approve and send-back keep a Manager-only matcher, placed
before the broader one, so role enforcement does not depend on the guard
alone. An unknown or other-Tenant id is `404`. Another Agent's invoice in the
same Tenant is `403`, the same as the current-month routes' "Not your
Contract".

A `DRAFT` that is not open to its Agent (a past-month draft never sent) is
refused with `409` and the coded body the other conflicts use,
`{ "code": "PAST_MONTH_DRAFT_NOT_SENDABLE", "message": … }`, carried by a
`Reason` enum on its conflict exception as `AgentLoginConflictException` and
`TesterConflictException` do. A non-`DRAFT` stays the plain `409` it is today.

**The sent-back list.** `GET /api/client-invoices/sent-back`, Agent only. It
returns the caller's own Contracts' sent-back invoices, oldest send-back first,
each as `{ id, contractId, clientName, country, billingMonth, currency,
sentBackAt, sentBackReason }`. It returns no amounts: they are being edited
and belong to the invoice's own page. A Manager or a Tester gets `403`.

### Schema and contract

- **One additive migration**, the version after `edit-client-invoice-lines`'
  (`V57` if that one is `V56`): nullable `sent_back_at timestamptz` and
  `sent_back_reason varchar(1000)` on `client_invoices`. This follows
  `Request`'s distinct-reason-column prior art (`cancellation_reason`,
  `rejection_reason`), not a shared reason field. No data is rewritten, and
  `client_invoice_lines` is not touched.
- **`ClientInvoiceResponse`** gains `sentBackAt` and `sentBackReason`. Both
  are **null for a Tester caller**, on the current-month read, and the PDF
  never prints them. The reason is a note from the Manager to the Agent, not
  part of the statement. The line fields are `edit-client-invoice-lines`'
  and do not change here.
- The reason and `sentBackAt` **stay on the row through the resend**. They are
  overwritten by the next send-back, so the Manager sees "Sent back on … :
  reason" while reviewing the resend. The "sent back" state is still
  `DRAFT` + `sentBackAt`, so a resent invoice is no longer in it.

### Frontend: Manager

- **`SendBackClientInvoiceControl`**, a new client control on the Client
  Invoice detail page, beside Approve, shown only while `SENT`. It follows
  `PendingRequestDecisionControls`' reject flow:
  - a **Send back** trigger expands a small inline form holding a required
    reason field (a textarea, labelled "Reason for sending back", with the
    hint "Tell the Agent what is wrong or missing. They can change any line
    and attach files before sending it again."), **Confirm send back** and
    **Back**;
  - an empty reason is refused before any request is made;
  - a `409` shows the "no longer awaiting approval — refresh" copy that
    `ApproveClientInvoiceControl` already uses;
  - other failures show a generic retry message, and the typed reason is kept;
  - on success it hands the returned invoice to the detail view, which
    re-renders in place.

  It uses no dialog, so `DialogShell` and its debt (tech-debt.md:16) are not
  touched. The control takes the endpoint's URL, not a Client Invoice id, so
  `send-an-agent-invoice-back` mounts the same control.
- **`ClientInvoiceDetailView`**:
  - a sent-back draft's status note reads "Sent back to the Agent on
    {date} — waiting for them to resend", followed by the reason in a quoted
    block. Its lines are shown read-only, with the per-SIM lines and edited
    markers `edit-client-invoice-lines` adds;
  - a resent `SENT` invoice shows a quiet line under its timestamps:
    "Previously sent back on {date}: {reason}";
  - Approve stays the one pill, and Send back is a secondary control at the
    8px radius.

### Frontend: Agent

- **Prefactor: the Agent's invoice card addresses the invoice by its id.**
  The card inside `AgentClientInvoicesView` is extracted into
  `AgentClientInvoiceCard(invoice)`, carrying `EditClientInvoiceLineControl`
  as `edit-client-invoice-lines` puts it on the card. `SendClientInvoiceControl`,
  `AttachCarrierInvoiceFileControl` and `EditClientInvoiceLineControl` take an
  invoice id and call the by-id routes, and file downloads use the by-id
  route. Download PDF stays on the current-month route, because the Agent is
  refused the by-id PDF. The current-month page and the new by-id page render
  the same card.
- **Client Invoices page.** Above the Contract switcher, a "Sent back to you"
  section lists the sent-back invoices. Each row shows the Contract, the
  billing month, when it was sent back, the reason (clamped to two lines), and
  an **Open** row action. The section is not rendered at all when the list is
  empty, so a page with nothing sent back looks exactly as it does today. When
  the current month's invoice is itself sent back, its card shows the reason
  too.
- **New page `/agent/client-invoices/{invoiceId}`.** It has a breadcrumb back
  to Client Invoices and renders `AgentClientInvoiceCard`. A sent-back draft
  gets a warning-toned notice ("Sent back by the Manager on {date}", then the
  reason, then "Correct what is needed, then send it again."), its lines in the
  editor exactly as sent, Attach file and Send. After a successful send the
  page refreshes and shows the invoice as sent. A past-month draft never sent
  renders read-only, with no editor, Attach file or Send. Another Agent's id,
  or an unknown id, shows the not-found state.
- **Badge.** A sent-back draft shows a **Sent back** badge in the `warning`
  tone instead of **Draft**. It is added to `lib/status.ts`'s Client Invoice
  maps as a derived display state, not a backend status.
- **Send confirmation copy.** "Sends to the Manager and Client, and locks the
  numbers. This can't be undone." becomes "Sends to the Manager and Client,
  and locks the numbers. Only the Manager can send it back to you."
- **BFF.** Pass-through proxies for the new routes, in the same shape as every
  other proxy, using `backendFetch`.

### Untouched, and why that is correct

- **Review Queue and Dashboard card.** Membership is `SENT` for Client
  Invoices, so a send-back leaves the queue and a resend rejoins it. The
  `waitingSince` value is the new `sentAt`: the wait on the Manager starts
  again at the resend. Its total sums the stored lines, as
  `edit-client-invoice-lines` leaves it.
- **The line model and the pay rule.** Both are used as
  `edit-client-invoice-lines` builds them; no column, rule or audit method of
  theirs changes.
- **Tester.** `requireCanView` already refuses a draft, so a sent-back
  current-month invoice shows the existing "No Invoice sent yet" state until
  it is resent.

## Design direction

Two surfaces, both **Operate**, built entirely from shipped patterns. The
`design` slot is not needed, and `DESIGN.md` does not change.

- **Manager detail page.** The inline reason form copies the Pending Requests
  reject form: 8px controls, a `danger`-toned inline error, and Approve
  remaining the single pill (Pill-Is-Primary Rule). The quoted reason uses the
  existing `canvas-soft` hairline block the page already uses for its amounts
  summary.
- **Agent Client Invoices page and by-id page.** The "Sent back to you"
  section is a Card holding a Table, using the existing cell rhythm and
  `TableScroll` for narrow viewports, with row actions in the soft-indigo row
  tone. The sent-back notice uses `warning`/`warning-bg`, the tone DESIGN.md
  already assigns to "awaiting" states. The **Sent back** badge is a
  `warning` pill badge, driven centrally from `lib/status.ts`. The line editor
  is `edit-client-invoice-lines`' and is not restyled here.

Visual goldens:

- **No new goldens.**
- The "Sent back to you" section renders nothing when empty, so the Agent's
  Client Invoices golden (if captured, as re-approved by
  `edit-client-invoice-lines`) should not move. The Manager detail page is
  backend-driven and not in the backend-free suite.
- Any golden that moves is a finding.

## Constraints

- **Lands after `edit-client-invoice-lines` is merged, all six tickets.** No
  ticket here starts before.
- **One additive migration** of two nullable columns. No data rewritten, and
  no check constraint changed (`DRAFT` is already allowed).
- `APPROVED` stays terminal. The only backward edge is `SENT → DRAFT`, taken
  only by a Manager.
- A send-back writes no row of `client_invoice_lines` and never clears
  `linesStored`. Nothing on a sent-back invoice is recomputed, by a read, by
  its month ending, or by the resend.
- A resend writes no line row and never fails on `client_invoice_lines`'
  unique constraints.
- A send-back and a resend change no Agent Invoice. An edit on a sent-back
  invoice changes one only through `editLine`'s rule.
- A send-back and an approval of the same invoice are serialized by the
  Client Invoice row lock. No invoice may ever be `APPROVED` while carrying
  `DRAFT`'s editability or a send-back newer than its approval.
- A send-back reason is required, not blank, and at most 1000 characters. It
  never appears in a log line, the PDF, or any Tester-facing response.
- From `SENT` onward every read still serves the stored lines (ADR 0001).
- An Agent's writes by id (attach, line edits, send) apply only to a `DRAFT`
  open to its Agent: sent back, or of the current billing month.
- By-id routes never create an invoice. An unknown or other-Tenant id is
  `404`. Role gates are enforced at the matcher and again in the guard.
- Backend tests run under `IntegrationTest`: singleton Testcontainers Postgres,
  with each method rolled back. No repository mocks (Backend rule 5).
- e2e and visual runs use the isolated stack (`docs/agents/implementer-notes.md`).
  An e2e failure is judged against a controlled comparison on an idle
  machine, never waved off as a flake (tech-debt: suite unreliable under load).
- Maven runs under JDK 21, and Checkstyle stays clean.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, the rendered
accessibility tree, and what a user sees. They never assert repository calls
or component internals.

1. **Backend: the HTTP API seam (primary; existing).** Prior art:
   `ClientInvoiceByIdApiTest` (by-id read and approve, `OtherTenantFixture`
   404), `ClientInvoiceApiTest` (current-month send and freeze), and
   `ClientInvoiceLineEditApiTest`'s story-25 fixture case, which this test
   plays with the real send-back. A new `ClientInvoiceSendBackApiTest`
   covers:
   - send-back of a sent **current-month** invoice whose SIM line and one Fee
     line the Agent edited before sending → `DRAFT`, reason and time
     returned, and the same lines, amounts and `computedAmount`s; a Postpaid
     SIM added after the send-back adds no line and moves no amount;
   - a Fee logged after the first send does not appear on the sent-back
     invoice, every line stays as sent, and the resend succeeds with the
     frozen lines equal to the lines before it, with no server error;
   - send-back of a sent **past-month** invoice, written directly by a
     fixture in the way `DemoDataLoader.writePastClientInvoice` does after
     `edit-client-invoice-lines` (stored lines, `linesStored` true): the lines
     come back as sent; the Agent reads it by id, edits a SIM line by id,
     attaches a file and resends; the resent invoice serves the edited amount.
     The past-month fixture is also the month-rollover state, so no clock
     needs faking;
   - send-back of a **backfilled** invoice (one `BASE_AMOUNT` line): it comes
     back with that single line, which the Agent edits by id and resends;
   - the Review Queue excludes the invoice after a send-back and includes it
     after the resend, ordered by the new `sentAt`, with the resent total;
   - send-back of a draft or an approved invoice → `409`, with a blank or
     1001-character reason → `400`;
   - the `agentToken()` and `testerToken()` callers are refused a send-back
     with `403`; a Manager is refused `PUT …/lines` and `POST …/send` by id
     with `403`;
   - the other Agent's token, and a Tester, are refused the by-id read, files,
     line edits and send with `403`;
   - the Agent's by-id send, attach and line edit of a past-month `DRAFT`
     that was never sent (written by a fixture) → `409` with code
     `PAST_MONTH_DRAFT_NOT_SENDABLE`, and the invoice is unchanged; a
     current-month draft never sent is accepted;
   - `OtherTenantFixture`'s invoice gives `404` on every route;
   - the Agent gets the sent-back list for their own Contracts only, and a
     Manager or a Tester gets `403`;
   - a Tester's current-month read of a sent-back invoice gives `403`, and
     after the resend it succeeds with `sentBackReason` null;
   - approving after a send-back gives `409`;
   - **pay, through the Agent Invoice's own read:** a send-back and a resend
     leave a draft, a sent and an approved Agent Invoice's Local Support Fees
     unchanged; an edit by id on the sent-back invoice moves a `SENT` Agent
     Invoice by exactly the difference (as `edit-client-invoice-lines`
     builds open question 1's answer) and leaves an `APPROVED` one
     unchanged. The full pay rule stays proven by
     `LocalSupportFeesFollowClientInvoiceApiTest`.
2. **The race is proven at the same seam, not with mocks.** Two threads send
   back and approve the same invoice. Exactly one wins, the other gets `409`,
   and the final invoice is either `APPROVED` with no `sentBackAt`, or `DRAFT`
   with `sentBackAt` and no `approvedAt`, its lines as sent in both cases.
   This test commits real transactions, so it cannot use `IntegrationTest`'s
   rollback. It cleans up its own rows. Prior art: the committing race tests
   `edit-client-invoice-lines` adds.
3. **The prefactors are proven by existing suites passing unedited:**
   `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest`,
   `ClientInvoiceLineEditApiTest`, `LocalSupportFeesFollowClientInvoiceApiTest`,
   `edit-client-invoice-lines.spec.ts`,
   `client-invoice-submission-and-visibility.spec.ts` and
   `manager-invoice-review-queue.spec.ts`, and the component tests of
   `edit-client-invoice-lines`. The one exception is the Agent's send
   confirmation copy, which this feature changes on purpose; a test asserting
   the old copy is updated in the same commit, named in it.
4. **Frontend: component tests (Vitest + Testing Library, existing seam).**
   Prior art: `approve-client-invoice-control.test.tsx`,
   `client-invoice-detail-view.test.tsx`, `client-invoices-view.test.tsx` and
   `pending-request-decision-controls.test.tsx`. The tests cover:
   - the send-back control: expand and Back, an empty reason blocked, `409`
     and generic copy, reason kept on failure, and the success callback;
   - the detail view: Send back only while `SENT`, the sent-back note on a
     draft, and the "previously sent back" line on a resent invoice;
   - the Agent view: the section is absent when the list is empty, rows and
     their links when it is not, the **Sent back** badge, the notice's
     "Correct what is needed" line, the line editor present on a sent-back
     draft and absent on a past-month draft never sent, the editor calling
     the by-id route, and the new send-confirmation copy.
5. **Frontend: one e2e spec, `send-a-client-invoice-back.spec.ts`
   (existing seam).** Prior art: `client-invoice-submission-and-visibility.spec.ts`
   and `edit-client-invoice-lines.spec.ts`. It plays the journey on a
   Contract the spec creates itself:
   1. the Agent sends;
   2. the Manager opens the invoice from the Review Queue and sends it back
      with a reason;
   3. the queue no longer lists it;
   4. the Agent sees it under "Sent back to you" with the reason, opens it by
      id, sees the amounts as sent, edits a Postpaid SIM line and resends;
   5. the Manager sees it back in the queue, with the edited line marked
      "Edited · computed …" and the earlier reason, and approves it.

   A past month is not played in e2e, because e2e state does not roll back
   and the send path writes only the current month. The API seam covers the
   past month, and so does walkthrough step 9 on the demo data.
6. **No unit tests** of `canTransitionTo` or the service beyond the HTTP
   seam. The seam proves every edge (Backend rule 6).

## Decisions taken

### Settled by `edit-client-invoice-lines` (`docs/features/edit-client-invoice-lines/spec.md`)

- **The line model.** `client_invoice_lines`, one line per Postpaid SIM and
  per Fee (one `BASE_AMOUNT` line on backfilled invoices), each with a
  computed and a billed amount; `linesStored`, set at the first send and
  never cleared; a send that stores every line, after which nothing
  recomputes. This replaces the model this spec had assumed, and with it this
  spec's former "clear the Fee snapshot rows" and "a past month's base amount
  stays as sent" rules, both now dropped.
- **The send in one transaction under a row lock** is that spec's ticket 2.
  This spec's former first ticket, `send-client-invoice-in-one-transaction`,
  is dropped. Only the file-attach move remains here.
- **`editLine` takes a found invoice** so that the by-id route here calls it
  unchanged; that spec ships only the current-month edit route, so the by-id
  `PUT …/lines` is added here.
- **The Agent's pay follows the billed amounts until approval, then carries
  over** (the human's answer, 2026-10-01, `question-edit-lines-agent-pay`),
  with the sent-Agent-Invoice and late-Fee timing still open in
  `question-pay-after-agent-invoice-sent` and
  `question-pay-late-fee-after-client-invoice-sent`. This spec follows that
  spec's recommendation and raises neither again.
- **ADR numbering.** That spec records ADR 0004 and migration `V56`; this one
  takes ADR 0005 and the migration after.

### Answered by the human

- **A send-back refreshes nothing; every line is the Agent's to edit while
  the invoice is a draft** (2026-10-01, answer to
  `approve-send-a-client-invoice-back`). This replaces the 2026-09-30 rule
  "Fee lines go live, a past month's base amount stays as sent", its
  `baseAmountKept` flag, its "As sent" labels and its clearing of snapshot
  rows.
- **Corrections after approval carry forward** (2026-09-30). The Manager
  corrects them on the following month's invoice through `invoice-adjustment`.
  A Fee row is never edited or voided; only an invoice's line for it is.
- **The reason is seen only by the Manager and the Agent; the Agent opens
  their own invoices by id, and only a sent-back or current-month draft can
  be sent; a resent invoice queues from the resend; the Manager uses an
  inline form** (2026-10-01, approved unchanged).

### Taken alone

- **A sent-back invoice gains no line for a Fee or SIM added after the send.**
  This is `edit-client-invoice-lines`' `linesStored` rule applied unchanged,
  and its story-25 test asserts it. It supersedes this spec's earlier "a late
  Fee joins as a new pre-filled line". Because it conflicts with the epic's
  proof, it is put to the human as open question 1.
- **The send-back does not touch the pay rule, and the timing questions are
  not duplicated.** The rule reads the Agent Invoice's status, so a Client
  Invoice returning to draft reopens nothing on an approved Agent Invoice.
- **The same "open to its Agent" predicate gates attach, line edits and send
  by id.** The human approved it for send; letting the Agent edit or attach
  to a draft they can never send would be work with no outcome. A past-month
  draft never sent has lines pre-filled from today's Fleet, and no feature
  opens it yet.
- **The by-id line-edit route is Agent-only.** `edit-client-invoice-lines`
  gives the Manager no line edit; the by-id route keeps that.
- **File attach moves into `ClientInvoiceService`, taking a found invoice.**
  A second attach route is added, and one operation keeps the rule single.
- **The send-back hint and the Agent's notice say the Agent can change any
  line.** That is now true, and the Manager's reason is the instruction.
- **A new ADR 0005, with a dated "amended by" note on ADR 0001.** Re-opening a
  snapshot is a real decision with a trade-off and context someone would need
  later. ADR 0003's note waits for the Agent Invoice half, so it is never true
  before its code is.
- **Approve takes the Client Invoice row lock, which serializes send-back and
  approve.** Without it, a race can leave an invoice both approved and
  returned to the Agent's editor. The lock already exists for send and edit.
- **`sentAt` is cleared on a send-back and set again at the resend.** A draft
  has not been sent. The Review Queue then waits from the resend.
- **The reason stays on the row through the resend, and is overwritten by the
  next send-back.** The Manager reviewing the resend needs it, and the audit
  line records every event. A history table is a non-goal.
- **Two distinct columns (`sent_back_at`, `sent_back_reason`), not a shared
  reason field.** This is the epic's direction and `Request`'s prior art.
- **The reason is capped at 1000 characters.** A free-text column should have
  a bound. It is generous for a note, and cheap to raise.
- **The audit reuses `AuditLog.statusChanged` (`SENT → DRAFT`), without the
  reason text.** No new audit method is needed, and no audit line in the
  codebase carries free text. Line edits are audited by
  `edit-client-invoice-lines`' `clientInvoiceLineEdited`.
- **The Agent reaches invoices through the existing by-id routes, opened
  per-route to the Contract's own Agent.** It reuses the
  `manager-invoice-review-queue` identity-based addressing rather than adding
  a month parameter, the alternative that feature already rejected.
- **The Agent is refused the by-id PDF.** The Agent's PDF already works on the
  current-month route, and a past-month PDF is not needed for correction.
- **Another Agent's invoice is `403`, not `404`.** This matches the
  current-month routes' "Not your Contract". The Tenant boundary stays `404`.
- **The sent-back list is its own Agent-only endpoint and carries no
  amounts.** The page already fetches per Contract, and this list spans
  months. Amounts belong on the invoice's page, where they are edited.
- **The send-back control takes an endpoint URL, and "Sent back" is a derived
  display state in `lib/status.ts`.** Both are cheaper built once:
  `send-an-agent-invoice-back` reuses the control and the badge mapping
  unchanged.
- **The Agent's controls move to by-id routes, and the card is extracted.**
  One card, with its editor, serves the current-month page and the new by-id
  page, and every Agent action then addresses the invoice it shows.
- **The current-month send, file-attach and line-edit routes stay.** They are
  now unused by the UI but harmless, since each calls the same service.
- **The "Sent back to you" section is hidden when empty.** The page then looks
  unchanged for the common case, and no golden moves.
- **The send-confirmation copy is changed.** "This can't be undone" stops
  being true.
- **Testing: the existing HTTP seam and the existing component and e2e seams,
  plus one committing race test.** Prior art is `ClientInvoiceByIdApiTest`,
  `ClientInvoiceApiTest`, `ClientInvoiceLineEditApiTest`, the invoice control
  tests, `client-invoice-submission-and-visibility.spec.ts` and
  `edit-client-invoice-lines.spec.ts`. The race cannot be shown inside a
  rolled-back transaction.

## Open questions

1. **When a Manager sends an invoice back, can the Agent add a Fee of that
   month that was logged after the invoice was first sent?** Example: the
   Agent sends this month's Client Invoice on the 25th; on the 27th a $10
   Topup Fee for this month is logged; the Manager sends the invoice back
   saying "the topup is missing". Your epic's closing proof says the Agent
   "adds what was missing (a file, a late Fee of that month)", but the
   drafted `edit-client-invoice-lines` model freezes the list of lines at the
   first send, so the $10 could never go on this invoice and would wait for
   next month's `invoice-adjustment`. **Recommendation: yes, the late Fee
   appears on the sent-back invoice as a new pre-filled line, and every line
   it was sent with stays exactly as sent.** Reason: it is what the epic
   promised, the Client is billed for the month the Fee belongs to, no figure
   the Manager reviewed moves, and the Agent's pay is the same either way
   (the pay rule already counts an unbilled Fee at its amount). If yes, one
   rule in `edit-client-invoice-lines` changes (a sent-back draft also shows
   a pre-filled line for any Fee of its month with no line, and the resend
   stores it), and this spec's story 18, its tests and walkthrough step 3
   are reversed; this spec is drafted for "no", consistent with that spec as
   it stands. (Case 1.)

## Acceptance walkthrough

1. [agent] As the Contract's Agent, edit one Postpaid SIM line of the current month's Client Invoice through the API, send it, then log one more Fee on the Contract (used in step 3). As the Manager, `POST /api/client-invoices/{id}/send-back` with a reason, and show `200` with `status: DRAFT`, `sentBackAt`, `sentBackReason`, no `sentAt`, and every line, amount and `computedAmount` exactly as sent. (stories: 1, 2, 4, 16)
2. [agent] Show `GET /api/review-queue` no longer lists the invoice. Then send back a draft and an approved invoice and show `409`. Send an empty reason and a 1001-character reason and show `400`. (stories: 5, 7, 2)
3. [agent] Add a Postpaid SIM to the Contract and show the sent-back draft gains no line and no amount moves. Show the Fee logged in step 1 does not appear on it. As the Agent, edit one Fee line with `PUT /api/client-invoices/{id}/lines`, then resend by id and show `200`, `SENT`, the edited amount, and the frozen lines equal to those before the resend, with no server error. Show the queue lists the invoice again with `waitingSince` equal to the new `sentAt`. (stories: 9, 17, 18, 20, 28, 36)
4. [agent] As the Manager, read the resent invoice by id and show `sentBackAt` and `sentBackReason` are still there, and the edited line marked with its `computedAmount`. Approve it and show `APPROVED`. Then send back another sent invoice twice in a row, resending in between, and show both send-backs succeed. (stories: 10, 11, 12)
5. [agent] Run the race test and show exactly one of send-back and approve wins, the other is `409`, and the invoice is consistent in either final state with its lines as sent. (stories: 29, 8)
6. [agent] Call send-back with the Agent's and a Tester's token and show `403`. Call the by-id read, files, a line edit and send with another Agent's token and show `403`. Call every by-id route on `OtherTenantFixture`'s invoice and show `404`. As the Agent, send, attach to and edit a past-month draft that was never sent and show `409` `PAST_MONTH_DRAFT_NOT_SENDABLE` with the invoice unchanged. (stories: 24, 25, 31, 32)
7. [agent] As a Tester of the Contract's Client, read the current-month invoice while it is sent back and show `403`. Read it after the resend and show `200` with the resent numbers, `sentBackReason` null and no edit markers, and download the PDF and show it carries the resent numbers and no reason. (stories: 26, 27)
8. [agent] Grep the backend log for step 1 and show one `action=STATUS_CHANGE` audit line for `ClientInvoice` from `SENT` to `DRAFT`, with the Manager as actor and the Tenant id, and no reason text anywhere in the log. (stories: 30)
9. [agent] On the demo stack, pick a past-month `SENT` Client Invoice, note its lines, and add a Postpaid SIM to its Contract. Send the invoice back as the Manager. As its Agent, `GET /api/client-invoices/sent-back` and show it listed with its reason. Read it by id and show the lines equal to the noted ones. Edit a line by id, attach a file by id, resend, and show `SENT` with that file and the edited amount. Run the past-month and backfilled cases of `ClientInvoiceSendBackApiTest`, which are also the month-rollover state, and show them green. (stories: 6, 13, 14, 15, 16, 17, 19, 20, 36)
10. [agent] For the Contract's Agent and the invoice's month, read the Agent Invoice's Local Support Fees before the send-back, after it, and after the resend, and show all three equal. Then, with that Agent Invoice `SENT`, send the Client Invoice back again, edit a line by id up by $5.00, and show the Agent Invoice's Local Support Fees up by exactly $5.00 (as `question-pay-after-agent-invoice-sent` is answered; if the answer is no, show them unchanged) with one `agentInvoiceLocalSupportFeesFollowed` audit line; approve the Agent Invoice, edit another line, and show it unchanged. (stories: 33)
11. [agent] In a browser as the Manager, open a sent Client Invoice from the Review Queue. Show **Send back** beside **Approve**, which stays the only pill. Expand Send back, show the hint saying the Agent can change any line, submit empty, and show it is refused inline with the form still open. Enter a reason and confirm, then show the page re-render in place as a draft with the reason, the numbers as sent and no actions. Go back to the queue and the Dashboard and show the invoice is on neither. (stories: 1, 2, 3, 4, 5, 7)
12. [agent] In a second tab, approve a sent invoice. In the first tab, still showing it as sent, try to send it back and show the "refresh" message. (stories: 8)
13. [agent] Sign in as the Agent and open Client Invoices. Show "Sent back to you" listing the invoice with its Contract, month, date and reason, and the current month's card showing the reason and a **Sent back** badge. Open the row and show the by-id page with the notice ending "Correct what is needed, then send it again.", the line editor holding the lines as sent, Attach file and Send. (stories: 13, 14, 15, 16, 23)
14. [agent] On that page, change a Postpaid SIM line and a Fee line in the editor, and attach a file. Click Send and show the confirmation says only the Manager can send it back. Confirm, and show the invoice as sent with the new amounts and gone from "Sent back to you". (stories: 17, 19, 20, 21, 22)
15. [agent] As the Manager, show the invoice back in the Review Queue with its new total. Open it and show the edited lines with "Edited · computed …" and "Previously sent back on …: {reason}". Approve it. (stories: 9, 10, 11)
16. [agent] As the Agent, open by id a past-month draft that was never sent (from the demo data or a fixture) and show it read-only, with no editor, Attach file or Send. (stories: 25)
17. [agent] As an Agent with nothing sent back, show the Client Invoices page has no "Sent back to you" section. Run `mvn verify`, the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green, with the suites named in `## Testing decisions` item 3 unedited apart from the named send-copy exception, and no golden moved. (stories: 34)
18. [agent] Do the Manager's send-back and the Agent's open-edit-and-resend by keyboard alone, showing a visible focus ring at each stop. Repeat at the mobile breakpoint and show the reason form, the sent-back table and the by-id page usable within the viewport. (stories: 35)
19. [human] Send back a real invoice with the reason you would really write, then read it as the Agent would. Confirm the wording tells the Agent what happened and what to do, and that nothing tells the Tester. (stories: 2, 14, 26)
20. [human] Read ADR 0005 and ADR 0001's new note, and confirm they say what you settled: a send-back recomputes and clears nothing; the invoice returns to draft with the lines it was sent with, which the Agent edits; a send-back moves no pay; `approved` stays final; the freeze still protects every number while it is under review; and a wrong amount found after approval is corrected on the next month's invoice. (stories: 4, 11, 16, 33, 36)
21. [human] Send back a real last-month invoice whose Postpaid SIM line does not match what the carrier billed, then as the Agent correct that line, attach the carrier's file and resend. Confirm this is how you want errors caught before approval handled, that the numbers you saw as Manager never moved until the Agent changed them, and that what happened to the Agent's pay for that month is what you expect. (stories: 6, 15, 16, 17, 19, 20, 33, 36)

## Execution order

**Depends on: `edit-client-invoice-lines`, all six tickets merged.** Ticket 1
uses its stored lines, `linesStored`, its service send and row lock; ticket 2
uses `editLine`, the pay rule and the Agent card's editor.

There are two slices, both complete vertical paths, each demoable on its own.

1. `manager-sends-a-client-invoice-back`. Labels: `backend`, `frontend`.
   Depends on the feature `edit-client-invoice-lines`. (stories: 1, 2, 3, 4,
   5, 6, 7, 8, 10, 11, 12, 16, 26, 29, 30, 31, 32, 34, 35, 36)
   - Backend: the migration, the `SENT → DRAFT` edge, the send-back endpoint
     taking the existing row lock (approve made to take it too) and writing
     no line, the response fields and their omission for Testers, and the
     audit line.
   - Records: ADR 0005 and ADR 0001's note.
   - Frontend: the Manager's inline control with its hint, and the
     detail-view states.
   - Tests: the send-back cases of `ClientInvoiceSendBackApiTest` (send-back,
     lines as sent, queue, `409`/`400`/`403`/`404`, Tester), the race test,
     and component tests.
2. `agent-resends-a-sent-back-client-invoice`. Labels: `backend`, `frontend`.
   Depends on `manager-sends-a-client-invoice-back`. (stories: 9, 13, 14, 15,
   17, 18, 19, 20, 21, 22, 23, 24, 25, 27, 28, 33, 35)
   - Backend: file attach moved into `ClientInvoiceService`; the Agent's
     by-id read, files, attach, `PUT …/lines` and send routes with their
     matcher changes, gated by the "open to its Agent" predicate (`409`
     `PAST_MONTH_DRAFT_NOT_SENDABLE` otherwise); and the sent-back list
     endpoint.
   - Frontend: the extracted Agent card with its editor and by-id controls,
     the "Sent back to you" section, the by-id Agent page with its "Correct
     what is needed" notice and read-only state for a past-month draft never
     sent, the badge, the copy change and the BFF proxies.
   - Tests: the Agent and pay cases of `ClientInvoiceSendBackApiTest`,
     component tests, and the e2e spec.
   - If open question 1 is answered yes, the late-Fee line is built in
     `edit-client-invoice-lines` first, and this ticket's story-18 test is
     reversed.
