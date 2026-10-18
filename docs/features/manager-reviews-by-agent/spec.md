---
feature: manager-reviews-by-agent
epic: client-invoices-sent-by-the-month
status: approved
date: 2026-10-18
---

<!-- sdlc:template spec 1 -->

# The Manager reviews Client Invoices by Agent

## Problem

The human, on 2026-10-18: "The manager UI is impacted because the manager
would need to review invoices for an agent on a given month in bulk. He can
still send one or n back after its review, but on the ui, it would be great if
he can pick an agent > then see a list of all the invoices he needs to review."

Today, after `agent-sends-the-month`:

- An Agent sends a whole month of Client Invoices at once. The Manager receives
  it as separate rows in one flat Review Queue (`/manager/invoices`), mixed
  with every other Agent's invoices and with the Agents' own Agent Invoices,
  filtered only by type. Nothing groups the rows by Agent or by month.
- To get through one Agent's month the Manager opens each row, approves or
  sends it back, returns to the queue, and finds the next row of the same
  Agent among the others.
- Nothing says how far through an Agent's month the Manager is (how many are
  still to review, how many approved, how many sent back).
- Approving is one invoice at a time. Approving a clean month of six takes six
  round trips.

Settled with the human (epic, 2026-10-18), and not reopened here:

- **UI M1**: the Manager's Invoices page gains an Agents side list with a count
  waiting beside each Agent, a dash when none. Picking an Agent shows that
  Agent's month of Client Invoices as a list (Client, total, status: To review /
  Approved / Sent back) with "Approve the N left to review". Each row opens
  today's detail page, which gains "Next invoice →". Approve and Send back stay
  per invoice.
- **A sent-back invoice is resent on its own**; the Manager approves the others
  meanwhile.
- **Specs approved in advance** ("implements this in full without stop"). The
  orchestrator settles the remaining choices and records them.

Settled by the orchestrator (epic, 2026-10-18), told to the human:

- "Approve the N left to review" is included.
- The Agent's own pay invoice is unchanged.

## Journeys

Second and last feature of `docs/roadmap/client-invoices-sent-by-the-month.md`.

- **Bill the Client for the month** (`exists`, stays `exists`). The Manager's
  half now goes by Agent: they pick the Agent, see the month, work through it
  with "Next invoice →" or approve what is left at once. The journey's proof,
  `client-invoice-submission-and-visibility.spec.ts`, gains the Manager step
  through the Agents side list. The journey's prose in `docs/journeys.md` ("the
  Manager approves it") becomes "the Manager reviews the month by Agent and
  approves it". The orchestrator writes it at delivery.
- **Work through what is waiting** (`exists`, stays `exists`). The Review Queue
  stays, unchanged, as the page's "All Agents" view. Its proof,
  `manager-invoice-review-queue.spec.ts`, keeps passing unchanged.
- **Look back at finished invoices** (`wanted`, stays `wanted`). The month
  select below lets a Manager open an Agent's earlier, fully approved month.
  That is a side effect, not this journey: there is no filter across Agents or
  Contracts and no paid Agent Invoice history.

## Goals / Non-goals

Goals:

- An **Agents side list** on `/manager/invoices`: "All Agents" first (today's
  Review Queue), then every Agent of the tenant by name. Beside each row, how
  many invoices wait on the Manager, or "—" when none. Below `xl`, the same
  rows in a dropdown.
- An **Agent's month** view: the month, when the Agent sent it, "{k} of {n} to
  review", the Client Invoices that reached the Manager (Client, status, total,
  Review →), the Agent's own Agent Invoice for that month once sent, and a
  month select.
- **Month approval**: "Approve the {k} left to review" approves exactly the
  invoices the Manager saw as To review, in one transaction, all or nothing,
  behind a two-step confirm.
- **"Next invoice →"** on the Client Invoice detail page: the next invoice still
  to review in that invoice's Agent's month. The breadcrumb goes back to that
  month.
- Approve and Send back stay per invoice, unchanged.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No removal or change of the Review Queue.** `GET /api/review-queue`, its
  response, its order, its type filter and `ReviewQueueView` are unchanged. It
  is the "All Agents" view.
- **No month-level send-back.** The Manager sends back one invoice at a time,
  as today. No "send back the month" and no multi-select send-back.
- **No multi-select approval** ("approve these three"). Month approval approves
  every invoice still To review in the month, or nothing.
- **No approval from the list row.** Rows have Review → only. Approving a
  single invoice stays on its detail page.
- **No change to the Agent Invoice**: not its lifecycle, override, approve, mark
  paid, its detail page or its breadcrumb. It is listed under its Agent. It is
  not approved by month approval.
- **No change to the Agent's or the Tester's pages.** An approval made in bulk
  reads exactly like one made alone.
- **No drafts in the Manager's month.** An invoice the Agent has never sent (a
  late Contract's draft, or a month not yet sent) is not listed. It has not
  reached the Manager.
- **No new dashboard tile or dashboard link.** The Manager dashboard's
  "Pending approvals" card and its links are unchanged. They now land on a
  detail page that has "Next invoice →".
- **No notification or mail** to the Agent when their invoices are approved.
- **No schema change.** No month entity, no month status, no "reviewed" flag.
- **No history browser** across Agents, Contracts or paid Agent Invoices (the
  "Look back" journey).
- **No reordering of the side list by count.** Agents stay in name order.

## User stories

The Agents side list:

1. As a Manager, I want an "Agents" side list on my Invoices page, with one row per Agent of my tenant in name order, so that I can pick whose invoices I review.
2. As a Manager, I want beside each Agent the number of their invoices waiting on me (their Client Invoices to review, plus their Agent Invoice awaiting approval or payment), or "—" when none, so that I see at a glance who needs me.
3. As a Manager, I want "All Agents" as the first row, with the total waiting beside it, selected when I arrive, showing today's Review Queue unchanged (both invoice types, the type filter, longest waiting first), so that nothing waiting is ever hidden and my way of working still works.
4. As a Manager, I want a pick to swap the detail in place and write it to the address (`?agent=`) without a history entry, a modified click to open it in a new tab, and a reload to keep my pick, as on my other side lists.
5. As a Manager on a narrow screen (below `xl`), I want the same rows in a dropdown with the Agents icon, each with its count as a second line ("3 waiting" / "Nothing waiting"), with no sideways scroll, so that I can pick an Agent at any width.
6. As a Manager following a link to an Agent who no longer exists in my tenant, I want the page to open on All Agents, so that a stale link is harmless.

The Agent's month:

7. As a Manager, I want picking an Agent to show one month of theirs headed "{Month Year} · Sent to you on {date} · {k} of {n} to review", so that I see where I stand on it.
8. As a Manager, I want that month's Client Invoices that reached me listed one per row, by Client name, with the Client, the status (To review, Approved, Sent back), the total in its currency, and a Review → that opens today's detail page, so that I see the month as one list.
9. As a Manager, I want the month shown first to be the one that has waited longest (the oldest month with something waiting on me), or else the Agent's latest sent month, so that opening an Agent takes me straight to work.
10. As a Manager, I want a Month select listing every month the Agent has sent, newest first, each with what waits on me in it, written to the address (`?month=`), so that I can open another month.
11. As a Manager, I want an invoice the Agent never sent (a late Contract's draft, or a month not yet sent) left out of the list, so that I only see what reached me.
12. As a Manager, picking an Agent who has never sent anything, I want "{Agent} hasn't sent a month yet." and no list, so that I know why there is nothing.
13. As a Manager opening a month the Agent hasn't sent, I want "{Agent} hasn't sent {Month Year} yet." with the Month select still there, so that I can move to a month that exists.
14. As a Manager, I want the Agent's own Agent Invoice for that month, once sent, as its own row under the list (its status, its total, and Review → its page), so that everything this Agent needs from me for the month is in one place.
15. As a Manager whose month has nothing left to review, I want the header to read "{a} of {n} approved" (with "· {s} sent back" when any) and no approve button, so that a finished month looks finished.
16. As a Manager, when the Agent's month fails to load, I want "Couldn't load {Agent}'s invoices." and "Refresh the page to try again.", so that a failure is not mistaken for an empty month.

Approve the N left to review:

17. As a Manager, I want "Approve the {k} left to review" in the month's header when {k} > 0, opening a two-step confirm ("Approves the {k} Client Invoices of {Agent} for {Month Year} still to review. An approved invoice is final." with Back and "Confirm approve {k}"; "Confirm approve" for one), so that approving many at once is never a single slip.
18. As a Manager, I want the confirm to approve exactly the invoices I saw as To review, all or nothing, with one shared approval time, leaving Approved and Sent back rows untouched, so that a clean month takes one action.
19. As a Manager whose month changed since the page loaded (an invoice sent back or approved elsewhere, or a resend arrived), I want "This month changed since the page loaded. Reload and try again." and nothing approved, so that I never approve an invoice I did not see.
20. As a Manager whose month was already fully approved elsewhere, I want "Nothing is left to review. Reload to see the month." and nothing changed, so that a double approval is harmless.
21. As a Manager, after a month approval, I want the page to refresh: the rows Approved, the header "{n} of {n} approved", and the Agent's count in the side list lowered, so that the page tells the truth.

The detail page:

22. As a Manager, I want Approve and Send back to stay on each invoice's detail page exactly as today, so that a single invoice is still judged on its own.
23. As a Manager, I want "Next invoice →" on a Client Invoice's detail page, opening the next invoice still to review in that invoice's Agent's month, in the month list's order, wrapping round to the start and never the one I am on, so that I work through the month without going back to the list.
24. As a Manager on the month's last invoice to review, I want "Nothing else to review in {Month Year}." where "Next invoice →" would be, so that I know I am done.
25. As a Manager, I want the detail page's breadcrumb to read "Invoices › {Agent} › {Client}", with the Agent crumb opening that Agent's month, so that I return to the list I came from.
26. As a Manager reaching a detail page any other way (the Review Queue, the dashboard, the Contract page), I want the same "Next invoice →" and breadcrumb, worked out from the invoice's own Agent and month, so that every entry leads into the month.
27. As a Manager, when the Agent's month cannot be loaded on the detail page, I want the page as today without "Next invoice →", so that a failed read never blocks a review.

The rules, held by the server:

28. As the business, I want month approval allowed only to a Manager (an Agent or a Tester gets 403), for an Agent of the Manager's own tenant (404 otherwise), for any billing month, so that approving stays the Manager's act and a month can be approved after it closes.
29. As the business, I want month approval refused with a coded 409 and nothing written: `MONTH_CHANGED` when the given invoice ids are not exactly the month's invoices To review, `NOTHING_TO_APPROVE` when none is To review; and a 400 for a malformed month or a missing or empty id list, so that no unseen invoice is approved.
30. As the business, I want month approval all or nothing under concurrency: of two racing month approvals one wins and the other gets 409; a send-back or a resend racing one is serialised with it, so that a month is never half approved.
31. As the business, I want the Agents list, an Agent's months and an Agent's month to be Manager-only reads, tenant-scoped, that never create or change an invoice, so that browsing is safe.

The others:

32. As an Agent, I want my invoices approved in bulk to read on my page exactly as one approved alone ("Approved"), so that nothing changes for me.
33. As a Tester, I want nothing to change, so that a bulk approval is invisible to me.
34. As a Manager, I want my dashboard unchanged: its "Pending approvals" links open the detail page (now with "Next invoice →"), and "Review all" opens All Agents, so that my habits still work.

The record and the team:

35. As the team, I want each month approval to log one status line per invoice, as a single approval does, plus one audit line naming the Agent, the month and the count, so that a bulk approval is traceable.
36. As the team, I want the journey proof's Manager step to go through the Agents side list, a new e2e spec to cover the side list, the month, month approval and "Next invoice →", and every existing e2e spec to pass unchanged, so that the suite proves the new path and keeps the old one.
37. As the team, I want `CONTEXT.md` to name month approval and "To review", and `DESIGN.md` to describe the Agents side list and the month review header, so that the glossary and the design match the product.

## Solution

### Prefactoring: debt paid on the way

- **One approve step.** `ClientInvoiceService.approve` is split into its guard
  and a private step `markApproved(invoice, now, principal)` that sets
  `APPROVED` and `approvedAt` and logs the status change. The per-invoice
  approve and month approval both call it, so the two can never approve
  differently (the same move `markSent` made for the send). Behaviour is
  unchanged.
- **Projection reads, not entity reads, for the new lists.** Debt :96 notes
  that reads which serve stored lines rely on the service's read-only
  transaction. The new reads are constructor-expression projections with their
  totals summed in the query, as `findQueueRows` does. They load no lazy
  association, so they do not deepen that debt.

### The model: derived, no entity

```text
waiting on the Manager (agent)  = the agent's Client Invoices in SENT (any month)
                                + the agent's Agent Invoices in SENT or APPROVED
                                  (exactly the agent's rows of the Review Queue)
reached the Manager (invoice)   = firstSentAt is set
the Manager's month (agent, M)  = the agent's Client Invoices of billing month M
                                  that reached the Manager
review status (invoice)         = TO_REVIEW  if SENT
                                  APPROVED   if APPROVED
                                  SENT_BACK  if DRAFT (and so sentBackAt is set)
months of an agent              = every M with an invoice that reached the Manager,
                                  or a non-DRAFT Agent Invoice
waiting in month M              = TO_REVIEW invoices of M
                                + 1 if the Agent Invoice of M is SENT or APPROVED
```

"All Agents" counts the Review Queue's items, so it is the sum of the Agents'
counts.

### Reads: `ReviewByAgentController`

A new controller in `web` (the invoicing cluster has not moved, ADR 0006).
Every route is under `/api/review-queue/**`, Manager-only at the matcher level
(the existing `/api/review-queue` matcher widens to `/api/review-queue/**`).
Everything is tenant-scoped, read-only and never creates an invoice.

- **`GET /api/review-queue/agents`** → `[{agentId, agentName, waiting}]`, every
  Agent of the tenant ordered by `lower(name)`, then id. `waiting` comes from
  two grouped counts: Client Invoices `SENT` by the Contract's Agent, and Agent
  Invoices `SENT`/`APPROVED` by Agent.
- **`GET /api/review-queue/agents/{agentId}/months`** →
  `[{billingMonth, waiting}]`, newest first, per the model above. Unknown or
  other-tenant Agent → 404.
- **`GET /api/review-queue/agents/{agentId}/months/{YYYY-MM}`** → the
  Manager's month:

  ```text
  { agentId, agentName, billingMonth,
    monthSentAt,                 // repository.monthSentAt(agent, M); null if unsent
    clientInvoices: [ { id, contractId, clientName, reviewStatus,
                        currency, totalAmount, sentAt, approvedAt, sentBackAt } ],
    agentInvoice: { id, status, currency, totalAmount } | null }   // null unless non-DRAFT
  ```

  `clientInvoices` are the invoices that reached the Manager, ordered by
  `lower(clientName)`, then invoice id. That order is the list's and "Next
  invoice →"'s. `totalAmount` is the sum of the stored lines (every listed
  invoice has stored lines: they are stored at send and kept through a
  send-back, ADR 0004/0005). The Agent Invoice total uses the shared
  `AgentInvoiceTotal` expression. 400 for a malformed month, 404 for an
  unknown Agent.

`ClientInvoiceRepository` gains `countSentByAgent(tenantId)`,
`findReviewMonthRows(tenantId, agentId)` and `findReviewRows(tenantId,
agentId, billingMonth)`. `AgentInvoiceRepository` gains
`countWaitingByAgent(tenantId, statuses)` and a non-DRAFT month lookup with
its total. The months list merges the two in the controller's service.

### Month approval

```java
ClientInvoiceMonthApproveResponse approveMonth(UUID agentId, LocalDate billingMonth,
                                               Set<UUID> expectedInvoiceIds,
                                               AuthenticatedPrincipal principal);
```

On `ClientInvoiceService`, `@Transactional`, in this order:

1. lock the Agent's month: `findMonthForUpdate(agentId, billingMonth)`. That is
   the month send's read: Client Invoice rows only, in id order;
2. `toReview` = the locked rows in `SENT`. Empty → 409 `NOTHING_TO_APPROVE`;
3. `expectedInvoiceIds` not equal to the ids of `toReview` → 409
   `MONTH_CHANGED`;
4. one `Instant now`; `markApproved(invoice, now, principal)` for each;
5. `AuditLog.clientInvoiceMonthApproved(agentId, billingMonth, count, actor,
   tenant)`, after the per-invoice status lines.

There is no current-month check: approval has never had one, and a month is
reviewed after it ends.

**Locks.** Send-back, the per-invoice approve, resend and line edits each lock
one Client Invoice row and take no other lock. The month send and month
approval lock the month's rows in id order. Lock order therefore admits no
cycle. A racing single-row write either commits first, and month approval sees
it (`MONTH_CHANGED`), or waits and finds the invoice `APPROVED` (409, as
today). Month approval touches no Agent Invoice. Approving a Client Invoice has
never moved the Agent Invoice.

**HTTP.** `POST /api/agents/{agentId}/client-invoice-months/{YYYY-MM}/approve`,
Manager-only through the existing `/api/agents/**` matcher. It mirrors the
Agent's `/api/me/client-invoice-months/{month}/send`. A new
`ClientInvoiceMonthApprovalController` checks the Agent is in the caller's
tenant (404 otherwise) and uses the shared `ClientInvoiceErrors` handlers.

- Body **required**: `{ "invoiceIds": [uuid, …] }`, non-empty; else 400.
- 200 `{billingMonth, approvedAt, invoiceIds}`.
- 400 malformed month; 403 Agent or Tester; 404 unknown Agent.
- 409 coded `MONTH_CHANGED`, `NOTHING_TO_APPROVE`, body `{code, message}`.

`ClientInvoiceConflictException.Reason` gains `NOTHING_TO_APPROVE`.

### Frontend

**Types** (`lib/api/types.ts`) mirror the three reads and the approval
response. `reviewStatus` is served, never derived in the browser.

**The page** (`app/manager/invoices/page.tsx`) reads `?agent=`, `?month=` and
`?type=` once. It reads the queue and the Agents list in parallel. When
`?agent=` names a listed Agent, it also reads that Agent's months, resolves the
month, and reads that month. An unknown Agent falls back to All Agents. The
month resolves like this, as a pure helper `reviewMonthFor(months, param,
currentMonth)`:

```text
param parses as YYYY-MM                → param
else oldest month with waiting > 0     → it
else newest listed month               → it
else                                   → the current month (an empty state follows)
```

The page renders in `SurfacePage` `bleed`, title "Invoices", subtitle "Waiting
on you, longest waiting first" under All Agents and "{Agent}'s invoices, month
by month" under an Agent.

**`ManagerInvoicesView`** (client) holds the `SideListLayout`:

- `list`: `SideList` labelled "Agents". The first row is `all`, named "All
  Agents". Then one row per Agent in server order. The `badge` slot carries the
  count as plain `text-label` `tnum` `ink-mute` text with a visually hidden
  " waiting" ("3 waiting"). With none, an `aria-hidden` "—" and a visually
  hidden "nothing waiting". Row hrefs: `/manager/invoices` and
  `/manager/invoices?agent={id}`. A pick is a `router.replace` through
  `selectionHref` with `{scroll: false}` (prior art: the Manager's Contracts),
  which drops `?month=` and `?type=`.
- `dropdown`: `ContractSwitcher` with `icon={IconAgents}`. The options are the
  same rows, each with `meta` "{n} waiting" / "Nothing waiting".
- A tenant with no Agent: `list` and `dropdown` are null, and the queue shows
  alone, as today.
- When the Agents list fails to load: no column and no dropdown. The queue
  shows as today.
- Detail under All Agents: `ReviewQueueView` as today. When the queue failed:
  today's "Couldn't load the Review Queue" state.
- Detail under an Agent: `AgentMonthReview`.

**`AgentMonthReview`** (client):

- **Header**: one hairline `Card` row (`px-4 py-3`), a `region` named "Month".
  Left: the `h2` "{Month Year}" (`text-sm` 600), then one sentence.
  - With {k} > 0: "Sent to you on {formatDate(monthSentAt)} · {k} of {n} to
    review", the count `ink-secondary` `tnum`.
  - With {k} = 0: "Sent to you on {date} · {a} of {n} approved", plus " · {s}
    sent back" when any.

  Right: `ApproveClientInvoiceMonthControl` when {k} > 0. Under the sentence:
  the Month select, a native `select` in the Input style labelled "Month". Its
  options are "{Month Year} · {w} waiting", or "{Month Year}" with nothing
  waiting, plus the resolved month if it is not listed. A change is a
  `router.replace` to `?agent=&month=`. Below `md` the button wraps under the
  text, right-aligned.
- **List**: `Table` in `TableScroll` (today's queue table look). The columns
  are Client, Status, Total, and an action column with a visually hidden name.
  - Status: the Client Invoice `Badge` with Manager labels "To review"
    (warning), "Approved" (success), "Sent back" (warning, with
    `IconAlertTriangle` in place of the dot, as the Agent's side list mark).
  - Action: the queue's row-tone "Review →" link, `aria-label` "Review Client
    Invoice: {Client} — {Agent}, {Month Year}" (the queue's wording), to
    `/manager/invoices/client/{id}`.
- **Agent Invoice row**: when `agentInvoice` is present, one hairline `Card`
  row under the table: "Agent Invoice", its existing Agent Invoice status
  `Badge`, its total, and a row-tone "Review →" (`aria-label` "Review Agent
  Invoice: {Agent}, {Month Year}", the queue's wording), to
  `/manager/invoices/agent/{id}`.
- **Empty and failed states** (`EmptyState`, `IconInbox`):
  - no months at all: "{Agent} hasn't sent a month yet.", with "Their Client
    Invoices appear here once they send their month." No select;
  - an unsent month: "{Agent} hasn't sent {Month Year} yet.", with the select
    above it;
  - a month with no Client Invoice but a sent Agent Invoice: the header without
    a count ("No Client Invoice sent for {Month Year}.") and the Agent Invoice
    row;
  - a failed months or month read: "Couldn't load {Agent}'s invoices.",
    "Refresh the page to try again." (`IconAlertTriangle`).

**`ApproveClientInvoiceMonthControl`** follows `SendClientInvoiceMonthControl`
(the two-step inline confirm, its focus handling, `aria-busy`):

- the primary `sm` pill "Approve the {k} left to review";
- the confirm: "Approves the {k} Client Invoices of {Agent} for {Month Year}
  still to review. An approved invoice is final.", with a `ghost` Back and the
  primary "Confirm approve {k}" ("Confirm approve" for one; "Approving…" while
  pending);
- `POST` to a new BFF route mirroring the backend path, through `forwardJson`,
  with the body `{invoiceIds}` (the month's To review ids). On 200,
  `refreshPage`;
- errors under the confirm, `role="alert"`, `danger` `text-label-sm`:
  - `MONTH_CHANGED`: "This month changed since the page loaded. Reload and try again.";
  - `NOTHING_TO_APPROVE`: "Nothing is left to review. Reload to see the month.";
  - any other: "Couldn't approve. Try again."; a network failure: the existing
    "Couldn't reach the server…" line.

**The detail page** (`app/manager/invoices/client/[invoiceId]`):

- The page already reads the invoice and `/api/contracts`. From the Contract it
  takes `agentId` and `agentName` and reads
  `/api/review-queue/agents/{agentId}/months/{YYYY-MM}` of the invoice's
  billing month.
- Breadcrumb: "Invoices" (`/manager/invoices`, unchanged), then "{Agent}"
  (`/manager/invoices?agent={id}&month={YYYY-MM}`), then "{Client}". With the
  Contract not found: today's two crumbs.
- `ReviewMonthNext`, a new component rendered by the page above
  `ClientInvoiceDetailView` and right-aligned. It renders nothing when the
  month read failed.
  - With a next invoice: a secondary `sm` link "Next invoice →"
    (`IconArrowRight`), with accessible name "Next invoice: {Client}".
  - With none: `text-label` `ink-mute` "Nothing else to review in {Month Year}."
- `nextToReview(clientInvoices, currentId)` is a pure helper. It returns the
  first `TO_REVIEW` row after the current one in list order, wrapping round and
  never the current one. When the current invoice is not listed, it returns the
  first `TO_REVIEW` row. With none, it returns null. It is computed at page
  load and not recomputed after an in-place Approve or Send back (the current
  invoice is excluded anyway).
- `ClientInvoiceDetailView` is unchanged.

**The visual stub backend** serves the three reads:

- Agents: three Agents, one with "3" and one with "—".
- Jordan's stub month: one To review, one Approved, one Sent back, and a sent
  Agent Invoice. Its earlier month, fully approved, for the select.
- The month of the stub's Manager detail invoice, with a next invoice to
  review, so that its golden shows "Next invoice →".

## Design direction

Design slot: `impeccable`. `DESIGN.md` is the world. No new token or colour.

- **The Agents side list** is the shared `SideList` as it stands. The count
  sits in the badge column as quiet `ink-mute` `tnum` text, not a `Badge`. A
  count is not a status, and the badge column already cuts names (debt :76),
  so a short number is the lightest thing that can sit there. "—" reads "none"
  without a zero shouting.
- **The month header** is the Month bar's shape (`agent-sends-the-month`),
  mirrored for the Manager: one hairline row, the month and one sentence on the
  left, the action on the right. "Approve the {k} left to review" is the screen's
  one pill. Row "Review →" links keep the row tone. Once nothing is left to
  review, the header holds no button.
- **The list** reuses the Review Queue's table and its row-tone Review link, so
  the two views of the same page read as one family.
- **"Next invoice →"** is a secondary `sm` control, never a pill. The detail
  page's Approve stays its one pill.
- **`DESIGN.md`** gains, in the same commit as the code:
  - in Side list, the Manager's Invoices as a user ("Agents": "All Agents" then
    every Agent by name, the count waiting as `ink-mute` `tnum` text in the
    badge column, "—" when none);
  - in Contract Switcher, the Agents icon for that page's dropdown;
  - a "Month review" entry under Components (the header, the one-pill rule, the
    Manager status labels To review / Approved / Sent back, and "Next invoice →"
    as a secondary control above the detail card).

## Constraints

- Inherits every constraint of `agent-sends-the-month`,
  `send-a-client-invoice-back`, `manager-invoice-review-queue` and ADR 0001,
  0004 and 0005, except where this spec says otherwise.
- **All or nothing.** Month approval writes in one transaction. A refusal
  writes nothing.
- **One lock order**: Client Invoice rows only, in id order, through
  `findMonthForUpdate`. No Agent Invoice lock and no store call inside month
  approval's transaction (Backend 8).
- **HTTP contract changes. Nothing else changes**: three new Manager-only
  `GET`s under `/api/review-queue/**`, one new `POST` under `/api/agents/**`,
  and one new 409 code. `GET /api/review-queue`, the per-invoice approve and
  send-back, and `ClientInvoiceResponse` are unchanged.
- **No migration.** No new column, index or table.
- **Packages**: new classes go in the layer packages (`web`, `dto`,
  `repository`) because the invoicing cluster has not moved (ADR 0006,
  Backend 13).
- **No new dependency**, in main or test scope.
- Every isolated run (tests, e2e, acceptance) uses its own throwaway Postgres
  and SeaweedFS. Nothing touches the human's compose project `rsa-prod`, its
  ports 3000/8080/5432 or its volumes. The e2e suite runs on an idle machine.
  A failure is never called a flake without a controlled comparison. Maven runs
  with JDK 21.

## Testing decisions

Tests assert external behaviour: HTTP status and body, the rows a request
leaves, and what a user sees and can do on the page.

1. **The HTTP seam, real Postgres (`IntegrationTest`)**, using a fresh Agent per
   case with `ClientInvoiceMonthTestSupport.sendTheMonth` (prior art
   `ClientInvoiceMonthSendApiTest`, `ReviewQueueApiTest`):
   - new `ReviewByAgentApiTest`:
     - the Agents list: names in order, `waiting` counting `SENT` Client
       Invoices and a `SENT`/`APPROVED` Agent Invoice, 0 for an Agent with
       nothing, another tenant's Agents absent;
     - the sum of `waiting` equals the Review Queue's length;
     - months: newest first, `waiting` per month, an Agent Invoice-only month
       listed, a never-sent draft not;
     - the month: rows ordered by Client name, `reviewStatus` for SENT,
       APPROVED and a sent-back draft, totals from stored lines, a late
       never-sent draft absent, `monthSentAt`, `agentInvoice` null while
       `DRAFT` and present once sent;
     - 404 unknown or other-tenant Agent; 400 malformed month; 403 for an Agent
       and a Tester on all three;
     - no invoice created by any read;
   - new `ClientInvoiceMonthApproveApiTest`:
     - three sent, approve with the three ids: 200, all `APPROVED` with one
       `approvedAt`;
     - a sent-back one in the month: untouched, the others approved;
     - ids short by one, or with an extra: 409 `MONTH_CHANGED`, nothing
       approved;
     - nothing `SENT`: 409 `NOTHING_TO_APPROVE`;
     - a past month approves;
     - missing body or `[]`: 400; `2026-13`: 400;
     - Agent and Tester: 403; another tenant's Agent: 404;
     - after approval, a send-back of one is 409 as today, and the Agent's GET
       of the invoice reads `APPROVED`.
   No existing backend test file changes. `ClientInvoiceApiTest`'s approve
   cases prove the `markApproved` split leaves the single approve unchanged.
2. **Concurrency** (outside the shared transaction, prior art
   `ClientInvoiceMonthSendConcurrentTest`), new
   `ClientInvoiceMonthApproveConcurrentTest`:
   - two month approvals: one 200, one 409 (`NOTHING_TO_APPROVE`), each invoice
     approved once;
   - a month approval racing a send-back of one of its invoices: either the
     send-back wins and the approval gets `MONTH_CHANGED` with nothing
     approved, or the approval wins and the send-back gets 409. Never some
     approved and others not.
3. **Unit (JUnit 5 + Mockito)** only where cheaper than a database case: the
   order of month approval's checks.
4. **Frontend component tests (Vitest + Testing Library)**, all new files:
   - `manager-invoices-view.test.tsx`:
     - the side list rows, their order and accessible names ("Avery Stone 3
       waiting", "Blake Moss nothing waiting");
     - All Agents selected by default with the queue rendered;
     - a pick calling `router.replace` with `?agent=`;
     - the dropdown's options and meta;
     - no column with no Agents.
   - `agent-month-review.test.tsx`:
     - the header sentence in each state;
     - the rows, statuses and Review link names;
     - the Agent Invoice row present and absent;
     - the select's options and its `router.replace`;
     - each empty and failed state.
   - `approve-client-invoice-month-control.test.tsx`:
     - the label for many and for one;
     - the confirm's text and focus;
     - the request body;
     - each error code's text.
   - `review-month-next.test.tsx`: the link with its accessible name, and
     "Nothing else to review in {Month Year}.".
   - `review-months.test.ts`: `reviewMonthFor` and `nextToReview`,
     table-driven.

   `review-queue-view.test.tsx`, `client-invoice-detail-view.test.tsx` and
   `approve-client-invoice-control.test.tsx` do not change.
5. **End to end, on the isolated stack.**
   - New `manager-reviews-by-agent.spec.ts`, with a fresh Agent per test
     (`createAgentWithLogin`, `createClientAndContractWithAgent`,
     `attachCarrierInvoice`, `selectInSideList(page, "Agents", …)`):
     - the side list count;
     - the month list;
     - working through with "Next invoice →" (send back one, approve one);
     - the breadcrumb back to the month;
     - "Approve the {k} left to review" after a stale page refuses with
       `MONTH_CHANGED`, then succeeds after a reload;
     - the Agent Invoice row;
     - the dropdown at 390px with no sideways scroll.
   - The journey proof `client-invoice-submission-and-visibility.spec.ts`
     keeps its Contract-page step, which approves the Postpaid Contract's
     invoice. It then opens the fresh Agent from the Agents side list, sees "1
     of 2 to review", and approves the empty Contract's invoice with "Approve
     the 1 left to review", ending on "2 of 2 approved".
   - Every other e2e spec is expected to pass unchanged. All Agents is the
     default view, the queue's Review links and the breadcrumb's "Invoices"
     link keep their names and hrefs, and the new links never repeat a queue
     link's name on one page: `manager-invoice-review-queue`,
     `send-a-client-invoice-back` (its 390px overflow check now includes the
     dropdown), `edit-client-invoice-lines`, `manager-dashboard`,
     `agent-dashboard`, `client-invoices-side-list` and
     `agent-sends-the-month`. A ticket that finds one must change returns
     `blocked` with the reason rather than editing it unlisted.
6. **Visual goldens.**
   - New: `manager-invoices-{desktop,mobile}-{light,dark}` (All Agents) and
     `manager-invoices-agent-{…}` (Jordan's stub month), from two new entries
     in `surfaces.spec.ts`.
   - Moved: `manager-client-invoice-check-mark-{…}` (the breadcrumb's Agent
     crumb and "Next invoice →"), from `client-invoice-files.spec.ts`
     unchanged.
   - No other golden should move.
7. **`verify`** stays the command in `docs/agents/sdlc.json`.

## Decisions taken

- **(after review) The Review Queue table is `dense` and its subject cell wraps (`min-w-40`), under All Agents and alone.** Reason: beside the 256px side list at 1440px the queue clipped its Review link; its content, order, type filter and API are unchanged, only its cell padding and wrapping. Replaces "`ReviewQueueView` unchanged" for its layout only. (Orchestrator under the human's advance approval, 2026-10-18; spec review F1.)
- **Spec approved by the human in advance** ("implements this in full without stop", epic 2026-10-18).
- **The Review Queue stays, as the side list's first row "All Agents", selected by default.** (settled by the orchestrator under the human's advance approval) Reason: it keeps the "Work through what is waiting" journey, the dashboard's "Review all" link and every existing e2e spec working unchanged. Nothing waiting, of either type, is ever behind a pick.
- **The Agent Invoice keeps two homes: unchanged in the Review Queue, and as its own row under its Agent's month once sent.** (settled by the orchestrator under the human's advance approval) Reason: the human asked to "pick an agent > then see a list of all the invoices he needs to review", and the Agent's pay invoice is one of them. It is reviewed on its own page (override, approve, mark paid), so it is a row with Review →, never part of month approval.
- **An Agent's count is their rows of the Review Queue: Client Invoices `SENT` plus their Agent Invoice `SENT` or `APPROVED`; "—" when none.** (settled by the orchestrator under the human's advance approval) Reason: the side list then partitions the queue exactly. "All Agents" is the sum, and a dash always means nothing waits, which would be false if an Agent Invoice awaiting payment were left out.
- **Every Agent of the tenant is listed, in name order, including one with no Contract or a deactivated login.** (settled by the orchestrator under the human's advance approval) Reason: stable positions beat a list that reorders as counts change. The count already says who needs attention, and an Agent with history must stay reachable.
- **The Manager's month lists only invoices that reached the Manager (`firstSentAt` set); never-sent drafts are left out.** (settled by the orchestrator under the human's advance approval) Reason: the Manager reviews what was sent. A draft's total is still moving and has nothing to act on.
- **Manager status labels "To review", "Approved", "Sent back", served as `reviewStatus`.** (settled by the orchestrator under the human's advance approval) Reason: they are the human's words for this list. Serving them keeps the rule in the backend.
- **The month shown first is the oldest with something waiting, else the newest sent, else the current month; a Month select lists every sent month.** (settled by the orchestrator under the human's advance approval) Reason: invoices are sent during their month and reviewed after it may have ended, so "current month" would open November on an empty page while October waits. Longest-waiting-first is the queue's own rule. The select keeps an older resend or a past month reachable.
- **The month header reads "Sent to you on {date} · {k} of {n} to review", or "{a} of {n} approved[ · {s} sent back]" when nothing is left.** (settled by the orchestrator under the human's advance approval) Reason: it mirrors the Agent's Month bar ("Sent to Manager on {date}").
- **Month approval approves exactly the ids the page showed as To review; any difference is `MONTH_CHANGED`, all or nothing.** (settled by the orchestrator under the human's advance approval) Reason: an approved invoice is final (no un-approve exists). Approving one the Manager did not see, such as a resend that arrived meanwhile, would be the wrong product quietly. The same rule as the month send's `invoiceIds`.
- **The id list is required for month approval (unlike the month send's optional one).** Reason: approval is a judgement on what was reviewed. An API caller must say what it judged.
- **A two-step inline confirm for month approval, while the single Approve stays one click.** (settled by the orchestrator under the human's advance approval) Reason: the single Approve's own doc reasons that it follows a review on the same page. Month approval is reachable from a list, the case that doc names as needing a confirm.
- **Confirm and error copy as written in the Solution; "Confirm approve" for one.** (settled by the orchestrator under the human's advance approval) Reason: it follows the human's "Approve the N left to review" and the month send's tone.
- **No current-month check on month approval.** Reason: approval never had one, and a month is reviewed after it ends.
- **Month approval locks through `findMonthForUpdate`, the month send's read.** Reason: one lock order (Client Invoice rows by id) for every multi-row write. Single-row writes take one lock, so no cycle is possible.
- **"Next invoice →" goes to the next invoice still To review in the month's list order, wrapping round, never the current one; with none, "Nothing else to review in {Month Year}."** (settled by the orchestrator under the human's advance approval) Reason: "to work through the Agent's month" means through what is left. Stepping onto approved rows would waste the Manager's clicks. Wrapping catches an earlier row skipped on the way.
- **"Next invoice →" and the Agent crumb appear on every Client Invoice detail page, from the invoice's own Agent and month, whatever the way in.** (settled by the orchestrator under the human's advance approval) Reason: no `?from=` state to carry or lose. A dashboard or queue entry lands in the month too.
- **"Next invoice →" is computed at page load and not recomputed after an in-place Approve or Send back.** Reason: the current invoice is already excluded, so the link stays right. A stale target (approved elsewhere) opens a read-only page, which is harmless.
- **The breadcrumb becomes "Invoices › {Agent} › {Client}"; the page title stays "{Client} — {Agent}".** (settled by the orchestrator under the human's advance approval) Reason: the Agent crumb is the way back to the month. The title and the "Invoices" crumb keep the names existing specs and habits use.
- **Below `xl`, the side list's rows become the shared `ContractSwitcher` with the Agents icon and the count as the option's meta line.** (settled by the orchestrator under the human's advance approval) Reason: DESIGN.md's side list rule (exactly one of column or dropdown at any width, a per-page icon).
- **A pick drops `?month=` and `?type=`.** Reason: each Agent opens on their own default month, and the type filter belongs to All Agents.
- **Reads under `/api/review-queue/**`; the write under `/api/agents/{id}/client-invoice-months/{month}/approve`.** Reason: the reads are views of the Review Queue (Manager-only already). The write mirrors the Agent's `/api/me/client-invoice-months/{month}/send` and falls under the Manager-only `/api/agents/**`.
- **Projection queries with totals summed in SQL for every new read.** Reason: `findQueueRows` is the prior art, and debt :96 warns against entity reads that rely on a read-only transaction.
- **`markApproved` extracted and shared.** Reason: the single approve and month approval must never differ; `markSent` set the pattern.
- **Audit: one `clientInvoiceMonthApproved` line per month approval, on top of each invoice's status line.** Reason: per-invoice lines stay comparable with a single approval, and the month line ties them together.
- **No migration.** Reason: everything is derived from existing columns (`first_sent_at`, `status`, `sent_back_at`), and `uq_client_invoices_contract_billing_month` already indexes the month lookups by Contract.
- **Test seams: the existing HTTP seam with real Postgres, component tests at the new views and control, e2e through the real page; no new seam.** Reason: those seams already prove the queue, approve, send-back and the month send.
- **Four tickets, one PR (below).** Reason: the reads are demoable by API alone. The side list and the month list are one read-only slice. Month approval and "Next invoice →" are two independent user-facing acts on top of it, cut apart so each fits one context.

## Open questions

None

## Acceptance walkthrough

The `[agent]` steps run against an isolated stack the agent starts and tears
down itself. It never touches the human's compose project `rsa-prod`, ports
3000/8080/5432 or its volumes:

- a throwaway Postgres and a throwaway SeaweedFS (`docker run --rm`, exact
  names, free ports, the pinned tag, dev-only credentials);
- the backend on the `demo` profile on a free port;
- a production build of the frontend on a free port.

Logins are in the root `README.md`. "Avery" and "Blake" are fresh Agents the
walkthrough creates as the Manager (Create agent, login shown once).

1. [agent] (stories: 36, 37) Run `verify` on an idle machine. Show:
   - it is green;
   - the new goldens `manager-invoices-*` and `manager-invoices-agent-*` exist;
   - `manager-client-invoice-check-mark-*` moved (the Agent crumb, "Next
     invoice →"), and no other golden moved;
   - no throwaway container is left behind.

2. [agent] (stories: 1, 2, 3, 4, 6, 34) As the Manager, create Avery with three
   Contracts: A and B (one Postpaid SIM each) and C (nothing). Create Blake
   with one Contract. As Avery, attach both files and send the month.
   - As the Manager, open `/manager/invoices`. Show the "Agents" column: "All
     Agents" first and selected, its count equal to the queue's rows; Avery "3
     waiting"; Blake "—". Show the queue as before, with its type filter and
     Review links.
   - Pick Avery: the address gains `?agent=` with no new history entry.
     Reload: Avery is still selected.
   - Cmd-click Blake: a new tab opens on Blake.
   - Open `/manager/invoices?agent=<random uuid>`: All Agents.
   - From the dashboard, "Review all" opens All Agents.

3. [agent] (stories: 7, 8, 11, 12, 15) On Avery, show:
   - the header "{Month Year} · Sent to you on {today} · 3 of 3 to review"
     and "Approve the 3 left to review";
   - rows A, B, C in Client name order, each "To review" with its total and a
     Review link.

   As the Manager, add Contract D for Avery. As Avery, open D's card (a late
   draft). Back as the Manager, show D absent from Avery's month. On Blake:
   "Blake hasn't sent a month yet.", no list, no button.

4. [agent] (stories: 22, 23, 24, 25, 26) From Avery's month, open A. Show:
   - the breadcrumb "Invoices › Avery › A";
   - "Next invoice →" named "Next invoice: B".

   Then work through the month, leaving A to review:
   - follow Next to B and send B back with a reason;
   - B's "Next invoice →" goes to C. Approve C;
   - C's Next goes to A (wrapped round). Reload C: still A;
   - on A, show "Nothing else to review in {Month Year}." (B is sent back, C
     approved);
   - click the Avery crumb: Avery's month with A "To review", B "Sent back", C
     "Approved", the header "1 of 3 to review" and "Approve the 1 left to
     review";
   - open any invoice still waiting from the dashboard's Pending approvals
     card. Show its Agent crumb and its "Next invoice →" (or "Nothing else to
     review") there too.

5. [agent] (stories: 15, 17, 18, 19, 21, 35) As the Manager, open Avery's month
   in a page ("1 of 3 to review") and keep it open. As Avery, in another
   context, resend B from its card.
   - On the open page, click "Approve the 1 left to review". Show the
     confirm's text, Back returning focus, then confirm: "This month changed
     since the page loaded. Reload and try again." Query the database: A and B
     still `SENT`.
   - Reload: "2 of 3 to review", "Approve the 2 left to review". Confirm: A and
     B `APPROVED` with one shared `approved_at`, C untouched.
   - Show the header "3 of 3 approved", no button, and Avery's count "—".
   - Show in the logs one status line per invoice plus the month approval line
     naming Avery, the month and 2.

6. [agent] (stories: 20, 28, 29, 30, 31, 32, 33) Through the API:
   - month approval with stale ids: 409 `MONTH_CHANGED`, nothing approved;
   - with nothing `SENT`: 409 `NOTHING_TO_APPROVE`;
   - with no body, `[]`, or `2026-13`: 400;
   - as an Agent and as a Tester: 403, also on the three reads;
   - for an unknown Agent: 404;
   - for a fresh Agent with a sent month, two parallel month approvals: one
     200, one 409.

   Show:
   - the three reads leave the `client_invoices` row count unchanged;
   - as Avery, A reads "Approved" on the Agent's page as a single approval
     would;
   - as A's Tester, A is visible as before.

7. [agent] (stories: 9, 10, 13, 14) As Avery, send the Agent Invoice from My
   Invoice. As the Manager, show Avery "1 waiting" and the Agent Invoice row
   under Avery's month, "Awaiting approval", whose Review opens its page. Then
   insert a Client Invoice for Avery in the previous month directly in the
   database (`SENT`, with `first_sent_at` and stored lines) and reload Avery.
   Show:
   - the previous month opens by default (oldest waiting);
   - the Month select lists both months, newest first, with their waiting
     counts;
   - picking the current month writes `?month=`;
   - `?month=2025-01` reads "Avery hasn't sent January 2025 yet." with the
     select;
   - through the API, month approval of the previous month: 200 (a past
     month approves).

8. [agent] (stories: 6, 16, 27) Insert an Agent of the second tenant in the
   database.
   - Open `/manager/invoices?agent=<its id>`: the page opens on All Agents.
   - Its months read answers 404.

   The failed-read states ("Couldn't load {Agent}'s invoices.", and no "Next
   invoice →" when the month read fails) cannot be produced on a healthy stack
   without breaking every read. Show their passing component tests by name in
   the `verify` output instead.

9. [agent] (stories: 5) At 390px and 1024px, on `/manager/invoices`, show:
   - the Agents dropdown with the Agents icon, each option with its "{n}
     waiting" / "Nothing waiting" line;
   - picking Avery shows the month with the button wrapped under the header
     text;
   - no sideways scroll on the page, the month or the detail page.

10. [human] (stories: 1, 2, 7, 8, 17, 23, 25) On your own stack (`rsa-prod`),
    rebuild with your usual `up -d --build`. Open Invoices as the Manager:
    - check the Agents list on the left and the counts beside each Agent;
      "All Agents" is the queue you know;
    - pick an Agent whose month is sent: check the list (Client, status,
      total) and the header;
    - open one invoice and use "Next invoice →" to go through the month;
      approve or send back as you would; use the Agent crumb to get back;
    - on a month with several left to review, try "Approve the N left to
      review".

    Confirm it reads and behaves as you expected.

## Execution order

Five tickets, one PR. Each ticket lists every existing test file it changes.
The merger refuses any other.

1. `review-by-agent-reads`: the three reads with their projection queries and
   the widened matcher. Split from the former single backend ticket after the
   critic (R2); runs in parallel with the next. Tests: new `ReviewByAgentApiTest`.
   No existing test class changes. Labels: backend. No dependency. (stories: 31)
2. `approve-a-month-api`: the `markApproved` split; month approval, its
   controller, `NOTHING_TO_APPROVE` and its audit line. Tests: new
   `ClientInvoiceMonthApproveApiTest`, `ClientInvoiceMonthApproveConcurrentTest`
   (and optionally a new `ClientInvoiceMonthApproveServiceTest`);
   `ClientInvoiceMonthTestSupport` may gain an approve helper (the one existing
   test-source file it may change). Labels: backend. No dependency. (stories:
   28, 29, 30, 32, 33, 35)
3. `agents-side-list-on-invoices`: the types; the page's reads and
   `reviewMonthFor`; `ManagerInvoicesView` with the side list and the dropdown;
   `AgentMonthReview` read-only (header without the button, list, Agent Invoice
   row, select, empty and failed states); the stub's reads; the two new
   `surfaces.spec.ts` entries and their new goldens; the `DESIGN.md` side list
   and Contract Switcher lines. Tests:
   - new `manager-invoices-view.test.tsx`, `agent-month-review.test.tsx`,
     `review-months.test.ts`, `manager-reviews-by-agent.spec.ts` (the side list
     and month cases);
   - changed: `tests/visual/surfaces.spec.ts`, `tests/visual/stub-backend.mjs`.

   Labels: frontend. Depends on `review-by-agent-reads`. (stories: 1, 2, 3, 4, 5,
   6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 34)
4. `approve-the-rest-of-the-month`: `ApproveClientInvoiceMonthControl` in the
   header, its BFF route, the copy and errors, and the `DESIGN.md` "Month
   review" entry. Tests:
   - new `approve-client-invoice-month-control.test.tsx`;
   - changed: `agent-month-review.test.tsx` (the button's presence),
     `manager-reviews-by-agent.spec.ts` (the approval cases), the
     `manager-invoices-agent-*` goldens (the pill), `stub-backend.mjs` if the
     stub needs a To review row for the pill.

   Labels: frontend. Depends on `agents-side-list-on-invoices` and `approve-a-month-api`. (stories: 17,
   18, 19, 20, 21)
5. `next-invoice-on-the-detail-page`: the detail page's month read, the
   breadcrumb's Agent crumb, `ReviewMonthNext` and `nextToReview`, the stub
   month for the detail invoice, the journey proof's Manager step, and the
   `CONTEXT.md` declarations. Tests:
   - new `review-month-next.test.tsx`;
   - changed: `review-months.test.ts` (`nextToReview` cases),
     `manager-reviews-by-agent.spec.ts` (the Next and breadcrumb cases),
     `client-invoice-submission-and-visibility.spec.ts`,
     `tests/visual/stub-backend.mjs`, the
     `manager-client-invoice-check-mark-*` goldens.

   Labels: frontend. Depends on `approve-the-rest-of-the-month` (shared stub,
   spec file and goldens). (stories: 22, 23, 24, 25, 26, 27, 36, 37)
