---
feature: real-client-dashboard
epic: real-dashboards
status: approved
date: 2026-10-01
---

<!-- sdlc:template spec 1 -->

# Real Client dashboard

## Problem

A Tester's home page is the first thing they see after signing in, and every
figure on it is invented. `frontend/app/client/page.tsx` reads all of it from
`frontend/lib/demo/client.ts`. The header names "Aurora Retail Group", a Client
that does not exist. The viewer chip reads "Nadia Okafor · Tester", a person who
does not exist. Active Fleet and Open Requests count fabricated Smartphones, SIM
Cards and Requests. The "Latest Client Invoice" card lists French and British
Contracts with euro and pound totals that nobody sent. Dana Whitfield of Solstice
Retail Group signs in and sees another company's name, Fleet and invoices.

That is the harm the epic ranks second, after the Agent's page: the page is
wrong about whose data it shows. And it cannot answer the one question a home
page exists for, which is where things stand for my company today.

Two smaller defects come with it. The stat grid fakes a 600 ms loading
skeleton (`useSimulatedLoad`) in front of data that was never loading. And
every surface in the app, including pages that are fully real, ends with a
"Demo data — names, amounts and statuses on this screen are synthetic" footer
(`docs/tech-debt.md`, the `surface-page.tsx` entry, F12). Once this page is
real, that footer is untrue everywhere except the Manager dashboard.

## Journeys

Advances `docs/roadmap/real-dashboards.md`. This is the second of its three
features; the epic's `## Reworked` explains the order.

- **See where things stand on arrival** (`partial`, stays `partial`): this
  feature makes the Tester's part of the journey true. The Client dashboard
  shows the Tester's own Client and that Client's own numbers, and each figure
  can be traced to a record. The journey reaches `exists` only when
  `real-manager-dashboard` has also landed, so this feature does not edit
  `docs/journeys.md`.

## Goals / Non-goals

Goals:

- The Client dashboard's header names the signed-in Tester's own Client, and
  the viewer chip names the signed-in Tester.
- Active Fleet, Open Requests and the Latest Client Invoice card come from the
  database, scoped to the Tester's own Client.
- A region that cannot be loaded says so on its own and never shows a zero, an
  empty list or a made-up figure instead.
- `frontend/lib/demo/client.ts` is deleted. Nothing on this page is compiled
  into the frontend any more.
- The "Demo data — synthetic" footer appears only on the one surface that still
  shows demo figures, the Manager dashboard.
- The Client Portal's visual goldens are rendered against the stub backend.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No change to the Manager dashboard's figures.** That is
  `real-manager-dashboard`. `frontend/lib/demo/types.ts`,
  `frontend/lib/demo/manager.ts` and `frontend/lib/demo/contracts.ts` stay.
  `useSimulatedLoad` stays too, because the Manager's stat grid still calls it.
  The Manager page changes in one way only: it opts in to the demo footer
  (`## Solution`, Prefactoring).
- **No Client Invoice history for the Tester.** The new read returns at most
  one invoice per Contract, the latest sent or approved one. A list of past
  invoices, for the Tester or for the Manager, is not this feature. The
  Manager's list is `invoice-history`.
- **No change to `/client/invoices`.** It keeps showing each Contract's
  current-month invoice only. Only the label of the dashboard's link to it
  changes (`## Decisions taken`).
- **No widening of an existing route.** `GET /api/contracts/{id}/client-invoice`,
  `GET /api/client-invoices/{id}` and `/api/clients/**` keep exactly the access
  they have today. `MeResponse` is not changed.
- **No new figures, cards or links.** The page keeps its two stat cards and its
  Latest Client Invoice card. It gains only the not-linked, empty and
  unavailable states it needs to be honest.
- **No person's name for a Tester.** A Tester has no name in the data model,
  only a login. The chip shows the username, as on every other Client Portal
  page. Adding a Tester display name is a schema change for another feature.
- **No Contract switcher on the dashboard.** `DESIGN.md` allows one for
  multi-Contract Clients. The page has never had one, and adding one is not part
  of making it real.
- **No loading skeleton or `loading.tsx` to replace the simulated one.**
- **No masks on the Client dashboard's goldens.** They are rendered from stubbed
  data, not blanked out.
- **No fix to `maxDiffPixelRatio`** (`docs/tech-debt.md`, the
  `frontend/playwright.config.ts` entry). Goldens are deleted before they are
  recaptured instead (`## Constraints`).
- **No migration to `BillingMonth.current()`** of the controllers that compute
  the month inline (backend debt F9). New code uses `BillingMonth.current()`
  where it needs the month. This feature's read does not need it.

## User stories

1. As a Tester, I want my dashboard's header to name my own Client, so that I know at a glance the page is about my company.
2. As a Tester, I want the viewer chip on my dashboard to show my own login, so that I am never shown as someone else.
3. As a Tester whose Client holds no Contract yet, I want my Client's name in the header all the same, so that the page still says whose it is.
4. As a Tester on a phone, I want my Client's name to stay in the header and wrap if it is long, so that it is never cut off or dropped.
5. As a Tester, I want to see how many Smartphones and SIM Cards my Client has in active use across all its Contracts, so that I know what we have to test with.
6. As a Tester, I want a Smartphone that is In Repair to count as part of the active Fleet, and a Retired unit not to count, so that the figure matches what we still hold.
7. As a Tester, I want to see how many of my Client's Requests are still open across all its Contracts, counting those waiting on the Manager's approval, so that I know how much support is still under way.
8. As a Tester, I want the Open Requests card to say which statuses it counts, so that I know what "open" means.
9. As a Tester, I want, for each of my Client's Contracts, the most recent Client Invoice my Agent has sent or the Manager has approved, so that I know what we were last billed.
10. As a Tester, I want each of those rows to show the Contract, the billing month, its status and its total in the Contract's own currency, so that I can read it without opening the invoice.
11. As a Tester, I want an invoice my Agent is still drafting never to appear on my dashboard, so that I only see statements the Agent stands behind.
12. As a Tester, I want a Contract with no sent or approved invoice yet to say "No invoices yet", so that an empty row doesn't look broken.
13. As a Tester whose Client holds no Contract yet, I want the invoice card to say so plainly, so that an empty card doesn't look broken.
14. As a Tester, I want a link from the invoice card to my Client's Invoices page, so that I can go from the summary to the full statement.
15. As a Tester, I want a figure that couldn't be loaded to say so, on its own card, with the rest of the page still showing, so that I never mistake a failure for a real zero.
16. As a person whose Tester login is not linked to a Client, I want to be told that instead of seeing numbers, so that I know to ask the Manager.
17. As a Tester, I want the page to say clearly that it couldn't load when my own Client cannot be read, so that I'm never shown a page about nobody.
18. As a Tester, I want the dashboard to show only my own Client's Contracts, Fleet, Requests and invoices, so that my view is exactly my scope.
19. As a Tester, I want my dashboard to reflect the database as soon as the page loads, with no artificial delay, so that I see what is true now.
20. As a Tester, I want opening my dashboard to change nothing anyone else sees, and in particular never to create a Client Invoice, so that looking is only looking.
21. As an Agent or a Company Manager, I want the Tester's new reads to show a Tester nothing they could not already see, so that giving Testers a dashboard widens no one's access.
22. As someone testing the product by hand, I want every figure on the Client dashboard to be traceable to a record in the `demo` profile's database, so that I can check the page against the data.
23. As a user of any real page, I want no "Demo data — synthetic" footer under real figures, so that the app never labels true data as fake.
24. As the team, I want the Client dashboard's visual goldens to render real layouts from stubbed data, so that a golden moves only when the page does.
25. As the team, I want no Client data compiled into the frontend, so that a fabricated figure can never ship on this page again.

## Solution

### Where each figure comes from

| On the page | Source | Notes |
|---|---|---|
| Header (subtitle) | the caller's own Client (new read, below), its `name` | wraps on mobile through `SurfacePage`'s existing `wrapSubtitle`, never truncated or dropped |
| Viewer chip | `GET /api/me`, its `username` | `<username> · Tester`, as on `/client/fleet`, `/client/requests` and `/client/invoices` |
| Active Fleet | every Contract in `GET /api/contracts`, its `smartphones` and `sim-cards` | Smartphones `ACTIVE` or `IN_REPAIR`, plus SIM Cards `ACTIVE`; meta unchanged, "Smartphones + SIM Cards, all Contracts" |
| Open Requests | every Contract's `GET /api/contracts/{id}/requests` | Requests `PENDING_APPROVAL`, `SUBMITTED` or `IN_PROGRESS`; meta "Pending Approval, Submitted or In Progress" (the human's answer, `## Decisions taken`) |
| Latest Client Invoice rows | one row per Contract in `GET /api/contracts`, joined to the new latest-invoices read by `contractId` | a row shows `<clientName> — <country label>`, `formatBillingMonth(billingMonth)`, the status `Badge` through `clientInvoiceStatusToneByValue` / `clientInvoiceStatusLabelByValue`, and `Money(totalAmount, currency)`. A Contract with no entry shows "No invoices yet" |

### Backend: the caller's own Client

A new `MeClientController`, beside `MeAgentController` and in the same shape.
Both routes resolve the Client from the authenticated principal through
`CallerIdentityResolver.resolveClientId` and take no id from the request:

```
GET /api/me/client
200 { "clientId", "name" }
404 — the caller is not a Tester linked to a Client (any Manager or Agent, or an unlinked Tester login)

GET /api/me/client/latest-client-invoices
200 [ { "contractId", "invoiceId", "billingMonth", "status", "currency", "totalAmount" } ]
    — one entry per Contract of the caller's Client that has at least one Client Invoice
      in SENT or APPROVED: the one with the latest billingMonth. A Contract with none is absent.
      status is "SENT" or "APPROVED", never "DRAFT".
404 — as above
```

- **Selection lives in `ClientInvoiceService`** (Backend rule 2), as a method
  that takes the caller's Client and returns the summaries.
  `ClientInvoiceRepository` gains one derived query: the latest Client Invoice
  of a Contract with a status in a given set, ordered by `billingMonth`
  descending. The Contracts are the caller's Client's Contracts, within the
  caller's Tenant.
- **`totalAmount` is the invoice's frozen total**, computed by the same code
  path `ClientInvoiceService.toResponse` uses: the snapshot base amount plus the
  snapshotted Fee lines (ADR 0001). It therefore equals the total on the
  invoice's own page and PDF.
- **A new, narrow DTO** (`ClientInvoiceSummaryResponse`), not
  `ClientInvoiceResponse`: it carries no Fee lines, no files and no send-back
  fields.
- **No write, ever.** The read never creates a draft. It does not touch the
  get-or-create path.
- **No `SecurityConfig` change.** `/api/me/**` matches no rule and falls
  through to `.anyRequest().authenticated()`, as `/api/me/agent` does. Because
  neither path carries an id, a Tester can address only their own Client.

**How it relates to the neighbouring features.**

- `send-a-client-invoice-back` (spec awaiting approval) returns a sent invoice
  to `DRAFT`. This read then excludes it with no change, and falls back to the
  previous sent or approved month. That matches that spec's story 23, under
  which a sent-back invoice disappears from the Tester's view. The summary DTO
  has no `sentBackAt` or `sentBackReason`, so that spec's "the reason is hidden
  from Testers" rule holds here by construction.
- `invoice-history` builds a Manager-facing list of final invoices across the
  Tenant, filtered by month, Contract and Agent. This read is not a list. It
  returns one row per Contract, for the caller's own Client only, and it lives
  in the caller's `/api/me` namespace. History will not duplicate it. If
  history later grows a Tester-facing list, this read can become a thin query
  over it.

### Frontend: the page

The Client dashboard moves into a route group, `app/client/(dashboard)/`, with
its own `error.tsx`. The URL stays `/client`. This is the shape
`real-agent-dashboard` settled for Frontend rule 6. The page is an async Server
Component and loads its data with `backendFetch` / `backendFetchJsonOrNull`.
It reads `/api/me/client` and `/api/me` first, then the Contract list, then
every Contract's Smartphones, SIM Cards and Requests, plus the latest-invoices
read, in parallel.

- **Identity.** If `GET /api/me/client` is `404`, the page renders a not-linked
  `EmptyState` in a `Card`, "Your login isn't linked to a Client yet", with
  "Ask your Manager to link your login to your Client before you can see your
  dashboard." It renders no stat cards. If either identity read fails any other
  way, the page **throws**. The `(dashboard)` `error.tsx` then renders one
  `EmptyState`, "Couldn't load your dashboard — reload the page to try again",
  under a header that names no one. Every figure on the page is scoped by these
  reads.
- **Regions fail alone.** If the Contract list fails, all three regions show
  their unavailable state. If any one Contract's Smartphones or SIM Cards fail,
  Active Fleet renders `—` with "Couldn't load your Fleet". If any one
  Contract's Requests fail, Open Requests renders `—` with "Couldn't load your
  Requests". A failed latest-invoices read makes the invoice card show an
  `EmptyState`, "Couldn't load your invoices", with "Reload the page to try
  again." A region never shows a count that silently dropped a Contract. That is
  why the page does not use `backendFetchList`, which turns a failure into `[]`.
- **Empty states.** With no Contract, Active Fleet and Open Requests show `0`,
  which is a true zero, and the invoice card shows an `EmptyState`, "No
  Contracts yet", with "Your Contracts and their invoices will show up here once
  the Manager sets them up." A Contract with no entry in the latest-invoices
  read shows "No invoices yet" in its row, as today.
- **The link** in the invoice card's header reads "Open Invoices" instead of
  "View all", and still goes to `/client/invoices` (`## Decisions taken`). The
  card's subtitle stays "Most recent month per Contract".
- **Small type sizes.** Every line this feature writes or rewrites in the page
  and in `ClientDashboardStats` uses the two type-scale tokens the
  `type-scale-tokens` enabler adds (Frontend rule 8, as amended), never
  `text-[12px]` / `text-[13px]`. Shared components this feature only calls,
  such as `StatCard`, are not touched for this.

### Frontend: the stat grid

`ClientDashboardStats` becomes a presentational component with no
`useSimulatedLoad` and no `"use client"`. It renders its grid at once, still
marked `data-testid="dashboard-ready"`. Its two props become `number | null`,
which is the `ManagerDashboardStats` / `AgentDashboardStats` contract. `null`
renders a muted `aria-hidden` "—" with an sr-only "Unavailable" and the
"Couldn't load…" meta, exactly as the Agent's grid does. Each card gets a
`data-testid` (`active-fleet-stat`, `open-requests-stat`) so the degraded state
can be asserted.

### Prefactoring

- **The demo footer becomes opt-in** (pays `docs/tech-debt.md` F12).
  `SurfacePage` gains a boolean `demoData` prop, false by default, and renders
  `DemoNote` only when it is set. The Manager dashboard is the one surface
  still showing demo figures, and it is the only page that sets it.
  `real-manager-dashboard` removes the prop and `DemoNote` together. Every
  non-Manager full-page golden loses its footer and is recaptured, deleted
  first. The `manager-*` dashboard, `manager-menu-open-*` and
  `change-password-dialog-open-*` goldens all render `/manager`, and they must
  not move. The orchestrator removes the paid entry at delivery.
- **`ClientDashboardStats` stops calling `useSimulatedLoad`.** The hook stays
  for the Manager grid.

### Visual suite

`tests/visual/stub-backend.mjs` gains Tester callers:

- `visual-tester-session`: a Tester of "Solstice Retail Group", username
  `dana.whitfield@solsticeretail.example`. `GET /api/me/client` returns that
  Client. `GET /api/contracts` returns two Contracts, one in the United States
  (USD) and one in the United Kingdom (GBP). Each Contract's `smartphones`,
  `sim-cards` and `requests` return mixed statuses, including a Retired unit,
  an In Repair Smartphone and a Pending Approval Request, so the filters are
  visible in the figures. `GET /api/me/client/latest-client-invoices` returns
  one `SENT` entry for the first Contract and one `APPROVED` entry for the
  second, each with a fixed `billingMonth`.
- `visual-tester-unlinked-session` (`/api/me/client` → `404`),
  `visual-tester-failing-identity-session` (`/api/me/client` → `500`) and
  `visual-tester-degraded-session` (one Contract's SIM Cards, its Requests and
  the latest-invoices read → `500`). These are not captured as goldens. They are
  asserted in `surfaces.spec.ts`, as the Agent's are.

The `client` surface moves onto `visual-tester-session`, and its four goldens
are recaptured, deleted first.

## Design direction

Conforms to `DESIGN.md`; no `DESIGN.md` change. The surface is **Operate**.
`DESIGN.md` already settles the layout: "a responsive stat-card grid (… 2 on
Client …) followed by a linking preview list", and "Don't surface a draft
invoice to the Client Portal". The page keeps exactly that grid and that
preview list.

Every state is built from components that already ship: `StatCard` with the
Agent grid's unavailable rendering, `EmptyState` inside a `Card`, `Badge`
through the central Client Invoice tone maps in `lib/status.ts`, and `Money`.
No new component. The only new tokens are the two type-scale sizes of the
`type-scale-tokens` enabler, which name the 12px and 13px sizes `DESIGN.md`'s
Label role already uses; nothing renders differently. The `design` slot does not
need to be engaged.

## Constraints

- Inherits every constraint in the `remote-support-mvp` spec: the stack, Tenant
  scoping, and an explicit currency on every amount.
- `GET /api/me/client` and `GET /api/me/client/latest-client-invoices` act only
  on the caller's own Client, resolved from the principal. No path variable or
  query parameter may name a Client or a Contract.
- The latest-invoices read never returns a `DRAFT` and never creates an
  invoice. Its `totalAmount` is the frozen total (ADR 0001), never a live
  computation.
- No `SecurityConfig` change and no Flyway migration.
- The page imports nothing from `@/lib/demo`. `frontend/lib/demo/client.ts` is
  deleted and `frontend/lib/demo/types.ts` is not, because `lib/status.ts` still
  imports it until `real-manager-dashboard`.
- Whole-page failures are thrown to the `(dashboard)` `error.tsx`. A region that
  fails alone logs the failure and renders its own "Couldn't load…" state while
  the rest of the page works, which Frontend rule 6 (amended by the human,
  2026-10-01) names as the intended pattern. It never renders `0`, an empty list
  or a fallback figure in place of a failure.
- Frontend rule 8 (amended): this feature's new and rewritten lines use the
  `@theme` type-scale tokens, not `text-[12px]` / `text-[13px]`. The ~233
  existing arbitrary uses elsewhere are not migrated; they stay as known debt.
- Visual recapture: because `maxDiffPixelRatio` lets `--update-snapshots`
  silently keep a stale baseline, every golden this feature changes is deleted
  before it is recaptured. A recaptured golden's diff against `main` must show
  only the intended change: the footer gone, and for `client-*` the new
  content.
- Backend tests run under `IntegrationTest` (a singleton Testcontainers
  Postgres, rolled back per method). Mocking a repository there is banned.
- e2e and visual runs use an isolated stack on ports other than
  3000/8080/5432, per `docs/agents/implementer-notes.md`. No test writes to the
  user's compose stack, including `postgres-demo`. No e2e step rewrites a
  `billing_month` in the database.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, the rendered
accessibility tree, and what a user sees. They never assert repository calls or
component internals.

1. **Backend: the HTTP API seam, which already exists.** `IntegrationTest` with
   MockMvc against real Postgres. Prior art: `AgentOwnRecordApiTest` (the
   sibling `/api/me/agent`), `ClientInvoiceApiTest` (a Tester's visibility of a
   sent invoice) and `AgentInvoiceByIdApiTest` (moving an invoice's
   `billingMonth` into the past through the repository to build an older
   month). The tests cover:
   - a Tester gets their own Client's name;
   - a Manager, an Agent and the unlinked `tester@example.com` get `404`, and no
     token gets `401`;
   - latest-invoices returns the current month when it is `SENT`;
   - it falls back to an older `APPROVED` month when the current month is
     `DRAFT` or absent;
   - it omits a Contract with only a draft;
   - it never returns another Client's Contract, including a Contract of
     `OtherTenantFixture`;
   - `totalAmount` equals the invoice's own `GET …/client-invoice` total;
   - a Fee logged after sending does not move `totalAmount`;
   - calling the read creates no `client_invoices` row.
2. **Frontend: component tests for `ClientDashboardStats`.** Vitest and Testing
   Library, in the shape of `components/agent/dashboard-stats.test.tsx`. They
   cover each card's value and meta (Open Requests' meta reads "Pending
   Approval, Submitted or In Progress"), each `null` rendering "—" and its
   "Couldn't load…" meta, and the grid rendering at once, marked
   `dashboard-ready`, with no skeleton.
3. **Frontend: one Playwright e2e spec against the real backend**,
   `tests/e2e/client-dashboard.spec.ts`. Prior art:
   `client-invoice-submission-and-visibility.spec.ts`, `agent-dashboard.spec.ts`
   and `helpers.ts` (`createContractWithTester`, `addSmartphone`,
   `addSimCard`, `submitRequestAsTester`). As the Manager, create a Client, a
   Contract with the seeded Agent, a Tester and a small Fleet. As the Tester,
   submit a Request. Then show the dashboard with the Client's name, the
   Tester's username, the right Active Fleet, Open Requests counting the
   Request whether it is at Pending Approval, Submitted or In Progress, and "No
   invoices yet". Have the Agent send the invoice and show the row as "Awaiting
   approval" with its total. Have the Manager approve it and show "Approved".
   The async Server Component's failure branches are covered by the visual
   suite's stub tokens, because component tests do not render async server
   components.
4. **Visual goldens: the existing backend-stubbed suite.** `client-*` moves
   onto `visual-tester-session`. The not-linked, failing-identity and degraded
   states are asserted as non-golden tests beside the Agent's.
5. **No new seam.** Each behaviour is tested at the highest seam that already
   exists and can see it.

## Decisions taken

- **The Client's name comes from a new `GET /api/me/client`, not from
  `GET /api/contracts`.** Reason: the Contract list names the Client only if it
  holds a Contract, so a new Client would lose its own name (story 3).
  `/api/clients/**` is Manager-only. A principal-keyed read in `/api/me` follows
  `/api/me/agent` and needs no `SecurityConfig` change.
- **"Latest sent or approved Client Invoice per Contract" is served by a new
  principal-scoped `GET /api/me/client/latest-client-invoices`, not by a
  per-Contract route under `/api/contracts/{id}/client-invoice`.** Reason: no
  route lists a Contract's past invoices for any role, and `/client/invoices`
  reads only the current month. One principal-scoped call needs no ownership
  guard. A Contract with no invoice is simply absent, so the "none" case needs
  no 204 or 404 for the loader to tell apart from a failure. The route is
  additive and cheap to remove.
- **The new read is for the caller's own Client only: a Manager or an Agent
  gets `404`.** Reason: it keeps the read narrow, so it does not become a
  second history API before `invoice-history` designs one.
- **It returns a narrow summary DTO, not `ClientInvoiceResponse`.** Reason: the
  card needs five fields. `send-a-client-invoice-back` adds `sentBackReason` to
  `ClientInvoiceResponse` and must hide it from Testers. A DTO that never
  carries it cannot leak it.
- **`totalAmount` uses the same frozen-total code path as the invoice's own
  view.** Reason: the dashboard and the invoice must never disagree (ADR
  0001).
- **The selection sits in `ClientInvoiceService`, the controller only
  resolves the caller.** Reason: Backend rule 2. The code is packaged in the
  existing `web` / `repository` / `dto` layout, per Backend rule 13's
  transition clause.
- **Active Fleet counts Smartphones `ACTIVE` or `IN_REPAIR` plus SIM Cards
  `ACTIVE`.** Reason: this is the shipped page's own rule (Smartphones not
  Retired, SIM Cards Active) mapped onto the real statuses. The card's meta is
  unchanged.
- **Open Requests counts Pending Approval, Submitted and In Progress, with the
  meta "Pending Approval, Submitted or In Progress".** The human's answer,
  2026-10-01 (`docs/inbox/question-tester-open-requests-pending-approval.md`).
  Reason: from the Tester's side a Request waiting on the Manager is not done.
  The Agent's dashboard leaves it out only because the Agent cannot start it
  yet, and that does not apply to a Tester.
- **The viewer chip shows the Tester's username.** Reason: a Tester has no
  name in the data model, and every other Client Portal page already shows
  `<username> · Tester`.
- **The invoice card's link reads "Open Invoices", not "View all".** Reason:
  `/client/invoices` shows only the current month, so "View all" promises
  something the page does not do. For example, a row showing last month's
  Approved invoice leads to "No Invoice sent yet for this Contract". The change
  is copy only and cheap to undo, and walkthrough step 12 shows it.
- **Invoice rows keep the Client Portal's Contract label,
  `<clientName> — <country label>`.** Reason: it is the label the Tester's
  Fleet, Requests and Invoices pages use, so the dashboard matches the pages it
  links to.
- **A failed identity read throws to a route-group `error.tsx`. Every other
  failure takes out only its own region, logged.** Reason: Frontend rule 6 as
  amended by the human on 2026-10-01, and the Agent dashboard's settled shape. Every figure is scoped by the
  Client, so without it nothing honest can be shown.
- **Each stat goes unavailable if any one Contract's read fails. The page does
  not use `backendFetchList`.** Reason: that helper turns a failure into `[]`,
  which would render as a quietly low count.
- **The empty-state copy is "No Contracts yet" and "No invoices yet",
  informational only.** Reason: the Tester can do nothing about either; the
  Manager sets up Contracts and the Agent sends invoices. It is cheap to change,
  and walkthrough step 12 shows it.
- **The demo footer is paid now, as an opt-in `demoData` prop that only the
  Manager dashboard sets.** Reason: after this feature, `/manager` is the only
  page with demo figures. The code change is a few lines. Its cost is a
  mechanical golden recapture in a ticket of its own, so the review can check
  that the only change is the footer.
- **Assumption, not verified in code: the Contract repository or
  `ContractAccessGuard.visibleContracts` can list a Client's Contracts within a
  Tenant.** `GET /api/contracts` already scopes to a Tester's Client, so the
  query exists in some form. If it does not, the ticket adds a derived query.
- **Testing: the existing HTTP seam for both reads, component tests for the
  stat grid, one e2e spec against a real backend, and the existing stubbed
  visual suite for layout and failure states.** Reason: each is the highest
  existing seam that can see its behaviour, and no new seam is added. Prior art
  is named in `## Testing decisions`.
- **The not-linked, failing and degraded states are played through stub
  session tokens.** Reason: a partial backend failure cannot be staged on a
  real stack, and this is the Agent dashboard's precedent. On a real stack, the
  not-linked state is also playable with the baseline `tester@example.com`.
- **Every changed golden is deleted before recapture.** Reason: the
  `maxDiffPixelRatio` debt.
- **The 12px and 13px sizes become two `@theme` tokens in an enabler of their
  own, `type-scale-tokens`, named `--text-label-sm` (12px) and `--text-label`
  (13px), with no line-height of their own.** Reason: Frontend rule 8 (amended)
  points at `@theme`, and the human approved this small enabler. The names
  follow the house style of semantic `@theme` names (`--color-ink-secondary`)
  and `DESIGN.md`'s Label role (12–13px). Setting no line-height makes
  `text-label-sm` render exactly as `text-[12px]` does, so no golden moves. A
  separate ticket keeps the token change reviewable on its own; renaming is
  cheap.
- **Only this feature's new and rewritten lines use the tokens; the ~233
  existing `text-[12px]` / `text-[13px]` uses are not migrated.** Reason: the
  migration touches nearly every surface and would recapture every golden, with
  no user-visible change. It stays as debt, which the orchestrator records in
  `docs/tech-debt.md` at delivery.

## Open questions

None

## Acceptance walkthrough

Run against an isolated stack with the backend on the `demo` profile (its own
fresh database, seeded by `DemoDataLoader`), never the user's compose stack.
Logins are in the root `README.md`.

1. [agent] Sign in as Dana (`dana.whitfield@solsticeretail.example`) and open `/client`. Show the header reading "Solstice Retail Group", the viewer chip "dana.whitfield@solsticeretail.example · Tester", and no occurrence of "Aurora Retail Group", "Nadia Okafor" or "EUR" on the page. (stories: 1, 2, 25)
2. [agent] At a 390 px wide viewport, show the header still contains "Solstice Retail Group" in full. (stories: 4)
3. [agent] As Dana, list `GET /api/contracts` and each Contract's Smartphones and SIM Cards through the API. Show Active Fleet equals the Smartphones `ACTIVE` or `IN_REPAIR` plus the SIM Cards `ACTIVE`, and that a Retired unit is not counted. (stories: 5, 6, 18, 22)
4. [agent] As Dana, list each Contract's Requests. Show Open Requests equals the count of Requests at Pending Approval, Submitted or In Progress, that a Pending Approval Request is counted and a Request in any other status is not, and that the card's meta reads "Pending Approval, Submitted or In Progress". (stories: 7, 8, 22)
5. [agent] As Dana, call `GET /api/me/client/latest-client-invoices`. Show the United States Contract's row is this month, "Awaiting approval", with the total its `GET …/client-invoice` returns, in USD. Show the United Kingdom Contract's row is last month, "Approved", in GBP, even though its current month is a Draft. (stories: 9, 10, 11, 22)
6. [agent] As Jordan, log a Fee on the United States Contract. Reload Dana's dashboard and show that row's total has not moved. Show that no `client_invoices` row was created by Dana's visits, by counting them before and after. (stories: 9, 20)
7. [agent] As Jordan, send the United Kingdom Contract's current invoice. Reload Dana's dashboard and show that row now reads this month, "Awaiting approval", with no delay or skeleton before the figures appear. (stories: 9, 11, 19)
8. [agent] Sign in as Noah (`noah.kim@harborline.example`) and show "Harbor Line Logistics", his own chip, one Contract, and none of Solstice's Contracts, Fleet or invoices. (stories: 1, 2, 18)
9. [agent] Call `GET /api/me/client` and the latest-invoices read as the Manager, as Jordan and as `tester@example.com`, and show `404` for each. Call them with no token and show `401`. Then sign in as `tester@example.com`, open `/client`, and show the not-linked message and no stat cards. (stories: 16, 21)
10. [agent] On a fresh non-demo isolated database, as the Manager, create a Client with a Tester and no Contract. Sign in as that Tester and show the Client's name in the header, `0` on both stat cards, and "No Contracts yet". Then add a Contract and show its row reading "No invoices yet". (stories: 3, 12, 13)
11. [agent] Against the stub backend: with the degraded Tester session, load `/client` and show Active Fleet and Open Requests reading "—" with their "Couldn't load…" lines, and the invoice card reading "Couldn't load your invoices", with the header still rendered and never a `0`. With the failing-identity session, show the single "Couldn't load your dashboard" message. (stories: 15, 17)
12. [agent] Follow "Open Invoices" from the dashboard and land on `/client/invoices`. Show `frontend/lib/demo/client.ts` deleted, `frontend/lib/demo/types.ts` still present, and no `@/lib/demo` import in the Client dashboard or `ClientDashboardStats`. Show `frontend/app/globals.css`'s `@theme` holding the two type-scale tokens, and no `text-[12px]` / `text-[13px]` in the Client dashboard or `ClientDashboardStats`. Show the "Demo data" footer present on `/manager` and absent on `/client`, `/client/fleet`, `/agent` and `/manager/carriers`. Run the full visual suite and show that only the intended goldens changed, each deleted first, and that the `/manager` goldens did not move. Then run `docs/agents/sdlc.json`'s `verify` green. (stories: 14, 23, 24, 25)
13. [human] Read the copy this feature adds: "Open Invoices", "No Contracts yet", the not-linked message and the "Couldn't load…" lines. Confirm each reads right to a Tester. (stories: 12, 13, 14, 15, 16)
14. [human] Sign in as Dana against the demo profile. Confirm every figure on the dashboard is one you can find on her Fleet, Requests and Invoices pages, and that nothing on it belongs to Harbor Line Logistics. (stories: 18, 22)

## Execution order

Six tickets. The two enablers and the backend read are independent. The three
dashboard slices run in a line, because all edit the same page and the stub,
and all recapture the `client-*` goldens. The demo-footer enabler goes before
them so that the goldens are recaptured once against a footer-less shell, and
the type-scale enabler goes before them so that their new UI is written with
the tokens from the start. The spec's first cut had two dashboard slices; the
first was split so that each ticket fits one context.

1. `surface-demo-note-opt-in` (enabler): `SurfacePage` gains `demoData`, which only the Manager dashboard sets; every non-Manager golden is recaptured, deleted first. Modules: app-shell, Manager dashboard page, visual suite. No dependency. (stories: 23)
2. `type-scale-tokens` (enabler): `frontend/app/globals.css`'s `@theme` gains `--text-label-sm` (12px) and `--text-label` (13px), with no line-height; no existing use is migrated and no golden moves. Modules: theme. No dependency. (stories: none; enables Frontend rule 8 for tickets 4, 5 and 6)
3. `tester-own-client-reads`: `GET /api/me/client` and `GET /api/me/client/latest-client-invoices`, with the repository query, the service method, the summary DTO and their integration tests. Modules: backend `web`, `repository`, `dto`. No dependency. (stories: 3, 9, 11, 18, 20, 21)
4. `client-dashboard-identity`: the `(dashboard)` route group and its `error.tsx`, the real header and chip, the not-linked and failing-identity states, and the stub's Tester sessions for identity; `client-*` goldens recaptured. Modules: Client dashboard page, visual suite. Depends on `surface-demo-note-opt-in`, `type-scale-tokens` and `tester-own-client-reads`. (stories: 1, 2, 4, 16, 17, 24)
5. `client-dashboard-fleet-and-requests`: Active Fleet and Open Requests (Pending Approval, Submitted or In Progress) from the Contracts, with their unavailable states, `ClientDashboardStats` off `useSimulatedLoad` with its component tests, and the stub's Contract, Fleet and Request fixtures and degraded session; `client-*` goldens recaptured. Modules: Client dashboard page, `components/client`, visual suite. Depends on `client-dashboard-identity`. (stories: 5, 6, 7, 8, 15, 18, 19, 24)
6. `client-dashboard-latest-invoices`: the Latest Client Invoice card from the new read, with its empty and unavailable states and the "Open Invoices" link; the stub's latest-invoices fixture; `frontend/lib/demo/client.ts` deleted; the `client-dashboard.spec.ts` e2e spec; `client-*` goldens recaptured. Modules: Client dashboard page, visual suite, e2e suite. Depends on `client-dashboard-fleet-and-requests`, because the page's remaining imports from `@/lib/demo/client` go in this slice. (stories: 3, 9, 10, 12, 13, 14, 15, 22, 24, 25)
