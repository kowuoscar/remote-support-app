---
feature: agent-sends-the-month
epic: client-invoices-sent-by-the-month
status: approved
date: 2026-10-18
---

<!-- sdlc:template spec 1 -->

# The Agent sends a month's Client Invoices together

## Problem

The human, on 2026-10-18: "Invoices should be sent in bulk. The way I am
seeing this is for a given month, let's say October, the agent can only send
all to its manager where all the carriers invoices are attached. When all its
client invoices are ready, only then can he send it to the manager."

Today, after `carrier-invoices-per-sim`:

- The Agent sends each Client Invoice on its own, from its card ("Send Client
  Invoice"). One Contract's invoice can reach the Manager while the Agent's
  other Contracts for the same month are still unfinished.
- Nothing shows the Agent where the month stands as a whole. Each card says
  how many Postpaid SIMs still need their carrier invoice, but only once that
  card is opened; the side list shows each invoice's status only.
- A draft exists only once a page or a write touches it. Nothing makes sure
  every Contract the Agent holds has its invoice for the month.
- The server accepts a single invoice's first send whenever its own Postpaid
  SIM lines have their files.

Settled with the human (epic, 2026-10-18), and not reopened here:

- **UI A1**: a month bar above today's Agent Client Invoices page,
  "October 2026 · 4 of 6 ready · [Send all 6 to Manager]", disabled with the
  reason while not ready; after sending, "Sent to Manager on {date}". The
  per-invoice Send is removed. The side list marks each row ready (✓) or with
  the count of missing files.
- **A sent-back invoice is resent on its own**: its card shows its own
  Resend; the rest of the month stays where it is and the Manager approves
  the others meanwhile.
- **Specs approved in advance** ("implements this in full without stop"); the
  orchestrator settles the remaining choices and records them.

Settled by the orchestrator (epic, 2026-10-18), told to the human:

- Every Contract the Agent holds that month gets an invoice: sending creates
  any missing current-month draft so the batch is complete. An invoice with
  nothing to bill is part of the batch and is sent at $0, as today.
- The Agent sends during the month itself, as today. `month-closes-on-the-fifth`
  (awaiting approval) must account for this feature.
- A Contract added after the month was sent: its invoice is sent on its own.
- The Agent Invoice is unchanged.
- Testers see the invoices once sent, as today.

## Journeys

First feature of `docs/roadmap/client-invoices-sent-by-the-month.md`.

- **Bill the Client for the month** (`exists`, stays `exists`). The Agent
  attaches the carrier invoices across all their Contracts, watches the month
  bar count them ready, and sends the whole month at once. The journey's
  proof, `client-invoice-submission-and-visibility.spec.ts`, moves to a fresh
  Agent whose month is unsent, and sends through the bar. The Manager's half
  (review by Agent) is `manager-reviews-by-agent`; here the Manager's side is
  unchanged.

## Goals / Non-goals

Goals:

- A month bar on the Agent's Client Invoices page: the month, how many of the
  Agent's current-month Client Invoices are ready, and "Send all {n} to
  Manager", enabled only when all are ready.
- One server action, the **month send**, that sends every current-month Client
  Invoice of the Agent's Contracts in one transaction: all or nothing,
  creating any missing draft first.
- The server enforces the rule: a never-sent current-month invoice cannot be
  sent on its own while the Agent's month is not sent (coded 409).
- The per-invoice send stays for exactly two cases: the resend of a sent-back
  invoice, and the invoice of a Contract added after the month was sent.
- Each side list row of a draft the Agent still has to send shows "✓ Ready" or
  "{n} files missing".
- The month's sent date is recorded and shown, and survives a send-back.
- Demo data, backend tests and the e2e suite follow the new rule.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No Manager change.** No Agents side list, no grouping, no "Approve the N
  left", no "Next invoice →". That is `manager-reviews-by-agent`. The Review
  Queue simply receives the month's invoices as separate rows, as if sent one
  by one at the same instant. Approve and Send back stay per invoice.
- **No month entity, no month status, no "unsend".** "The month is sent" is
  derived from the invoices. There is no month row, no month-level send-back,
  and no way to take a sent month back.
- **No sending of a past or future month.** The month send is for the
  current UTC month only, as the per-invoice send is today. Extending the
  window to the 5th of the next month is `month-closes-on-the-fifth`'s.
- **No change to what "ready" means for a single invoice**: every Postpaid SIM
  line has its Carrier Invoice File, exactly the existing send gate. No new
  condition (no "Fees reviewed", no minimum amount, no Tester sign-off).
- **No bar on the by-id invoice page** (`/agent/client-invoices/{id}`) and no
  bar on the Agent dashboard or in the nav. The bar lives on the Client
  Invoices page only.
- **No readiness in the Contract Switcher's options** below `xl`. The bar
  above it carries the count at every width; the switcher is unchanged.
- **No change to the Agent Invoice**, its send, or Local Support Fees. Sending
  the month does not send, create or move the Agent Invoice.
- **No notification or mail** to the Manager or the Testers.
- **No Tester change.** A Tester sees each invoice from `SENT` on, as today.
- **No removal of the per-invoice send routes.** They stay, narrowed. The
  dead contract-scoped BFF routes (debt :21) are not touched by this feature.
- **No partial month send** ("send the 4 that are ready"). The human's rule is
  all or nothing.

## User stories

The month bar:

1. As an Agent, I want a bar at the top of my Client Invoices page naming the current month and how many of my Client Invoices for it are ready ("October 2026 · 4 of 6 ready"), so that I see at a glance how far the month is.
2. As an Agent, I want "Send all 6 to Manager" in that bar, disabled while any invoice is not ready, with the reason under it ("2 invoices still need their carrier invoices."), so that I know what stands between me and sending.
3. As an Agent, I want the button enabled once every invoice is ready, and a second step to confirm ("Sends all 6 Client Invoices to the Manager and the Clients, and locks their numbers. Only the Manager can send one back to you." with Back and "Confirm send all 6"), so that a one-way action is never a single slip.
4. As an Agent, I want one send to send every one of my current-month Client Invoices, one per Contract I hold, all or nothing, so that the Manager receives my month complete.
5. As an Agent, I want an invoice with nothing to bill (no Postpaid SIM, no Fee) counted as ready and sent at $0 with the rest, so that every Contract is accounted for.
6. As an Agent, I want the send to create the draft of any Contract whose invoice was never opened, so that no Contract is left out of the month.
7. As an Agent, after sending, I want the bar to read "October 2026 · Sent to Manager on Oct 18, 2026", and to keep that date even when the Manager sends one invoice back, so that I know when my month went.
8. As an Agent, I want "Send all 6" to send exactly the 6 invoices I saw: if my Contracts changed since the page loaded, the send is refused with "Your Contracts changed since this page loaded. Reload and try again." and nothing is sent.
9. As an Agent whose invoice stopped being ready after the page loaded (a SIM began billing), I want "An invoice is no longer ready. Reload to see which." and nothing sent, so that I know what to do.
10. As an Agent who already sent the month from another tab, I want "This month was already sent. Reload to see it.", so that a double send is harmless.
11. As an Agent whose page was loaded last month, I want "The month has changed. Reload to see this month's invoices." and nothing sent, so that a page left open over midnight never sends the wrong month.
12. As an Agent, when one of my invoices failed to load, I want the bar to say "Couldn't load every invoice." with the button disabled and "Reload the page to send the month." under it, so that I never send a month I could not see.
13. As an Agent with one Contract, I want the button to read "Send 1 to Manager" and the confirm "Confirm send", so that the words fit.
14. As an Agent with no Contracts, I want no bar, and the page's existing empty state, so that I am not offered a month with nothing in it.

The invoice cards:

15. As an Agent, while my month is not sent, I want no Send on any card, and on each editable never-sent card the line "Sent with the month, from the bar above.", so that I know where sending happens.
16. As an Agent, I want each card to keep saying how many Postpaid SIMs still need their carrier invoice ("2 Postpaid SIMs still need their carrier invoice."), so that I see what is missing on the invoice in front of me.
17. As an Agent, I want Attach, Remove and line edits on my drafts to work exactly as today, so that preparing the month is unchanged.
18. As an Agent whose invoice the Manager sent back, I want its card to show "Resend Client Invoice", gated by its own carrier invoices as today, sending that invoice alone, so that one correction does not hold or resend the rest of the month.
19. As an Agent who was given a Contract after my month was sent, I want that Contract's card to show "Send Client Invoice", gated by its own carrier invoices, sending it alone, so that a late Contract is billed without reopening the month.
20. As an Agent, I want the bar to keep reading "Sent to Manager on {date}" while a sent-back or late invoice waits on its own Send, so that the month's state does not flip back.

The side list:

21. As an Agent, I want each side list row whose current-month invoice I still have to send (never sent, sent back, or late) to show "✓ Ready" or "{n} files missing" (1: "1 file missing") under the Client name, so that I see which Clients need work without opening each one.
22. As an Agent, I want no readiness mark on a row whose invoice is sent or approved, or failed to load, so that the mark only appears where there is something to do.
23. As an Agent, I want the mark and the bar's count to follow an attach or a remove as soon as the page refreshes, so that the page tells the truth.
24. As an Agent on a narrow screen (below `xl`), I want the bar above the Contract Switcher, so that the month's count and its Send are there at every width.

The rule, held by the server:

25. As the business, I want the server to refuse the first send of a single current-month invoice while the Agent's month is not sent, with a 409 coded `MONTH_NOT_SENT`, whatever the page showed, so that no invoice reaches the Manager ahead of its month.
26. As the business, I want the month send refused with a 409 coded `CARRIER_INVOICES_MISSING` listing every invoice not ready (`invoiceIds`), with nothing written, so that no month reaches the Manager incomplete.
27. As the business, I want the month send all or nothing under concurrency: of two racing month sends one wins and the other gets 409 `MONTH_ALREADY_SENT`; an attach, remove or line edit racing it is serialised with it; never a month half sent, so that the rule holds under load.
28. As the business, I want the month send refused for any month but the current UTC month, with a 409 coded `NOT_THE_CURRENT_MONTH`, so that `month-closes-on-the-fifth` has one predicate to widen.
29. As the business, I want only an Agent to send their own month (a Manager or a Tester gets 403), so that sending stays the Agent's act.

The others:

30. As a Tester, I want to see each of my Client's invoices once it is sent, as today, so that the month send changes nothing for me but timing.
31. As a Manager, I want the month's invoices to arrive in the Review Queue as separate invoices, each approved or sent back on its own as today, so that my review is unchanged until `manager-reviews-by-agent`.

The record and the team:

32. As the team, I want each Client Invoice to record when it was first sent (kept through a send-back, backfilled for invoices sent before this feature), and one audit line per month send naming the Agent, the month and the count, so that "the month is sent" and its date are derived from the invoices and every month send is traceable.
33. As the team, I want the demo to send Jordan's month through the month send, with a carrier invoice on every Postpaid SIM line of both of his Contracts, and to leave Priya's month unsent; with the store down at startup, Jordan's month stays unsent and the demo still starts.
34. As the team, I want the backend tests to send through the month send by one helper, the e2e journey proof to send a fresh Agent's month through the bar, and the other e2e specs to keep sending Jordan's later Contracts on their own, so that the suite proves the new rule and stays green.
35. As the team, I want `CONTEXT.md` to name the Agent's month, the month send and a ready Client Invoice, and the Client Invoice entry to say a first send goes through the month send, so that the glossary matches the code.

## Solution

### Prefactoring: debt paid on the way

- **One get-or-create of a current-month draft.** `ClientInvoiceController`
  holds `getOrCreateDraftForCurrentMonth` and `createDraft` (with the V11
  unique-constraint fallback). The month send needs the same. They move into
  one small component, `ClientInvoiceDrafts` (in `web`; the invoicing cluster
  has not moved, ADR 0006), which both the controller and the month send call.
  Behaviour is unchanged.
- **`BillingMonth.current()` in the Client Invoice cluster.** The controller
  and `ClientInvoiceService.requireOpenToAgent` compute the month inline
  (debt :98). Both, and the new code, use `BillingMonth.current()`. The other
  controllers named in debt :98 are not touched.
- **One send step.** `ClientInvoiceService.send` is split into the guards and
  a private step `markSent(invoice, now, principal)` that stores the lines,
  sets `SENT`, `sentAt`, `firstSentAt` (if not set) and logs the status change.
  The per-invoice send and the month send both call it, so the two can never
  store lines differently.

### Schema: one column

At the Flyway number the orchestrator reserves (V63 as of `main` 4d90523):

```sql
ALTER TABLE client_invoices ADD COLUMN first_sent_at TIMESTAMPTZ;
UPDATE client_invoices
   SET first_sent_at = COALESCE(sent_at, approved_at, sent_back_at, created_at)
 WHERE status <> 'DRAFT' OR sent_back_at IS NOT NULL;
```

- Set at an invoice's first send, never changed again, never cleared by a
  send-back. `ClientInvoice` gains `firstSentAt`.
- The backfill only fills the new column. For a sent-back draft it takes the
  send-back time, the closest record left (its first send was earlier); a
  `DRAFT` never sent stays `NULL`.

### The model: the Agent's month, derived

```text
Agent's month (agent, M) = every Client Invoice whose Contract's Agent is `agent`
                           and whose billingMonth is M
month sent               = some invoice of the month has firstSentAt
month sentAt             = min(firstSentAt) over the month's invoices
ready (one invoice)      = DRAFT, and every POSTPAID_SIM line has its file
                           (exactly the existing send gate)
```

No month entity. `ClientInvoiceRepository` gains:

- `findMonthForUpdate(agentId, billingMonth)`: the month's invoices,
  `PESSIMISTIC_WRITE`, ordered by id (one lock order for every month send);
- `monthSentAt(agentId, billingMonth)`: the `min(firstSentAt)`, or null.

### The month send: `ClientInvoiceMonthService`

A new component in `web`. Its one write:

```java
ClientInvoiceMonthSendResponse send(UUID agentId, LocalDate billingMonth,
                                    Optional<Set<UUID>> expectedInvoiceIds,
                                    AuthenticatedPrincipal principal);
```

1. **Current month only.** `billingMonth` must equal `BillingMonth.current()`,
   else 409 `NOT_THE_CURRENT_MONTH`.
2. **Complete the month, outside the transaction.** For every Contract of the
   Agent (`ContractRepository` by tenant and Agent), `ClientInvoiceDrafts`
   gets or creates the current-month draft, each in its own short transaction
   as today (the V11 constraint settles a race with a page view). No Contract
   → 409 `NOTHING_TO_SEND`.
3. **One transaction** (`@Transactional`, Backend 3 keeps it on the service):
   1. lock the month: `findMonthForUpdate`. The locked rows **are** the batch.
      A Contract created after step 2 has no invoice yet, so it is, by
      definition, added after the month was sent (story 19).
   2. any locked invoice with `firstSentAt` → 409 `MONTH_ALREADY_SENT`;
   3. `expectedInvoiceIds` given and not equal to the batch's ids → 409
      `MONTH_CHANGED`;
   4. every invoice through the existing carrier gate; collect, do not stop at
      the first. Any not ready → 409 `CARRIER_INVOICES_MISSING`, body
      `{code, message, invoiceIds}`, nothing written;
   5. one `Instant now`; `markSent(invoice, now, principal)` for each;
   6. `AuditLog.clientInvoiceMonthSent(agentId, billingMonth, count, actor,
      tenant)`, after the per-invoice status lines.
4. Answer `200` with `{billingMonth, sentAt, invoiceIds}`.

**Locks.** The month send takes only Client Invoice row locks, in id order.
Attach, remove, line edit, approve, send-back and the per-invoice send each
lock one Client Invoice row first, as today. So a racing write either lands
before the month send (which then sees it) or waits and finds the invoice
`SENT` (409, as today). Two month sends of one Agent lock in the same order;
the second waits, then gets `MONTH_ALREADY_SENT`. No new lock order is
introduced against the Agent Invoice, which the month send does not touch.

### The per-invoice send, narrowed

In `ClientInvoiceService.send`, under the row lock, after the status check
and `requireOpenToAgent`, before the carrier gate:

- `firstSentAt == null` (never sent) and `monthSentAt(agent, billingMonth)` is
  null → 409 `MONTH_NOT_SENT` ("Send this month's Client Invoices together,
  from the month send").
- Otherwise the send proceeds as today. That admits exactly the resend of a
  sent-back draft (it has `firstSentAt`) and the first send of a Contract's
  draft once its month was sent (story 19).

Both per-invoice routes (by id, and the current-month Contract route) call
this one method, so both are narrowed.

A per-invoice send racing a month send that holds its row waits, then finds
it `SENT` (409). One whose invoice is outside the batch reads the committed
state: before the month send commits it gets `MONTH_NOT_SENT`, after it the
send is a late one and proceeds. Either answer is true at its moment.

**Transition.** An Agent whose current month already holds an invoice sent
before this feature has a sent month (its backfilled `firstSentAt`). The bar
shows it sent, and the remaining drafts send on their own, as late ones.

### HTTP contract

- **`POST /api/me/client-invoice-months/{month}/send`**, `{month}` as
  `YYYY-MM`. Agent-only at the matcher level, placed before the general
  `/api/me` fall-through; the Agent is the caller's, through
  `CallerIdentityResolver.resolveAgentId` (an Agent login without an Agent is
  403). New `ClientInvoiceMonthController`, with the shared
  `ClientInvoiceErrors` handlers.
  - Body optional: `{ "invoiceIds": [uuid, …] }`. Absent, the batch is
    whatever the month holds.
  - 200 `{billingMonth, sentAt, invoiceIds}`.
  - 400 for a malformed month.
  - 403 for a Manager, a Tester, or an unlinked Agent login.
  - 409, coded: `NOT_THE_CURRENT_MONTH`, `NOTHING_TO_SEND`,
    `MONTH_ALREADY_SENT`, `MONTH_CHANGED`, `CARRIER_INVOICES_MISSING` (with
    `invoiceIds`).
- **The per-invoice sends** (`POST /api/client-invoices/{id}/send`,
  `POST /api/contracts/{contractId}/client-invoice/send`) can now answer 409
  `MONTH_NOT_SENT`. Nothing else about them changes.
- **`ClientInvoiceResponse` gains two fields**, serialised for the Agent and
  the Manager, null/false for a Tester:
  - `monthSentAt` (nullable instant): the invoice's Agent's month `sentAt`;
  - `sendableOnItsOwn` (boolean): `DRAFT`, open to its Agent, and (sent back
    or `monthSentAt` set). It tells a card whether its own Send applies, so
    the frontend never re-derives the rule.
- `ClientInvoiceConflictException.Reason` gains `MONTH_NOT_SENT`,
  `MONTH_ALREADY_SENT`, `MONTH_CHANGED`, `NOT_THE_CURRENT_MONTH`,
  `NOTHING_TO_SEND`. The exception carries an optional list of invoice ids,
  which `ClientInvoiceErrors.conflict` adds as `invoiceIds` when present; every
  other coded body stays `{code, message}`.

### Demo data

`DemoDataLoader` today attaches US-1's files and sends US-1 alone, which the
new rule refuses. It now attaches one generated PDF per Postpaid SIM line of
**both** of Jordan's current-month drafts (US-1 and US-2), outside any
transaction, through `CarrierInvoiceFileService.attach`, then sends Jordan's
month through `ClientInvoiceMonthService.send`. With the store down (probed
first, as today), it logs one WARN and leaves both drafts unsent; a refusal
part way leaves the files already attached and the month unsent. Priya's UK-1
draft is left unsent, so the demo shows both bar states.

### Frontend

**Types.** `ClientInvoiceDetail` gains `monthSentAt: string | null` and
`sendableOnItsOwn: boolean`. One helper, `clientInvoiceMonth(invoices)`, turns
the page's loaded invoices into the bar's state:

```ts
type ClientInvoiceMonthState =
  | { kind: "unsent"; billingMonth: string; total: number; ready: number;
      invoiceIds: string[] }
  | { kind: "sent"; billingMonth: string; sentAt: string }
  | { kind: "incomplete"; billingMonth: string }; // an invoice failed to load
```

`sent` when any loaded invoice has `monthSentAt`; `incomplete` when any
Contract's invoice is null; else `unsent`, with `ready` counted by
`missingCarrierInvoiceLines(invoice).length === 0` on each `DRAFT`. The month
label comes from the invoices' `billingMonth` (`formatBillingMonth`).

**The month bar**, `SendClientInvoiceMonthControl`, first in the
`SideListLayout`'s `lead` (above "Sent back to you"), so it sits at the top of
the detail column at `xl` and above the Contract Switcher below it. Absent
when the Agent has no Contracts.

- **Unsent**: "{Month Year}" (`text-sm` 600) · "{ready} of {total} ready"
  (`ink-secondary`, `tnum`), and a primary `sm` button "Send all {total} to
  Manager" ("Send 1 to Manager" for one). Disabled while `ready < total`, with
  one `text-label-sm` `ink-mute` line linked by `aria-describedby`: "1 invoice
  still needs its carrier invoices." / "{n} invoices still need their carrier
  invoices."
- **Confirm**: the two-step inline confirm of today's `SendClientInvoiceControl`
  (its focus handling and `aria-busy` pending state): "Sends all {n} Client
  Invoices to the Manager and the Clients, and locks their numbers. Only the
  Manager can send one back to you.", Back, "Confirm send all {n}" ("Confirm
  send" for one; "Sending…" while pending).
- **The request**: `POST` to a new BFF route mirroring the backend path, with
  `forwardJson`, body `{invoiceIds}` from the loaded invoices. On 200 the page
  refreshes (`refreshPage`).
- **Errors**, under the confirm, `role="alert"`:
  - `CARRIER_INVOICES_MISSING`: "An invoice is no longer ready. Reload to see which."
  - `MONTH_CHANGED`: "Your Contracts changed since this page loaded. Reload and try again."
  - `MONTH_ALREADY_SENT`: "This month was already sent. Reload to see it."
  - `NOT_THE_CURRENT_MONTH`: "The month has changed. Reload to see this month's invoices."
  - any other: "Couldn't send. Try again."; network: the existing "Couldn't reach the server…" line.
- **Sent**: "{Month Year} · Sent to Manager on {formatDate(sentAt)}", with
  `IconCheckCircle` (`h-3.5`, `success`) before "Sent", no button.
- **Incomplete**: "{Month Year} · Couldn't load every invoice.", the button
  disabled, with "Reload the page to send the month." as its note.

**The card** (`AgentClientInvoiceCard`), on an editable draft:

- `sendableOnItsOwn` true: `SendClientInvoiceControl` as today, labelled
  "Resend Client Invoice" / "Confirm resend" when the draft is sent back, and
  "Send Client Invoice" / "Confirm send" otherwise (a late Contract). It
  reads a `MONTH_NOT_SENT` refusal as "Send this month from the bar above.
  Reload to see it."
- `sendableOnItsOwn` false: no Send. The missing-files note
  ("{n} Postpaid SIMs still need their carrier invoice.") stays, as its own
  line under Attach, and under it "Sent with the month, from the bar above."
  (`text-label-sm`, `ink-mute`).
- The by-id page uses the same card and fields, and shows no bar.

**The side list.** `SideListItem` gains `status?: ReactNode`, line 2 left,
after the Country when one is shown ("Kenya · ✓ Ready"). It is last in DOM
order, so the accessible name reads name, Country, status badge, mark,
readiness. On a row whose current-month invoice is a `DRAFT` (never sent,
sent back, or late):

- no file missing: `IconCheckCircle` (`h-3`, `success`) then "Ready";
- otherwise: "1 file missing" / "{n} files missing", `ink-mute`.

No readiness on a sent or approved row, or a row whose invoice failed to load.
The status badge and the "Sent back" mark are unchanged.

**The visual stub backend** serves Jordan's stub month unsent: one invoice
ready and one with a file missing, `monthSentAt` null and `sendableOnItsOwn`
false, so the Agent goldens show the bar "1 of 2 ready" disabled with its
note, both readiness marks, and the card without Send.

## Design direction

Design slot: `impeccable`. `DESIGN.md` is the world. No new token or colour.

- **The month bar**: Operate. One hairline `Card` row, `px-4 py-3`, the month
  and count on the left, the button on the right; below `md` the button wraps
  under the text, right-aligned. It is the page's one primary pill while the
  month is unsent (the cards have no Send then); once sent, the bar holds no
  button and a sent-back or late card's Send is the one pill. The disabled
  state is the primary's existing 40% opacity plus the note, one plain
  sentence, no danger tone: an unfinished month is not an error.
- **The sent bar**: Read. The success check and one sentence; no badge.
- **The readiness mark**: quiet. `text-label-sm` on line 2 under the name, the
  check in `success`, the missing count in `ink-mute`. It never sits in the
  badge column, so the name is not cut further (debt :76).
- **The card**: unchanged apart from Send. The "Sent with the month" line is
  `ink-mute`, right-aligned under Attach where Send stood.
- **`DESIGN.md`** gains a "Month bar" entry under Components (the shape above,
  the one-pill rule) and, in the Side list Row paragraph, the readiness line:
  "on a row whose current-month invoice is a draft still to send, line 2 left
  reads '✓ Ready' (`IconCheckCircle` `h-3`, `success`) or '{n} files missing'
  (`ink-mute`), after the Country when shown; never on a sent, approved or
  unloaded row."

## Constraints

- Inherits every constraint of `carrier-invoice-per-sim`,
  `send-a-client-invoice-back`, `edit-client-invoice-lines` and ADR 0001, 0004
  and 0005, except where this spec says otherwise.
- **All or nothing.** The month send writes in one transaction. A refusal at
  any check writes nothing (the drafts created in step 2 remain; they are
  what any page view would have created).
- **One lock order**: Client Invoice rows only, ordered by id. No store call
  and no Agent Invoice lock inside the month send's transaction (Backend 8).
- **The month is the current UTC month**, through `BillingMonth.current()`,
  checked in one place per path, so `month-closes-on-the-fifth` widens it in
  one place each for the month send and `requireOpenToAgent`.
- **HTTP contract changes. Nothing else changes.** The new route and its codes,
  `MONTH_NOT_SENT` on both per-invoice sends, `invoiceIds` on the month send's
  `CARRIER_INVOICES_MISSING`, and `monthSentAt` / `sendableOnItsOwn` on the
  Client Invoice response.
- **Migration**: one forward-only Flyway script (Backend 9) at the number the
  orchestrator reserves. It adds one nullable column and fills it; it rewrites
  no existing column.
- **Packages**: new classes go in the layer packages (`web`, `dto`,
  `repository`) because the invoicing cluster has not moved; the frozen list
  gains their entries (ADR 0006, Backend 13).
- **No new dependency**, in main or test scope.
- Every isolated run (tests, e2e, acceptance) uses its own throwaway Postgres
  and SeaweedFS. Nothing touches the human's compose project `rsa-prod`, its
  ports 3000/8080/5432 or its volumes. The e2e suite runs on an idle machine;
  a failure is never called a flake without a controlled comparison. Maven
  runs with JDK 21.

## Testing decisions

Tests assert external behaviour: HTTP status and body, the rows a request
leaves, and what a user sees and can do on the page.

1. **The HTTP seam, real Postgres and SeaweedFS (`IntegrationTest`,
   `ObjectStoreTestSupport`)**, a new `ClientInvoiceMonthSendApiTest`, using a
   fresh Agent per case where it needs several Contracts:
   - three Contracts (two Postpaid with files, one with nothing): 200, all
     three `SENT` with one `sentAt` and one `firstSentAt`, lines stored, the
     empty one at total 0;
   - a Contract never viewed: the send creates its draft and sends it;
   - one invoice missing a file: 409 `CARRIER_INVOICES_MISSING` with exactly
     the not-ready `invoiceIds`; every invoice still `DRAFT`, `linesStored`
     false, no line row;
   - `invoiceIds` not matching: 409 `MONTH_CHANGED`, nothing sent;
   - second send: 409 `MONTH_ALREADY_SENT`;
   - last month or next month: 409 `NOT_THE_CURRENT_MONTH`; `2026-13`: 400;
   - no Contracts: 409 `NOTHING_TO_SEND`;
   - Manager, Tester: 403; another Agent's invoices are never in the batch;
   - the per-invoice send of a never-sent current-month draft while the month
     is unsent: 409 `MONTH_NOT_SENT` on both routes, body `{code, message}`;
   - after the month send: a sent-back invoice resends alone (200, the others
     untouched); a Contract created afterwards sends alone (200);
   - `monthSentAt` and `sendableOnItsOwn` on the response in each state, and
     `monthSentAt` unchanged by a send-back and resend;
   - a pre-feature month (an invoice `SENT` with `firstSentAt` set directly):
     the month reads sent and a remaining draft sends alone.

   Prior art: `ClientInvoiceApiTest`'s send cases and
   `ClientInvoiceSendBackApiTest`'s coded-409 assertion.
2. **Concurrency** (outside the shared transaction, prior art
   `ClientInvoiceConcurrentSendTest`, `CarrierInvoiceFileAttachConcurrentSendTest`):
   - two month sends of one Agent: one 200, one 409 `MONTH_ALREADY_SENT`,
     lines stored once per invoice;
   - a month send racing a file remove on one of its invoices: either the
     remove wins and the month send gets `CARRIER_INVOICES_MISSING` with no
     invoice sent, or the month send wins and the remove gets 409; never a
     sent invoice missing its file, never some invoices sent and others not;
   - `ClientInvoiceConcurrentSendTest` keeps its per-invoice race on a late
     Contract's draft (the month sent first): one 200, one 409.
3. **Migration test** for the backfill: a `SENT`, an `APPROVED`, a sent-back
   and a never-sent row migrated from the previous version get the expected
   `first_sent_at`, and the never-sent stays null. Prior art
   `ClientInvoiceLinesMigrationTest`.
4. **Existing backend tests that send.** About nineteen classes send through
   the per-invoice routes; a first send now needs the month. One helper beside
   `CarrierInvoiceTestSupport`, `sendTheMonth(mockMvc, agentToken)`, posts the
   month send for the current month. Because the seeded Agent holds no
   Contract in the test database, a test with one Contract for him sends the
   same single invoice as before. A test that needs one of several Contracts
   sent and another not uses a second Agent. Resends after a send-back keep
   the per-invoice route. What these tests assert does not change.
   `DemoDataLoaderApiTest` asserts Jordan's two current-month invoices `SENT`
   with one file per Postpaid SIM line and one `firstSentAt`, and Priya's
   `DRAFT`. Unit tests (JUnit 5 + Mockito) cover the month service's order of
   checks where a Mockito test is cheaper than a database case.
5. **Frontend component tests (Vitest + Testing Library)**:
   - `send-client-invoice-month-control.test.tsx` (new): each state's text; the
     disabled button and its described-by note; one-Contract wording; the
     confirm's focus; the request's body; each error code's text.
   - `client-invoices-view.test.tsx`: the bar from the loaded invoices
     (unsent, sent, incomplete, absent with no Contracts); no Send on a card
     while unsent, with the two lines; "Resend Client Invoice" on a sent-back
     card; "Send Client Invoice" on a late one; the readiness marks and their
     absence on sent, approved and failed rows; the accessible name order.
   - `send-client-invoice-control.test.tsx`: the Resend wording and the
     `MONTH_NOT_SENT` text.
   - The `clientInvoiceMonth` helper, table-driven.
   - Fixtures gain the two fields.
6. **End to end, on the isolated stack.**
   - The journey proof, `client-invoice-submission-and-visibility.spec.ts`,
     creates a fresh Agent with a login (a new `createAgentWithLogin` helper,
     prior art `create-agent-with-login.spec.ts` and
     `readRevealedPasswordAndClose`) holding two Contracts, one with a
     Postpaid SIM and one with nothing; as that Agent it shows "1 of 2 ready"
     and the disabled button, attaches, sends the month through the bar, and
     then the Tester and the Manager steps as today.
   - A new `agent-sends-the-month.spec.ts`, same fresh-Agent pattern: the
     side list marks; a send-back resent alone with the bar's date unchanged;
     a Contract added after the send, sent alone.
   - **Every other spec keeps the seeded Agent Jordan**, whose month the demo
     has sent at startup, so each spec's new Contract is a late one and keeps
     "Send Client Invoice" on its card. Specs that resend after a send-back
     click "Resend Client Invoice". `clientInvoiceRowName` accepts the
     readiness text; `attachCarrierInvoicesForEveryPostpaidSim` waits for the
     card, not the Send button. `helpers.ts` documents that Jordan's demo month
     is sent. Specs that count the demo's sent or waiting invoices (dashboards,
     Review Queue) are checked for US-2 now being sent.
7. **Visual goldens.** The Agent Client Invoices page goldens change, in the
   same commit as the stub and the code that move them:
   `agent-client-invoices-*`, and, where their capture includes the bar or the
   card's header, `client-invoice-attach-dialog-open-*`,
   `client-invoice-files-paused-*` and `client-invoice-remove-confirm-open-*`.
   `client-invoices-side-list-rows.spec.ts` gains a case for a readiness mark
   beside a Country and a "Sent back" mark. No other golden should move.
8. **`verify`** stays the command in `docs/agents/sdlc.json`.

## Decisions taken

- **Spec approved by the human in advance** ("implements this in full without stop", epic 2026-10-18).
- **(at ticket cut) The isolated e2e backend runs without the `demo` profile, so `frontend/tests/e2e/global-setup.ts` gives Jordan a no-bill Contract and sends his month once** (tolerating `MONTH_ALREADY_SENT` on a reused database, failing loudly otherwise). Reason: every existing e2e spec then sends its own Contract as a late one, unchanged; only the journey proof and the new month spec use a fresh Agent. (Orchestrator, 2026-10-18.)

- **"The month is sent" is derived from a new `first_sent_at` on each Client Invoice, not a month entity.** Reason: one nullable column gives an exact, send-back-proof "sent" and date; a month row would duplicate what the invoices already say, and `sentAt` alone is cleared by a send-back.
- **The month counts as sent as soon as any of its invoices has been sent, including one sent before this feature.** (settled by the orchestrator under the human's advance approval) Reason: one rule covers the late Contract and the transition; on the human's stack an October with invoices already sent one by one finishes one by one, rather than having two rules for one month.
- **Every Contract the Agent holds today is in the batch; Contracts have no end date.** Reason: the schema has no Contract end, and the settled rule is "every Contract the Agent holds that month".
- **The month send creates missing drafts first, in their own short transactions, then locks the month; the locked rows are the batch.** Reason: draft creation already relies on the V11 constraint and must not abort the send's transaction; a Contract created in between is, by definition, added after the send.
- **All checks before any write; all not-ready invoices reported together (`invoiceIds`).** Reason: all or nothing is the human's rule, and one refusal should name everything to fix.
- **The per-invoice send stays, narrowed by `MONTH_NOT_SENT`; both per-invoice routes share it.** Reason: the resend and the late Contract still need a single send; enforcing the rule in the one method both routes call keeps API callers honest.
- **Route `POST /api/me/client-invoice-months/{YYYY-MM}/send`.** Reason: it is the caller's own month (`/api/me/agent` is the prior art); the month in the path makes a page left open over midnight fail loudly instead of sending the next month, and gives `month-closes-on-the-fifth` the month to widen.
- **An optional `invoiceIds` body, refused with `MONTH_CHANGED` when it differs.** (settled by the orchestrator under the human's advance approval) Reason: "Send all 6" then sends exactly the six the Agent saw; an API caller may omit it.
- **No month GET; the bar is derived on the page from the invoices it already loads, plus `monthSentAt` and `sendableOnItsOwn` on each invoice.** Reason: the page already loads (and creates) every current-month invoice; two fields keep the server the only judge of "sent" and "sendable alone" without a second read model.
- **`sendableOnItsOwn` is served, not derived in the browser.** Reason: the rule (open to its Agent, sent back or month sent) lives in the backend; the card and the by-id page read one boolean.
- **Copy of the bar, its notes, the confirm and its errors as written in the Solution.** (settled by the orchestrator under the human's advance approval) Reason: they follow the human's own wording ("4 of 6 ready", "Send all 6 to Manager", "Sent to Manager on {date}") and today's Send control's tone.
- **"Send 1 to Manager" for one Contract.** (settled by the orchestrator under the human's advance approval) Reason: "Send all 1" reads wrong.
- **The bar sits first in the page's lead area, above "Sent back to you", at every width.** (settled by the orchestrator under the human's advance approval) Reason: "a month bar above today's page"; the lead is the slot that is above the detail and the switcher at every width.
- **While the month is unsent, each editable card says "Sent with the month, from the bar above." and keeps its missing-files note.** (settled by the orchestrator under the human's advance approval) Reason: a card that lost its Send must say where sending went, and the note is the per-invoice detail the bar's count summarises.
- **A sent-back card reads "Resend Client Invoice"; a late Contract's card keeps "Send Client Invoice".** (settled by the orchestrator under the human's advance approval) Reason: the human named "Resend" for the sent-back case; a late invoice is a first send.
- **The sent bar shows no count of sent-back or late invoices.** (settled by the orchestrator under the human's advance approval) Reason: the human's sent wording is the date only; "Sent back to you", the side list badges and the readiness mark already point at each one.
- **Readiness in the side list on line 2, left, as "✓ Ready" / "{n} files missing", on every current-month draft still to send (never sent, sent back, late).** (settled by the orchestrator under the human's advance approval) Reason: the human asked for ✓ or the count; the left of line 2 does not widen the badge column, which already cuts names (debt :76), and keeps the "Sent back" mark where it is.
- **The word "Ready" beside the ✓.** Reason: the row's accessible name is its text; an icon alone would read as nothing.
- **No readiness on sent or approved rows.** Reason: their badge already says the work is done.
- **The bar's "incomplete" state when any invoice failed to load.** Reason: a month the page could not fully see must not be sent from it.
- **The demo sends Jordan's whole month (US-1 and US-2) and leaves Priya's unsent.** Reason: US-1 alone can no longer be sent; sending both keeps the demo's sent invoice for Dana and adds one for Noah, and Priya's unsent month shows the bar's other state.
- **e2e: the journey proof and the new spec use a fresh Agent; every other spec keeps Jordan, whose demo month is sent, and sends its Contracts as late ones.** Reason: all e2e specs share Jordan's month in one database; making every spec send the month would couple them to each other's unfinished drafts. A fresh Agent gives the bar a clean month.
- **Backend tests: one `sendTheMonth` helper; a second Agent where a test needs some Contracts unsent.** Reason: the seeded Agent holds no Contract in the test database, so most tests' month send is their old single send.
- **`ClientInvoiceDrafts` extracted, `BillingMonth.current()` adopted in the Client Invoice cluster only, and one `markSent` step shared.** Reason: the month send needs each; the rest of debt :98 is outside this feature.
- **Audit: one `clientInvoiceMonthSent` line per month send, on top of each invoice's status line.** Reason: the per-invoice lines stay comparable with today's; the month line ties them together.
- **`NOTHING_TO_SEND` for an Agent with no Contract.** Reason: the bar is absent then; an API call still gets a clear refusal rather than a 200 that sent nothing.
- **Test seams: the existing HTTP seam with real Postgres and SeaweedFS, component tests at the view and the new control, e2e through the real page; no new seam.** Reason: those seams already prove send, send-back and the carrier gate.
- **Three tickets, one PR (below).** Reason: the server rule is demoable through the API alone; the bar and cards are one user-facing slice; the side list mark builds on both and shares their goldens.

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

Logins are in the root `README.md`. "Avery" is a fresh Agent the walkthrough
creates as the Manager (Create agent, login shown once), so that their month
starts unsent.

1. [agent] (stories: 34) Run `verify` on an idle machine. Show:
   - it is green;
   - only the goldens named in Testing decisions 7 changed, and the
     `agent-client-invoices-*` ones show the bar "1 of 2 ready" disabled with
     its note, both readiness marks, and a card without Send;
   - no throwaway container is left behind.

2. [agent] (stories: 1, 2, 5, 14, 15, 16, 21) As the Manager, create Avery and
   three Contracts for Avery: A (two Postpaid SIMs), B (one Postpaid SIM and
   one Prepaid SIM), C (nothing). As Avery, open Client Invoices. Show:
   - the bar "{Month Year} · 1 of 3 ready", "Send all 3 to Manager" disabled,
     "2 invoices still need their carrier invoices.";
   - side list rows: A "2 files missing", B "1 file missing", C "✓ Ready";
   - no Send on any card; A's card shows "2 Postpaid SIMs still need their
     carrier invoice." and "Sent with the month, from the bar above.";
   - C's card totals $0.

   Before Avery's first visit, as a second fresh Agent with no Contract, show
   no bar and the "No contracts yet" state.

3. [agent] (stories: 11, 25, 26, 28, 29) Through the API (the page's wording for each code is shown by its component test in step 1):
   - as Avery, `POST /api/client-invoices/{C}/send` and the Contract route for
     C: 409 `{code: MONTH_NOT_SENT}`;
   - as Avery, the month send: 409 `CARRIER_INVOICES_MISSING` with
     `invoiceIds` exactly A and B; query the database: all three `DRAFT`,
     `lines_stored` false, `first_sent_at` null, no line rows;
   - the month send for last month and next month: 409
     `NOT_THE_CURRENT_MONTH`; `2026-13`: 400;
   - as the Manager and as a Tester: 403.

4. [agent] (stories: 17, 21, 23) As Avery, attach the three missing carrier
   invoices through the form. After each, show the row's mark and the bar's
   count move ("2 of 3", then "3 of 3 ready"), and the button enabled. Remove
   one file and show it go back to "2 of 3" and "1 file missing"; attach it
   again.

5. [agent] (stories: 6, 8) As the Manager, add Contract D (nothing on it) for
   Avery. Without reloading Avery's page, click "Send all 3 to Manager" and
   confirm. Show "Your Contracts changed since this page loaded. Reload and try
   again.", nothing sent, and D's draft now in the database. Reload: "4 of 4
   ready". Then, for a third fresh Agent "Blake" with one Contract never
   opened, call the month send without a body: 200, Blake's draft created and
   `SENT` at $0.

6. [agent] (stories: 3, 4, 7, 22, 32) As Avery, click "Send all 4 to Manager".
   Show the confirm's text, Back returning focus, then confirm. Show:
   - the bar "{Month Year} · Sent to Manager on {today}";
   - every row "Awaiting approval" with no readiness mark; no Send anywhere;
   - in the database: four `SENT`, one shared `sent_at` and `first_sent_at`,
     lines stored; the audit log's month line naming Avery, the month and 4.

7. [agent] (stories: 10, 27) Through the API as Avery, month send again: 409
   `MONTH_ALREADY_SENT`. With a fourth fresh Agent whose month is ready, fire
   two month sends in parallel: one 200, one 409, one line row per line.

8. [agent] (stories: 9, 12, 13) Create a fresh Agent "Casey" with one
   Contract holding a Postpaid SIM, and attach its file. As Casey, show
   "1 of 1 ready", "Send 1 to Manager", and, after clicking it, "Confirm
   send". Before confirming, add a second Postpaid SIM to that Contract as the
   Manager in another browser context; then confirm. Show "An invoice is no
   longer ready. Reload to see which." and the invoice still `DRAFT`. The
   "Couldn't load every invoice." state cannot be produced on a healthy stack
   without breaking the backend for every read; show instead its passing
   `client-invoices-view.test.tsx` case by name in the `verify` output.

9. [agent] (stories: 30, 31) As the Tester of A's Client, show A's invoice
   visible and downloadable; as C's Tester, the $0 invoice. As the Manager,
   show the Review Queue listing Avery's four invoices as four rows, each
   approvable on its own.

10. [agent] (stories: 18, 20, 21) As the Manager, approve A and send B back
    with a reason. As Avery, show:
    - B's row "Sent back" with "✓ Ready"; its card's notice and "Resend
      Client Invoice";
    - the bar still "Sent to Manager on {same date}";
    - remove B's file: "1 file missing", Resend disabled with its note;
      attach it again and resend: 200, B `SENT`, A still `APPROVED`, C and D
      untouched, `first_sent_at` of B unchanged.

11. [agent] (stories: 19, 20) As the Manager, add Contract E with a Postpaid
    SIM for Avery. As Avery, show E's row "Draft · 1 file missing", its card's
    "Send Client Invoice" disabled with its note, the bar unchanged. Attach,
    send: 200. Through the API, the per-invoice send of E before attaching is
    409 `CARRIER_INVOICES_MISSING`, not `MONTH_NOT_SENT`.

12. [agent] (stories: 24) At 390px and at 1024px, show the bar above the
    Contract Switcher, wrapping without horizontal scroll, for Priya's unsent
    month (demo) and Avery's sent one.

13. [agent] (stories: 33) Restart the backend on a fresh demo database. Show:
    - Jordan's US-1 and US-2 current-month invoices `SENT`, one file per
      Postpaid SIM line, one shared `first_sent_at`; Jordan's bar sent;
    - Priya's bar unsent, with UK-1's readiness;
    - with SeaweedFS stopped at startup, the demo still starts, logs one WARN,
      and Jordan's month is unsent.

14. [agent] (stories: 32, 35) Show the migration test's output (the backfill
    of a sent, an approved, a sent-back and a never-sent row), the declared
    `CONTEXT.md` entries and the `DESIGN.md` lines.

15. [human] (stories: 1, 2, 3, 7, 18, 21) On your own stack (`rsa-prod`),
    rebuild with your usual `up -d --build`. If an Agent already sent an
    invoice for this month before the update, that Agent's month shows as
    sent and its other drafts send one by one; use an Agent with nothing sent
    this month to see the bar. As that Agent:
    - open Client Invoices: check the bar's count, the disabled "Send all"
      with its reason, and the side list's "✓ Ready" / "files missing";
    - attach the missing carrier invoices and watch the count reach "all
      ready";
    - send the month; check "Sent to Manager on {date}";
    - as the Manager, send one back; as the Agent, resend it from its card.

    Confirm it reads and behaves as you expected.

## Execution order

Five tickets, one PR. The first draft of this section proposed three; two were
split after grepping the existing tests (the first-send rule and the ~19 test
classes it moves are a ticket of their own, and the bar and the cards are two
slices).

1. `send-the-month-api`: the prefactoring (`ClientInvoiceDrafts`,
   `BillingMonth.current()` in the cluster, the shared `markSent`), the
   `first_sent_at` migration and its test, `ClientInvoiceMonthService` and
   `ClientInvoiceMonthController` with the matcher and the coded 409s,
   `monthSentAt` and `sendableOnItsOwn` on the response, the audit line, the
   `sendTheMonth` helper, the concurrency tests. Labels: backend. No
   dependency. (stories: 4, 5, 6, 26, 27, 28, 29, 32)
2. `first-send-only-through-the-month`: the `MONTH_NOT_SENT` gate on the
   per-invoice send, the demo loader's month send, every backend test moved to
   the helper, the e2e global setup that sends Jordan's month. Labels: backend.
   Depends on `send-the-month-api`. (stories: 25, 30, 31, 33, 34)
3. `month-bar-sends-the-month`: the BFF route, the types and
   `clientInvoiceMonth` helper, `SendClientInvoiceMonthControl` in the lead,
   the stub, the Agent page goldens, the `DESIGN.md` month bar entry,
   `createAgentWithLogin`, the journey proof on a fresh Agent. Labels:
   frontend. Depends on `send-the-month-api`. (stories: 1, 2, 3, 7, 8, 9, 10,
   11, 12, 13, 14, 24, 34)
4. `cards-defer-to-the-month-bar`: the card without Send and its two lines,
   Resend and the late Contract's Send, the `MONTH_NOT_SENT` text, the other
   specs' Resend wording, the `attachCarrierInvoicesForEveryPostpaidSim` wait,
   `agent-sends-the-month.spec.ts`. Labels: frontend. Depends on
   `month-bar-sends-the-month` and `first-send-only-through-the-month`.
   (stories: 15, 16, 17, 18, 19, 20, 34)
5. `side-list-readiness-mark`: the `SideListItem` line-2 slot, the readiness on
   the Client Invoices rows, the side-list-rows visual case and the goldens it
   moves, `clientInvoiceRowName`, the `DESIGN.md` side list line, the
   `CONTEXT.md` declarations. Labels: frontend. Depends on
   `cards-defer-to-the-month-bar` (shared view, stub, goldens and helpers).
   (stories: 21, 22, 23, 35)
