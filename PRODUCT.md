# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Next.js (App Router) frontend consuming a Spring Boot 3 / Java 21 REST API backed by PostgreSQL. Decided in the approved feature spec (`docs/features/remote-support-mvp/spec.md`), not delegated.

## Users

Three roles sharing one tenant:

- **Company Manager** — configures Clients, Agents, Contracts and Fleets; approves both invoice types; sets each Agent's standing salary and Rollout Advance.
- **Local Support Agent** — executes support Requests, logs Fees, keeps Fleet status accurate, assembles both monthly invoice types for their own Contracts.
- **Client Tester** — an individual login belonging to a Client company; submits support Requests and views their Client's Fleet, Contracts and Invoices.

A fourth role, SuperAdmin, exists in the auth model but has no dedicated screens this iteration.

## Product Purpose

Give a company that hires in-country local-support Agents a shared system of record for the physical resources (Smartphones, SIM Cards) it provides to Clients, the day-to-day support Requests Agents fulfill against those resources, and the two monthly invoicing cycles — Client billing and Agent reimbursement plus salary — that result from that work.

## Positioning

Ties resource assignment (Fleet), support activity (Requests), and billing (Fees, invoices) into one traceable chain: every billable Fee always resolves back to the Request that caused it, and every invoice line is reconstructable from logged activity rather than assembled from memory at month end.

## Operating Context

A monthly cycle. Through the month, Agents log Requests and Fees against their Contracts' Fleets. At month end, an Agent closes a Client Invoice per Contract (postpaid base amount + that month's Fees, with carrier invoice files attached) and their own Agent Invoice (Local Support Fees summed across all their Contracts, plus salary, plus the Rollout Advance adjustment). A Manager reviews and approves both, and tracks the Agent Invoice's status through to paid. Actual money movement — Client payment collection, Agent payout — happens outside the app.

## Capabilities and Constraints

- Multi-tenant schema (`tenant_id` in 22 of the 46 migrations); a single tenant is seeded and operated today, with a second expected — see the `tenant-scoped-sign-in` epic.
- Responsive web only — no native mobile app this iteration.
- No payment-processor integration; invoice status is set manually by the Manager.
- Every monetary field carries an explicit currency code; a Contract's currency is derived from its Agent's country.
- Topup fees are priced from a per-country Carrier catalog of Topup Options, with the Agent free to adjust the amount. (Superseded 2026-09-21: this line previously recorded the catalog as deferred; `carrier-catalog` shipped it.)

## Brand Commitments

None yet. No existing name or visual identity to preserve — this iteration establishes the first visual world.

## Evidence on Hand

No existing screens, sample data, or brand assets. This is an internal Operate tool with no marketing surfaces — no testimonials, pricing, or promotional claims apply.

## Product Principles

1. Every billable amount traces back to a logged Request — no invoice line without a linked event.
2. Each role sees exactly its own scope: a Manager sees the whole tenant, an Agent sees only their own Contracts, a Tester sees only their own Client's data.
3. Money is reviewed before it moves: both invoice types pass through an explicit Manager approval step; the app never executes payment itself.
4. The Fleet is the single source of truth for what a Client currently has — it drives both support and billing.

## Accessibility & Inclusion

Reasonable defaults for an internal operations tool: keyboard operability, sufficient contrast, visible focus states. No formal compliance standard is targeted this iteration.
