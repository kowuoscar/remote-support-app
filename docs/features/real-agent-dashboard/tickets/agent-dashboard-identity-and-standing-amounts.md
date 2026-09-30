---
id: agent-dashboard-identity-and-standing-amounts
title: Show the signed-in Agent their own name, country and standing amounts on the dashboard
status: ready-for-agent
depends_on: [agent-own-record-read]
labels: [frontend]
stories: [1, 2, 9, 10, 11, 16, 17, 19]
---

## Context

Second slice of `spec.md` (`## Execution order`, item 2). Consumes `GET /api/me/agent` from `agent-own-record-read` to make the Agent dashboard's header, viewer chip, currency and "Standing salary + advance" card real, and lays the shared pieces the later slices reuse: the null-on-failure JSON loader (spec `## Solution`, "Prefactoring"), `AgentDashboardStats` off `useSimulatedLoad` and off `@/lib/demo/types`, the not-linked and page-level failure states, and the `visual-agent-session` token with the extended stub.

Until `agent-dashboard-invoice-figures` and `agent-dashboard-requests` land, the Local Support Fees, Open Requests and My Invoice status cards and the Recent Requests card keep reading `frontend/lib/demo/agent.ts`; the invoice status is adapted to the real `AgentInvoiceStatusValue` so the component compiles without the demo type. Do not replace those figures here.

## Acceptance criteria

- [ ] The header reads `<name> · <country>` and the viewer chip reads `<name> · Agent` for the signed-in Agent; the header and viewer chip no longer show "Camille Duforet" or "France".
- [ ] The "Standing salary + advance" card shows the Agent's real salary and "+ <amount> Rollout Advance" in the Agent's own currency, zero when none was set.
- [ ] An unlinked login (`404` from the read) sees the not-linked `EmptyState` and no stat cards.
- [ ] When `GET /api/me/agent` fails any other way, the page shows only "Couldn't load your dashboard — reload the page to try again".
- [ ] The stat grid renders at once, marked `data-testid="dashboard-ready"`, with no simulated skeleton; nullable props render `—` with a "Couldn't load…" meta.
- [ ] The stub answers `visual-agent-session` with Jordan Ellis, United States, USD, a salary and a non-zero Rollout Advance; an unlinked token gets `404` and a failing-identity token gets `500` from `GET /api/me/agent`; the four `agent-*` goldens are deleted, then recaptured, unmasked.

## Tests

- **Component seam (Vitest + Testing Library, spec Testing decisions 2):** new `AgentDashboardStats` test in the shape of `components/manager/dashboard-stats.test.tsx`: values and metas from props; nullable props render `—` and "Couldn't load…"; status badge label per `AgentInvoiceStatusValue`; grid present immediately with `dashboard-ready`, no skeleton.
- **Stubbed visual seam, Playwright assertions (not goldens):** `unlinked-agent-shows-not-linked-message` loads `/agent` with the unlinked token and asserts the not-linked message and no stat cards; `failing-identity-shows-page-level-message` loads `/agent` with the failing-identity token and asserts the single "Couldn't load your dashboard" message and no stat cards.
- **Visual goldens (Testing decisions 4):** four `agent-*` goldens recaptured once, deleted first; no other golden moves.

## Regression

- At risk: the Agent console's other pages (viewer label stays `<username> · Agent`), the Manager and Client stat grids that still call `useSimulatedLoad`, every other visual golden.
- Existing tests expected to change: `frontend/tests/visual/surfaces.spec.ts` (the `agent` entry moves to `visual-agent-session`, and gains the two named assertions) and `frontend/tests/visual/stub-backend.mjs` (new Agent route and tokens), per the spec's Visual suite section. The four `agent-*` golden images are replaced under `frontend/tests/visual/__screenshots__`. Every pre-existing test keeps passing unmodified.
- `frontend/lib/demo/agent.ts` and `types.ts` stay in this ticket.

## Observability

A failed identity read, and any failure through the new null-on-failure loader, is logged server-side with a label naming the endpoint and the response status.
