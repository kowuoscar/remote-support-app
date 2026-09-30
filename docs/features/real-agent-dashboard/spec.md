---
feature: real-agent-dashboard
epic: real-dashboards
status: approved
date: 2026-09-30
---

<!-- sdlc:template spec 1 -->

# Real Agent dashboard

## Problem

An Agent's home page is the first thing they see after signing in, and every
figure on it is invented. `frontend/app/agent/page.tsx` reads all of it from
`frontend/lib/demo/agent.ts`: the header and the viewer chip say "Camille
Duforet · France", the standing salary is €2,400, the Rollout Advance is €300,
the Local Support Fees total is a sum over fabricated Client Invoices, the
Open Requests count and the Recent Requests card list Requests that do not
exist, and "My Invoice status" shows an August invoice nobody sent. A real
Agent — Jordan Ellis in the United States, say — signs in and is shown another
person's name, pay and advance, in another currency.

That is the harm the epic ranks first: this page is not merely stale, it is
wrong about a person. And it hides whether anything actually needs the Agent
today, which is the only question a home page exists to answer.

Two smaller defects ride along. The page fakes a 600 ms loading skeleton
(`useSimulatedLoad`) in front of data that was never loading. And its Recent
Requests card renders fixed September dates through `formatRelativeAge`
against the wall clock, so the Agent console's visual goldens drift a day for
every day the suite is run (`docs/tech-debt.md`, the `frontend/lib/demo/agent.ts`
entry) — the epic assigns paying that debt to this feature.

## Journeys

Advances `docs/roadmap/real-dashboards.md`, the first of its three features;
the epic's `## Reworked` explains why the Agent's page goes first.

- **See where things stand on arrival** (`partial`, stays `partial`): this
  feature makes the Agent's third of the journey true — the Agent's dashboard
  shows their own name and their own numbers, each traceable to a record. The
  journey reaches `exists` only when `real-client-dashboard` and
  `real-manager-dashboard` have also landed, so `docs/journeys.md` is not
  edited by this feature.

## Goals / Non-goals

Goals:

- The Agent's dashboard shows the signed-in Agent's own name and country, and
  the viewer chip names them.
- Every one of the four stat cards and every Recent Requests row comes from
  the database, scoped to the signed-in Agent: Local Support Fees for the
  month, Open Requests, the status of their own Agent Invoice, and their
  standing salary and Rollout Advance.
- A region that cannot be loaded says so, alone, and never shows a zero or a
  fabricated figure in its place.
- `frontend/lib/demo/agent.ts` is deleted, and nothing compiles a figure into
  this page any more.
- The Agent console's visual goldens are rendered against the stub backend and
  stop drifting with the wall clock.

Non-goals — each is something a reasonable agent would otherwise build:

- **No change to the Client's or the Manager's dashboard.** Those are
  `real-client-dashboard` and `real-manager-dashboard`. `frontend/lib/demo/types.ts`,
  `frontend/lib/demo/client.ts`, `frontend/lib/demo/manager.ts` and
  `frontend/lib/demo/contracts.ts` stay; `useSimulatedLoad` stays too, because
  the Manager and Client stat grids still call it.
- **No `updatedAt` on Request, and no migration.** Recent Requests orders by
  when a Request was raised (see `## Decisions taken`). V55 stays free.
- **No new aggregate endpoint for the dashboard.** Every figure comes from a
  route that already exists, plus one small read of the caller's own Agent
  (`## Solution`). No "dashboard summary" route bundling them.
- **No Local Support Fees sum computed in the frontend or over the Fee table.**
  The figure is the Agent Invoice's own line. A Fee-table sum would miss every
  Contract's postpaid base amount and disagree with the invoice.
- **No non-creating Agent Invoice read.** The dashboard uses the existing
  current-month get-or-create read, exactly as My Invoice does.
- **No change to who may change a standing amount.** Setting one stays
  Manager-only; the Agent gains a read of their own, nothing else, and the
  Manager-only `/api/agents/{agentId}/standing-amounts` route is not widened.
- **No past invoices on the dashboard.** "My Invoice status" is this month's
  invoice, the one My Invoice shows. An Agent Invoice history is not this
  feature.
- **No change to the viewer label on the Agent's other pages.** Requests,
  Fleet, Stock, Carriers, Client Invoices and My Invoice keep
  `<username> · Agent`. Aligning them is a separate, cosmetic change.
- **No new links, cards or figures.** The page keeps its four stat cards and
  its Recent Requests card, with their current labels, and gains only the
  unavailable, empty and not-linked states it needs to be honest.
- **No loading skeleton or `loading.tsx` in place of the simulated one.**
- **No masks on the Agent dashboard's goldens.** They are rendered against
  stubbed data, not blanked out.
- **No fix to `maxDiffPixelRatio`** (`docs/tech-debt.md`, `frontend/playwright.config.ts`
  entry). This feature works around it when recapturing (`## Constraints`)
  and leaves the setting alone.

## User stories

1. As an Agent, I want my dashboard's header to show my own name and country,
   so that I know at a glance the page is about me.
2. As an Agent, I want the viewer chip on my dashboard to show my own name, so
   that I am never shown as someone else.
3. As an Agent, I want to see this month's Local Support Fees across all my
   Contracts, so that I know how much I have fronted and will be reimbursed.
4. As an Agent, I want that Local Support Fees figure to be exactly the one on
   my own Agent Invoice, so that the dashboard and My Invoice never disagree.
5. As an Agent, I want the Local Support Fees card to name the billing month it
   covers, so that I know which month's work it counts.
6. As an Agent who has already sent this month's invoice, I want the Local
   Support Fees card to tell me it shows the figure I sent, so that I don't
   expect a Fee logged afterwards to move it.
7. As an Agent, I want to see how many of my Requests are Submitted or In
   Progress across all my Contracts, so that I know how much work is waiting
   on me.
8. As an Agent, I want to see the status of this month's Agent Invoice and the
   month it is for, so that I know whether I still have to send it or am
   waiting on the Manager.
9. As an Agent, I want to see my standing salary in effect this month, so that
   I know what my invoice will claim.
10. As an Agent, I want to see my standing Rollout Advance in effect this
    month, shown as zero when none has ever been set, so that I know what
    cash-flow support I have.
11. As an Agent, I want every amount on my dashboard in my own currency, so
    that I never read a figure in someone else's.
12. As an Agent, I want the five most recently raised Requests across all my
    Contracts, newest first, so that I see new work as soon as I arrive.
13. As an Agent, I want each Recent Requests row to show the Request type, its
    Contract, the Tester it is for, how long ago it was raised and its status,
    so that I can tell which one needs me without opening the queue.
14. As an Agent, I want the Recent Requests card to link to my Requests queue,
    so that I can go straight from the preview to acting on it.
15. As an Agent with no Requests yet, I want the Recent Requests card to say so
    plainly, so that an empty list doesn't look broken.
16. As an Agent, I want a figure that couldn't be loaded to say so, on its own
    card, with the rest of the page still showing, so that I never mistake a
    failure for a real zero.
17. As a person whose login is not linked to an Agent record, I want to be told
    that, instead of seeing numbers, so that I know to ask my Manager.
18. As an Agent, I want the dashboard to show only my own Contracts' Requests
    and my own amounts, so that my view is exactly my scope.
19. As an Agent, I want my dashboard to reflect the database the moment the
    page loads, with no artificial delay, so that I see what is true now.
20. As an Agent, I want opening my dashboard to have no effect beyond the one
    opening My Invoice already has, so that visiting my home page never changes
    what anyone else sees.
21. As a Company Manager, I want standing amounts to stay mine alone to change,
    and one Agent never able to read another's, so that giving Agents their own
    figure takes nothing away from my control.
22. As someone testing the product by hand, I want every figure on the Agent
    dashboard traceable to a record in the `demo` profile's database, so that
    I can check the page against the data.
23. As the team, I want the Agent dashboard's visual goldens to render real
    layouts from stubbed data and not drift with the date, so that a golden
    only moves when the page does.
24. As the team, I want no Agent data compiled into the frontend, so that a
    fabricated figure can never ship on this page again.

## Solution

### Where each figure comes from

| On the page | Source | Notes |
|---|---|---|
| Header name and country, viewer chip, currency | the caller's own Agent (new read, below) | `<name> · <country label>`; chip `<name> · Agent` |
| Standing salary + Rollout Advance | the same read | resolved for the current month, the same resolution the Manager's edit form uses |
| Local Support Fees — `<month>` | this month's Agent Invoice, its `localSupportFees` line | the month label is the invoice's own `billingMonth`, never the wall clock |
| My Invoice status | this month's Agent Invoice, its `status` and `billingMonth` | |
| Open Requests | every Request of every Contract in `GET /api/contracts`, status Submitted or In Progress | Pending Approval is excluded: the Agent cannot start it (CONTEXT.md) |
| Recent Requests | the same Requests, all statuses, newest `createdAt` first, first five | |

### Backend: the caller's own Agent

One new read, `GET /api/me/agent`, in the caller's own `/api/me` namespace,
in a small controller of its own beside `MeController` — the same shape
`self-service-password-change` gave `POST /api/me/password`. It resolves the
Agent from the authenticated principal through `CallerIdentityResolver`, takes
no id from the request, and returns:

```
GET /api/me/agent
200 { "agentId", "name", "country", "currency",
      "salaryAmount", "rolloutAdvanceAmount" }
404 — the caller's login is not linked to an Agent (any Manager or Tester, or an unlinked Agent login)
```

`salaryAmount` and `rolloutAdvanceAmount` are resolved by the existing
`StandingAmountService` for the current billing month, exactly as
`AgentStandingAmountController`'s GET resolves them for the Manager. An
Agent with no Rollout Advance ever set gets `0`.

`/api/me/**` matches no rule in `SecurityConfig` and falls through to
`.anyRequest().authenticated()`, so **no `SecurityConfig` change is needed**.
Because the path carries no id, one Agent cannot address another's figures,
and the Manager-only `/api/agents/{agentId}/standing-amounts` stays exactly as
it is. `MeResponse` is not changed.

### Backend: nothing else

The page reads, unchanged: `GET /api/contracts` (already scoped to the Agent's
own Contracts), `GET /api/contracts/{contractId}/requests` per Contract, and
`GET /api/agents/{agentId}/invoice` (the current-month get-or-create read My
Invoice already uses). No migration, no new column, no change to Request.

### Frontend: the page

The Agent dashboard stays an async Server Component and loads its data through
`backendFetch`, following the Manager dashboard's pattern: the caller's Agent
first, because the invoice read needs its id, then the invoice, the Contracts
and every Contract's Requests in parallel.

- **Identity.** If `GET /api/me/agent` is `404`, the page renders the
  not-linked `EmptyState` My Invoice already shows ("Your login isn't linked
  to an Agent record yet…"), and no stat cards. If it fails any other way, the
  page renders a single "Couldn't load your dashboard — reload the page to try
  again" `EmptyState`, because every figure is scoped by it.
- **Regions fail alone.** Each loader returns `null` on failure and logs it,
  never throws (with `unstable_rethrow` first, as on the Manager page). A
  `null` invoice makes the Local Support Fees and My Invoice status cards
  render `—` with a "Couldn't load your invoice" meta. A `null` Contract list,
  or any one Contract's Requests failing, makes the Open Requests card render
  `—` with "Couldn't load your Requests", and the Recent Requests card show an
  unavailable state — never a count that silently drops a Contract. That is
  why the page does **not** use `backendFetchList`, which turns a failure into
  `[]`.
- **Recent Requests rows.** `<Request type label> · <Client name> — <country label>`
  (the Contract label the Agent's Requests page already uses), then
  `Raised by <raisedByUsername> · raised <relative age>` with the age inside a
  `<time dateTime>` element, and the status `Badge` keyed by the real status
  value. The card's subtitle becomes "Across all your Contracts" (it hardcodes
  "both" today). With no Requests at all, the card shows an `EmptyState`,
  "No Requests yet", and keeps its "Open queue" link.
- **One clock per render.** The page reads the time once and passes it to
  every `formatRelativeAge` call, which already accepts a `now` argument.
- **Local Support Fees meta.** While the invoice is Draft: "Running total, all
  your Contracts" (as today). From Sent onward the line is frozen (ADR 0003),
  so the meta reads "As sent on your invoice".

### Frontend: the stat grid

`AgentDashboardStats` becomes a presentational component with no
`useSimulatedLoad`: it renders its grid immediately, still marked
`data-testid="dashboard-ready"` so the visual suite's wait is unchanged. Its
props become nullable where a region can fail — the same `number | null`
contract `ManagerDashboardStats` uses — and its invoice status is typed by the
real `AgentInvoiceStatusValue`, rendered through the existing
`agentInvoiceStatusToneByValue` / `agentInvoiceStatusLabelByValue` maps.

### Prefactoring

- **`AgentDashboardStats` stops importing `@/lib/demo/types`**: it moves from
  the demo `AgentInvoiceStatus` to the real `AgentInvoiceStatusValue` and the
  `…ByValue` maps. `frontend/lib/demo/types.ts` itself stays for
  `real-manager-dashboard` to delete, and `lib/status.ts` still imports it.
- **A null-on-failure JSON loader.** The Manager page hand-writes one
  `try / unstable_rethrow / log / null` function per endpoint. This feature
  needs four more of the same, so it adds one shared helper beside
  `backendFetch` that reads a JSON body or returns `null`, logging a labelled
  message. The Manager page is not migrated onto it here — it belongs to
  `real-manager-dashboard`, which can.
- **The relative-time clock debt.** Deleting `frontend/lib/demo/agent.ts`
  removes the fixed timestamps. The stub backend's Agent Request fixtures give
  `createdAt` **relative to the stub's own current time** (now minus a fixed
  offset), and `formatRelativeAge` counts elapsed whole days rather than
  calendar days, so the rendered ages are the same on every run. That is the
  entry's own suggested cure ("seed relative-safe dates"), and it leaves the
  ages visible in the golden rather than masked.

### Visual suite

The `agent` surface gains the `visual-agent-session` token, and
`tests/visual/stub-backend.mjs` gains, for the Agent caller only:
`GET /api/me/agent` (Jordan Ellis, United States, USD, a salary and a non-zero
Rollout Advance so the meta line is exercised), `GET /api/contracts` (two
Contracts), `GET /api/contracts/{id}/requests` (enough Requests across both to
fill five rows and cover Submitted, In Progress, Completed and Pending
Approval), and `GET /api/agents/{agentId}/invoice` (a Draft invoice with a
fixed `billingMonth`, so the Local Support Fees label never changes). The four
`agent-*` goldens are recaptured; no other golden may move.

The stub also gains two more Agent session tokens, used by the walkthrough
and not captured as goldens: an **unlinked** one, whose `GET /api/me/agent`
is `404`, and a **degraded** one, whose invoice route and one Contract's
Requests route answer `500`. They make the not-linked and unavailable states
playable in a browser without breaking a real database.

## Design direction

Conforms to `DESIGN.md`; no `DESIGN.md` change. The surface is **Operate**.
`DESIGN.md` already settles its shape: "Dashboards use a responsive stat-card
grid (… up to 4 on Agent …) followed by a linking preview list into the
relevant full table", and "each surface leads with money and status … open
Requests, invoice state". The page keeps exactly that grid and preview list.

Every state is assembled from shipped components: `StatCard` with `—` and a
"Couldn't load…" meta (the Manager dashboard's unavailable stat), `EmptyState`
inside a `Card` (My Invoice's not-linked and failure states), `Badge` with the
real status tone maps, `Money`. No new component, no new token. The `design`
slot does not need to be engaged.

## Constraints

- Inherits every constraint in the `remote-support-mvp` spec: stack, tenant
  scoping, explicit currency on every amount.
- `GET /api/me/agent` acts only on the caller's own Agent, resolved from the
  principal. No path variable and no query parameter may name an Agent.
- No `SecurityConfig` change. `/api/agents/{agentId}/standing-amounts` stays
  Manager-only for GET and POST.
- No Flyway migration. V55 stays free.
- Local Support Fees is read from the Agent Invoice's own `localSupportFees`
  line, never summed from Fees or Client Invoices.
- Every month label on the page comes from the Agent Invoice's `billingMonth`,
  never from the wall clock.
- A region that fails renders an unavailable state; it never renders `0`, an
  empty list, or a fallback figure in its place.
- The page imports nothing from `@/lib/demo`. `frontend/lib/demo/agent.ts` is
  deleted; `frontend/lib/demo/types.ts` is not.
- Visual recapture: because `maxDiffPixelRatio` lets `--update-snapshots`
  silently keep a stale baseline, the four `agent-*` goldens are **deleted
  before** being recaptured, so each one is provably new.
- Backend tests run under `IntegrationTest` (singleton Testcontainers Postgres,
  per-method rollback). Mocking a repository there is banned.
- e2e and visual runs go against an isolated stack on ports other than
  3000/8080/5432, per `docs/agents/implementer-notes.md`; the user's
  docker-compose stack, including `postgres-demo`, is never written to by a
  test.
- No test changes a seeded user's data in a way the rest of the suite relies
  on; e2e specs build their own Contracts and Requests.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, the rendered
accessibility tree, and what a user sees. Never repository calls or component
internals.

1. **Backend — the HTTP API seam, the existing one.** `IntegrationTest` +
   MockMvc against real Postgres. Prior art: `ChangeOwnPasswordApiTest` (the
   other `/api/me/*` endpoint), `AgentIdentityApiTest` (how an Agent login
   resolves to its Agent) and `AgentInvoiceApiTest` (standing amounts feeding
   the invoice). Covers: the Agent gets their own name, country, currency and
   standing amounts; the amounts are the ones resolved for the current month,
   not a future-effective change a Manager just made; Rollout Advance is `0`
   when never set; a Manager, a Tester and an unauthenticated caller are
   refused (`404` for the first two, `401` for the last); and the Manager-only
   standing-amounts route still refuses an Agent with `403`.
2. **Frontend — component tests for `AgentDashboardStats`.** Vitest + Testing
   Library, in the shape of `components/manager/dashboard-stats.test.tsx`.
   Covers: each card's value and meta from props; every nullable prop
   rendering `—` and its "Couldn't load…" meta; the Draft-versus-sent Local
   Support Fees meta; the status badge label per `AgentInvoiceStatusValue`;
   and that the grid renders at once, marked `dashboard-ready`, with no
   skeleton.
3. **Frontend — Playwright e2e against the real backend, one spec.** Prior
   art: `agent-invoice-submission-and-approval.spec.ts` and the shared
   `helpers.ts`. As a Manager, create a Contract for the seeded Agent, have a
   Tester submit a Request on it, then sign in as the Agent and show the
   dashboard naming Jordan Ellis, counting the Request as open, listing it
   first in Recent Requests, and showing the invoice's status and amounts.
   The async Server Component's loading and failure branches are covered
   here and by the visual suite, since component tests do not render async
   server components (the convention `manager-invoice-review-queue` set).
4. **Visual goldens — the existing backend-stubbed suite.** The `agent`
   surface moves onto `visual-agent-session` and the extended stub (see
   `## Solution`). Its four goldens are recaptured once, deleted first. They
   carry no mask, and the Recent Requests ages stay in the image, because the
   stub's dates are relative to the stub's own clock.
5. **No new seam.** Everything above uses a seam that already exists; the
   highest one that can see each behaviour is used.

## Decisions taken

- **Recent Requests orders by when a Request was raised (`createdAt`), and
  each row says "raised <age>" instead of "updated <age>"** — Request has no
  `updatedAt`, and adding one is a migration with a backfill plus a write on
  every status change (the Agent's status controls, the Manager's approve and
  reject, completion, cancellation) for one card's sort order. The Agent's
  Requests queue already shows `createdAt` as its age column, so the preview
  and the full table agree. The feature line asks for "recent" Requests, not
  "recently updated". Nothing is foreclosed: an `updatedAt` can be added later
  if the human wants the other ordering, and walkthrough step 13 puts this in
  front of them.
- **The Agent's name, country, currency and standing amounts come from a new
  `GET /api/me/agent`, not from widening the Manager-only standing-amounts
  route and not from `GET /api/contracts`** — the Contract list names the
  Agent only if they hold a Contract, so a new Agent would lose their own
  name. Widening `/api/agents/{agentId}/standing-amounts` would add a matcher
  and an ownership guard to a Manager route. A read in `/api/me`, keyed by
  the principal, needs neither and has prior art in `POST /api/me/password`.
  It is additive and cheap to remove.
- **The standing salary and Rollout Advance card reads standing amounts, not
  the Agent Invoice's salary and new-advance lines** — the card is labelled
  "Standing salary + advance", and CONTEXT.md's Standing amount is the rate,
  not one invoice's line. The two diverge once an invoice is sent: a Manager's
  per-invoice override (ADR 0003) changes the invoice's line and never the
  standing amount.
- **The new read gets its own controller beside `MeController`** —
  `MeController` is documented as the reference read-only identity endpoint,
  and this one depends on `StandingAmountService`, which it has no other use
  for. It follows the `self-service-password-change` precedent.
- **`MeResponse` is not extended** — it is read by every console and by the
  e2e helpers. Adding Agent-only money to "who am I" would widen a shared
  contract for one page.
- **"My Invoice status" is this month's Agent Invoice** — it is the only
  invoice the Agent's console addresses (CONTEXT.md: the Agent's own view is
  "this Agent's invoice for the current month"), and it is the one the My
  Invoice page opens, so the card and the page it points at agree.
- **The dashboard reads the invoice through the existing get-or-create route,
  accepting that the first visit of the month creates the Draft** — My
  Invoice already does exactly this. A Draft is not in the Review Queue and
  changes no figure anyone else sees. A non-creating read would be a new
  route for no visible difference.
- **Local Support Fees meta changes from "Running total…" to "As sent on your
  invoice" once the invoice is sent** — from Sent onward the line is frozen
  (ADR 0003), and "running total" would then be untrue whenever a Fee was
  logged after sending. It is a copy change on one card and cheap to undo;
  walkthrough step 14 shows it.
- **Open Requests counts Submitted and In Progress only, as today** — the
  card's shipped meta already says so, and a Pending Approval Request is one
  the Agent can see but cannot start (CONTEXT.md). Recent Requests shows every
  status.
- **The Recent Requests empty state is informational only ("No Requests
  yet"), with the card's existing "Open queue" link kept** — the Agent has
  nothing to do about an empty list, and the link to the queue already covers
  logging one proactively. It is cheap to change; walkthrough step 14 shows it.
- **A failed identity read renders one page-level message; any other failure
  takes out only its own region** — every figure is scoped by the Agent, so
  without it there is nothing honest to show. Past that point each region
  fails alone, the Manager dashboard's rule.
- **Open Requests goes unavailable if any one Contract's Requests fail, and
  the page does not use `backendFetchList`** — that helper turns a failure
  into `[]`, which would render as a quietly low count.
- **`useSimulatedLoad` is removed from the Agent's stat grid only, with no
  skeleton or `loading.tsx` replacing it** — the data is awaited on the
  server, so there is nothing to show a skeleton for. The hook stays for the
  Manager and Client grids until their features remove it.
  `data-testid="dashboard-ready"` stays so the visual suite needs no new wait.
- **A shared null-on-failure JSON loader is added, and the Manager page is not
  migrated onto it** — this page needs four such loaders, so writing them
  once saves code now. Migrating the Manager page belongs to
  `real-manager-dashboard`, which rewrites that page anyway.
- **The clock debt is paid by stub dates relative to the stub's own time, not
  by masking or by freezing a clock** — the page renders server-side, so a
  browser-clock freeze (`page.clock`) cannot reach it. Masking would hide the
  very line the golden should check. Relative fixture dates keep the text
  stable and visible.
- **The viewer chip on this page shows the Agent's name, while the Agent's
  other pages keep showing the username** — the feature line asks for the
  Agent's own name on this page. Changing six other pages' chips is outside
  its scope and is purely cosmetic.
- **Testing: the existing HTTP seam for the endpoint, component tests for the
  stat grid, one e2e spec for the page against a real backend, the existing
  stubbed visual suite for layout** — each is the highest existing seam that
  can see its behaviour. No new seam is added. Prior art is named in
  `## Testing decisions`.
- **The not-linked and unavailable states are played through two extra stub
  session tokens, not by corrupting a database** — no API path produces an
  unlinked Agent login, and a partial backend failure cannot be staged on a
  real stack. The stub already tells callers apart by token, so two tokens
  are the cheapest honest way to show those states in a browser.
- **The four `agent-*` goldens are deleted before recapture** — the
  `maxDiffPixelRatio` debt means `--update-snapshots` can keep a stale file.
  Deleting first makes each recapture provable, without touching the setting.

- **The ticket cut was accepted after the critic's recheck failed on one line
  (orchestrator, 2026-09-30).** The recheck passed R1–R5 and R7–R8. R6 failed
  on `agent-own-record-read`, which named a test class that does not exist.
  The orchestrator applied the critic's own replacement verbatim, naming
  `AgentInvoiceApiTest`, `AgentIdentityApiTest` and `ChangeOwnPasswordApiTest`,
  checked that the three exist, and did not escalate. A wrong file name is not
  the spec being unclear, which is what a second failure escalates.

- **`agent-dashboard-invoice-figures` was re-merged after a Regression line was
  added, not rebuilt (orchestrator, 2026-09-30).** The merger's test guard
  refused `frontend/tests/visual/surfaces.spec.ts` because the ticket's
  `## Regression` did not name it. The diff was 13 lines added and 0 removed:
  one new degraded-invoice case, the fixture for the ticket's own
  unavailable-card criterion. The orchestrator named it in `## Regression`
  instead of spending the ticket's one retry on identical work.

## Open questions

None

## Acceptance walkthrough

Run against an isolated stack with the backend on the `demo` profile (its own
fresh database, seeded by `DemoDataLoader`), never the user's compose stack.

1. [agent] Sign in as `agent@example.com` and open `/agent`; show the header reading "Jordan Ellis · United States", the viewer chip "Jordan Ellis · Agent", and no occurrence of "Camille Duforet", "France" or "EUR" anywhere on the page. (stories: 1, 2, 11, 24)
2. [agent] As Jordan, call `GET /api/me/agent` and show it returns his own Agent id, name, country, `USD`, salary `2700` and Rollout Advance `0`; show the "Standing salary + advance" card displaying `$2,700` with "+ $0 Rollout Advance". (stories: 9, 10, 11)
3. [agent] As Jordan, call `GET /api/agents/<his id>/invoice` and show that the Local Support Fees card equals its `localSupportFees`, that the card's month is its `billingMonth`, that the My Invoice status card reads "Awaiting approval" for that month (the loader sends his invoice), and that the Local Support Fees meta reads "As sent on your invoice". (stories: 3, 4, 5, 6, 8)
4. [agent] Sign in as Priya Shah (her demo login, from the root `README.md`) and show her own name, "United Kingdom", amounts in GBP, her standing Rollout Advance as the meta, a Draft invoice, and the meta "Running total, all your Contracts". (stories: 1, 6, 10, 11, 18)
5. [agent] As Jordan, list `GET /api/contracts` and each Contract's Requests through the API; show the Open Requests card equals the number in Submitted or In Progress, and that the Pending Approval Return is not counted. (stories: 7, 18, 22)
6. [agent] Show the Recent Requests card's five rows are the five newest by `createdAt` across both of Jordan's Contracts, each with its type, "Client — United States" label, the Tester's username, "raised <age>" and its status badge; follow "Open queue" and land on `/agent/requests`. (stories: 12, 13, 14)
7. [agent] As a Tester of one of Jordan's Contracts, submit a new Topup; reload Jordan's dashboard and show Open Requests went up by one and the new Request heads Recent Requests as "raised today", with no delay or skeleton before the figures appear. (stories: 7, 12, 19)
8. [agent] Call `GET /api/me/agent` as the Manager and as a Tester and show `404` for both, and with no token and show `401`; call `GET /api/agents/<Priya's id>/standing-amounts` as Jordan and show `403`; and show a Manager's standing-amount change still succeeds and does not move Jordan's dashboard figure until its effective month. (stories: 18, 21)
9. [agent] Before and after Jordan's first dashboard visit on a fresh (non-demo) isolated database, list the Review Queue as the Manager and show it unchanged, and show the invoice the dashboard created is the same Draft My Invoice then opens. (stories: 20)
10. [agent] Against the stub backend with the degraded Agent session (invoice and one Contract's Requests answering `500`), load `/agent` and show the Local Support Fees, My Invoice status and Open Requests cards reading "—" with their "Couldn't load…" lines, Recent Requests unavailable, and the header and standing amounts still rendered — never a `0`; then stop the stub and show the single page-level "Couldn't load your dashboard" message. (stories: 16)
11. [agent] Against the stub backend with the unlinked Agent session, load `/agent` and show the not-linked message and no stat cards; then, on a fresh non-demo isolated database where Jordan holds no Contract, sign in as Jordan and show "No Requests yet" and an Open Requests of `0`. (stories: 15, 17)
12. [agent] Show `frontend/lib/demo/agent.ts` deleted, `frontend/lib/demo/types.ts` still present, and no `@/lib/demo` import in the Agent dashboard page or `AgentDashboardStats`; show the stub's Agent Request fixtures computing `createdAt` from the stub's current time; run the full visual suite and show only the four `agent-*` goldens recaptured (deleted first), unmasked, and every other golden unchanged; then run `docs/agents/sdlc.json`'s `verify` green. (stories: 23, 24)
13. [human] Look at Jordan's Recent Requests card and confirm that ordering by when a Request was raised, with "raised <age>", is what you want from "recent" — rather than a "recently updated" ordering, which would need a new column on every Request. (stories: 12, 13)
14. [human] Read the copy this feature adds — "As sent on your invoice", "No Requests yet", and the "Couldn't load…" lines — and confirm each reads right to an Agent. (stories: 6, 15, 16)
15. [human] Sign in as Jordan against the demo profile and confirm every figure on the dashboard is one you can find in the data — My Invoice, the Requests queue — and that nothing on the page belongs to anyone else. (stories: 4, 18, 22)

## Execution order

Four slices in a line, each a complete path and demoable on its own; the
dashboard slices extend the stub backend for what they wire and recapture the
four `agent-*` goldens, so they cannot run in parallel.

1. `agent-own-record-read` — `GET /api/me/agent` with its integration tests. (stories: 21)
2. `agent-dashboard-identity-and-standing-amounts` — the page's real header, viewer chip, not-linked and failure states; the standing salary and advance card; the null-on-failure loader; `AgentDashboardStats` off `useSimulatedLoad` and off `@/lib/demo/types`, with its component tests; the `agent` visual surface moved onto `visual-agent-session`. Depends on `agent-own-record-read`. (stories: 1, 2, 9, 10, 11, 16, 17, 19)
3. `agent-dashboard-invoice-figures` — Local Support Fees and My Invoice status from this month's Agent Invoice, with the month label, the Draft/sent meta and the unavailable state; the e2e spec's invoice cases. Depends on `agent-dashboard-identity-and-standing-amounts`. (stories: 3, 4, 5, 6, 8, 20)
4. `agent-dashboard-requests` — Open Requests and Recent Requests from the Agent's Contracts, ordered by `createdAt`, with the empty and unavailable states and one clock per render; stub fixtures with relative dates; `frontend/lib/demo/agent.ts` deleted. Depends on `agent-dashboard-invoice-figures`, because the page's imports of `currentMonthLabel`, `myAgentInvoices` and `runningLocalSupportFees` from that file go in slice 3. (stories: 7, 12, 13, 14, 15, 18, 22, 23, 24)
