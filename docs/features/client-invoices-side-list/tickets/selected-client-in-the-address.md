---
id: selected-client-in-the-address
title: The selected Client is kept in the page address
status: done
depends_on: [contract-side-list-on-desktop]
labels: [frontend, invoicing]
stories: [10, 11, 12, 20]
---

## Context

`spec.md` `### Selection` (the address), `### The page` (`searchParams`) and
`### The single invoice page`. Needs the side list and the selection state from
`contract-side-list-on-desktop`. Constraint: the parameter is written with
`history.replaceState` only, never `router.push` or `router.replace`
(`## Constraints`, The address). Also pays the by-id page's silent-read debt.
Satisfies the frontend DoD in `docs/agents/frontend.md`.

## Acceptance criteria

- [ ] Selecting a Client from the side list or from the Contract Switcher writes `?contract=<id>` to the address, adds no history entry (Back leaves the page), and makes no request; a reload, or opening that address, reopens the same Client with its row `aria-current`.
- [ ] A `contract` value naming none of the Agent's Contracts (another Agent's, or `nonsense`) opens the first row with no error, redirect or log.
- [ ] On arrival the selected row is scrolled into view inside the column: with 14 Contracts, `?contract=<the last row's id>` shows that row current and within the column's viewport.
- [ ] On the single invoice's page, the "Client Invoices" breadcrumb links to `/agent/client-invoices?contract=<contractId>` and returns with that Client selected; the not-found links stay plain `/agent/client-invoices`.
- [ ] When the by-id page's Contracts read fails, the failure is logged and the title still reads "Client Invoice".

## Tests

- **Component (`client-invoices-view.test.tsx`):** valid `initialContractId` makes that row current; an unknown id makes the first current; a row click and a switcher choice write `?contract=<id>` to `window.location` and make no fetch; `AgentClientInvoicePageView`'s breadcrumb links to `?contract=<contractId>`, and rendered with a `null` `contractLabel` it still shows the title "Client Invoice" (the degraded-title fallback). That the failed read is logged is proved by the existing `frontend/lib/api/backend.test.tsx` cases for `backendFetchJsonOrNull` logging a failure, which the by-id page now uses; no new test is needed for the log line.
- **End to end (`client-invoices-side-list.spec.ts`):** `?contract=` in the address and the same Client after a reload; an unknown `?contract=` opens the first row; with 14 Contracts, `?contract=<last id>` has that row within the column's viewport; Open then the breadcrumb returns with that Client selected; Back leaves the page; at 390px a switcher choice is kept in the address after a reload.

## Regression

At risk: the router refresh after a mutation, which must keep the selection (the refresh re-renders the current address), and the by-id page's breadcrumb and title. Protecting tests, all unmodified: `names the Client and country in the breadcrumb` and `not found: honest copy and the breadcrumb back` (`client-invoices-view.test.tsx`, lines 394 and 406), and the existing send and edit cases of that file and of `send-client-invoice-control.test.tsx` and `edit-client-invoice-line-control.test.tsx` that call `router.refresh`.

Existing tests expected to change: none modified. `client-invoices-view.test.tsx` and `client-invoices-side-list.spec.ts` gain cases; every pre-existing case keeps passing unmodified.

## Observability

The by-id page's Contracts read goes through `backendFetchJsonOrNull`, so a failed read is logged as that helper already does; no new event type.
