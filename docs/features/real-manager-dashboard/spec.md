---
feature: real-manager-dashboard
epic: real-dashboards
status: draft
date: 2026-10-06
---

<!-- sdlc:template spec 1 -->

# Real Manager dashboard

## Problem

The Company Manager's home page is half real. Its Review Queue card, its
"Pending approvals" count and its "Pending Requests" count are read from the
database. Everything else on it is invented. `frontend/app/manager/page.tsx`
reads five of its seven stat cards from `frontend/lib/demo/manager.ts`:

- "Billed this month" is `41280` and "Payout this month" is `22940`, two
  literals with no source, both printed as US dollars.
- "Clients", "Agents" and "Contracts" count the fabricated rows in
  `frontend/lib/demo/contracts.ts`.
- The viewer chip reads "Priya Ashford · Manager", a person who does not exist.
  Every other Manager page reads plain "Manager".

A Manager who signs in against the demo profile sees $41,280 billed while the
database holds one sent Client Invoice for this month, worth $135.99. They see
a payout of $22,940 while no Agent Invoice of this month has been approved.
They also see counts of Clients, Agents and Contracts that match nothing on
their Clients, Agents and Contracts pages. This is the last page in the
product that shows fabricated figures. So it is the one surface that still
carries the "Demo data — synthetic" footer.

The two money figures have never been defined. The epic leaves three choices to
this spec: what "billed" means, what "payout" means, and which month "this
month" is (`docs/roadmap/real-dashboards.md`, `## Reworked`). A fourth choice
comes from the code. A Contract's currency is its Agent's country's currency
(`Country` → `Currency`: USD, EUR, GBP, MXN, PHP). The demo story already has
USD Contracts (Jordan Ellis) and a GBP Contract (Priya Shah). The system holds
no exchange rate, so one "USD" total over every invoice cannot be computed
honestly.

Three smaller defects come with it. The stat grid fakes a 550 ms loading
skeleton (`useSimulatedLoad`) in front of data that was never loading. The
visual suite renders `/manager` with no backend and masks its three live
regions. And `frontend/lib/status.ts` still imports `frontend/lib/demo/types.ts`
for seven demo-keyed status maps that nothing uses any more (`docs/tech-debt.md`,
the `frontend/lib/status.ts` entry).

## Journeys

Advances `docs/roadmap/real-dashboards.md`. This is the last of its three
features; the epic's `## Reworked` explains why the Manager goes last.

- **See where things stand on arrival** (`partial` → `exists`). After the Agent's
  and the Client's dashboards, this feature makes the Manager's part true. With
  all three landed, each role signs in and sees their own name, or their
  login, and their own numbers. Every figure can be traced to a record. The
  orchestrator moves the journey to `exists` at delivery. This is the epic's
  closing proof.

## Goals / Non-goals

Goals:

- The viewer chip names the signed-in Manager's login.
- "Clients", "Agents" and "Contracts" count the Tenant's real rows.
- "Billed this month" and "Payout this month" are real, defined figures. A new
  Tenant-wide aggregate read serves them, and the card says what they count.
- No money figure ever adds amounts of different currencies together.
- A region that cannot be loaded says so on its own card. It never shows a zero
  or a made-up figure in its place.
- `frontend/lib/demo` is deleted in full. Nothing in the frontend imports
  `@/lib/demo`. The "Demo data" footer, its `demoData` prop and
  `useSimulatedLoad` are gone, because no page shows demo figures any more.
- The `/manager` visual goldens are rendered against the stub backend, with no
  masks.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No currency conversion and no exchange-rate source.** The figures are not
  converted into one currency (open question 3's recommendation). Adding a rate
  feed is a hosted-service decision for another feature.
- **No forecast or expected figure.** A draft invoice's live total is not shown
  as "expected", "to bill" or "accrued". Only what the open questions settle is
  counted.
- **No breakdown by Contract, Client or Agent, no chart, no trend against
  earlier months, no month picker.** Each money card is one figure per
  currency, for one month. A per-month list of final invoices is
  `invoice-history`.
- **No new links or cards.** The grid keeps its seven cards and the page keeps
  its Pending approvals card. A money card does not become a link.
- **No change to the Review Queue, Pending Requests, or their counts.** They are
  already real. Only their visual-suite fixtures are added.
- **No Manager display name.** A Manager has no name in the data model, only a
  login. The chip shows the username, as the Client Portal does for a Tester.
  The other Manager pages keep their plain "Manager" chip.
- **No count endpoint.** The three counts come from the existing list routes,
  as the epic settles ("its counts are one list call each").
- **No "active" filter on the counts.** A Client, an Agent or a Contract has no
  active or archived state in the data model. Every row of the Tenant counts.
- **No adjustment logic.** `invoice-adjustment` (draft) adds signed adjustments
  to invoice totals. This feature defines how its figures will treat them
  (`## Solution`). It does not depend on that feature and builds none of it.
- **No loading skeleton or `loading.tsx` to replace the simulated one.**
- **No fix to `maxDiffPixelRatio`** (`docs/tech-debt.md`). Changed goldens are
  deleted before they are recaptured instead.
- **No migration to `BillingMonth.current()`** of controllers that compute the
  month inline (backend debt F9). The new read uses `BillingMonth.current()`.

## User stories

1. As a Company Manager, I want the viewer chip on my dashboard to show my own login, so that I am never shown as someone else.
2. As a Company Manager, I want the Clients card to count every Client of my Tenant, so that it matches my Clients page.
3. As a Company Manager, I want the Agents card to count every Agent of my Tenant, so that it matches my Agents page.
4. As a Company Manager, I want the Contracts card to count every Contract of my Tenant, so that it matches my Contracts page.
5. As a Company Manager, I want the Clients card's caption not to call them "active", so that it doesn't claim a state the product doesn't track.
6. As a Company Manager, I want "Billed this month" to total the Client Invoices of the current billing month that are sent or approved, so that I know what we have billed our Clients this month.
7. As a Company Manager, I want a Client Invoice still in draft, including one I sent back, never to count as billed, so that the figure only holds statements the Agent stands behind.
8. As a Company Manager, I want "Payout this month" to total the Agent Invoices of the current billing month that I approved or marked paid, so that I know what we owe or have paid our Agents for this month.
9. As a Company Manager, I want an Agent Invoice that is still a draft or still awaiting my review never to count as payout, so that the figure holds only pay I have agreed to.
10. As a Company Manager, I want each money card to say which month and which statuses it counts, so that I know what the figure means without asking.
11. As a Company Manager, I want each invoice counted at exactly the total its own page shows, including my override on an Agent Invoice, so that the dashboard and the invoice never disagree.
12. As a Company Manager, I want a Fee logged after an invoice was sent, or a standing-amount change, not to move a figure that counts that invoice, so that the dashboard shows what was billed and agreed, not a live estimate.
13. As a Company Manager whose Contracts are in several currencies, I want each money card to show one total per currency, so that dollars and pounds are never added together.
14. As a Company Manager, I want the currencies on a money card always in the same order, so that I can read the card at a glance from one day to the next.
15. As a Company Manager, I want a money card with nothing to count yet to say so in words, not show a zero in a made-up currency, so that I can tell "nothing yet" from "zero dollars".
16. As a Company Manager, I want the money figures to change when an invoice is sent, approved, sent back or marked paid, so that the page is true each time I open it.
17. As a Company Manager, I want the money figures to count only my own Tenant's invoices, so that my view is exactly my scope.
18. As a Company Manager, I want a figure that couldn't be loaded to show "—" with a "Couldn't load…" line on its own card, with the rest of the page still showing, so that I never mistake a failure for a real zero.
19. As a Company Manager, I want the page to still render, with a plain "Manager" chip, when my own login can't be read, so that one failed read never hides every figure.
20. As a Company Manager, I want my dashboard to show its figures as soon as the page loads, with no artificial delay, so that I see what is true now.
21. As a Company Manager, I want opening my dashboard to change nothing, and never to create an invoice, so that looking is only looking.
22. As an Agent or a Tester, I want the new Tenant-wide read refused to me, so that no one outside the Manager role sees what the company bills or pays.
23. As someone testing the product by hand, I want every figure on the Manager dashboard to be traceable to records in the `demo` profile's database, so that I can check the page against the data.
24. As a user of any page, I want no "Demo data — synthetic" footer anywhere, so that the app never labels true data as fake and never leaves a fake figure unlabelled.
25. As the team, I want no fixture data compiled into the frontend and no simulated loading, so that a fabricated figure or a fake skeleton can never ship again.
26. As the team, I want the Manager dashboard's visual goldens to render real layouts from stubbed data, with no masks, so that a golden moves only when the page does.
27. As a Company Manager, once adjustments exist, I want an invoice's landed adjustments counted in its figure and pending ones not counted, so that the dashboard keeps agreeing with the invoice.

## Solution

The money definitions below are the open questions' recommendations. If the
human answers otherwise, the definitions change and the shape stays the same.

### Where each figure comes from

| On the page | Source | Notes |
|---|---|---|
| Viewer chip | `GET /api/me`, its `username` | `<username> · Manager`. If the read fails, plain "Manager" (story 19) |
| Pending approvals | `GET /api/review-queue` | unchanged |
| Pending Requests | `GET /api/pending-requests` | unchanged |
| Billed this month | new `GET /api/tenant-totals`, `billed` | one `Money` line per currency. Meta "Sent or approved · `<Month YYYY>`", where the month is the read's own `billingMonth` and never the wall clock |
| Payout this month | new `GET /api/tenant-totals`, `payout` | one `Money` line per currency. Meta "Approved or paid · `<Month YYYY>`" |
| Clients | `GET /api/clients`, its length | meta "In this tenant" (was "Active this tenant") |
| Agents | `GET /api/agents`, its length | meta unchanged, "Across all countries" |
| Contracts | `GET /api/contracts`, its length | meta unchanged, "Client × Agent pairs" |

### The money definitions

```
billingMonth  = BillingMonth.current()           (first day of the current UTC month)

Billed this month, per currency =
  Σ total(ci)  over Client Invoices ci of the caller's Tenant
               with ci.billingMonth = billingMonth
               and  ci.status in {SENT, APPROVED}
  total(ci)    = the sum of its stored Client Invoice lines (ADR 0004),
                 the same total its own page, its PDF and the Review Queue show

Payout this month, per currency =
  Σ total(ai)  over Agent Invoices ai of the caller's Tenant
               with ai.billingMonth = billingMonth
               and  ai.status in {APPROVED, PAID}
  total(ai)    = its four frozen figures (ADR 0003): Local Support Fees + Salary
                 + Rollout Advance repayment + new advance, after any Manager override
```

- **Only frozen totals are counted.** Every counted status is past the send, so
  every counted total is stored. A draft's live computation is never read.
  Opening the dashboard writes nothing, and it never runs a get-or-create path.
- **Grouped by the invoice's own `currency`.** It is copied from the Contract,
  and the Contract's from the Agent's country. Amounts in different currencies
  are never added together.
- **A sent-back Client Invoice is `DRAFT`** (ADR 0005). It drops out of "Billed"
  when it is sent back and comes back in when it is resent. That needs no
  special case.
- **How adjustments will count, once `invoice-adjustment` lands.** That spec
  makes an adjustment part of an invoice's frozen figures once it *lands*. For a
  Client Invoice it is an adjustment line. For an Agent Invoice it is a fifth
  figure. The rule here is that each figure counts an invoice at the total its
  own page shows. So a landed adjustment counts in the invoice it landed on,
  signed: a credit lowers the figure and a charge raises it. A pending or
  withdrawn adjustment never counts. A Client Invoice's adjustment lines are
  stored lines, so the line sum includes them with no change. The Agent side
  needs the fifth figure added to `total(ai)`. To keep that a one-place change,
  the aggregate and the Review Queue build their Agent Invoice total from one
  shared expression (`## Decisions taken`). Whichever feature lands second
  updates it. This feature adds nothing for adjustments now.

### Backend: the Tenant-wide aggregate

A new `TenantTotalsController`, Manager-only through a `SecurityConfig` matcher
beside `/api/review-queue`'s. It takes no parameter. The Tenant comes from the
principal and the month from `BillingMonth.current()`.

```
GET /api/tenant-totals
200 {
  "billingMonth": "2026-10-01",
  "billed": [ { "currency": "GBP", "amount": "20.00" }, { "currency": "USD", "amount": "135.99" } ],
  "payout": [ { "currency": "USD", "amount": "2835.99" } ]
}
    — each list holds one entry per currency with at least one counted invoice,
      ordered by currency code; a list with nothing counted is []
403 — an Agent or a Tester
401 — no token
```

- **Selection and summing live in a service** (Backend rule 2), with
  `ClientInvoiceRepository` and `AgentInvoiceRepository` gaining one grouped
  query each. They sum per currency in the database, in the shape of the
  existing `findQueueRows` queries: the Client side over `ClientInvoiceLine`,
  the Agent side over the snapshot columns. Both are filtered by Tenant,
  `billingMonth` and a set of statuses.
- **A new DTO** (`TenantTotalsResponse` with a `CurrencyAmount` entry). Amounts
  are decimals serialised the way every other amount in the API is.
- **No schema change and no Flyway migration.** The existing `tenant_id` and
  `billing_month` columns are enough at this volume.

### Frontend: the page

`app/manager/page.tsx` stays an async Server Component and keeps today's shape:
each region loads alone and fails alone, and nothing throws. It reads
`/api/me`, `/api/review-queue`, `/api/pending-requests`, `/api/tenant-totals`,
`/api/clients`, `/api/agents` and `/api/contracts` in parallel, each through
`backendFetchJsonOrNull`, which gives `null` on failure, logged.

- **No `(dashboard)` route group and no `error.tsx`.** The Agent and Client
  dashboards throw when identity fails, because every figure on them is scoped
  by the caller's Agent or Client. Here, no figure is scoped by `/api/me`: the
  backend scopes every read by the token's Tenant. A failed `/api/me` only
  costs the chip its username (story 19).
- **Regions fail alone.** A failed list read makes its count card show `—` with
  an sr-only "Unavailable" and a "Couldn't load…" meta ("Couldn't load your
  Clients", "…Agents", "…Contracts"). A failed `/api/tenant-totals` makes both
  money cards show `—` with "Couldn't load this month's totals". This is the
  Agent grid's rendering, already used by the two pending counts.
- **Nothing to count** (`[]`) renders the value "None yet" in the card's value
  slot, muted, with the month meta unchanged. It never renders `0` in a
  currency nobody chose (story 15).
- **Several currencies** render as a short stack of `Money` lines in the value
  slot, one per currency, in the read's order, each `tabular-nums` through
  `Money`. Six currencies at most can occur.

### Frontend: the stat grid

`ManagerDashboardStats` becomes presentational, with no `"use client"` and no
`useSimulatedLoad`. It renders at once, still marked `data-testid="dashboard-ready"`.
Its props become:

```ts
billedThisMonth: CurrencyAmount[] | null;   // null = unavailable
payoutThisMonth: CurrencyAmount[] | null;
billingMonth: string | null;                // "YYYY-MM-01" from the read, for the meta
clientCount: number | null;
agentCount: number | null;
contractCount: number | null;
```

Every card gets a `data-testid` (`billed-stat`, `payout-stat`, `clients-stat`,
`agents-stat`, `contracts-stat`), beside the two that exist.

### Prefactoring and removal

- **The demo-keyed status maps are deleted, not moved.** The epic expected the
  invoice-status types in `frontend/lib/demo/types.ts` to need a new home. The
  code shows that their only importer is `frontend/lib/status.ts`, and only for
  seven maps nothing uses any more: `requestStatusTone`, `smartphoneStatusTone`,
  `simStatusTone`, `clientInvoiceStatusTone`, `clientInvoiceStatusLabel`,
  `agentInvoiceStatusTone` and `agentInvoiceStatusLabel`. Their `…ByValue`
  replacements, keyed by the real enums in `lib/api/types`, already serve
  every page. Deleting the seven maps frees `demo/types.ts`. This pays the
  `frontend/lib/status.ts` entry in `docs/tech-debt.md`. The stale comments that
  point at the deleted maps are reworded.
- **`frontend/lib/demo/` is deleted**: `manager.ts`, `contracts.ts` and
  `types.ts`.
- **The demo footer goes.** `SurfacePage` loses its `demoData` prop, and
  `DemoNote` is deleted. `demo-footer.spec.ts` becomes one assertion that
  `/manager` shows no "Demo data" footer, or is folded into `surfaces.spec.ts`.
- **`useSimulatedLoad` is deleted**, along with `StatCardSkeleton` if nothing
  else renders it. Today only the Manager grid does.

### Visual suite

`tests/visual/stub-backend.mjs` gains, for `visual-manager-session`:

- `GET /api/review-queue`: two items, one Client Invoice and one Agent Invoice,
  with `waitingSince` relative to the stub's own clock. This is the Agent
  dashboard's settled answer to the clock debt.
- `GET /api/agents` and `GET /api/contracts` for a Manager, if not already
  served, reusing the stub's existing Client, Agents and Contracts.
- `GET /api/tenant-totals`: a fixed `billingMonth`, `billed` in two currencies
  (GBP and USD) and `payout` in one (USD).
- `visual-manager-degraded-session`: `/api/me`, `/api/tenant-totals` and
  `/api/agents` → `500`. This is asserted in `surfaces.spec.ts` as a non-golden
  test, beside the Agent's and the Tester's degraded sessions.
- `visual-manager-empty-session`: `/api/tenant-totals` returns `[]` for both
  lists. This is asserted as a non-golden test that both cards read "None yet".

The `manager` surface, `viewer-menu.spec.ts` and `change-password-dialog.spec.ts`
move from the placeholder cookie onto `visual-manager-session` and drop their
masks. Their goldens are recaptured, deleted first: `manager-*`,
`manager-menu-open-*` and `change-password-dialog-open-*`.

## Design direction

Conforms to `DESIGN.md`; no `DESIGN.md` change. The surface is **Operate**.
`DESIGN.md` already settles the layout: "a responsive stat-card grid (3-column
on Manager …) followed by a linking preview list", and each surface "leads with
money and status (pending approvals, billed/payout totals …)". The grid keeps
its seven cards in their order, and the Pending approvals card stays below it.

Every state is built from components that already ship: `StatCard`, with the
unavailable rendering the two pending counts already use, and `Money`, which
already carries tabular figures and an explicit currency. A multi-currency value
is a vertical stack of `Money` lines in the existing value slot. It is not a new
component, and its type size is the existing primary value's. "None yet" uses
the muted ink token. New and rewritten lines use the `@theme` type-scale tokens
(`text-label-sm`, `text-label`), never `text-[12px]` / `text-[13px]` (Frontend
rule 8, as amended). The `design` slot does not need to be engaged.

## Constraints

- Inherits every constraint in the `remote-support-mvp` spec: the stack, Tenant
  scoping, and an explicit currency on every amount.
- **No figure adds amounts of different currencies.** No figure is converted.
- `GET /api/tenant-totals` acts only on the caller's own Tenant, resolved from
  the principal. No parameter may name a Tenant, a month or a status. It is
  Manager-only.
- It reads frozen totals only (ADR 0003, ADR 0004). It never reads a draft's
  live computation, never writes, and never creates an invoice.
- Each invoice counts at the same total its own page and the Review Queue show.
  Any change to how an invoice's total is computed, `invoice-adjustment`'s
  included, must keep the two in agreement (`## Solution`, adjustments).
- The month shown is the read's `billingMonth`, never the frontend's wall clock.
- No Flyway migration. Should one prove necessary, it takes the next free Flyway
  version at merge (V60 today).
- The frontend imports nothing from `@/lib/demo`, and the directory no longer
  exists. `DemoNote`, `demoData` and `useSimulatedLoad` no longer exist.
- A region that fails logs the failure and renders its own "Couldn't load…"
  state while the rest of the page works (Frontend rule 6, as amended). It never
  renders `0`, an empty list or a fallback figure in place of a failure.
- Visual recapture: because of `maxDiffPixelRatio`, every golden this feature
  changes is deleted before it is recaptured. Each recaptured golden's diff
  against `main` shows only the intended change.
- Backend tests run under `IntegrationTest`. Mocking a repository there is
  banned.
- e2e and visual runs use an isolated stack on ports other than
  3000/8080/5432, per `docs/agents/implementer-notes.md`. No test writes to the
  user's compose stack, including `postgres-demo`. No e2e step rewrites a
  `billing_month` in the database.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, the rendered
accessibility tree, and what a user sees. They never assert repository calls or
component internals.

1. **Backend: the HTTP API seam, which already exists.** `IntegrationTest` with
   MockMvc against real Postgres. Prior art: `ReviewQueueApiTest` (Tenant-wide
   Manager reads over both invoice kinds, Manager-only access, an older billing
   month built through the repository), `MeClientApiTest` (frozen totals, no row
   created by a read) and `AgentInvoiceByIdApiTest` (override and mark-paid
   transitions). The tests cover:
   - an empty Tenant gets the current `billingMonth` and two empty lists;
   - a `SENT` Client Invoice and an `APPROVED` one of this month both count, at
     the total `GET /api/client-invoices/{id}` returns;
   - a `DRAFT`, a sent-back draft, and a `SENT` invoice of last month do not
     count;
   - a Fee logged after sending does not move `billed`;
   - an Agent Invoice counts once `APPROVED` and stays counted when `PAID`, at
     its total including a Manager override; one that is `DRAFT` or `SENT`, or
     of last month, does not count;
   - a USD and a GBP Contract give two entries, ordered by currency code, never
     one sum;
   - `OtherTenantFixture`'s invoices never count;
   - an Agent and a Tester get `403`, and no token gets `401`;
   - calling the read creates no `client_invoices` or `agent_invoices` row.
2. **Frontend: component tests for `ManagerDashboardStats`.** Vitest and Testing
   Library; the file exists (`components/manager/dashboard-stats.test.tsx`) and
   loses its fake-timer step. They cover one currency, two currencies in the
   given order, `[]` rendering "None yet", each `null` rendering "—" with its
   "Couldn't load…" meta, the month meta, the "In this tenant" caption, and the
   grid rendering at once as `dashboard-ready`.
3. **Frontend: one Playwright e2e spec against the real backend**,
   `tests/e2e/manager-dashboard.spec.ts`. Prior art:
   `manager-invoice-review-queue.spec.ts` (sending and approving both invoice
   kinds), `agent-dashboard.spec.ts`, `client-dashboard.spec.ts` and
   `helpers.ts`. The e2e database is shared across specs, so the spec asserts
   *differences*, never absolute totals. It reads the money cards, then:
   - has the Agent send a Client Invoice and shows "Billed" grew by exactly that
     invoice's total in its currency;
   - has the Manager approve it and shows "Billed" unchanged;
   - has the Agent send their Agent Invoice and shows "Payout" unchanged;
   - has the Manager approve it and shows "Payout" grew by its total.

   It also shows the three counts equal to the lengths of the Clients, Agents
   and Contracts lists through the API, and the chip reading the Manager's
   username. Because component tests cannot render the async Server Component,
   its failure branches are covered by the stub sessions in the visual suite.
4. **Visual goldens: the existing backend-stubbed suite.** `/manager` moves onto
   `visual-manager-session` with no masks. The degraded and empty states are
   asserted as non-golden tests.
5. **No new seam.** Each behaviour is tested at the highest seam that already
   exists and can see it.

## Decisions taken

- **The money figures come from one new Manager-only read,
  `GET /api/tenant-totals`, and the counts from the three existing list
  routes.** Reason: the epic settles the counts as "one list call each". Money
  has no endpoint, and one read for both money figures keeps a single month
  for both. With separate reads, a failed money read does not blank the counts.
- **The read takes no parameter: Tenant from the principal, month from
  `BillingMonth.current()`, statuses fixed in the service.** Reason: nothing on
  the page chooses them, and a parameterised read would be a second history API
  before `invoice-history` designs one. It is additive and cheap to change.
- **The read returns its `billingMonth`, and the card's month label comes from
  it.** Reason: the page and the figure can then never disagree about the month
  near midnight UTC on the 1st. This follows the Agent dashboard's "never the
  wall clock".
- **Sums are grouped by currency in the database, in the shape of the existing
  `findQueueRows` queries.** Reason: the invoice count is small. A grouped sum
  avoids one line read per invoice, and the queue queries are prior art for
  both total expressions.
- **The Agent Invoice total is one shared expression, used by the Review Queue
  query and the new aggregate.** Reason: `invoice-adjustment` adds a fifth
  figure to it. One expression makes that a single edit, so the queue and the
  dashboard cannot drift. The Client side already shares "sum of stored lines".
- **The page does not throw on a failed `/api/me`. The chip falls back to plain
  "Manager", and there is no `(dashboard)` route group.** Reason: unlike the
  Agent and Client dashboards, no figure here is scoped by the caller's record.
  The backend scopes each read by the token. The page already loads each region
  alone. The change is cheap to undo.
- **The chip shows `<username> · Manager`.** Reason: a Manager has no name in
  the data model. This matches the Tester's `<username> · Tester`.
- **The Clients card's caption reads "In this tenant", not "Active this
  tenant".** Reason: no Client has an active state to filter on. The Clients
  page's own subtitle already says "N in this tenant". The change is copy only,
  and walkthrough step 11 shows it.
- **A money card with nothing to count reads "None yet".** Reason: `0` needs a
  currency, and picking one would be invented. "—" already means "couldn't
  load". The change is copy only, and walkthrough step 11 shows it.
- **Several currencies render as stacked `Money` lines in the existing value
  slot, ordered by currency code.** Reason: there is no new component, the
  order is stable from day to day (story 14), and at most six lines can occur.
  This follows open question 3's recommendation. The layout is cheap to change.
- **The demo-keyed status maps in `lib/status.ts` are deleted, not moved, and
  `lib/demo/types.ts` goes with them.** Reason: the code shows nothing uses
  them. The real-enum maps replaced each one, so there is nothing to move. This
  pays the recorded debt.
- **`DemoNote`, `demoData` and `useSimulatedLoad` are deleted.** Reason: their
  last user was this page. The `real-client-dashboard` spec assigned their
  removal here.
- **The visual stub's Review Queue dates are relative to the stub's clock, with
  no masks.** Reason: this is the Agent dashboard's settled answer. The page
  renders server-side, so a browser clock cannot reach it, and masking would
  hide real changes.
- **No `(dashboard)` `error.tsx` and no `loading.tsx`.** Reason: no whole-page
  failure exists to route, and the page has never needed a loading state.
- **Code goes in the existing `web` / `repository` / `dto` layout.** Reason:
  Backend rule 13's transition clause, as in both sibling features.
- **Testing: the existing HTTP seam for the read, component tests for the grid,
  one e2e spec asserting differences against a real backend, and the stubbed
  visual suite for layout and failure states.** Reason: each is the highest
  existing seam that can see its behaviour, and no seam is added. The e2e
  database is shared, so absolute totals are not stable there.
- **`/api/tenant-totals` is made Manager-only by one `SecurityConfig` matcher,
  beside `/api/review-queue`'s `hasRole("MANAGER")` rule.** Reason: that is
  where the Review Queue's guard lives (verified), and the change is additive.

## Open questions

1. **What does "Billed this month" count?** Recommendation: the Client Invoices
   *for the current billing month* (October on 6 October) that are **sent or
   approved**. Drafts never count, including one the Manager sent back. Reason:
   sending is the moment a Client is billed, and the Tester already sees sent
   invoices. A draft's total is a live estimate that can still move. "This
   month" then means what it means on the Agent's dashboard. The cost is that
   the figure reads low early in the month, until the Agents send. Example from
   the demo story: today it would read **$135.99**, from Solstice's US Contract,
   which Jordan has sent. The Solstice UK and Harbor Line drafts don't count
   until they are sent. The alternative is "the last completed month"
   (September), relabelled "Billed for September": it is complete, but it is
   not "this month". (case 1)
2. **What does "Payout this month" count?** Recommendation: the Agent Invoices
   *for the current billing month* that the Manager has **approved or marked
   paid**. Reason: that is the pay the company has agreed to. A sent invoice is
   still awaiting review and is already counted on the "Pending approvals"
   card. The month matches question 1. Example from the demo story: today it
   would read **"None yet"**. Jordan's October Agent Invoice (about $2,836) is
   only sent and Priya's is a draft. It moves to about $2,836 the moment the
   Manager approves Jordan's invoice. The alternatives are "paid only" (cash
   actually out), "sent, approved or paid" (everything claimed), or "the last
   completed month" (September, where Jordan's and Priya's invoices are both
   paid). (case 1)
3. **How are amounts in different currencies shown?** Contracts *do* differ.
   Each Contract takes its Agent's currency, and the demo has USD (Jordan) and
   GBP (Priya) Contracts, so one "USD" figure would add pounds to dollars.
   Recommendation: **one total per currency on the same card**, for example
   "$135.99" over "£20.00", with no conversion. Reason: the product holds no
   exchange rate. Converting needs a rate source, a hosted service and a
   decision about which day's rate, which belongs to its own feature. The
   alternative is to show only USD and drop other currencies, which hides real
   money. (case 1; conversion would also be case 2)

## Acceptance walkthrough

Run against an isolated stack with the backend on the `demo` profile (its own
fresh database, seeded by `DemoDataLoader`), never the user's compose stack.
Logins are in the root `README.md`. The figures assume the open questions'
recommendations.

1. [agent] Sign in as the Manager (`manager@example.com`) and open `/manager`. Show the chip "manager@example.com · Manager", and no occurrence of "Priya Ashford", "41,280", "22,940" or "Demo data" on the page. Show that the grid appears with no skeleton first. (stories: 1, 20, 24)
2. [agent] Through the API, list `/api/clients`, `/api/agents` and `/api/contracts` as the Manager. Show the three cards equal their lengths (2, 3 and 3 in the demo story) and the Clients caption reads "In this tenant". (stories: 2, 3, 4, 5, 23)
3. [agent] Call `GET /api/tenant-totals` as the Manager. Show `billingMonth` is this month and `billed` holds one USD entry equal to the total `GET /api/client-invoices/{id}` returns for Solstice's US Contract's invoice. Show the Solstice UK and Harbor Line drafts are not counted. Show the "Billed this month" card shows that amount with the meta "Sent or approved · <this month>". (stories: 6, 7, 10, 11, 23)
4. [agent] Show "Payout this month" reads "None yet" with "Approved or paid · <this month>", although Jordan's Agent Invoice is sent. As the Manager, approve it. Reload and show "Payout" equal to that invoice's own total in USD. Mark it paid and show the figure unchanged. (stories: 8, 9, 10, 15, 16)
5. [agent] As Priya, send Solstice UK's Client Invoice. Reload and show "Billed" with two lines, GBP then USD, each equal to its invoices' totals, and never one summed figure. (stories: 13, 14, 16)
6. [agent] As the Manager, send that UK invoice back. Show "Billed" loses its GBP line. As Priya, resend it, and show the GBP line returns. (stories: 7, 16)
7. [agent] As Jordan, log a Fee on Solstice's US Contract. Reload and show "Billed" unchanged. Override the new-advance line on Priya's sent Agent Invoice, approve it, and show "Payout" gains a GBP line equal to that invoice's overridden total. (stories: 11, 12, 13)
8. [agent] Count `client_invoices` and `agent_invoices` rows, load `/manager` three times, and show the counts unchanged. (stories: 21)
9. [agent] Call `GET /api/tenant-totals` as Jordan and as Dana and show `403`, and with no token show `401`. Only one Tenant is seeded, so run the backend test that seeds `OtherTenantFixture` invoices and show it green, with those invoices never counted. (stories: 17, 22)
10. [agent] Against the stub backend: with the degraded Manager session, show "Billed" and "Payout" reading "—" with "Couldn't load this month's totals", Agents reading "—" with "Couldn't load your Agents", the chip reading "Manager", and every other card still showing. With the empty session, show both money cards reading "None yet". (stories: 15, 18, 19)
11. [agent] Show `frontend/lib/demo/` gone, no `@/lib/demo`, `DemoNote`, `demoData` or `useSimulatedLoad` anywhere in `frontend/`, and no `text-[12px]` / `text-[13px]` in the page or `ManagerDashboardStats`. Run the full visual suite and show that only `manager-*`, `manager-menu-open-*` and `change-password-dialog-open-*` changed, each deleted first, with no masks on the dashboard. Then run `docs/agents/sdlc.json`'s `verify` green. (stories: 24, 25, 26)
12. [human] Read the copy this feature adds: "In this tenant", "None yet", "Sent or approved · <month>", "Approved or paid · <month>", and the "Couldn't load…" lines. Confirm each reads right to a Manager. (stories: 5, 10, 15, 18)
13. [human] Sign in as the Manager against the demo profile. Confirm that each money figure is one you can rebuild from the invoices on the Invoices pages and their detail pages, and that a two-currency card reads clearly at desktop and phone widths. (stories: 13, 14, 23)
14. [agent] Once `invoice-adjustment` has landed, if it lands later, record a credit on an approved invoice and send the next month's invoice. Confirm the dashboard counts the credit only in the month it landed. Until then, confirm the spec's adjustment rule matches that feature's spec. (stories: 27)

## Execution order

Five tickets. The prefactor and the backend read do not depend on each other.
The three page slices run in a line, because all three edit the same page, the
same stub and the same goldens.

1. `delete-demo-status-maps` (enabler): delete the seven demo-keyed maps in `lib/status.ts` and their import of `lib/demo/types.ts`, rewording the comments that name them. No golden moves. Modules: `lib/status`. No dependency. (stories: none; frees `lib/demo/types.ts` for ticket 5)
2. `tenant-totals-read`: `GET /api/tenant-totals`, with the two grouped repository queries, the shared Agent Invoice total expression (adopted by the Review Queue query), the service, the DTO, the Manager-only guard and the integration tests. Modules: backend `web`, `repository`, `dto`, security. No dependency. (stories: 6, 7, 8, 9, 11, 12, 13, 14, 17, 21, 22, 27)
3. `manager-dashboard-identity-and-counts`: the chip from `/api/me` with its fallback; the Clients, Agents and Contracts counts from the list routes with their unavailable states and the "In this tenant" caption; `ManagerDashboardStats` off `useSimulatedLoad` with its component tests; the stub's Manager fixtures (Review Queue with relative dates, lists) and the degraded session; `manager-*`, `manager-menu-open-*` and `change-password-dialog-open-*` moved onto `visual-manager-session`, masks dropped, recaptured. Modules: Manager dashboard page, `components/manager`, visual suite. No dependency. (stories: 1, 2, 3, 4, 5, 18, 19, 20, 26)
4. `manager-dashboard-money`: "Billed this month" and "Payout this month" from the new read, with per-currency lines, "None yet", the month meta and the unavailable state; component tests; the stub's totals fixture and empty session; the `manager-dashboard.spec.ts` e2e spec; goldens recaptured. Modules: Manager dashboard page, `components/manager`, visual suite, e2e suite. Depends on `tenant-totals-read` and `manager-dashboard-identity-and-counts`. (stories: 6, 8, 10, 13, 14, 15, 16, 18, 23)
5. `delete-frontend-demo-fixtures`: delete `frontend/lib/demo/`, `DemoNote`, `SurfacePage`'s `demoData`, `useSimulatedLoad` and an unused `StatCardSkeleton`; rewrite `demo-footer.spec.ts` as a no-footer assertion; recapture only if a golden moves. Modules: app-shell, `lib`, `components/ui`, visual suite. Depends on `delete-demo-status-maps` and `manager-dashboard-money`. (stories: 24, 25)
