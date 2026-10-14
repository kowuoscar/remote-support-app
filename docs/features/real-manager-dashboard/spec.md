---
feature: real-manager-dashboard
epic: real-dashboards
status: approved
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

The chip cannot simply show the Manager's own name, because a Manager has none
in the data model, only a login. An Agent's name lives on their Agent record
and a Tester's on their Tester record, but a Manager is a bare Login. The human
settled on 2026-10-14 that a Manager has a name (e.g. "Yazan Abuhassan"), that
the chip shows it, and that the Manager can correct it themselves from their
account menu (`## Decisions taken`). This feature adds the stored **Manager
name** and that correction. The SuperAdmin's entry of the name at creation
belongs to a later epic (`## Solution`, "Carried to `tenant-administration`").

The two money figures have never been defined. The epic leaves three choices to
this spec: what "billed" means, what "payout" means, and which month "this
month" is (`docs/roadmap/real-dashboards.md`, `## Reworked`). A fourth choice
comes from the code. A Contract's currency is its Agent's country's currency
(`Country` → `Currency`: USD, EUR, GBP, MXN, PHP). The demo story already has
USD Contracts (Jordan Ellis) and a GBP Contract (Priya Shah). The system holds
no exchange rate, so one "USD" total over every invoice cannot be computed
honestly. The human settled all four on 2026-10-08 (`## Decisions taken`).

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

- The viewer chip names the signed-in Manager by their Manager name, or by
  their login while no name has been set.
- A Manager can correct their own Manager name from their account menu, next to
  "Change password".
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
  converted into one currency (settled by the human on 2026-10-08). Adding a rate
  feed is a hosted-service decision for another feature.
- **No forecast or expected figure.** A draft invoice's live total is not shown
  as "expected", "to bill" or "accrued". Only the statuses settled on
  2026-10-08 are counted.
- **No breakdown by Contract, Client or Agent, no chart, no trend against
  earlier months, no month picker.** Each money card is one figure per
  currency, for one month. A per-month list of final invoices is
  `invoice-history`.
- **No new links or cards.** The grid keeps its seven cards and the page keeps
  its Pending approvals card. A money card does not become a link.
- **No change to the Review Queue, Pending Requests, or their counts.** They are
  already real. Only their visual-suite fixtures are added.
- **No SuperAdmin entry of a Manager name, and no Manager creation.** Entering
  the name when a Manager is created is
  `tenant-administration`'s `create-a-tenant-with-its-first-manager`
  (`## Solution`, "Carried to `tenant-administration`").
- **No backfill and no seeded name.** The Manager the database setup creates
  (V2, and the `demo` profile) stays unnamed, and shows their login until they
  set a name (settled by the human on 2026-10-14).
- **No name on any other Manager page's chip.** The other Manager pages keep
  their plain "Manager" chip (see `
- **The Manager's name shows on the Dashboard's chip only; other Manager pages keep
  plain "Manager".** Reason: answered by the human on 2026-10-14, as recommended.
  It matches the Agent console, whose Dashboard alone shows the Agent's name;
  naming every page is a small follow-up if wanted.

## Open questions

None.

## Acceptance walkthrough

Run against an isolated stack with the backend on the `demo` profile (its own
fresh database, seeded by `DemoDataLoader`), never the user's compose stack.
Logins are in the root `README.md`. The figures follow the money definitions
the human settled on 2026-10-08.

1. [agent] Sign in as the Manager (`manager@example.com`), whose name the database setup leaves unset, and open `/manager`. Show the chip "manager@example.com · Manager", and no occurrence of "Priya Ashford", "41,280", "22,940" or "Demo data" on the page. Show that the grid appears with no skeleton first. (stories: 20, 24, 28)
2. [agent] Open the account menu and show its items "Change name", "Change password", "Log out", in that order, reachable by arrow keys. Choose "Change name" and show an empty "Name" field. Submit it blank and show "Enter your name." with no request sent; submit 101 characters and show "Your name can be at most 100 characters." with the text kept. Enter "  Yazan Abuhassan  " and save. Show the dialog closes and the chip reads "Yazan Abuhassan · Manager" with no sign-in in between, and still after a reload. Call `GET /api/me` and show `name` "Yazan Abuhassan", trimmed, and `username` unchanged. Reopen "Change name" and show the field holding "Yazan Abuhassan". Log out and sign in again with the same email and password. (stories: 1, 29, 30, 31, 32)
3. [agent] As the Manager, open `/manager/invoices` and show "Change name" in its account menu. Sign in as Jordan and as Dana and show their menus hold only "Change password" and "Log out". Call `PUT /api/me/name` as Jordan and as Dana and show `403`, and with no token show `401`. (stories: 29, 33)
4. [agent] Through the API, list `/api/clients`, `/api/agents` and `/api/contracts` as the Manager. Show the three cards equal their lengths (2, 3 and 3 in the demo story) and the Clients caption reads "In this tenant". (stories: 2, 3, 4, 5, 23)
5. [agent] Call `GET /api/tenant-totals` as the Manager. Show `billingMonth` is this month and `billed` holds one USD entry equal to the total `GET /api/client-invoices/{id}` returns for Solstice's US Contract's invoice. Show the Solstice UK and Harbor Line drafts are not counted. Show the "Billed this month" card shows that amount with the meta "Sent or approved · <this month>". (stories: 6, 7, 10, 11, 23)
6. [agent] Show "Payout this month" reads a plain `0`, with no currency symbol, and "Approved or paid · <this month>", although Jordan's Agent Invoice is sent. As the Manager, approve it. Reload and show "Payout" equal to that invoice's own total in USD. Mark it paid and show the figure unchanged. (stories: 8, 9, 10, 15, 16)
7. [agent] As Priya, send Solstice UK's Client Invoice. Reload and show "Billed" with two lines, GBP then USD, each equal to its invoices' totals, and never one summed figure. (stories: 13, 14, 16)
8. [agent] As the Manager, send that UK invoice back. Show "Billed" loses its GBP line. As Priya, resend it, and show the GBP line returns. (stories: 7, 16)
9. [agent] As Jordan, log a Fee on Solstice's US Contract. Reload and show "Billed" unchanged. Override the new-advance line on Priya's sent Agent Invoice, approve it, and show "Payout" gains a GBP line equal to that invoice's overridden total. (stories: 11, 12, 13)
10. [agent] Count `client_invoices` and `agent_invoices` rows, load `/manager` three times, and show the counts unchanged. (stories: 21)
11. [agent] Call `GET /api/tenant-totals` as Jordan and as Dana and show `403`, and with no token show `401`. Only one Tenant is seeded, so run the backend tests that seed `OtherTenantFixture` invoices and Managers and show them green, with those invoices never counted and those Managers' names never touched. (stories: 17, 22, 33)
12. [agent] Against the stub backend: with the degraded Manager session, show "Billed" and "Payout" reading "—" with "Couldn't load this month's totals", Agents reading "—" with "Couldn't load your Agents", the chip reading "Manager", and every other card still showing. With the empty session, show both money cards reading a plain `0` with no currency symbol, and the chip reading the stub's username followed by "· Manager". (stories: 15, 18, 19, 28)
13. [agent] Show `frontend/lib/demo/` gone, no `@/lib/demo`, `DemoNote`, `demoData` or `useSimulatedLoad` anywhere in `frontend/`, and no `text-[12px]` / `text-[13px]` in the page, `ManagerDashboardStats` or `ChangeNameDialog`. Run the full visual suite and show that only `manager-*`, `manager-menu-open-*` and `change-password-dialog-open-*` changed, each deleted first, and `change-name-dialog-open-*` was added, with no masks on the dashboard. Show the one new migration adds a nullable `users.name` and writes no data. Then run `docs/agents/sdlc.json`'s `verify` green. (stories: 24, 25, 26)
14. [human] Read the copy this feature adds: "In this tenant", the plain `0`, "Sent or approved · <month>", "Approved or paid · <month>", the "Couldn't load…" lines, and the "Change name" item and dialog with its messages ("Enter your name.", "Your name can be at most 100 characters.", "Couldn't change your name. Try again."). Confirm each reads right to a Manager. (stories: 5, 10, 15, 18, 29, 31)
15. [human] Sign in as the Manager against the demo profile. Confirm that each money figure is one you can rebuild from the invoices on the Invoices pages and their detail pages, that a two-currency card reads clearly at desktop and phone widths, and that a long name reads acceptably in the chip at phone width. (stories: 1, 13, 14, 23)
16. [agent] Once `invoice-adjustment` has landed, if it lands later, record a credit on an approved invoice and send the next month's invoice. Confirm the dashboard counts the credit only in the month it landed. Until then, confirm the spec's adjustment rule matches that feature's spec. (stories: 27)

## Execution order

Six tickets. The prefactor and the backend totals read do not depend on each
other or on the page. The four page slices run in a line, because all four edit
the same page, the same stub and the same goldens.

1. `delete-demo-status-maps` (enabler): delete the seven demo-keyed maps in `lib/status.ts` and their import of `lib/demo/types.ts`, rewording the comments that name them. No golden moves. Modules: `lib/status`. No dependency. (stories: none; frees `lib/demo/types.ts` for ticket 6)
2. `tenant-totals-read`: `GET /api/tenant-totals`, with the two grouped repository queries, the shared Agent Invoice total expression (adopted by the Review Queue query), the service, the DTO, the Manager-only guard and the integration tests. Modules: backend `web`, `repository`, `dto`, security. No dependency. (stories: 6, 7, 8, 9, 11, 12, 13, 14, 17, 21, 22, 27)
3. `manager-dashboard-identity-and-counts`: the chip from `/api/me` (`name` when present, else `username`, else plain "Manager"); the Clients, Agents and Contracts counts from the list routes with their unavailable states and the "In this tenant" caption; `ManagerDashboardStats` off `useSimulatedLoad` with its component tests; the stub's Manager fixtures (`/api/me`, Review Queue with relative dates, lists) and the degraded session; `manager-*`, `manager-menu-open-*` and `change-password-dialog-open-*` moved onto `visual-manager-session`, masks dropped, recaptured. Modules: Manager dashboard page, `components/manager`, visual suite. No dependency. (stories: 2, 3, 4, 5, 18, 19, 20, 26, 28)
4. `manager-changes-own-name`: the migration adding `users.name`, `User.name`, the `ManagerName` rule, `name` on `GET /api/me`, `PUT /api/me/name` with its Manager-only matcher and coded `400`s and `ManagerNameApiTest`; the "Change name" menu item, `ChangeNameDialog`, `viewerCanChangeName` through `SurfacePage`/`TopBar`/`ViewerActions` on every Manager page, their component tests, the e2e name step, `manager-menu-open-*` recaptured and `change-name-dialog-open-*` added. Modules: backend `domain`, `web`, `dto`, security, migrations; app-shell, Manager pages, visual suite, e2e suite. Depends on `manager-dashboard-identity-and-counts`. (stories: 1, 29, 30, 31, 32, 33)
5. `manager-dashboard-money`: "Billed this month" and "Payout this month" from the new read, with per-currency lines, the plain `0`, the month meta and the unavailable state; component tests; the stub's totals fixture and empty session (no `name` on its `/api/me`); the money steps of `manager-dashboard.spec.ts`; goldens recaptured. Modules: Manager dashboard page, `components/manager`, visual suite, e2e suite. Depends on `tenant-totals-read` and `manager-changes-own-name`. (stories: 6, 8, 10, 13, 14, 15, 16, 18, 23)
6. `delete-frontend-demo-fixtures`: delete `frontend/lib/demo/`, `DemoNote`, `SurfacePage`'s `demoData`, `useSimulatedLoad` and an unused `StatCardSkeleton`; rewrite `demo-footer.spec.ts` as a no-footer assertion; recapture only if a golden moves. Modules: app-shell, `lib`, `components/ui`, visual suite. Depends on `delete-demo-status-maps` and `manager-dashboard-money`. (stories: 24, 25)
