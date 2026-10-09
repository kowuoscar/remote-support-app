---
feature: month-closes-on-the-fifth
epic: invoice-correction-and-history
status: draft
date: 2026-10-08
---

<!-- sdlc:template spec 1 -->

# A month closes on the 5th

## Problem

A month's invoices lock too early, and then stay locked for good. Today a
Client Invoice is locked the moment its Agent sends it, and an approved one is
final (ADR 0001, ADR 0005). An Agent Invoice is frozen at send, and only a
Manager can change two of its lines, and only while it is `sent` (ADR 0003).
So an Agent who spots a wrong line the day after sending it cannot fix it. The
Manager must send it back, and if the Manager has already approved it, nobody
can fix it at all. The Agent's pay follows a Client Invoice edit only until the
Agent Invoice is approved (ADR 0004). After that, the difference is "a
carry-over" that nothing records.

The business doesn't work that way. The human settled it on 2026-10-08
(`docs/inbox/adjustment-which-month.md`,
`docs/inbox/adjustment-carry-over-automatic.md`, epic `## Reworked`):

- A month's Client Invoices and Agent Invoice **stay editable until the 5th of
  the following month**, whether they are draft, sent or approved. Until then
  the Agent and the Manager can both edit them, and the Manager can still send
  one back.
- **An Agent's edit until the 5th moves that month's pay directly.** Nothing
  is carried over automatically.
- **From the 6th the month is closed for everyone.** The Agent can't edit, and
  the Manager fixes every mistake with an adjustment on the current month's
  invoice (`invoice-adjustment`, the next feature).
- **A late invoice doesn't close.** An invoice still not approved on the 6th
  stays open, is flagged "Late" to the Manager (for example "September 2026 ·
  not sent · 5 days late"), and closes when it is approved. The same applies
  to the Agent Invoice.

The code has no idea of time beyond "the current UTC month". There is no clock
seam, no scheduler and no notion of "Late". The current month is read inline
in about seven places (tech-debt backend: Primitive Obsession). A past month's
draft that was never sent is refused outright
(`PAST_MONTH_DRAFT_NOT_SENDABLE`). An Agent has no page for last month's sent
invoice, and no page at all for an Agent Invoice of an earlier month.

## Journeys

Advances `docs/roadmap/invoice-correction-and-history.md`, the third of its
features in order, after `edit-client-invoice-lines` and
`send-a-client-invoice-back`, and before `invoice-adjustment`. An adjustment
exists only for a closed month, and this feature defines "closed".

- **Send an invoice back for correction** (`wanted`, stays `wanted`). Before
  the 5th, a correction no longer needs a send-back: the Agent or the Manager
  edits the invoice directly. Send-back stays for telling the Agent what to
  fix. The journey reaches `exists` only when `invoice-adjustment` and
  `send-an-agent-invoice-back` land, so `docs/journeys.md` is not edited here.
- **Bill the Client for the month** (`exists`, stays `exists`, amended). "Its
  numbers freeze at send (ADR 0001)" becomes "its numbers can be changed by
  the Agent and the Manager until the 5th of the following month, then close".
  `docs/journeys.md`'s sentence is updated at delivery.
- **Get the Agent paid for the month** (`exists`, stays `exists`, amended). The
  Agent's pay follows their Client Invoice edits until the month closes, even
  once approved. The Manager's override works on an approved Agent Invoice
  until the month closes.
- **Work through what is waiting** (`exists`, stays `exists`, extended). The
  Manager sees every Late invoice flagged, including those not yet sent.
- **Look back at finished invoices** (`wanted`): untouched. "Closed" is the
  definition of "finished" that `invoice-history` will use.

## Goals / Non-goals

**Goals**

- One rule, **open or closed**, decides who may change an invoice of either
  type. An invoice is **open** until its **month close** (the start of the 6th
  of the following month), and after that for as long as it is not approved.
  It is **closed** once it is approved and its month close has passed.
- While a Client Invoice is open, its Agent and any Manager may edit every
  line, whatever its status: draft, sent or approved. The Agent may attach
  Carrier Invoice Files to it, and the Agent may send it if it is a draft. A
  sent invoice stays sent and an approved one stays approved.
- While a month is open, an edit to its Client Invoice moves that month's Agent
  Invoice by exactly the difference, whether the Agent Invoice is sent or
  approved.
- While an Agent Invoice is open, a Manager may override its Salary and new
  advance whether it is sent or approved. A Late Agent Invoice draft of an
  earlier month can still be sent by its Agent.
- From the month close, every edit, attach, override and send-back of a closed
  invoice is refused for everyone, with a coded `409 MONTH_CLOSED`.
- An invoice that is not approved at its month close is **Late**. It stays
  open, the Manager sees it flagged "Late" with its month, its state and how
  many days late it is, and it closes when it is approved.
- The Agent can find every invoice of an earlier month that is still open,
  both Client Invoices and their own Agent Invoice.
- Open, closed and Late are computed from the clock when they are read. No
  job, no stored state, and an injected `Clock` makes every boundary testable.
- A new the month-close ADR records the rule, and dated notes amend ADR 0001, 0003, 0004
  and 0005.

**Non-goals.** Each of these is something a reasonable agent would otherwise
build.

- **No adjustment, credit or negative amount.** Correcting a closed month is
  `invoice-adjustment`. This feature only refuses edits to a closed invoice
  and names the adjustment in the refusal message.
- **No carry-over.** No edit ever records a pending amount for a later month
  (the human's answer of 2026-10-08). What happens when an edit reaches pay
  that is already final is open question 1.
- **No scheduler, no stored "closed" status, and no migration of the status
  enums.** Closing is a moment in time, read against the clock. No row is
  written at the close.
- **No Agent-facing edit of the Agent Invoice's own lines.** Salary and the
  Rollout Advance are standing amounts the Manager sets (CONTEXT.md). The
  Agent edits their pay only through their Client Invoice lines, which feed
  Local Support Fees.
- **No late-Fee lines on a sent or approved invoice.** A sent or approved
  invoice still reads exactly its stored lines (ADR 0004). A Fee logged after
  the send joins the invoice only through a send-back. It still counts in the
  Agent's pay at its logged amount, as today.
- **No back-dating a Fee.** A Fee logged on 3 October still lands in October
  (`FeeController`). A September correction is a September line edit.
- **No Agent Invoice send-back.** That is `send-an-agent-invoice-back`.
- **No Late flag on the Agent's or the Tester's screens.** The human asked for
  it to be shown to the Manager. The Agent sees the invoice in their
  earlier-months list with its status.
- **No notification**, email or count. The Late list and the flag are the
  whole signal.
- **No per-Tenant, per-country or configurable close day.** It is the 5th,
  for everyone. Time zone: open question 3.
- **No re-approval forced by an edit.** An approved invoice that is edited
  stays approved (the human: "an approved invoice stays editable until the
  5th").
- **No change to the Review Queue's membership or order.** Late `sent`
  invoices carry a flag there. Late invoices that are not sent are listed
  beside it, not inside it.
- **No change to how a never-sent draft is pre-filled.** A Late draft of an
  earlier month is pre-filled by the same computation as any draft. The Agent
  edits what is wrong.

## User stories

1. As an Agent, I want to edit a line of a Client Invoice I already sent, until the 5th of the following month, so that I fix a wrong figure without waiting for the Manager to send it back.
2. As an Agent, I want to edit a line of a Client Invoice the Manager already approved, until the 5th of the following month, so that a mistake found after approval is still mine to fix while the month is open.
3. As an Agent, I want my edit to a sent or approved Client Invoice to keep its status (sent stays sent, approved stays approved), so that fixing a line does not restart the review.
4. As an Agent, I want every edit to a Client Invoice of a month that is still open to move that month's Agent Invoice by exactly the difference, whether my Agent Invoice is sent or approved, so that my pay always matches what I bill.
5. As an Agent, I want to attach Carrier Invoice Files to a sent or approved Client Invoice while it is open, so that a carrier bill that arrives after I sent the invoice still gets on it.
6. As an Agent, I want my Client Invoices page to list every Client Invoice of an earlier month that is still open (last month's until the 5th, sent back, or Late), with its status and the date it closes, so that I can find last month's invoice to fix it.
7. As an Agent, I want each open invoice to show the last day it can be changed ("Can be changed until 5 October"), so that I know my deadline.
8. As an Agent, I want an approved invoice of a closed month to show as read-only, with a note that the month is closed and that corrections are now the Manager's, so that I know why I cannot edit it.
9. As an Agent, I want a Client Invoice of an earlier month that I never sent to stay editable and sendable while it is Late, so that a month I forgot can still be billed.
10. As an Agent, I want to open my Agent Invoice of an earlier month while it is open and see its figures as they stand, so that I can watch my Client Invoice edits reach my pay.
11. As an Agent, I want to send a Late Agent Invoice draft of an earlier month, so that a month I forgot can still be paid.
12. As a Company Manager, I want to edit any line of a Client Invoice that is open (draft, sent or approved) from its detail page, so that I can fix a figure myself rather than send it back.
13. As a Company Manager, I want my edit to move the Agent's pay exactly as the Agent's own edit would, so that the bill and the pay never disagree.
14. As a Company Manager, I want to override the Salary or the new advance on an approved Agent Invoice while it is open, as I can on a sent one, so that a pay mistake found after approval is fixed on that month's invoice.
15. As a Company Manager, I want to send back a sent Client Invoice until the 5th, and a Late one after it, so that I can still tell the Agent what to fix.
16. As a Company Manager, I want to send back an approved Client Invoice until the 5th, so that I can hand an invoice I approved too soon back to the Agent with a reason. (Open question 5. This story is dropped if the answer is no.)
17. As a Company Manager, I want to approve a Late invoice after the 5th exactly as I approve any other, so that it closes.
18. As a Company Manager, I want to see every Late invoice of both types in one place, each flagged "Late" with its month, its state and how many days late it is ("September 2026 · not sent · 5 days late"), so that I can chase what is keeping a month open.
19. As a Company Manager, I want a Late invoice that is waiting on me in the Review Queue to carry the same Late flag there, so that I approve the oldest debts first.
20. As a Company Manager, I want the Late flag to disappear the moment I approve the invoice, so that the list shows only what is still open.
21. As a Company Manager, I want every invoice detail page to say whether it is open, and until when, or closed, so that I know whether a fix is an edit or an adjustment.
22. As a Company Manager, I want an invoice I approved that was changed afterwards (by the Agent or by me) to show "Changed after approval" with the date, so that a figure I approved never moves without my noticing.
23. As a Company Manager, I want every edit, attach, override and send-back on a closed invoice refused for everyone, with a message saying the month is closed and that a correction is an adjustment on the current month, so that a closed month never changes.
24. As an Agent or a Company Manager, I want an edit made at the last moment of the 5th accepted and one made at the first moment of the 6th refused, so that the deadline means exactly what it says (time zone: open question 3).
25. As a Tester, I want an invoice that can still change to say so ("Can still change until 5 October"), and never to see edit markers or who changed what, so that a figure that moves does not surprise me (open question 2).
26. As a Tester, I want to be unable to edit any invoice, so that billing stays the Agent's and the Manager's.
27. As the company, I want an edit that comes after the Agent's pay for that month is final (marked paid, or approved and closed) to leave that pay as it is, and the Client Invoice to be changed anyway, so that money already paid never moves (open question 1).
28. As the company, I want only invoices that exist to be flagged Late, not every Contract that had no invoice that month (open question 4).
29. As the company, I want closing to happen without any job running, so that a month cannot fail to close or close late.
30. As the company, I want an edit and an approval of the same invoice that race each other, and an edit that races the month close, to leave one consistent state, so that no closed invoice is ever changed.
31. As the company, I want every edit to a sent or approved invoice audited exactly as a draft edit is (who, which invoice, which line, old and new amount), and every pay movement it causes audited, so that a figure that moved after review can be traced.
32. As an Agent, I want another Agent's invoices refused as before, and another Tenant's not found, so that opening invoices up in time does not open them up across people.
33. As everyone already using the product, I want sending, approving, marking paid, the Review Queue's membership and order, the PDF and the Tester's views to behave as before apart from what this spec changes, so that the change carries no release risk.
34. As an Agent or a Manager working by keyboard or on a phone, I want the line editor on a sent or approved invoice, the earlier-months list and the Late list usable without a mouse and at the mobile breakpoint, so that the deadline can be met from anywhere.

## Solution

### The rule: open, closed, Late

One small domain value, **`MonthClose`**, owns the rule for both invoice
types. It has no dependencies. It takes a billing month, a status family and
the current instant.

```
closesAt(billingMonth)   = start of day 6 of (billingMonth + 1 month), in the close zone (open question 3; UTC recommended)
lastOpenDay(billingMonth) = day 5 of (billingMonth + 1 month)

final(status)   = Client Invoice: APPROVED
                  Agent Invoice:  APPROVED or PAID
open            = now < closesAt  OR  not final(status)
closed          = not open
late            = now >= closesAt AND not final(status)
daysLate        = whole days from lastOpenDay to today, in the close zone   (6th → 1, 10th → 5)
```

Open question 1's recommendation adds one line: a `PAID` Agent Invoice is
also final for pay. Nothing else of the rule changes with the answer.

Nothing is stored. An invoice approved on 2 October closes at the start of
6 October without any write. A Late invoice approved on 10 October is closed
from that approval, because `now >= closesAt` already holds. This replaces the
**open to its Agent** predicate in `ClientInvoiceService` (sent back, or of the
current month), which only ever covered drafts, and with it the
`PAST_MONTH_DRAFT_NOT_SENDABLE` refusal. A past month's draft that was never
sent is now simply Late, and so open (story 9). The `Reason` enum keeps the
old value for one release, unused, and gains `MONTH_CLOSED`.

### Prefactoring: one clock

Today the "current month" is read inline in about seven places (tech-debt
backend, Primitive Obsession). A `java.time.Clock` bean is added, in UTC.
`BillingMonth.current(clock)` replaces every inline
`LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)` (`ClientInvoiceService`,
`ClientInvoiceController`, `AgentInvoiceController`,
`AgentStandingAmountController`, `AgentController`, `FeeController`, the demo
loader). Each one takes the bean, and every check this feature adds reads it.
The timestamps it writes (`sentAt`, `approvedAt`, line `editedAt`) also come
from the bean, so a test that moves the clock gets consistent times. Under the
`demo` profile only, a property can fix the clock to a given instant, for the
acceptance walkthrough. Every other profile ignores it, and the backend logs a
warning at start-up when it is set. This pays the tech-debt entry and is the
feature's first ticket.

### Client Invoice: what an open invoice allows

| Action | Who | Draft | Sent | Approved |
|---|---|---|---|---|
| Edit a line (`PUT /api/client-invoices/{id}/lines`, by id) | the Contract's Agent, any Manager | open | open | open |
| Edit a line (current-month route, unchanged address) | the Contract's Agent | open | open | open |
| Attach a Carrier Invoice File | the Contract's Agent, any Manager | open | open | open |
| Send | the Contract's Agent | open | — | — |
| Send back | Manager | — | open | open, if open question 5 is yes |
| Approve | Manager | — | always (approving is what closes a Late one) | — |
| PDF, files, read | as today | as today | as today | as today |

Every guarded action re-reads the invoice under the Client Invoice row lock,
as it does today, and then checks `MonthClose.open` against the clock. A closed
invoice gets `409 { code: "MONTH_CLOSED", message: "September 2026 closed on
5 October. Corrections are made by an adjustment on the current month." }`.
The check runs after the lock is taken, so an edit that waited on the lock past
the close is refused. Approve is never month-gated: after the close, any
invoice not yet approved is Late, and approving it closes it.

`editLine` drops its `DRAFT`-only refusal. On a `SENT` or `APPROVED` invoice,
`linesStored` is always true, so the edit takes the existing stored-lines
branch: it updates the stored row in place, never deletes one, and writes the
same `clientInvoiceLineEdited` audit line. The read rule of ADR 0004 is
unchanged. A `SENT` or `APPROVED` invoice serves exactly its stored rows. Its
lines' `computedAmount` stays the one stored at the first send, so "Edited ·
computed …" keeps meaning "differs from the computation".

The by-id `PUT …/lines` route opens to Managers. A Manager can now edit lines
that were the Agent's alone. The route's matcher and the access guard both
change, and the Manager reaches every Contract. `attachFile` drops its
`DRAFT`-only refusal the same way.

**Changed after approval.** When a line is edited or a file attached on an
`APPROVED` invoice, `changedAfterApprovalAt` is set to now (a nullable column,
overwritten by each later change). The response carries it to the Agent and
the Manager, and it is null for a Tester. It is not cleared by anything except
a send-back of an approved invoice (open question 5).

**Send-back of an approved invoice (only if open question 5 is yes).**
`ClientInvoiceStatus.canTransitionTo` gains `APPROVED → DRAFT`, taken only by
`sendBack` and only while open. It clears `approvedAt` and
`changedAfterApprovalAt`, and is otherwise ADR 0005's send-back unchanged:
same reason rules, nothing recomputed, no pay moved.

### Agent Invoice: what an open invoice allows

| Action | Who | Draft | Sent | Approved | Paid |
|---|---|---|---|---|---|
| Override Salary / new advance | Manager | — (nothing frozen) | open | open | — |
| Local Support Fees follow a Client Invoice edit | (the edit) | live read | open: moves by the difference | open: moves by the difference | never (open question 1) |
| Send | the Agent | open (the current month, or Late) | — | — | — |
| Approve, mark paid | Manager | as today | as today | as today | as today |

`AgentInvoiceService.override` accepts `SENT`, or `APPROVED` while open. An
override on an `APPROVED` invoice sets the Agent Invoice's own
`changedAfterApprovalAt`. `followClientInvoiceEdit` (ADR 0004's table) moves a
`SENT` invoice as today, and now also an `APPROVED` one while it is open, by
the same difference, with the same `agentInvoiceLocalSupportFeesFollowed`
audit line, and it sets `changedAfterApprovalAt` on an `APPROVED` one. It never
moves a closed or `PAID` invoice. The lock order stays as ADR 0004 and its
debt note record: Client Invoice, then Agent, then Agent Invoice.

The pay table, amended (it replaces ADR 0004's):

| That month's Agent Invoice | Effect of a Client Invoice edit |
|---|---|
| none, or `DRAFT` | Nothing written. It computes live. |
| `SENT` | Moves by exactly the difference (unchanged). |
| `APPROVED`, open | Moves by exactly the difference. **New.** |
| `APPROVED` and closed, or `PAID` | Nothing moves (open question 1's recommendation). The Client Invoice edit is still accepted if the Client Invoice is open. |

A Client Invoice edit is possible after its month close only when the Client
Invoice itself is Late. In that case the Agent Invoice may already be closed,
which is the second case of open question 1.

**The Agent reaches their own Agent Invoice by id.** Today
`AgentInvoiceByIdController` is Manager-only, and the Agent reaches only the
current month. `GET /api/agent-invoices/{id}` and `POST …/send` open to the
invoice's own Agent: the read at any status, the send only while open. This
follows `send-a-client-invoice-back`'s per-route opening of the Client Invoice
by-id routes. Override, approve and mark paid stay Manager-only at the matcher.
Another Agent gets `403`, and another Tenant `404`.

### Responses

`ClientInvoiceResponse` and `AgentInvoiceResponse` gain:

- `lastOpenDay` (a date, e.g. `2026-10-05`). It is given to Testers too, for
  open question 2's note.
- `open` (boolean).
- `late` (boolean) and `daysLate` (integer or null). These are null for a
  Tester.
- `changedAfterApprovalAt` (instant or null). This is null for a Tester.

`ReviewQueueItemResponse` gains `late` and `daysLate`.

### The Manager's Late list

`GET /api/late-invoices`, Manager only. It returns every Late invoice of either
type in the caller's Tenant, oldest month first, then the most days late, each
as `{ type, id, status, billingMonth, contractId, clientName, agentId,
agentName, currency, total, daysLate }`. Under open question 4's
recommendation, it lists invoices that exist as rows. Its query is "billing
month's close has passed and status is not final", one per type. It reads
without creating anything.

The Review Queue keeps its membership (Client Invoices `SENT`, Agent Invoices
`SENT` or `APPROVED`) and its order. Its rows gain the flag. An `APPROVED`
Agent Invoice waiting to be marked paid is never Late, because it is approved.

### The Agent's earlier-months list

`GET /api/client-invoices/sent-back` (Agent only, `send-a-client-invoice-back`)
is widened into `GET /api/client-invoices/still-open`. It returns the caller's
Client Invoices of a month before the current one that are open: sent back,
of last month before its close, or Late. Each row is the existing sent-back row
plus `status` and `lastOpenDay`. The old route stays as an alias for one
release and returns only the sent-back subset, so the existing frontend and
tests keep working until the frontend ticket moves over. A matching
`GET /api/agent-invoices/still-open` (Agent only) returns the caller's own
Agent Invoices of earlier months that are open, at most a handful, as
`{ id, billingMonth, status, lastOpenDay }`.

### Frontend

- **Agent, Client Invoices page.** The "Sent back to you" section becomes
  "Earlier months, still open". It shows the same table with one more column
  for status (badge: Draft, Sent back, Sent, Approved) and "Can be changed
  until {date}". The section is hidden when empty, as today.
  `AgentClientInvoiceCard` shows the line editor and Attach file whenever
  `open` is true, not only on drafts, and shows Send only on an open draft. A
  closed invoice shows "{Month} is closed. Corrections are now made by the
  Manager." in place of the existing "closed" note (which pays tech-debt
  frontend: Dead end for an old draft).
- **Agent, own invoice page.** An "Earlier months, still open" strip lists open
  Agent Invoices of earlier months, and each opens a by-id page that renders
  the existing invoice view with Send on an open draft.
- **Manager, Client Invoice detail.** `EditClientInvoiceLineControl` (already
  taking an invoice id) is mounted on the detail view whenever `open`. An
  "Open until {date}" or "Closed" line sits under the timestamps, and a
  "Changed after approval on {date}" note is shown when set. Send back follows
  the table above.
- **Manager, Agent Invoice detail.** The override form shows when `SENT`, or
  `APPROVED` and `open`, with the same open, closed and changed-after-approval
  lines.
- **Manager, invoices page.** Above the Review Queue, a "Late" section lists
  `GET /api/late-invoices`, each row reading "{Month} · {not sent | sent |
  sent back} · {n} days late" with Open. It is hidden when empty. Queue rows
  that are Late carry a **Late** badge.
- **Tester.** An invoice whose `open` is true carries a quiet line "Can still
  change until {date}" (open question 2's recommendation). Nothing else
  changes.
- **`lib/status.ts`** gains the Late badge (in the `danger` tone) and the
  open and closed labels, driven centrally, as "Sent back" was.
- **BFF.** Pass-through proxies for the new routes, in the existing shape.

### ADR plan

A new **ADR (the next free number in `docs/adr/` when it merges; no number is reserved), "A month's invoices stay open until the 5th of the following
month"**, recorded with the first behaviour ticket. It records:

- the rule above (open, closed, Late, computed on read, one close moment for
  every Tenant);
- why ADR 0001's reason is narrowed, not dropped: a figure can move only while
  its month is open, only by the Agent or a Manager, always audited, and an
  approved invoice that moves says so. From the close, a figure never moves
  again, and only an adjustment corrects it;
- the amended pay table;
- the alternatives rejected: a scheduled close job (it can fail or run late,
  and it would need a stored status); a stored `CLOSED` status (that is a
  migration of both status checks for something the clock already knows);
  sending an edited approved invoice back to review (the human said it stays
  editable).

Dated notes, each in the existing "Amended by the month-close ADR: …" shape:

- **ADR 0001**: from `sent` onward a figure may still move until the month
  close, by an edit of the Agent or a Manager. The freeze now holds from the
  close.
- **ADR 0003**: the override is allowed while `SENT`, or `APPROVED` and open,
  and Local Support Fees follow a Client Invoice edit while `SENT` or
  `APPROVED` and open. Its rejected alternative "override at `APPROVED`" is
  reversed until the close.
- **ADR 0004**: the pay table is replaced by the month-close ADR's, and `editLine` applies
  to every open invoice, not only drafts.
- **ADR 0005**: "`approved` stays terminal" becomes "an approved invoice is
  editable until its month close". If open question 5 is answered yes, the note
  adds the `APPROVED → DRAFT` send-back while open.

### Untouched, and why that is correct

- **Fees** still land in the month they are logged.
- **The status enums and their check constraints.** Open and closed are not
  statuses.
- **Review Queue membership, order and totals.** A total moves when a sent
  invoice is edited, because the queue sums the stored lines and the snapshot,
  as it always did.
- **Mark paid** is allowed whenever the invoice is `APPROVED`, as today. Under
  open question 1's recommendation it also ends any pay movement for that
  invoice.

## Design direction

Five surfaces, all **Operate**, built from shipped patterns. `DESIGN.md` does
not change.

- **Agent Client Invoices page, "Earlier months, still open".** The existing
  sent-back Card and Table, one column more, with `TableScroll` on narrow
  viewports.
- **Agent invoice card and Manager detail views.** The line editor of
  `edit-client-invoice-lines` is reused unchanged. The "Open until" and
  "Closed" lines use the muted metadata style under the timestamps. "Changed
  after approval" uses the `warning` tone note that the sent-back notice uses.
- **Manager Late section.** A Card and Table like the Review Queue. The
  **Late** badge is a `danger` pill from `lib/status.ts`. Open is a row action
  in the soft-indigo row tone. Approve stays the single pill on the detail
  page.
- **Tester invoice view.** One muted line. No badge.

Visual goldens: none are added. Every new section is hidden when empty, so the
backend-free goldens should not move. Any golden that moves is a finding.

## Constraints

- Lands after `send-a-client-invoice-back` (merged). Lands before
  `invoice-adjustment`, which uses `MonthClose.closed`.
- The month close is the start of the 6th of the month after the billing
  month, in one zone for the whole deployment (UTC unless open question 3 says
  otherwise). "Until the 5th" includes the whole of the 5th.
- Open, closed, Late and days late are computed from the injected `Clock` on
  every read. No scheduler, no `@Scheduled`, and no stored close state.
- One additive migration (next free version at merge): nullable
  `changed_after_approval_at timestamptz` on `client_invoices` and on
  `agent_invoices`. No data rewritten, no constraint changed.
- Every month-gated write re-reads the invoice under its existing row lock and
  then checks `open`. A closed invoice is never written by an edit, an attach,
  an override, a send, a send-back or a pay movement.
- A closed invoice is refused with `409` and code `MONTH_CLOSED`, the same
  coded-body shape as `PAST_MONTH_DRAFT_NOT_SENDABLE`.
- From `SENT` onward a Client Invoice still reads exactly its stored lines.
  Nothing is recomputed by an edit, by the close or by a read.
- A `PAID` Agent Invoice's figures never move.
- The pay movement uses the lock order Client Invoice, then Agent, then Agent
  Invoice. No Agent Invoice operation takes a Client Invoice lock.
- A Tester response never carries `late`, `daysLate`,
  `changedAfterApprovalAt`, edit markers or the send-back reason.
- The `demo`-profile clock override is ignored under every other profile.
- Backend tests run under `IntegrationTest` (singleton Testcontainers Postgres,
  each method rolled back). No repository mocks. Committing race tests clean
  up their own rows.
- e2e and visual runs use the isolated stack, on an idle machine
  (`docs/agents/implementer-notes.md`). A failure is judged against a
  controlled comparison, never called a flake.
- Maven runs under JDK 21, and Checkstyle stays clean.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, the audit line, the
rendered accessibility tree. They never assert repository calls or the
internals of `MonthClose`.

1. **The time seam: one settable `Clock` in `IntegrationTest` (new, the only
   new seam).** The test configuration provides a primary `Clock` that a test
   can set to an instant and that is reset before each method. Every boundary
   case is played by setting it: `2026-10-05T23:59:59Z` is open,
   `2026-10-06T00:00:00Z` is closed. The prior art it replaces for these
   cases is shifting `billingMonth` on the entity
   (`ClientInvoiceSendBackApiTest`, `AgentInvoiceByIdApiTest`). That still
   works for "long closed" fixtures, and those tests stay unedited.
2. **Backend: the HTTP API seam (primary; existing).** A new
   `MonthCloseApiTest`, with prior art `ClientInvoiceSendBackApiTest`,
   `ClientInvoiceLineEditApiTest`, `AgentInvoiceByIdApiTest` and
   `LocalSupportFeesFollowClientInvoiceApiTest`, covers:
   - for each of draft, sent and approved: the Agent and a Manager edit a line
     of a September invoice on 5 October at 23:59:59 (`200`, status
     unchanged), and the same edit on 6 October at 00:00 is `409
     MONTH_CLOSED` for an approved one and `200` for a draft or a sent one
     (Late), with the invoice unchanged on refusal;
   - attach on a sent and an approved invoice while open (`201`), and after
     the close on an approved one (`409`);
   - a September draft never sent, on 8 October: read by id shows `late`
     true and `daysLate` 3, the Agent edits it and sends it (`200`), and the
     Manager approves it. Then `late` is false, `open` is false, and every
     edit is `409`;
   - pay: with September's Agent Invoice `SENT`, then `APPROVED` (both before
     the close), a +$15.00 Client Invoice edit moves Local Support Fees by
     +$15.00 each time with one audit line, and the approved one shows
     `changedAfterApprovalAt`. With it `PAID`, or `APPROVED` after the close
     (a Late Client Invoice edited on 8 October), it does not move, and the
     Client Invoice edit is still `200`;
   - override on an `APPROVED` Agent Invoice while open (`200`), after the
     close (`409 MONTH_CLOSED`), and on `PAID` (`409`);
   - the Agent reads and sends their own Late Agent Invoice by id; another
     Agent gets `403`; `OtherTenantFixture` gets `404` on every new route;
   - `GET /api/late-invoices`: lists one Late invoice of each type with
     `daysLate`, excludes approved and current-month invoices, gives `403` to
     an Agent or a Tester, and is empty in the open window;
   - the Review Queue's Late flag on a `SENT` invoice after the close, and the
     queue's membership and order unchanged;
   - `still-open`: last month's sent invoice is listed on the 5th and gone on
     the 6th once approved; a Late one stays; the sent-back alias returns only
     sent-back rows;
   - a Tester's read carries `lastOpenDay` and `open`, with `late`,
     `daysLate` and `changedAfterApprovalAt` null;
   - a send-back of an approved invoice while open, and after the close (only
     if open question 5 is yes).
3. **Races at the same seam, committing.** These are an edit against an
   approve of the same invoice, and a Client Invoice edit against an Agent
   Invoice approve. Exactly one order wins, and the pay equals the billed
   lines in both orders. Prior art: `LocalSupportFeesRaceTest` (and its
   sleep-as-proof debt: poll `pg_locks` instead, if that test is touched).
4. **`MonthClose` itself has no unit test.** The HTTP seam proves every edge,
   including the 6th = 1 day late count and a February billing month (close
   on 6 March) (Backend rule 6).
5. **The clock prefactor is proven by every existing suite passing unedited.**
   The current month, Fee months and standing-amount months are unchanged
   under a real clock.
6. **Frontend: component tests (Vitest, existing seam).** Prior art:
   `client-invoices-view.test.tsx`, `client-invoice-detail-view.test.tsx`,
   `edit-client-invoice-line-control.test.tsx`. They cover the editor and
   Attach on an open sent or approved card, read-only with the closed note
   when closed, the earlier-months section hidden when empty, the Manager
   editor and the changed-after-approval note, the Late section and badge,
   and the Tester's "Can still change" line.
7. **Frontend: one e2e spec, `month-closes-on-the-fifth.spec.ts` (existing
   seam).** It plays only states that hold on any real date. The current month
   is always open: the Agent sends, the Manager approves, the Agent edits a
   line of the approved invoice, the Manager sees "Changed after approval" and
   the Agent's pay moved. A month two back is always closed: one invoice is
   rolled two months back by SQL (`frontend/tests/e2e/helpers.ts` prior art,
   isolated database only), approved, and is refused. A second one, left
   sent, shows as Late to the Manager. The open window of last month is not
   played in e2e, because it depends on today's date. The API seam and the
   walkthrough's fixed-clock steps cover it.

## Decisions taken

### Settled by the human (2026-10-08)

- Editable by the Agent and the Manager until the 5th of the following month,
  whether draft, sent or approved. The Manager can still send back.
- An Agent's edit until the 5th moves that month's pay. Nothing is carried
  over automatically.
- From the 6th the month is closed for everyone. Every fix after that is the
  Manager's adjustment, on the current month's invoice.
- An invoice not approved on the 6th stays open, is flagged "Late" to the
  Manager, and closes on approval. The same applies to the Agent Invoice.

### Taken alone

- **"The Agent edits their pay invoice" means their Client Invoice lines,
  which feed Local Support Fees.** Salary and Rollout Advance are standing
  amounts the Manager sets, and no Agent-facing Agent Invoice edit exists. The
  Manager's "edit" of an Agent Invoice is the existing override.
- **An edited approved invoice stays approved and gains a "Changed after
  approval" note for the Manager.** The human said it "stays editable", not
  "returns to review". The note keeps the spirit of ADR 0001 (a reviewed
  figure never moves unnoticed) at the cost of one nullable column. It is
  visible, but additive and cheap to remove. Veto it if you don't want it.
- **Open, closed and Late are computed on read from an injected `Clock`. No
  scheduler and no stored status.** A job can fail or run late, and the clock
  already knows. Late clears itself on approval with no write.
- **An injected `Clock`, replacing the seven inline `LocalDate.now(UTC)` calls,
  is the first ticket.** Testing "the 6th" needs it, and it pays a recorded
  debt.
- **The test seam is a settable `Clock` in `IntegrationTest`.** It is the
  highest seam that can play the boundary. Shifting `billingMonth`, the
  existing prior art, cannot express "5 October 23:59".
- **A `demo`-profile clock override exists for the walkthrough, ignored
  elsewhere.** It is the only way for a person to see the open window of
  last month on a real stack on any date.
- **The month close is checked after the row lock is taken.** An edit that
  waited past midnight is refused, so no closed invoice is ever written.
- **Approve is never month-gated.** Approving is what closes a Late invoice.
- **Attach is allowed on an open sent or approved Client Invoice.** It is part
  of "editable", and a carrier bill often arrives after the send.
- **The by-id line-edit route opens to Managers.** "The Manager can edit" has
  no other home, and the editor already takes an invoice id.
- **Days late counts from the 5th** (the 6th is 1 day late, the 10th is 5).
  This matches the human's example wording, and a day off-by-one is one
  constant to change.
- **Late invoices not yet sent are listed in their own "Late" section above
  the Review Queue, not inside it.** The queue means "waiting on you", and a
  Late draft is waiting on the Agent. Late `sent` rows carry the flag in the
  queue.
- **The Late flag is shown only to the Manager.** It is what the human asked
  for. The Agent sees the invoice in their earlier-months list.
- **The Agent's sent-back list widens into "Earlier months, still open", and
  the old route stays as an alias for one release.** It is one list for every
  invoice the Agent can still act on, and the alias keeps existing tests
  green until the frontend ticket moves.
- **The Agent reaches their own Agent Invoice by id (read, and send while
  open).** A Late Agent Invoice of an earlier month has no other address. This
  follows `send-a-client-invoice-back`'s per-route opening.
- **A Late draft never sent is pre-filled by the existing computation.** The
  Agent edits every line anyway (the human's answer of 2026-10-01).
- **`PAST_MONTH_DRAFT_NOT_SENDABLE` is retired in favour of
  `MONTH_CLOSED`.** The case it guarded no longer exists. The enum value stays
  one release for clients that read it.
- **One new the month-close ADR with dated notes on ADR 0001, 0003, 0004 and 0005.** The
  decision is hard to reverse once real invoices change after approval,
  surprising without context, and a real trade-off against ADR 0001.
- **A sent or approved invoice still shows no late-Fee lines.** ADR 0004's
  read rule is untouched, and the Fee still reaches pay at its logged amount.
- **Testing: one new seam (the clock) plus the existing HTTP, component and e2e
  seams, with e2e restricted to dates that hold on any day.** An e2e that
  depends on today's date would fail five days a month.

- **"Until the 5th" means UTC**: September closes at 00:00 UTC on 6 October — settled by the code, since every billing month is already a UTC month (`BillingMonth`, `LocalDate.now(ZoneOffset.UTC)`); one deadline everywhere. Moved from open questions by the orchestrator, 2026-10-08.
- **Late flags only invoices that exist** — the human's rule speaks of "an invoice still not approved"; a Contract whose Agent never opened that month's invoice has no invoice, and Contracts have no end date, so flagging them would flag inactive ones forever. Moved from open questions by the orchestrator, 2026-10-08.
- **Until the 5th a Manager may send back an approved Client Invoice** — the human said on 2026-10-08 that until the 5th "manager can send back too" and that an approved invoice stays editable; it returns to draft with the reason and is Late if not re-approved by the 6th. Client Invoices only. Moved from open questions by the orchestrator, 2026-10-08.

## Open questions

1. **When an edit reaches pay that is already final, does the pay move?**
   Two cases. (a) You mark September's Agent Invoice paid on 3 October, and on
   4 October the Agent raises a September Client Invoice line from $30 to
   $45. (b) September's Client Invoice is Late, the Agent's September pay was
   approved before the 6th, and on 10 October the Agent raises a line from
   $30 to $45. **Recommendation:** the Client's bill changes to $45, but the
   Agent's September pay stays as it is. If the $15 is owed, you record it as
   an adjustment on October's pay. A paid Agent Invoice counts as closed at
   once. *Reason:* money already paid cannot move, and after the 5th every
   pay fix is yours by adjustment. The alternative is to refuse "mark paid"
   until the 6th.
2. **Does the Client's Tester see an invoice's figures change until the 5th?**
   Today a Tester sees an invoice as soon as it is sent. Under the new rule,
   the Agent can change a $1,200 invoice to $1,250 on 4 October, after the
   Client has seen $1,200. **Recommendation:** yes. The Tester sees the
   figures as they stand, with the line "Can still change until 5 October"
   while the month is open. *Reason:* the Tester's Invoices page shows only
   the current month, which is never closed, so hiding open invoices would
   leave that page empty for good. The note removes the surprise.

## Acceptance walkthrough

Steps 1–10 run on the `demo` stack with the clock fixed by the `demo`-profile
override, unless they say otherwise. Today's real date (8 October 2026) is
used in steps 11–13.

1. [agent] Fix the demo clock to `2026-11-03T12:00:00Z`. As an Agent, `PUT /api/client-invoices/{id}/lines` on an October Client Invoice that is `SENT`, raising a line by $15.00. Show `200`, status still `SENT`, the new amount, `open: true`, `lastOpenDay: 2026-11-05`, and one `CLIENT_INVOICE_LINE_EDITED` audit line. Show the Agent's October Agent Invoice (`SENT`) Local Support Fees up by exactly $15.00, with one `AGENT_INVOICE_LOCAL_SUPPORT_FEES_FOLLOWED` line. (stories: 1, 3, 4, 31)
2. [agent] As the Manager, approve that Client Invoice and the October Agent Invoice. As the Agent, edit the same line by another $10.00, and attach a file. Show `200` both times, status still `APPROVED`, `changedAfterApprovalAt` set on both invoices, and the Agent Invoice's Local Support Fees up by exactly $10.00. (stories: 2, 3, 4, 5, 22)
3. [agent] As the Manager, edit a line of an October draft and of an October approved Client Invoice by id, and override Salary on the approved October Agent Invoice. Show `200` each time, the pay moved by the difference for the line edits, and `changedAfterApprovalAt` set. (stories: 12, 13, 14, 22)
4. [agent] Restart with the clock at `2026-11-05T23:59:59Z` and show one more edit accepted. Restart with `2026-11-06T00:00:00Z` and show the same edit, an attach and an override on the approved invoices all refused `409` with code `MONTH_CLOSED` and the message naming the adjustment. Show both invoices unchanged. Then run `MonthCloseApiTest`'s boundary cases and show them green. (stories: 23, 24, 30)
5. [agent] Still at `2026-11-06T00:00:00Z`, show an October Client Invoice left `SENT` and an October Agent Invoice left `DRAFT` both read `late: true`, `daysLate: 1`, `open: true`. Show `GET /api/late-invoices` lists both, and the Review Queue shows the sent one flagged Late with its order unchanged. As the Agent, edit the Late Client Invoice (`200`). (stories: 15, 18, 19)
6. [agent] Move the clock to `2026-11-10T09:00:00Z`. Show `daysLate: 5`. As the Agent, open the Late Agent Invoice by id and send it. As the Manager, approve both. Show `late: false`, `open: false`, both gone from the Late list, and every edit now `409 MONTH_CLOSED`. (stories: 11, 17, 20)
7. [agent] With the Agent's October pay approved and closed and a second October Client Invoice still Late, edit that invoice's line +$15.00 as the Agent. Show the edit `200` and the Agent Invoice unchanged. Mark another Agent's approved, still-open Agent Invoice paid, edit one of its Client Invoice lines, and show the Agent Invoice unchanged. (stories: 27)
8. [agent] In a browser at the clock `2026-11-03`, sign in as the Agent. Show "Earlier months, still open" listing October's sent and approved invoices with "Can be changed until 5 November". Open one, show the line editor and Attach file on a sent invoice and on an approved one, change a line, and show the new amount. Open the Agent's October Agent Invoice from the earlier-months strip and show the moved Local Support Fees. (stories: 6, 7, 10)
9. [agent] In a browser at `2026-11-10`, as the Agent, show a closed approved October invoice read-only with the note "October 2026 is closed. Corrections are now made by the Manager.". Open a Late never-sent October draft, edit a line and send it. (stories: 8, 9)
10. [agent] In a browser as the Manager, at `2026-11-10`, show the Late section reading "October 2026 · not sent · 5 days late" with Open, and the Late badge on a sent row in the Review Queue. Open a Late Client Invoice, show "Open until" replaced by Late wording and the line editor present, edit a line, and approve it. Show it leave the Late section. Open an approved invoice that was changed and show "Changed after approval on …". (stories: 12, 18, 19, 20, 21, 22)
11. [agent] On the real date (8 October 2026), demo seed: as a Tester, show a sent current-month invoice with "Can still change until 5 November", and no edit markers, `late` or `changedAfterApprovalAt` in the response. Try `PUT …/lines` as the Tester and show `403`. (stories: 25, 26)
12. [agent] As another Agent, read, edit and send the first Agent's invoices by id and show `403`. On `OtherTenantFixture`'s invoices, show `404` on every new route. Show `GET /api/late-invoices` gives `403` to an Agent and a Tester. (stories: 32)
13. [agent] With the clock override unset, run `mvn verify`, the vitest suite, typecheck, lint, the full isolated e2e suite including `month-closes-on-the-fifth.spec.ts`, and the visual suite. All are green, with existing suites unedited apart from those this spec names, and no golden moved. Show the backend logs no clock-override warning. (stories: 29, 33)
14. [agent] Do the Agent's edit of an approved invoice and the Manager's approve of a Late invoice by keyboard alone, with a visible focus ring at each stop. Repeat at the mobile breakpoint, and show the earlier-months table, the Late section and the line editor usable within the viewport. (stories: 34)
15. [agent] If open question 5 is answered yes, at `2026-11-03`, send back an approved October Client Invoice as the Manager. Show `DRAFT`, the reason, `approvedAt` cleared, and the Agent Invoice unchanged. Then at `2026-11-06` show the same send-back refused on a closed one. (stories: 16)
16. [human] On a real month: in the first days of the next month, change a line on an invoice you already approved, then check the Agent's pay for that month moved by the same amount and the invoice says "Changed after approval". Confirm that is how you want late corrections to look. (stories: 2, 4, 12, 22)
17. [human] On the 6th, open the Late list and confirm the invoices it flags, and the "not sent · N days late" wording, are the ones you would chase. Confirm an approved invoice of last month now refuses changes and points you to an adjustment. (stories: 18, 23, 28)
18. [human] Read the month-close ADR and the notes on ADR 0001, 0003, 0004 and 0005. Confirm they say what you settled: editable by the Agent and the Manager until the 5th whatever the status; an edit moves that month's pay; closed for everyone from the 6th; Late stays open and closes on approval; and paid pay never moves. (stories: 1, 2, 4, 23, 27)

## Execution order

Depends on `send-a-client-invoice-back`, merged. The ticket writer cuts the
final slices. This is the intended order:

1. `inject-a-clock-for-the-current-date`. Labels: `backend`, `enabler`. The
   `Clock` bean, `BillingMonth.current(clock)` at every inline call site, the
   settable test clock in `IntegrationTest`, and the `demo`-profile override.
   No behaviour change.
2. `client-invoices-stay-open-until-the-fifth`. Labels: `backend`. `MonthClose`,
   the open predicate replacing "open to its Agent" for edit, attach and send,
   `MONTH_CLOSED`, the Manager on the by-id line edit, `changedAfterApprovalAt`
   (the migration), the response fields, the month-close ADR and the notes on 0001, 0004
   and 0005. Depends on 1.
3. `agent-pay-follows-edits-until-the-fifth`. Labels: `backend`. The pay table
   (`APPROVED` while open moves; closed or `PAID` does not), the override on
   `APPROVED` while open, the Agent's by-id read and send of their own Agent
   Invoice, the note on ADR 0003, and the race tests. Depends on 2.
4. `late-invoices-flagged-to-the-manager`. Labels: `backend`.
   `GET /api/late-invoices`, and the Review Queue flag. Depends on 2 and 3.
5. `agent-lists-earlier-months-still-open`. Labels: `backend`. The `still-open`
   lists for both types, and the sent-back alias. Depends on 2 and 3.
6. `agent-edits-open-sent-and-approved-invoices`. Labels: `frontend`. The
   card's editor and attach while open, the closed note, the
   "Earlier months, still open" section and the Agent Invoice strip and by-id
   page. Depends on 5.
7. `manager-edits-and-sees-month-close-on-invoice-pages`. Labels: `frontend`.
   The Manager line editor, the open/closed/changed-after-approval lines, and
   the override on approved. Depends on 3.
8. `manager-sees-late-invoices`. Labels: `frontend`. The Late section and the
   queue badge, and carries the e2e spec. Depends on 4, 6 and 7.
9. `tester-sees-an-invoice-can-still-change`. Labels: `frontend`. Depends on 2.
   Its shape follows open question 2.
10. `manager-sends-back-an-approved-client-invoice`. Labels: `backend`,
    `frontend`. Depends on 2 and 7. Only if open question 5 is answered yes.
