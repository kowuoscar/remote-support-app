---
id: agents-side-list-on-invoices
title: Pick an Agent on the Invoices page and see their month of Client Invoices
status: in-progress
depends_on: [review-by-agent-reads]
labels: [frontend]
stories: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 34]
---

## Context

`spec.md` `### Frontend` (Types, The page, `ManagerInvoicesView`, `AgentMonthReview`, the visual stub backend), `## Design direction`, `## Testing decisions` items 4 to 6. Depends on `review-by-agent-reads` for the three reads. Frontend DoD in `docs/agents/frontend.md` applies; read `DESIGN.md` before the UI.

What lands: types in `lib/api/types.ts` for the three reads; the page `app/manager/invoices/page.tsx` reading `?agent=`, `?month=`, `?type=` once, reading the queue and the Agents list in parallel and, for a listed Agent, its months and month; the pure helper `reviewMonthFor(months, param, currentMonth)` in its own file under `frontend/lib/` (name the file beside the other pure helpers; `nextToReview` is added to the same file by `next-invoice-on-the-detail-page`); `ManagerInvoicesView` (Agents `SideList` with "All Agents" first, `ContractSwitcher` with `IconAgents` below `xl`; All Agents renders `ReviewQueueView` unchanged); `AgentMonthReview` read-only (header without the button, the table, the Agent Invoice row, the Month select, the empty and failed states). `DESIGN.md` gains the Side list and Contract Switcher lines in this commit (Rule 7). The stub backend serves the three reads (three Agents, one "3" and one "—"; Jordan's month with To review, Approved, Sent back and a sent Agent Invoice; an earlier fully approved month).

Not in this ticket: the approve button (`approve-the-rest-of-the-month`), "Next invoice →" and the breadcrumb change (`next-invoice-on-the-detail-page`). The header's `Right` slot stays empty; the next ticket fills it.

## Acceptance criteria

- [ ] On `/manager/invoices` the Manager sees an "Agents" side list: "All Agents" first and selected with the total waiting beside it, then every Agent in name order with "{n} waiting" (accessible name "Avery Stone 3 waiting") or "—" read as "nothing waiting"; All Agents shows today's Review Queue with its type filter and Review links unchanged; the dashboard's "Review all" lands on it.
- [ ] Picking an Agent swaps the detail in place and writes `?agent=` with no history entry (dropping `?month=` and `?type=`); a modified click opens the Agent in a new tab; a reload keeps the pick; `?agent=` naming no Agent of the tenant opens All Agents; a tenant with no Agent, or a failed Agents read, shows the queue alone with no column and no dropdown.
- [ ] Below `xl` the same rows are a dropdown with the Agents icon, each option with "{n} waiting" or "Nothing waiting" as its second line, and the page, the month and the detail page do not scroll sideways at 390px.
- [ ] An Agent's month opens on the oldest month with something waiting, else the newest sent month, headed "{Month Year} · Sent to you on {date} · {k} of {n} to review" (or "{a} of {n} approved" plus " · {s} sent back" when any, with no button), listing the Client Invoices that reached the Manager one per row by Client name with Client, status (To review, Approved, Sent back), total in its currency and "Review →" (accessible name "Review Client Invoice: {Client} — {Agent}, {Month Year}") to today's detail page; a late never-sent draft is absent.
- [ ] The Month select lists every month the Agent has sent, newest first, each "{Month Year} · {w} waiting" or "{Month Year}", and a change writes `?month=`; an unsent month reads "{Agent} hasn't sent {Month Year} yet." with the select still there; an Agent who never sent reads "{Agent} hasn't sent a month yet." with no list and no select.
- [ ] Once the Agent's Agent Invoice for the month is sent, a row under the table shows "Agent Invoice", its status badge, its total and "Review →" (accessible name "Review Agent Invoice: {Agent}, {Month Year}") to `/manager/invoices/agent/{id}`; it is absent while a draft; a month with no Client Invoice but a sent Agent Invoice reads "No Client Invoice sent for {Month Year}." with that row.
- [ ] A failed months or month read shows "Couldn't load {Agent}'s invoices." with "Refresh the page to try again.". The goldens `manager-invoices-{desktop,mobile}-{light,dark}` (All Agents) and `manager-invoices-agent-{...}` (Jordan's stub month) exist; no other golden moves.

## Tests

Seams per spec `## Testing decisions` items 4 to 6 (Vitest + Testing Library; Playwright e2e on the isolated stack; the visual stub). vitest collects only `*.test.tsx`, so the helper's test file is `.test.tsx`.

- **`components/manager/manager-invoices-view.test.tsx` (new):** side list rows, their order and accessible names ("Avery Stone 3 waiting", "Blake Moss nothing waiting"); All Agents selected by default with the queue rendered; a pick calls `router.replace` with `?agent=`; the dropdown's options and meta; no column and no dropdown with no Agents or a failed Agents read.
- **`components/manager/agent-month-review.test.tsx` (new):** the header sentence in each state; the rows, statuses and Review link names; the Agent Invoice row present and absent; the select's options and its `router.replace`; each empty state and the failed state.
- **`lib/review-months.test.tsx` (new, table-driven; the spec calls it `review-months.test.ts`, renamed to be collected):** `reviewMonthFor` for a valid param, oldest waiting, newest listed, none (current month), a malformed param.
- **`tests/e2e/manager-reviews-by-agent.spec.ts` (new, fresh Agent per test with `createAgentWithLogin`, `createClientAndContractWithAgent`, `attachCarrierInvoice`, `selectInSideList(page, "Agents", …)`):** the side list count and All Agents default; the month list; a reload keeps the pick; the Agent Invoice row; the dropdown at 390px with no sideways scroll.
- **Visual:** two new `surfaces.spec.ts` entries; goldens captured new.

## Regression

At risk: `/manager/invoices` for every spec that opens it and clicks a queue Review link (All Agents is the default and renders `ReviewQueueView` as before; the new month rows and the dropdown are not on the page until an Agent is picked, so no queue link name is repeated), the `ReviewQueueView` and its type filter, the Manager's other side-list pages, the overflow check in `send-a-client-invoice-back.spec.ts` (375px on the Agent page, not this page; unaffected).

Existing tests expected to change, each with the reason:

- `frontend/tests/visual/surfaces.spec.ts`: gains the two `manager-invoices` entries (the `ready` and slug unions and the entries list); every existing entry and case keeps passing unmodified.
- `frontend/tests/visual/stub-backend.mjs`: serves the Agents list, months and month reads (Manager session only); every existing route keeps its answer.
- Goldens: only the new `manager-invoices-*` and `manager-invoices-agent-*` files are added.
- `review-queue-view.test.tsx`, `contracts-view.test.tsx`, `client-invoice-detail-view.test.tsx`, `lib/api/types.test.tsx` and every existing e2e spec (`manager-invoice-review-queue`, `send-a-client-invoice-back`, `edit-client-invoice-lines`, `manager-dashboard`, `agent-dashboard`, `client-invoices-side-list`, `agent-sends-the-month`) stay unedited; a ticket that finds one must change returns `blocked` with the reason.

## Observability

N/A: a read-only presentation over existing reads; nothing is written or logged by the page.
