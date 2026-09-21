---
feature: demo-data-story
status: accepted
date: 2026-09-19
---

# Demo data story

> Requested by the user at the end of the carrier-catalog, request-types-and-flow
> and returns-and-agent-stock work: "make sure the demo data is clean and tells
> a story, instead of sample data without links; clean and rebuild the whole
> seeded data to cover each feature". The approach below is an orchestrator
> call, marked where it matters.

## Problem

Opening the app locally shows data nobody would recognise as a business. Each
ticket added a few rows in its own migration — a Demo Client here, a Demo
Agent there, one Pending Approval Request, one SIM installed in one phone — so
the seed is a pile of unrelated fixtures. Most features have no demo data at
all: no Return, nothing in Stock, no rejected Request, no invoice history.

Two structural problems sit under that:

- **Seed lives in Flyway migrations**, which run once. Anything dated "this
  month" is stale the month after, which is why current-month invoices have to
  come from a separate script (`scripts/seed-demo-invoice-review.sh`).
- **The integration tests run the same migrations.** Demo rows leak into what
  tests see, and the committed e2e flow runs against docker-compose's own
  database, so demo data there breaks e2e specs (the script's own header warns
  about this).

## Goals / Non-goals

**Goals**

- The Flyway seed shrinks to the **test baseline**: only what tests share.
- A **demo story** covering every feature is built on demand for local use,
  from a single place, with links between everything, anchored to the current
  month when it runs.
- The demo story and the e2e suite never share a database.
- Every login in the story is documented with what to look at.

**Non-goals**

- Production data or a production seeding mechanism.
- Deleting data a developer created by hand in their local database: the
  documented path to a clean story is resetting the demo database.
- Changing any feature behaviour.
- Randomised or generated volume; the story is small and hand-written.

## User stories

1. As a developer, I want `docker compose up --build` to show a coherent business with history, so that I can demo and test every feature without creating data first.
2. As a developer, I want every role's login documented with what that role should look at, so that I know where each feature lives.
3. As a Manager in the demo, I want Clients, Agents in two Countries, Contracts, and an invoice history of approved and paid months, so that the console looks like a running business.
4. As a Manager in the demo, I want invoices waiting in the Review Queue and Requests waiting in Pending Requests, including a Return awaiting Dispositions, so that I can try every decision.
5. As a Manager in the demo, I want Agent Stock holding units, so that the Stock page has content.
6. As an Agent in the demo, I want a Carrier catalog, Fleets with both Owners, SIM Cards installed in Smartphones (one phone holding two), and Requests at every status, so that my screens have content.
7. As an Agent in the demo, I want open Requests I can complete — including one I can fulfil from my Stock and a Return needing cancellation dates — so that I can try each completion.
8. As a Tester in the demo, I want my Client's Fleet, Requests with details and a rejection reason, and invoices, so that the Client Portal has content.
9. As a developer, I want the Flyway seed to hold only the test baseline, so that tests don't depend on demo rows.
10. As a developer, I want the e2e suite and the demo on separate databases, so that running one never breaks the other.
11. As a developer, I want to reset the demo with one documented command, so that I can always get back to the clean story.

## Solution

**Test baseline (Flyway).** One new migration deletes the ad hoc demo rows
added by earlier seed migrations, in dependency order and by their fixed ids
only. What stays is what tests reference: the tenant, the Manager login, the
Agent login and its Agent (Jordan Ellis, United States), the Tester login, and
the United States Carrier catalog with its Topup Options and Postpaid Plans.
Tests that used the removed rows (`ContractApiTest`, `TopupFeeFromOptionApiTest`
and the `DEMO_TESTER_*` / `SEEDED_DEMO_*` constants) create their own fixtures
instead. Applied migrations are never edited.

**Demo story (loader).** A Spring component active only under a `demo`
profile builds the story at startup when it isn't there yet, marked by a fixed
id so a restart doesn't duplicate it. Orchestrator call: a loader rather than
more migrations, because it runs against a clock (current month) and never
runs in tests. Current-month activity goes through the application's own
services, so approvals, completions, snapshots and Fleet effects are exactly
what the app produces. Past months — history that services can only write "now"
— are written directly, consistently with what those services would have
stored (sent/approved snapshots, standing-amount history).

**Separate databases.** docker-compose gets a second Postgres service for the
demo, with its own volume; the compose backend runs with the `demo` profile
against it. The existing `postgres` service keeps `remote_support` for the
committed e2e flow and local backend runs. Orchestrator call: a separate
service rather than a second database in the same server, because Postgres
only runs init scripts on an empty volume — a separate service needs no reset
of an existing local volume. Resetting the demo means removing the demo
volume only, documented.

**The story.** A company providing in-person mobile-testing support:

- **Agents.** Jordan Ellis in the United States (existing login) and Priya
  Shah in the United Kingdom, both with logins, salaries and standing-amount
  history; Priya with a Rollout Advance. One legacy Agent without a login, so
  the Manager's "Create login" flow has a target.
- **Carrier catalogs.** United States (existing) and United Kingdom, each with
  Topup Options and Postpaid Plans, and one archived entry.
- **Clients and Testers.** Two Clients, one with Contracts in both Countries;
  several Testers, one flagged Primary Contact; each role's Tester login
  documented.
- **Fleets.** Smartphones of both Owners, serials missing on one, one In
  Repair; SIM Cards prepaid and postpaid on Plans, installed in phones (one
  phone with two, some uninstalled); one Postpaid SIM cancelled this month
  through a completed Return.
- **Requests over the month**, each linked to real units: a completed Reboot,
  a completed Topup with its Fee from an Option, a completed SIM Swap exchange,
  a Provision Smartphone at Pending Approval, a Provision SIM approved and In
  Progress, a Replace Smartphone completed (old phone retired, SIM carried
  over), a Replace SIM rejected with a reason, an Other repair completed with a
  Fee, a cancelled Request with a reason, a Return completed with each
  Disposition, a Return at Pending Approval awaiting Dispositions, and a
  Provision Smartphone approved and ready to be fulfilled from Stock.
- **Stock.** Units kept by each Agent from completed Returns.
- **Invoices.** Two past months: Client Invoices approved, Agent Invoices paid.
  This month: one Client Invoice sent (in the Review Queue), the others draft;
  one Agent Invoice sent, one draft.

**Retired.** `scripts/seed-demo-invoice-review.sh`, which the story replaces.

**Documentation.** The docker-compose file header and the README (create one
if missing) list every login, its password, and what to look at, plus the reset
command.

## Design direction

N/A — no user interface.

## Constraints

- The loader never runs in integration tests or the committed e2e flow.
- The loader writes nothing when the story is already present.
- The baseline migration deletes the rows identified by the fixed ids earlier
  seed migrations used, plus every row that references them — anything a
  developer created on the demo Client, its Contracts, Agents, Testers or
  Fleet — so it succeeds on a local database that was used by hand. It never
  deletes by pattern or by table, and never touches a row unconnected to the
  demo rows.
- Every constraint of the three earlier features holds for demo data: frozen
  invoice totals match their snapshots, a unit is in exactly one place, a
  Smartphone holds at most two SIM Cards.
- Passwords in the story are demo-only and documented.

## Testing decisions

- Existing suites stay green on the trimmed baseline: backend `mvn verify`,
  frontend lint, typecheck and Vitest, the full e2e and visual suites.
- One backend integration test boots the `demo` profile against a fresh
  database and asserts the story's shape: every Request type and status
  present, a unit in Stock, a cancelled Postpaid SIM, a sent Client Invoice
  and Agent Invoice, frozen totals equal to their snapshots. A second start
  adds nothing.
- A manual walkthrough of each documented login in a real browser against the
  demo stack.

## Open questions

None

## Execution order

1. `trim-seed-to-test-baseline` — Flyway seed reduced to what tests share; tests own their fixtures.
2. `demo-story-loader` — the `demo` profile loader, its own database, documentation, script retired. Blocked by 1.
