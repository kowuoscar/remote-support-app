---
feature: remote-support-mvp
status: accepted
date: 2026-09-15
---

# Remote Support Platform MVP

## Problem

A company hires local Agents in different countries to give in-person mobile-testing support to its Clients: providing Smartphones and SIM Cards, and handling day-to-day requests (topups, reboots, SIM swaps, device provisioning, repairs). Today this relationship — who has which device, what was requested, what it costs, who owes what — has no shared system of record. At month end, an Agent has to reconstruct everything they fronted for every Client they support, plus their own salary claim, with nothing to prove or scan the numbers against. The company's Manager has no single place to review a Client's monthly bill or an Agent's payment claim before approving it, and the Client has no visibility into what they're being charged for until a bill arrives.

## Goals / Non-goals

**Goals**

- A Company Manager can onboard Clients and Agents, and stand up Contracts between them, each with its own Fleet of Smartphones and SIM Cards.
- Testers (a Client's own users) can submit support Requests against their Client's Contracts and track them to completion.
- Agents can fulfill Requests, log Fees traced to those Requests, and keep their Contracts' Fleets accurate (repairs, provisioning).
- Agents can close out a monthly Client Invoice per Contract (postpaid base amount + Fees + attached carrier invoices) for Manager review, visible read-only to the Client once sent.
- Agents can submit a monthly Agent Invoice (full fee reimbursement + salary + Rollout Advance) to the Manager for approval and status tracking through to paid.
- A Manager can set and change each Agent's standing salary and Rollout Advance, and override either on one specific invoice without changing the standing rate.
- Role-based access means every actor sees only their own Contracts, their own Client's data, or (for the Manager) everything in the tenant.

**Non-goals**

- SuperAdmin-specific screens or capabilities. The role is reserved in the auth model; its concrete scope is deferred to a later iteration.
- Running multiple tenants simultaneously. The schema is tenant-scoped throughout, but the MVP seeds and operates a single tenant.
- Native mobile apps. Responsive web only.
- In-app payment execution or a payment-processor integration. Invoice status is tracked manually; money movement happens outside the app.
- A Manager-/Agent-configurable topup catalog. Agents free-type fee amounts and descriptions this iteration; a per-country catalog is a phase-2 addition.
- A Client Admin role or per-Tester resource restriction. Every Tester at a Client sees all of that Client's Requests, Fleet and Invoices.
- Any external client-billing or collections integration beyond read-only in-app visibility of the Client Invoice.

## User stories

1. As a Company Manager, I want to create a Client record, so that I can start supporting a new customer.
2. As a Company Manager, I want to create an Agent record with their country and standing monthly salary, so that they can be assigned to Contracts.
3. As a Company Manager, I want to create a Contract between a Client and an Agent, so that a Fleet can be provisioned for that pairing.
4. As a Company Manager, I want to add Smartphones and SIM Cards (postpaid or prepaid) to a Contract's Fleet, so that Testers have resources to use.
5. As a Company Manager, I want to set and update an Agent's standing monthly salary, so that it auto-populates their future invoices, taking effect from the next invoice cycle.
6. As a Company Manager, I want to set and update an Agent's standing Rollout Advance amount, so that their monthly invoice reflects the agreed cash-flow support.
7. As a Company Manager, I want to review a submitted Client Invoice together with its attached carrier invoices, so that I can approve it as final for accounting.
8. As a Company Manager, I want to approve a Client Invoice, so that it's locked as the accounting record for that Contract's month.
9. As a Company Manager, I want to review a submitted Agent Invoice, so that I can approve it before payment.
10. As a Company Manager, I want to override the salary or Rollout Advance line on one specific Agent Invoice, so that I can apply a one-off adjustment without changing the Agent's standing rate.
11. As a Company Manager, I want to approve an Agent Invoice, so that it moves into a payable state.
12. As a Company Manager, I want to mark an Agent Invoice as paid once payment has happened outside the app, so that its status reflects reality.
13. As a Company Manager, I want to see every Client, Agent, Contract and Fleet in the tenant, so that I have full operational oversight.
14. As an Agent, I want to see all my Contracts across the Clients I support, so that I can navigate between them.
15. As an Agent, I want to filter my Requests and Fleet by Contract, so that I don't have to sift through unrelated Clients' data.
16. As an Agent, I want to see incoming Requests from Testers, so that I know what needs action.
17. As an Agent, I want to log a Request on a Tester's behalf, so that I can record support I performed proactively.
18. As an Agent, I want to move a Request through submitted → in-progress → completed (or cancelled), so that its status reflects the real-world work.
19. As an Agent, I want to log a Fee against a Request (topup, provisioning, repair), so that it appears on that Contract's Client Invoice.
20. As an Agent, when I log a Fee proactively (with no prior Request), I want the system to create the linking Request automatically, so that every Fee stays traceable.
21. As an Agent, I want to change a Smartphone or SIM Card's status (e.g. Active → In Repair → Active, or retire one and provision its replacement), so that the Fleet reflects reality.
22. As an Agent, I want to open a Contract's Client Invoice for the month and see the postpaid base amount and Fee lines at a glance, so that I can review it before sending.
23. As an Agent, I want to attach carrier invoice files to a Client Invoice, so that the Manager has supporting documentation for the postpaid charges.
24. As an Agent, I want to submit a Client Invoice, so that it becomes visible to the Client and moves into the Manager's review queue.
25. As an Agent, I want the system to total my Local Support Fees across every one of my Contracts for the month automatically, so that I don't calculate it by hand.
26. As an Agent, I want my standing salary and Rollout Advance to auto-populate on my monthly invoice, so that I only need to review before submitting.
27. As an Agent, I want to submit my Agent Invoice to the Manager, so that it can be approved and paid.
28. As an Agent, I want to see the status history of my Client Invoices and my own Agent Invoice, so that I know what's pending, approved or paid.
29. As a Tester, I want to log in with my own account, so that I can access my Client's data without sharing a login.
30. As a Tester, I want to submit a support Request (reboot, topup, SIM swap), so that my Agent can act on it.
31. As a Tester, I want to see every Request raised by anyone at my Client company, not just my own, so that I have full visibility into our support activity.
32. As a Tester, I want to see my Client's Fleet, loaded per Contract, so that I know which Smartphones and SIM Cards are provisioned without heavy filtering.
33. As a Tester, I want to see my Client's Contracts, so that I can switch between them (e.g. our France Contract vs. our US Contract).
34. As a Tester, I want to view a Client Invoice once the Agent has sent it, so that I understand what we're being billed for, scannable at a glance.
35. As a Tester, I want to generate a PDF of a Client Invoice on demand, so that I can share or archive it.
36. As a Tester, I want one of us to be designated the primary contact, visible to the Agent and Manager, so that they know who to reach for account-level questions.
37. As a SuperAdmin, I want my role reserved in the auth model now, so that platform-wide, cross-tenant capabilities can be added later without a rework.

## Solution

**Architecture.** A single monorepo with `/backend` (Spring Boot 3, Java 21, REST API, PostgreSQL 16, JWT-based auth via Spring Security) and `/frontend` (Next.js, App Router, consumes the API). The schema is tenant-scoped from the start — every core table carries a tenant reference — but the MVP seeds and runs a single tenant; SuperAdmin (cross-tenant) capabilities are reserved as a role with no dedicated screens this iteration.

**Core entities and relationships.**

- **Client**: the company whose Testers receive support. Has many Testers and many Contracts.
- **Tester**: an individual login belonging to a Client. All Testers of a Client see all of that Client's data (Requests, Fleet, Invoices) — no per-Tester restriction. One Tester may be flagged as the primary contact (a visible designation, not an extra permission).
- **Agent**: a person hired in one country (which fixes their currency). Has many Contracts, a standing monthly salary, and a standing Rollout Advance amount, both Manager-set.
- **Contract**: the relationship between exactly one Client and one Agent. A Client may hold several Contracts (typically one per country/Agent); an Agent may hold several Contracts (typically with different Clients in their own country). A Contract's currency is inherited from its Agent's country. Each Contract owns exactly one Fleet.
- **Fleet**: the Smartphones and SIM Cards provisioned under one Contract.
  - **Smartphone**: status `Active` → `In Repair` → `Active`, or `Retired` when replaced.
  - **SIM Card**: flavor `Postpaid` (carries a fixed monthly fee that rolls into the Contract's base amount) or `Prepaid` (no monthly fee). Status `Active` or `Retired`.
- **Request**: a support action — `Reboot`, `Topup`, `SIM Swap`, `Provision Smartphone`, `Provision SIM`, `Repair` — raised by a Tester or logged by an Agent on a Tester's behalf, against one Contract. Status `Submitted` → `In Progress` → `Completed`, with `Cancelled` as an exception path. Carries no monetary amount itself.
- **Fee**: a billable line item an Agent logs against a Contract, always linked to the Request that caused it. `Reboot` and a like-for-like `SIM Swap` never produce a Fee. `Topup`, `Provision Smartphone`, `Provision SIM` and `Repair` do. A Fee the Agent logs proactively (no pre-existing Request) auto-creates its linking Request so every Fee stays traceable.
- **Client Invoice**: one per Contract per month. Base amount = the sum of the monthly fee of every `Postpaid` SIM active in the Contract's Fleet at month-end; plus every Fee logged against that Contract that month; plus attached carrier-invoice files (opaque supporting documents, not modeled entities). Lifecycle: `draft` (the Agent is assembling it) → `sent` (submitted by the Agent — becomes read-only visible to the Client's Testers at this point) → `approved` (locked by the Manager). Rendered as PDF on demand from the stored structured data, never stored as a static file.
- **Agent Invoice**: one per Agent per month, sent to the Manager. Line items:
  - *Local Support Fees* — the sum, across every one of the Agent's Contracts that month, of (that Contract's Client Invoice base amount + that Contract's Fee total). The Agent fronts everything — base postpaid charges and additional fees alike — and this line is the full reimbursement.
  - *Salary* — the Agent's standing monthly salary, auto-populated; the Manager may override the value on this one invoice at approval time without changing the standing rate.
  - *Rollout Advance — repayment of previous month* — the negative of the amount advanced last month.
  - *Rollout Advance — new advance* — the Agent's current standing Rollout Advance amount. These two lines net to zero except in the invoice cycle right after the Manager has approved a change to the standing amount (a standing-amount change takes effect starting the *next* month's invoice, identically to how salary changes take effect).
  - Lifecycle: `draft` → `sent` → `approved` → `paid`, every transition set manually by the Manager. No in-app payment execution.

**Access control.**

- **Manager**: full CRUD on Clients, Agents, Contracts and Fleets within the tenant; sets Agent standing salary and Rollout Advance; approves both invoice types and advances Agent Invoice status through to `paid`.
- **Agent**: full CRUD on Requests, Fees and Fleet status within their own Contracts; authors and sends their Contracts' Client Invoices and their own Agent Invoice; cannot approve either.
- **Tester**: create/read Requests within their Client's Contracts; read-only on Fleet, Contracts, and any Client Invoice that is not in `draft`.
- **SuperAdmin**: reserved role, cross-tenant, no dedicated screens this iteration.

## Design direction

Committed world: **Stripe**-inspired (`~/workspace/frontend-resources/awesome-design-md/design-md/stripe/DESIGN.md`) — deep navy ink, electric-indigo primary, tabular figures for monetary values, tight-radius pill buttons, dashboard track in a dark app shell. Chosen for the fit between its financial-infrastructure register and an app whose core object is invoices and fee line items.

Surfaces, all **Operate** (the visitor completes a task, not just reads or is persuaded):

- Manager Console — Operate: configure Clients/Agents/Contracts/Fleets, review and approve invoices.
- Agent Console — Operate: log Requests/Fees, manage Fleet status, assemble and submit both invoice types.
- Client Portal — Operate: submit and track Requests, review Fleet and Invoices.

`impeccable new-work` composes the project's own world from this pinned reference during the design-system ticket; it is not copied wholesale.

## Constraints

- Backend: Java 21, Spring Boot 3, PostgreSQL 16, JWT auth via Spring Security.
- Frontend: Next.js (App Router), responsive web only — no native mobile app this iteration.
- Repository layout: single monorepo, `/backend` and `/frontend`.
- Every monetary field stores an explicit currency code; a Contract's currency is derived from its Agent's country. No global single-currency assumption is baked into the schema, even though the MVP happens to run each Contract in one currency.
- Schema is tenant-scoped throughout; the MVP seeds and runs exactly one tenant.
- No payment-processor or external accounting-system integration. Invoice statuses are set manually by the Manager.

## Testing decisions

Two seams, one per runtime — the fewest the architecture allows:

- **Backend — HTTP API seam.** Spring Boot integration tests (`MockMvc`/`RestAssured`) against real REST controllers, backed by a real PostgreSQL via Testcontainers. Covers all business logic: Contract/Fleet CRUD, Request→Fee traceability and the Reboot/like-for-like-swap exemption, Client Invoice and Agent Invoice generation math (including the Rollout Advance net-to-zero behavior and its one-month-delayed change), invoice status-transition rules, and role-based access (a Manager/Agent/Tester can only reach what they should). This is the primary seam; no isolated unit tests of individual services.
- **Frontend — browser seam.** Playwright, driven at the accessibility-tree level (`playwright-cli snapshot`), covering golden-path journeys per role: a Tester submits a Request → an Agent fulfills it and logs a Fee → an Agent sends a Client Invoice → a Client Tester sees it → a Manager approves an Agent Invoice. Integration coverage of frontend+backend together for the critical paths; it does not re-test business logic already covered by the API seam.

Visual regression (goldens per surface × theme × breakpoint) is established in the design-system ticket per `~/.claude/sdlc/frontend.md`, and required by the Definition of Done for any ticket labelled `frontend`.

## Open questions

None.

## Execution order

1. `backend-bootstrap` — Spring Boot API, tenant-scoped schema, JWT auth
2. `design-system` — Next.js app shell, Stripe-pinned `DESIGN.md`, surface briefs, goldens
3. `auth-login-flow` — real login reaching a role-appropriate shell
4. `manager-entity-setup` — Manager creates Clients, Testers, Agents, Contracts
5. `fleet-management` — Manager/Agent/Tester manage and view Fleet
6. `tester-request-submission` — Tester submits a Request; Agent sees it queued
7. `agent-request-fulfillment` — Agent progresses Requests and logs proactive ones
8. `fee-logging-and-provisioning` — Agent logs Fees traced to Requests; provisioning updates Fleet
9. `client-invoice-generation` — Agent builds a Contract's draft Client Invoice
10. `client-invoice-submission-and-visibility` — send, Client visibility, Manager approval
11. `agent-standing-amounts-and-invoice-generation` — standing salary/advance and Agent invoice assembly
12. `agent-invoice-submission-and-approval` — send, Manager override/approve/mark paid
