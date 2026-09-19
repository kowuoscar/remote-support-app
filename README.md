# Remote Support Platform

A platform for a business that hires local support Agents in different countries to provide
in-person mobile-testing support to a Client's Testers. See `CONTEXT.md` for the domain
vocabulary and `docs/features/` for the specs and tickets this was built from.

## Running it locally, with the demo story

```
docker compose up --build
```

opens the full stack at http://localhost:3000 (frontend) / http://localhost:8080 (backend API),
backed by its own Postgres (`postgres-demo`). On first startup the backend builds a small,
hand-written demo story — a company with two Agents in two Countries, two Clients, several
Testers, a Fleet, and a month of Requests, Fees and invoices — so every screen has something to
show without creating data first. A restart leaves the story alone (it only builds once).

This demo stack is separate from the plain Postgres (`postgres`) the frontend's e2e suite runs
against (`scripts/run-backend-for-e2e.sh`) — the two never share a database, so running one never
disturbs the other.

### Logins

All passwords below are demo-only, never used anywhere real.

| Role | Login | Password | What to look at |
|---|---|---|---|
| Manager | `manager@example.com` | `ChangeMe123!` | Dashboard (stat tiles), Clients/Contracts, Agents (Jordan Ellis has a login and standing-amount history; **Marcus Webb** has none yet — try "Create login" on his page), Carriers (US and UK catalogs), Stock (both Agents' kept units), Requests (a Provision SIM at Pending Approval, and a Return on Harbor Line's Contract still awaiting Dispositions), Review Queue (a sent Client Invoice and a sent Agent Invoice waiting for approval) |
| Agent — Jordan Ellis (United States) | `agent@example.com` | `AgentDemo123!` | Fleet and Requests on both of Jordan's Contracts (Solstice Retail Group's US Contract, Harbor Line Logistics) — a Reboot, Topup with Fee, SIM Swap exchange and Other repair all completed this month, a cancelled Request, a Provision Smartphone **In Progress and ready to fulfil from Stock** (Jordan's Stock holds "Old Field Phone" from Harbor Line's Return); Stock (one Smartphone); Contracts' Client Invoices (one sent, one still draft); Agent Invoice (sent this month, two prior months paid, with a salary raise partway through the history) |
| Agent — Priya Shah (United Kingdom) | `priya.shah@example.com` | `PriyaDemo123!` | Fleet and Requests on Solstice Retail Group's UK Contract — a Replace Smartphone completed (old phone retired, its SIM carried over), a Replace SIM rejected with a reason, a Provision SIM In Progress, and a Return completed with **all four Dispositions** (posted to Client, posted to company, a Postpaid SIM cancelled this month, one SIM kept in Priya's own Stock); Stock (one SIM Card); Client Invoice (still draft this month, two prior months approved); Agent Invoice (still draft this month, two prior months paid, including a Rollout Advance) |
| Tester — Dana Whitfield (Solstice Retail Group, Primary Contact) | `dana.whitfield@solsticeretail.example` | `SolsticeDemo123!` | Client Portal: both of Solstice's Contracts (US and UK) — Fleet, every kind of Request (including the rejected Replace SIM's reason), invoices |
| Tester — Marco Diaz (Solstice Retail Group) | `marco.diaz@solsticeretail.example` | `SolsticeDemo123!` | Same Client Portal as Dana — every Tester at a Client sees all of that Client's data |
| Tester — Elena Fischer (Solstice Retail Group) | `elena.fischer@solsticeretail.example` | `SolsticeDemo123!` | Same Client Portal as Dana |
| Tester — Noah Kim (Harbor Line Logistics) | `noah.kim@harborline.example` | `HarborDemo123!` | Client Portal: Harbor Line's one Contract — a fresh Topup still waiting to be started, and a Return awaiting the Manager's Dispositions |
| Tester — Sofia Alvarez (Harbor Line Logistics) | `sofia.alvarez@harborline.example` | `HarborDemo123!` | Same Client Portal as Noah |
| Tester (unlinked, baseline) | `tester@example.com` | `TesterDemo123!` | No Client — useful for checking an empty/unauthorized state, not part of the story |

### Resetting the demo alone

The demo database is a separate Postgres service and volume from the e2e one, so you can wipe it
without touching anything else:

```
docker compose rm -sf backend postgres-demo
docker volume rm <project>_postgres_demo_data
docker compose up --build
```

`<project>` is your Compose project name — normally the directory this repo is checked out into
(e.g. `remote-support-app_postgres_demo_data`). Run `docker volume ls` if you're not sure of the
exact name. The backend rebuilds the story from scratch on the next `up`.

## Running the backend against a real database, without the demo

`scripts/run-backend-for-e2e.sh` starts the plain `postgres` Compose service and runs the backend
locally (`mvn spring-boot:run`, no Spring profile) against it — the same one seeded by Flyway's
own baseline (a Manager, an Agent, an unlinked Tester, the US Carrier catalog), with none of the
demo story. This is what the frontend's e2e suite (`frontend/tests/e2e`) uses.

## Tests

- Backend: `cd backend && mvn verify` (JDK 21 — `JAVA_HOME=/opt/homebrew/opt/openjdk@21` or
  equivalent; a newer JDK breaks Lombok).
- Frontend: `cd frontend && npm run lint && npx tsc --noEmit && npx vitest run`.
- E2E and visual suites: `frontend/tests/e2e` and `frontend/tests/visual` (Playwright) — run
  against an isolated backend/frontend/Postgres, never against the demo stack or your own running
  `docker compose` stack.
