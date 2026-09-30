---
id: agent-dashboard-requests
title: Show the Agent's open Request count and five most recent Requests from their Contracts
status: ready-for-agent
depends_on: [agent-dashboard-invoice-figures]
labels: [frontend]
stories: [7, 12, 13, 14, 15, 18, 22, 23, 24]
---

## Context

Third slice of `spec.md` (`## Execution order`, item 3). Open Requests and Recent Requests come from `GET /api/contracts` and each Contract's `GET /api/contracts/{contractId}/requests`, not `backendFetchList`, so a failure is never a quiet low count (spec `## Solution`, "Frontend: the page"). It finishes the page: `frontend/lib/demo/agent.ts` is deleted and the clock debt is paid by stub fixtures dated relative to the stub's own time. It depends on `agent-dashboard-invoice-figures`: `page.tsx` imports `currentMonthLabel`, `myAgentInvoices` and `runningLocalSupportFees` from `lib/demo/agent.ts` until that ticket removes them, so the deletion cannot compile earlier. The Agent id, loader and nullable props come from the identity ticket, already merged beneath it.

Decision on ordering by `createdAt` and "raised <age>" is the spec's (`## Decisions taken`).

## Acceptance criteria

- [ ] Open Requests shows the count of Submitted and In Progress Requests across all the Agent's own Contracts; Pending Approval is not counted.
- [ ] Recent Requests lists the five newest Requests by `createdAt`, newest first, across all Contracts, each row showing Request type, "Client — country" Contract label, "Raised by <username> · raised <age>" in a `<time dateTime>` element, and a status badge.
- [ ] The card's subtitle reads "Across all your Contracts" and its "Open queue" link goes to `/agent/requests`.
- [ ] An Agent with no Requests sees "No Requests yet" with the link kept and Open Requests `0`.
- [ ] If the Contract list or any one Contract's Requests fails, Open Requests shows `—` with "Couldn't load your Requests" and Recent Requests shows an unavailable state; nothing shows a dropped-Contract count.
- [ ] A Request a Tester submits appears on reload as the newest row, "raised today", and raises Open Requests by one, with no skeleton or delay.
- [ ] `frontend/lib/demo/agent.ts` is deleted, `frontend/lib/demo/types.ts` remains, and neither the dashboard page nor `AgentDashboardStats` imports `@/lib/demo`; the page reads the time once for every age.

## Tests

- **E2E (spec Testing decisions 3):** extend `frontend/tests/e2e/agent-dashboard.spec.ts`, created by `agent-dashboard-invoice-figures` (whose cases `dashboard-shows-invoice-status-and-amounts`, `dashboard-draft-is-the-one-my-invoice-opens` and `dashboard-visit-leaves-review-queue-unchanged` remain and must still pass). New cases: `dashboard-lists-submitted-request-first-and-counts-it-open` (Manager creates a Contract for the seeded Agent, a Tester submits a Request, the Agent's dashboard names Jordan Ellis, counts it open, lists it first as "raised today"); `agent-without-contracts-sees-no-requests-yet`.
- **Component seam (Vitest):** add cases to the `AgentDashboardStats` test file for Open Requests `—` and "Couldn't load your Requests".
- **Visual (Testing decisions 4):** stub Request fixtures cover Submitted, In Progress, Completed and Pending Approval across two Contracts with `createdAt` computed from the stub's current time; the degraded token's one Contract Requests route answers `500`; the four `agent-*` goldens are deleted and recaptured, unmasked, stable on re-run, and no other golden moves.
- **Backend:** N/A, no backend change.

## Regression

- At risk: the Agent's Requests queue (shares the Contract label), the Manager and Client dashboards (still use `@/lib/demo` and `useSimulatedLoad`), `lib/status.ts` (still imports the demo types), every other visual golden.
- Existing tests expected to change: `frontend/tests/visual/stub-backend.mjs` gains the Contract and Request routes; the four `agent-*` goldens are replaced; `frontend/tests/e2e/agent-dashboard.spec.ts` (created by the previous ticket) gains cases and the `AgentDashboardStats` component test file gains cases (every pre-existing case in both passes unmodified). The `docs/tech-debt.md` entry for `frontend/lib/demo/agent.ts` is removed as paid.

## Observability

A failed Contract or Requests load is logged server-side with a label naming the endpoint and the response status.
