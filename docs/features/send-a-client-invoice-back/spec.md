---
feature: send-a-client-invoice-back
epic: invoice-correction-and-history
status: draft
date: 2026-09-30
---

<!-- sdlc:template spec 1 -->

# Send a Client Invoice back

## Problem

A Company Manager who opens a sent Client Invoice from the Review Queue can do
only two things with it: approve it, or leave it. If something is missing,
such as a Carrier Invoice File or a Fee the Agent logged late in that month,
there is no way to hand it back to the Agent and say so. The incomplete
invoice gets approved and becomes the final record for the month, or it sits
in the Review Queue and nobody is told why.

Send-back is for errors caught **before approval**, that the Agent can fix by
adding what was missing. An amount already billed that is wrong is corrected
by the Manager on the following month's invoice (`invoice-adjustment`); a past
month is never reopened to edit or void a Fee.

The Client Invoice lifecycle only moves forward today
(`ClientInvoiceStatus.canTransitionTo`: `DRAFT → SENT → APPROVED`). ADR 0001
freezes the numbers at send so that a figure a Manager has reviewed can never
move quietly. `manager-invoice-review-queue` left "send back to Agent" out of
scope on purpose (its Non-goals).

There is a second gap behind the first. An Agent reaches a Client Invoice only
as "this Contract's invoice for the current month". A Manager usually reviews
last month's invoice this month. So an invoice the Manager sent back would
have no page where its Agent could find it.

## Journeys

Advances `docs/roadmap/invoice-correction-and-history.md`, the **first** of its
three features.

- **Send an invoice back for correction** (`wanted`, stays `wanted`). This
  feature delivers the Client Invoice half of the epic's closing proof: "a
  Manager sends a Client Invoice back with a reason; the Agent sees the reason,
  and the invoice as a draft again, adds what was missing (a file, a late Fee
  of that month), resends it with a fresh snapshot, and the Manager approves
  it". The journey reaches `exists` only when `send-an-agent-invoice-back` and
  `invoice-adjustment` land too, so `docs/journeys.md` is not edited here.
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
  Dashboard's Pending approvals card. Its Fee lines are computed live again.
  Its base amount is live again only while its billing month is the current
  month; for a past month it stays as sent, because today's Fleet is not that
  month's Fleet.
- The Agent sees every Client Invoice sent back to them, from any billing
  month, together with the reason and when it was sent back. The Agent can
  open such an invoice by its own identity, attach Carrier Invoice Files to it
  and resend it.
- A resend freezes a fresh snapshot, taken from what the invoice shows at that
  moment. Nothing is left over from the earlier send. The invoice goes back
  into the Review Queue, and the Manager approves it or sends it back again.
- ADR 0001 is amended to add the one backward edge, `SENT → DRAFT`, to say
  why the freeze still protects every figure a Manager is looking at, and to
  state that a past month's base amount stays frozen through a send-back.

**Non-goals.** Each of these is something a reasonable agent would otherwise
build.

- **No Agent Invoice change.** `send-an-agent-invoice-back` is its own feature.
  Its snapshot is four scalar columns, not a membership table, and whether an
  *approved* Agent Invoice may be sent back is still undecided. ADR 0003 is not
  edited here. The pieces this feature builds so that the sibling can reuse
  them are named under `## Decisions taken`.
- **No sending back an approved Client Invoice.** `approved` stays final and
  immutable (ADR 0001). Only `sent → draft` is added.
- **No editing, voiding or back-dating a Fee, now or later.** A Fee stays
  immutable, and its `billingMonth` is still set from when it was logged. A
  past month is never reopened to correct a Fee (human, 2026-09-30); there is
  no `correct-a-fee` feature.
- **No adjustment, credit or negative amount.** A wrong amount found after
  sending or approval is corrected by the Manager on the next month's invoice,
  which is `invoice-adjustment`, a separate feature of this epic.
- **No recomputing a past month's base amount.** SIM membership of a Contract
  carries no dates, so there is no Fleet history to recompute it from.
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
- **No removal of the current-month send and file-attach routes,** even though
  the Agent's UI stops calling them (see `## Decisions taken`).
- **No change to how the Review Queue selects, orders or totals invoices.**

## User stories

1. As a Company Manager, I want to send a sent Client Invoice back to its Agent from the invoice's detail page, so that an incomplete invoice is completed instead of approved or left waiting.
2. As a Company Manager, I want to be required to give a reason when I send an invoice back, so that the Agent knows what is wrong and what to add.
3. As a Company Manager who submits an empty reason, I want to be told inline and to keep the form open, so that I fix it rather than start again.
4. As a Company Manager, I want the detail page to show the invoice as a draft right after I send it back, with my reason and no actions left, so that I can see the send-back took effect.
5. As a Company Manager, I want a sent-back invoice to leave the Review Queue and the Dashboard's Pending approvals card, so that the queue shows only what is waiting on me.
6. As a Company Manager, I want to send back a Client Invoice of any billing month, so that last month's invoice can be completed before approval just like this month's.
7. As a Company Manager, I want no send-back action on a draft or an approved invoice, so that an approved invoice stays the final record.
8. As a Company Manager whose page is stale (another Manager already approved or sent it back), I want a clear message telling me to refresh, so that I do not assume my action worked.
9. As a Company Manager, I want a resent invoice to come back into the Review Queue, waiting since it was resent, so that I review the completed version.
10. As a Company Manager reviewing a resent invoice, I want to see that it was sent back before, when, and the reason I gave, so that I can check what was added against my request.
11. As a Company Manager, I want to approve a resent invoice exactly as I approve any sent one, so that a send-back ends in the normal final record.
12. As a Company Manager, I want to send the same invoice back again if it is still incomplete, so that there is no limit on getting it right.
13. As an Agent, I want every Client Invoice sent back to me, from any Contract and any billing month, listed on my Client Invoices page, so that I never miss one.
14. As an Agent, I want each sent-back invoice to show the Manager's reason and when it was sent back, so that I know what is wrong and what to add.
15. As an Agent, I want to open a sent-back invoice of a past billing month on its own page, so that I can work on it even though it is not this month's.
16. As an Agent, I want a sent-back invoice to show its Fee lines live again, and for the current month its base amount too, exactly as a draft does, so that what I add shows up before I resend.
17. As an Agent, I want to attach more Carrier Invoice Files to a sent-back invoice, so that a missing carrier bill can be supplied.
18. As an Agent, I want to resend a sent-back invoice, freezing what I now see, so that the Manager reviews the completed invoice.
19. As an Agent, I want the send confirmation to tell me the truth, which is that only a Manager can send an invoice back, instead of "this can't be undone", so that I am not misled.
20. As an Agent, I want a resent invoice to leave my sent-back list and show as sent, so that my list shows only what still needs me.
21. As an Agent, I want my current month's invoice card to show the reason when that invoice was sent back, so that I see it where I build the invoice.
22. As an Agent, I want to reach only my own Contracts' invoices by id, so that I cannot read or send another Agent's invoice.
23. As a Tester, I want a sent-back invoice to disappear from my Client's view until it is resent, and never to see the Manager's reason, so that I see only statements the Agent stands behind.
24. As a Tester, I want the resent invoice's new numbers and PDF once it is sent again, so that what I see matches what the Manager reviews.
25. As the company, I want a resend to freeze exactly the Fee lines and base amount the invoice shows at that moment, with none left over from the earlier send and no failure, so that the frozen figures are the ones the Agent saw.
26. As the company, I want a send-back and an approval that race each other to leave the invoice in one consistent state, never approved with its snapshot cleared, so that the final record is always complete.
27. As the company, I want every send-back recorded as an audit line (who, which invoice, from sent to draft), without the reason's text, so that the event can be traced.
28. As an Agent or a Tester, I want to be unable to send an invoice back, so that it stays the Manager's power.
29. As a Manager of one Tenant, I want every send-back or by-id read of another Tenant's invoice refused as if it did not exist, so that the Tenant boundary holds.
30. As an Agent, I want my Agent Invoice unaffected when one of my Client Invoices is sent back, so that my reimbursement does not wait on the Client paperwork (ADR 0002).
31. As everyone already using the product, I want sending, approving, the Review Queue, the Tester's view and the PDF to behave exactly as before for invoices never sent back, so that this change carries no release risk.
32. As a Manager or an Agent working by keyboard or on a phone, I want the send-back form and the sent-back list usable without a mouse and at the mobile breakpoint, so that the action is available wherever I work.
33. As a Manager or an Agent looking at a sent-back invoice of a past billing month, I want its base amount kept as it was sent and marked as such, so that nobody expects it to change and a wrong base amount is left to the Manager's next-month adjustment.
34. As the company, I want an invoice sent back in its own month but resent after that month has ended to keep the base amount it was first sent with, so that a month rolling over never bills a past month from today's Fleet.

## Solution

### The lifecycle, amended

```
DRAFT ──send (Agent)──▶ SENT ──approve (Manager)──▶ APPROVED
  ▲                       │
  └──send back (Manager)──┘   reason required
```

`ClientInvoiceStatus.canTransitionTo` gains `SENT → DRAFT`. `APPROVED` stays
terminal. An invoice is **sent back** when it is `DRAFT` and carries a
`sentBackAt`. That can only be true after a send-back, because a
get-or-created draft never has one.

The reads need no change for the Fee lines. `toResponse` already decides
"frozen" as `status != DRAFT`, so a sent-back invoice serves live Fee lines,
and they include any Fee logged against that Contract and month after the
first send. The base amount gets one new rule, under "The base amount of a
past month" below.

### ADR

**ADR 0004, "A Manager may send an invoice back to draft".** It records the
backward edge, and why ADR 0001's reason still holds: the freeze protects
every figure while a Manager is reviewing it (`sent`) or has approved it
(`approved`). A send-back is the Manager deliberately handing that review back,
and the resend is a new review of new frozen numbers. It states precisely what
goes live again:

- the Fee lines, always, whatever the billing month;
- the base amount, only while the invoice's billing month is the current
  month. For a past billing month the base amount stays exactly as it was
  sent, through the send-back and the resend, because SIM membership carries
  no dates and today's Fleet is not that month's Fleet;
- nothing else: a past month is never reopened to edit or void a Fee, and an
  amount found wrong after sending or approval is corrected by the Manager on
  the following month's invoice (`invoice-adjustment`).

ADR 0004 names the Client Invoice as implemented now and leaves the Agent
Invoice to `send-an-agent-invoice-back`, which will add its half and put the
matching note on ADR 0003. ADR 0001 gets a dated note, in the shape ADR 0003's
2026-09-17 note already set: "Amended by ADR 0004: `sent → draft` by a
Manager's send-back; a past month's base amount stays frozen; `approved` stays
terminal."

### Prefactoring: one send, in the service, in one transaction

Sending lives in `ClientInvoiceController` today: the transition check, the
snapshot, and the status and `sentAt` writes. It is not transactional. Each
repository call commits on its own, so a failure partway through would leave
some snapshot rows written for an invoice still in `DRAFT`. This feature adds
a second way to send (by id, below). So the send moves into
`ClientInvoiceService` as one `@Transactional` operation, taking an
already-found invoice. The current-month route calls it after its
get-or-create, and the new by-id route calls it after a lookup. The snapshot
code moves with it. Behaviour is unchanged, and `ClientInvoiceApiTest` and
`client-invoice-submission-and-visibility.spec.ts` pass unedited.

### Backend: send back

`POST /api/client-invoices/{invoiceId}/send-back`, Manager only (the existing
`/api/client-invoices/**` matcher), body `{ "reason": "…" }`.

```
200 ClientInvoiceResponse   — now DRAFT, live Fee lines, base amount per the rule below,
                               sentBackAt/sentBackReason set
400                          — reason blank, or longer than 1000 characters
404                          — no such invoice in the caller's Tenant
409                          — invoice is not SENT (draft, or already approved)
403                          — caller is not a Manager
```

`ClientInvoiceService.sendBack(invoice, reason, principal)`, `@Transactional`,
does the following:

1. Re-reads the invoice under a row lock (a locking finder on
   `ClientInvoiceRepository`). The approve operation takes the same lock, so
   an approval and a send-back that race each other run one after the other,
   and the second gets `409`.
2. Checks `canTransitionTo(DRAFT)`, or `409`.
3. Deletes every `ClientInvoiceFeeSnapshot` row of the invoice. This is
   required: `UNIQUE (client_invoice_id, fee_id)` (V13) would otherwise make
   the resend fail on every Fee already pinned.
4. Leaves `snapshotBaseAmount` in place, whatever the billing month. It is
   never cleared; whether it is served is decided by the billing month at
   read and at resend (below).
5. Sets `status = DRAFT`, clears `sentAt`, and sets `sentBackAt = now` and
   `sentBackReason`.
6. Writes `AuditLog.statusChanged("ClientInvoice", id, "SENT", "DRAFT", actor, tenant)`.
   This reuses the existing generic event, with no new audit method. The
   reason's text is never logged, following `requestRejected`'s rule. A
   reason is always required, so a `reasonGiven` flag would carry nothing.

### The base amount of a past month (answered 2026-09-30)

`ContractAmountService.baseAmount(contract, month)` reads **today's** Fleet.
It counts every currently-Active Postpaid SIM, whenever it joined, and drops
any SIM retired since without a cancellation date. For the current month that
is the right number. For a past month it is not, and nobody can correct it.
So, as the human answered:

- a sent-back invoice of the **current** billing month goes fully live: base
  amount and Fee lines;
- a sent-back invoice of a **past** billing month keeps the base amount it was
  sent with; only its Fee lines go live.

One rule in `ClientInvoiceService` implements both, evaluated against the
current month (UTC) at the moment of the read or the resend:

```
base amount is live  ⇔  status = DRAFT
                        ∧ (snapshotBaseAmount is null ∨ billingMonth = current month)
otherwise            →  snapshotBaseAmount
```

- `toResponse` serves the base amount by that rule. A never-sent draft has no
  `snapshotBaseAmount`, so it is live exactly as today. `SENT` and `APPROVED`
  serve the snapshot exactly as today.
- The resend's snapshot applies the same rule: it recomputes and overwrites
  `snapshotBaseAmount` when the base amount is live, and leaves it as it is
  otherwise.
- Because the value is never cleared, an invoice sent back in its own month
  and resent after the month has ended keeps the base amount it was first
  sent with (story 34). It does not read the new month's Fleet.

The response gains `baseAmountKept` (boolean): true when a `DRAFT` serves its
kept base amount. The draft view's per-SIM breakdown (`basePostpaidSims`) is
shown only when the base amount is live. When it is kept, the base amount line
reads "As sent — a wrong base amount is corrected by the Manager on a later
invoice" instead of the breakdown.

### Backend: the Agent reaches an invoice by its id

The by-id routes open to the Contract's own Agent. Each check goes through
`ClientInvoiceAccessGuard` against the invoice's Contract, after the
Tenant-scoped lookup:

| Route | Manager | Contract's own Agent | Other Agent / Tester |
|---|---|---|---|
| `GET /api/client-invoices/{id}` | yes | yes (any status) | 403 |
| `GET …/files`, `GET …/files/{fileId}` | yes | yes | 403 |
| `POST …/files` (new) | yes | yes, `DRAFT` only (409 otherwise) | 403 |
| `POST …/send` (new) | 403 | yes, a sent-back `DRAFT` or a current-month `DRAFT` only (409 otherwise) | 403 |
| `POST …/approve`, `POST …/send-back` | yes | 403 | 403 |
| `GET …/pdf` | yes | 403 (unchanged; the Agent's PDF stays on the current-month route) | 403 |

`SecurityConfig`'s `/api/client-invoices/**` matcher narrows from
Manager-only to Manager-or-Agent for exactly the `GET` read, the files routes
and `send`. Approve and send-back keep a Manager-only matcher, placed before
the broader one, so role enforcement does not depend on the guard alone. An
unknown or other-Tenant id is `404`. Another Agent's invoice in the same
Tenant is `403`, the same as the current-month routes' "Not your Contract".
File attach reuses the current-month route's storage and validation, which
move into `ClientInvoiceService` with the send.

The by-id send refuses a `DRAFT` that is neither sent back (no `sentBackAt`)
nor of the current billing month, that is, a past-month draft never sent. It
answers `409` with the coded body the other conflicts use,
`{ "code": "PAST_MONTH_DRAFT_NOT_SENDABLE", "message": … }`, carried by a
`Reason` enum on its conflict exception as `AgentLoginConflictException` and
`TesterConflictException` do. A non-`DRAFT` stays the plain `409` it is today.

**The sent-back list.** `GET /api/client-invoices/sent-back`, Agent only. It
returns the caller's own Contracts' sent-back invoices, oldest send-back first,
each as `{ id, contractId, clientName, country, billingMonth, currency,
sentBackAt, sentBackReason }`. It returns no amounts: they are live and belong
to the invoice's own page. A Manager or a Tester gets `403`.

### Schema and contract

- **One additive migration** (the next free version at merge; `V56` on `main`
  today): nullable `sent_back_at timestamptz` and
  `sent_back_reason varchar(1000)` on `client_invoices`. This follows
  `Request`'s distinct-reason-column prior art (`cancellation_reason`,
  `rejection_reason`), not a shared reason field. No data is rewritten.
- **`ClientInvoiceResponse`** gains `sentBackAt`, `sentBackReason` and
  `baseAmountKept`. `baseAmountKept` is false except on a `DRAFT` serving its
  kept base amount, which a Tester can never read. `sentBackAt` and
  `sentBackReason` are **null for a Tester caller**, on the current-month read and in the
  PDF, which never prints them. The reason is a note from the Manager to the
  Agent, not part of the statement.
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
    hint "Tell the Agent what is missing. They can attach files and pick up
    Fees logged during the invoice's month. They cannot change a Fee or the
    base amount of a past month."), **Confirm
    send back** and **Back**;
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
    block;
  - when `baseAmountKept`, the base amount line carries the "As sent" note
    (see the base amount section);
  - a resent `SENT` invoice shows a quiet line under its timestamps:
    "Previously sent back on {date}: {reason}";
  - Approve stays the one pill, and Send back is a secondary control at the
    8px radius.

### Frontend: Agent

- **Prefactor: the Agent's invoice card addresses the invoice by its id.**
  The card inside `AgentClientInvoicesView` is extracted into
  `AgentClientInvoiceCard(invoice)`. `SendClientInvoiceControl` and
  `AttachCarrierInvoiceFileControl` take an invoice id and call the by-id
  routes, and file downloads use the by-id route. Download PDF stays on the
  current-month route, because the Agent is refused the by-id PDF. The
  current-month page and the new by-id page render the same card.
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
  reason, then "Add what is missing, then send it again."), live Fee lines,
  the base amount live or marked "As sent" per `baseAmountKept`, Attach file
  and Send. The notice promises no edit: the Agent can add files and pick up
  Fees of that month, nothing more. After a successful send
  the page refreshes and shows the invoice as sent. Another Agent's id, or an
  unknown id, shows the not-found state.
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
  again at the resend.
- **Agent Invoice.** Local Support Fees are computed from the Contract's
  Fleet and Fees, never from a Client Invoice (ADR 0002), so a send-back moves
  nothing there.
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
  `warning` pill badge, driven centrally from `lib/status.ts`.

Visual goldens:

- **No new goldens.**
- The "Sent back to you" section renders nothing when empty, so the Agent's
  Client Invoices golden (if captured) should not move. The Manager detail
  page is backend-driven and not in the backend-free suite.
- Any golden that moves is a finding.

## Constraints

- **One additive migration** of two nullable columns. No data rewritten, and
  no check constraint changed (`DRAFT` is already allowed).
- `APPROVED` stays terminal. The only backward edge is `SENT → DRAFT`, taken
  only by a Manager.
- A send-back removes **every** `ClientInvoiceFeeSnapshot` row of that
  invoice, in the same transaction as the status change. A resend never fails
  on `UNIQUE (client_invoice_id, fee_id)`, and never serves Fee lines from
  the earlier send.
- A send-back and an approval of the same invoice are serialized by a row
  lock. No invoice may ever be `APPROVED` with its Fee snapshot cleared.
- A send-back reason is required, not blank, and at most 1000 characters. It
  never appears in a log line, the PDF, or any Tester-facing response.
- From `SENT` onward every read still serves the snapshot (ADR 0001). The
  only new live read is of a `DRAFT` that was sent back: its Fee lines always,
  its base amount only while its billing month is the current month.
- A send-back never clears `snapshotBaseAmount`. A past month's base amount is
  never recomputed from today's Fleet, by a read or by a resend.
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
   404) and `ClientInvoiceApiTest` (current-month send and live/frozen). A new
   `ClientInvoiceSendBackApiTest` covers:
   - send-back of a sent **current-month** invoice → `DRAFT`, live numbers,
     `baseAmountKept` false, reason and time returned; a Postpaid SIM added
     after the send-back moves its base amount;
   - a Fee logged after the first send appears live, and after the resend it
     appears **exactly once** in the frozen Fee lines. Before this feature,
     this resend produced a constraint violation;
   - send-back of a sent **past-month** invoice, written directly by a
     fixture in the way `DemoDataLoader.writePastClientInvoice` does:
     - its base amount stays as sent, with `baseAmountKept` true, even after
       a Postpaid SIM is added to the Fleet;
     - its Fee lines go live;
     - the Agent reads it by id, attaches a file and resends it, and the
       resent snapshot's base amount is still the one first sent;
     - this fixture is also the month-rollover case (story 34): a `DRAFT`
       with a kept base amount and a past billing month is exactly the state
       an invoice sent back in its own month is in once the month ends, so no
       clock needs faking;
   - the Review Queue excludes the invoice after a send-back and includes it
     after the resend, ordered by the new `sentAt`;
   - send-back of a draft or an approved invoice → `409`, with a blank or
     1001-character reason → `400`;
   - the `agentToken()` and `testerToken()` callers are refused a send-back
     with `403`;
   - the other Agent's token, and a Tester, are refused the by-id read, files
     and send with `403`;
   - the Agent's by-id send of a past-month `DRAFT` that was never sent
     (written by a fixture) → `409` with code
     `PAST_MONTH_DRAFT_NOT_SENDABLE`, and the invoice stays `DRAFT` with no
     snapshot; a current-month draft never sent is accepted;
   - `OtherTenantFixture`'s invoice gives `404` on every route;
   - the Agent gets the sent-back list for their own Contracts only, and a
     Manager or a Tester gets `403`;
   - a Tester's current-month read of a sent-back invoice gives `403`, and
     after the resend it succeeds with `sentBackReason` null;
   - approving after a send-back gives `409`.
2. **The race is proven at the same seam, not with mocks.** Two threads send
   back and approve the same invoice. Exactly one wins, the other gets `409`,
   and the final invoice is either `APPROVED` with its full snapshot or
   `DRAFT` with no Fee snapshot rows. This test commits real transactions, so it cannot use
   `IntegrationTest`'s rollback. It cleans up its own rows.
3. **The prefactors are proven by existing suites passing unedited:**
   `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest`,
   `client-invoice-submission-and-visibility.spec.ts` and
   `manager-invoice-review-queue.spec.ts`.
4. **Frontend: component tests (Vitest + Testing Library, existing seam).**
   Prior art: `approve-client-invoice-control.test.tsx`,
   `client-invoice-detail-view.test.tsx`, `client-invoices-view.test.tsx` and
   `pending-request-decision-controls.test.tsx`. The tests cover:
   - the send-back control: expand and Back, an empty reason blocked, `409`
     and generic copy, reason kept on failure, and the success callback;
   - the detail view: Send back only while `SENT`, the sent-back note on a
     draft, the "As sent" base amount note when `baseAmountKept`, and the
     "previously sent back" line on a resent invoice;
   - the Agent view: the section is absent when the list is empty, rows and
     their links when it is not, the **Sent back** badge, the notice's "Add
     what is missing" line, the "As sent" base amount with no per-SIM
     breakdown when `baseAmountKept`, and the new send-confirmation copy.
5. **Frontend: one e2e spec, `send-a-client-invoice-back.spec.ts`
   (existing seam).** Prior art: `client-invoice-submission-and-visibility.spec.ts`.
   It plays the journey on a Contract the spec creates itself:
   1. the Agent sends;
   2. the Manager opens the invoice from the Review Queue and sends it back
      with a reason;
   3. the queue no longer lists it;
   4. the Agent sees it under "Sent back to you" with the reason, opens it by
      id, adds what was missing (logs a Fee of the current month) and
      resends;
   5. the Manager sees it back in the queue, with the new Fee and the earlier
      reason, and approves it.

   A past month is not played in e2e, because e2e state does not roll back
   and the send path writes only the current month. The API seam covers the
   past month, and so does walkthrough step 9 on the demo data.
6. **No unit tests** of `canTransitionTo` or the service beyond the HTTP
   seam. The seam proves every edge (Backend rule 6).

## Decisions taken

### Answered by the human (2026-09-30)

- **Corrections carry forward; send-back is for errors caught before
  approval.** (Was open question 1, `question-send-back-cannot-correct-a-fee`.)
  When an invoice turns out wrong after it is sent or approved, the Manager
  corrects it on the following month's invoice, through the new
  `invoice-adjustment` feature, out of scope here. There is no
  `correct-a-fee`: a past month is never reopened to edit or void a Fee.
  Send-back ships as specified: the Agent adds what was missing (a file, a
  late Fee of that month) and resends, and the reason says what is wrong.
- **A past month's base amount stays as sent when sent back; only its Fee
  lines go live. A current-month invoice goes fully live.** (Was open question
  2, `question-sent-back-past-month-base-amount`.) Today's Fleet is not that
  month's Fleet, because SIM membership carries no dates. An error in a past
  base amount is corrected by the Manager's next-month adjustment. ADR 0004
  states this rule.

### Taken alone

- **The kept base amount is never cleared; the billing month decides, at read
  and at resend, whether it is served.** Clearing it for the current month
  only would let an invoice sent back on the last day of its month and resent
  the next day bill that month from the new month's Fleet. Keeping it and
  deciding by month covers that rollover with one rule, and makes the
  past-month fixture test the rollover case too, with no clock to fake.
- **The response carries `baseAmountKept`, and a kept base amount is marked
  "As sent".** The Agent and the Manager must see which number cannot move,
  and the note sends a wrong base amount to the Manager's next-month
  adjustment instead of implying the Agent can fix it.
- **The by-id send accepts only a sent-back draft or a current-month draft;
  a past-month draft never sent is refused with `409`
  `PAST_MONTH_DRAFT_NOT_SENDABLE`.** Such a draft has no kept base amount, so
  sending it would bill its month from today's Fleet. The Agent could not
  reach such drafts before this feature, so refusing them takes nothing away.
- **The send-back hint and the Agent's notice name only what the Agent can
  do.** The Manager's hint says the Agent can attach files and pick up Fees of
  that month but cannot change a Fee or a past base amount; the Agent's notice
  says "Add what is missing, then send it again." Neither promises a
  correction the Agent cannot make.

- **ADR 0004 is a new record, and ADR 0001 gets a dated "amended by" note.**
  Re-opening a snapshot is a real decision, with a trade-off and context
  someone would need later. Editing 0001's decision text would hide that it
  changed. ADR 0003's note waits for the Agent Invoice half, so it is never
  true before its code is.
- **The send moves into `ClientInvoiceService` as one transaction.** A second
  send path is added, and today's send is not atomic. One place keeps the
  snapshot rule single.
- **Send-back clears the Fee snapshot rows; send does not defensively delete
  them.** A draft then never carries stale frozen rows, and the constraint
  still catches any path that forgets.
- **A row lock serializes send-back and approve.** Without it, a race can
  leave an `APPROVED` invoice whose Fee snapshot rows were just deleted, so
  its final record would list no Fees. A lock is cheaper than adding a
  version column to every write.
- **`sentAt` is cleared on a send-back and set again at the resend.** A draft
  has not been sent. The Review Queue then waits from the resend, which is
  when the wait on the Manager restarts.
- **The reason stays on the row through the resend, and is overwritten by the
  next send-back.** The Manager reviewing the resend needs it, and the audit
  line records every event. A history table is a non-goal.
- **Two distinct columns (`sent_back_at`, `sent_back_reason`), not a shared
  reason field.** This is the epic's direction and `Request`'s prior art.
- **The reason is capped at 1000 characters.** A free-text column should have
  a bound. It is generous for a note, and cheap to raise.
- **The reason is hidden from Testers and never printed on the PDF.** It is
  internal correspondence between the Manager and the Agent, and a Tester
  never saw the draft it concerns.
- **The audit reuses `AuditLog.statusChanged` (`SENT → DRAFT`), without the
  reason text.** The epic established that no new audit method is needed, and
  no audit line in the codebase carries free text.
- **The Agent reaches invoices through the existing by-id routes, opened
  per-route to the Contract's own Agent.** This is the fix for the explorer's
  finding that a past-month invoice was unreachable. It reuses the
  `manager-invoice-review-queue` identity-based addressing rather than adding
  a month parameter, the alternative that feature already rejected.
- **The Agent is refused the by-id PDF.** The Agent's PDF already works on the
  current-month route, and a past-month PDF is not needed for correction. This
  keeps the widened surface minimal.
- **Another Agent's invoice is `403`, not `404`.** This matches the
  current-month routes' "Not your Contract". The Tenant boundary stays `404`.
- **The sent-back list is its own Agent-only endpoint and carries no
  amounts.** The page already fetches per Contract, and this list spans
  months. Amounts are live and belong on the invoice's page.
- **The inline reason form, not a dialog.** `PendingRequestDecisionControls`'
  reject form is the direct prior art, and it leaves `DialogShell`'s debt
  (tech-debt.md:16) untouched and independent of `manager-resets-a-password`.
- **The send-back control takes an endpoint URL, and "Sent back" is a derived
  display state in `lib/status.ts`.** Both are cheaper built once:
  `send-an-agent-invoice-back` reuses the control and the badge mapping
  unchanged. Nothing else is generalized ahead of that feature.
- **The Agent's controls move to by-id routes, and the card is extracted.**
  One card serves the current-month page and the new by-id page, and every
  Agent action then addresses the invoice it shows.
- **The current-month send and file-attach routes stay.** They are now unused
  by the UI but harmless, since both call the same service. Deleting them is a
  separate contract step and is not needed here.
- **The "Sent back to you" section is hidden when empty.** The page then looks
  unchanged for the common case, and no golden moves.
- **The send-confirmation copy is changed.** "This can't be undone" stops
  being true.
- **Testing: the existing HTTP seam and the existing component and e2e seams,
  plus one committing race test.** Prior art is `ClientInvoiceByIdApiTest`,
  `ClientInvoiceApiTest`, the invoice control tests and
  `client-invoice-submission-and-visibility.spec.ts`. The race cannot be
  shown inside a rolled-back transaction.

## Open questions

None

## Acceptance walkthrough

1. [agent] As the Contract's Agent, send the current month's Client Invoice through the API, then log one more Fee on the Contract (used in step 3). As the Manager, `POST /api/client-invoices/{id}/send-back` with a reason, and show `200` with `status: DRAFT`, `sentBackAt`, `sentBackReason`, no `sentAt`, and live numbers. (stories: 1, 2, 4)
2. [agent] Show `GET /api/review-queue` no longer lists the invoice. Then send back a draft and an approved invoice and show `409`. Send an empty reason and a 1001-character reason and show `400`. (stories: 5, 7, 2)
3. [agent] Show the Fee logged after the first send in step 1 now appears in the draft's live Fee lines. Resend by id as the Agent and show `200`, `SENT`, and that Fee in the frozen Fee lines exactly once, with no server error. Show the queue lists the invoice again with `waitingSince` equal to the new `sentAt`. (stories: 9, 16, 18, 25)
4. [agent] As the Manager, read the resent invoice by id and show `sentBackAt` and `sentBackReason` are still there. Approve it and show `APPROVED`. Then send back another sent invoice twice in a row, resending in between, and show both send-backs succeed. (stories: 10, 11, 12)
5. [agent] Run the race test and show exactly one of send-back and approve wins, the other is `409`, and the invoice is complete in either final state. (stories: 26, 8)
6. [agent] Call send-back with the Agent's and a Tester's token and show `403`. Call the by-id read, files and send with another Agent's token and show `403`. Call every by-id route on `OtherTenantFixture`'s invoice and show `404`. (stories: 22, 28, 29)
7. [agent] As a Tester of the Contract's Client, read the current-month invoice while it is sent back and show `403`. Read it after the resend and show `200` with `sentBackReason` null, and download the PDF and show it carries no reason. (stories: 23, 24)
8. [agent] Grep the backend log for step 1 and show one `action=STATUS_CHANGE` audit line for `ClientInvoice` from `SENT` to `DRAFT`, with the Manager as actor and the Tenant id, and no reason text anywhere in the log. (stories: 27)
9. [agent] On the demo stack, pick a past-month `SENT` Client Invoice, note its base amount, and add a Postpaid SIM to its Contract. Send the invoice back as the Manager. As its Agent, `GET /api/client-invoices/sent-back` and show it listed with its reason. Read it by id and show the base amount equal to the noted one with `baseAmountKept: true`, while the Fee lines are live. Attach a file by id, resend, and show `SENT` with that file and the base amount still equal to the noted one. Run the past-month case of `ClientInvoiceSendBackApiTest`, which is also the month-rollover state, and show it green. (stories: 6, 13, 14, 15, 17, 18, 33, 34)
10. [agent] Show the Agent Invoice for that Agent and month reads the same before and after the send-back and the resend. (stories: 30)
11. [agent] In a browser as the Manager, open a sent Client Invoice from the Review Queue. Show **Send back** beside **Approve**, which stays the only pill. Expand Send back, submit empty, and show it is refused inline with the form still open. Enter a reason and confirm, then show the page re-render in place as a draft with the reason and no actions. Go back to the queue and the Dashboard and show the invoice is on neither. (stories: 1, 3, 4, 5, 7)
12. [agent] In a second tab, approve a sent invoice. In the first tab, still showing it as sent, try to send it back and show the "refresh" message. (stories: 8)
13. [agent] Sign in as the Agent and open Client Invoices. Show "Sent back to you" listing the invoice with its Contract, month, date and reason, and the current month's card showing the reason and a **Sent back** badge. Open the row and show the by-id page with the notice ending "Add what is missing, then send it again.", live numbers, Attach file and Send. (stories: 13, 14, 15, 16, 21)
14. [agent] On that page, add what was missing: log a Fee of the current month on the Contract, reload, and show it among the live Fee lines. Click Send and show the confirmation says only the Manager can send it back. Confirm, and show the invoice as sent and gone from "Sent back to you". (stories: 16, 18, 19, 20)
15. [agent] As the Manager, show the invoice back in the Review Queue. Open it and show the new Fee and "Previously sent back on …: {reason}". Approve it. (stories: 9, 10, 11)
16. [agent] In a browser, as the Manager, open another past-month `SENT` demo invoice, expand Send back and show the form's hint saying the Agent cannot change a Fee or a past base amount, then send it back and show the base amount marked "As sent" with no per-SIM breakdown. As its Agent, open it by id and show the same "As sent" base amount and live Fee lines. (stories: 2, 16, 33)
17. [agent] As an Agent with nothing sent back, show the Client Invoices page has no "Sent back to you" section. Run `mvn verify`, the frontend vitest suite, typecheck, lint, the full isolated e2e suite and the visual suite, all green, with `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest` and the two existing invoice e2e specs unedited, and no golden moved. (stories: 31)
18. [agent] Do the Manager's send-back and the Agent's open-and-resend by keyboard alone, showing a visible focus ring at each stop. Repeat at the mobile breakpoint and show the reason form, the sent-back table and the by-id page usable within the viewport. (stories: 32)
19. [human] Send back a real invoice with the reason you would really write, then read it as the Agent would. Confirm the wording tells the Agent what happened and what to do, and that nothing tells the Tester. (stories: 2, 14, 23)
20. [human] Read ADR 0004 and ADR 0001's note, and confirm they say what you settled: a sent-back invoice's Fee lines go live again; its base amount goes live only in its own month and otherwise stays as sent; `approved` stays final; the freeze still protects every number while it is under review; and a wrong amount found later is corrected on the next month's invoice, never by reopening a past month. (stories: 4, 11, 33)
21. [human] Send back a real last-month invoice that is missing something the Agent can supply (a Carrier Invoice File, or a Fee logged late in that month), then as the Agent add it and resend. Confirm this is how you want errors caught before approval handled, that the base amount stayed as sent, and that nothing on either screen suggests the Agent can change a Fee. (stories: 14, 15, 16, 17, 18, 33)

## Execution order

There are three slices. Ticket 1 is an enabler. Tickets 2 and 3 are complete vertical paths,
each demoable on its own.

1. `send-client-invoice-in-one-transaction`. Labels: `enabler`, `backend`.
   Depends on nothing. (stories: —)
   - The send and its snapshot move into `ClientInvoiceService` as one
     `@Transactional` operation, along with file attach.
   - The existing suites pass unedited.
2. `manager-sends-a-client-invoice-back`. Labels: `backend`, `frontend`.
   Depends on `send-client-invoice-in-one-transaction`. (stories: 1, 2, 3, 4,
   5, 6, 7, 8, 10, 11, 12, 23, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34)
   - Backend: the migration, the `SENT → DRAFT` edge, the send-back endpoint
     with the row lock (approve too), deleting the Fee snapshot rows while
     keeping `snapshotBaseAmount`, the base amount rule in `toResponse` and in
     the snapshot (live only in the invoice's own month), the response fields
     (`baseAmountKept` included) and their omission for Testers, and the audit
     line.
   - Records: ADR 0004, stating the base amount rule precisely, and ADR
     0001's note.
   - Frontend: the Manager's inline control with its hint, and the
     detail-view states, including the "As sent" base amount.
   - Tests: API and race tests, and component tests.
3. `agent-resends-a-sent-back-client-invoice`. Labels: `backend`, `frontend`.
   Depends on `manager-sends-a-client-invoice-back`. (stories: 9, 13, 14, 15,
   16, 17, 18, 19, 20, 21, 22, 24, 25, 32, 33, 34)
   - Backend: the Agent's by-id read, files, attach and send routes with their
     matcher changes (the resend keeps a past month's base amount), and the
     sent-back list endpoint.
   - Backend: the by-id send refuses a past-month draft never sent with `409`
     `PAST_MONTH_DRAFT_NOT_SENDABLE`.
   - Frontend: the extracted Agent card and by-id controls, the "Sent back to
     you" section, the by-id Agent page with its "Add what is missing" notice
     and "As sent" base amount, the badge, the copy change and the BFF
     proxies.
   - Tests: API tests and component tests, plus the e2e spec.
