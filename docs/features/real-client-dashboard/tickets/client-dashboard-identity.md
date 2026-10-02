---
id: client-dashboard-identity
title: Name the signed-in Tester and their own Client on the Client dashboard
status: done
depends_on: [surface-demo-note-opt-in, type-scale-tokens, tester-own-client-reads]
labels: [frontend]
stories: [1, 2, 4, 16, 17, 24]
---

## Context

Fourth slice of `spec.md` (`## Execution order`, split: the spec's item 4 is cut in two so each fits one context). The dashboard moves into the route group `frontend/app/client/(dashboard)/` with its own `error.tsx`, the shape `frontend/app/agent/(dashboard)/` settled; the URL stays `/client`. The page becomes an async Server Component that reads `GET /api/me/client` and `GET /api/me` (spec `## Solution`, "Frontend: the page", Identity). A `404` renders the not-linked `EmptyState`; any other failure throws to `error.tsx`. The header (subtitle) is the Client's `name`, wrapped on mobile through `SurfacePage`'s `wrapSubtitle`; the chip is `<username> · Tester`.

Stat figures and the invoice card keep reading `@/lib/demo/client` until the next two tickets replace them; only `currentClient` is removed here. Lines this ticket writes use `text-label-sm` / `text-label` from `type-scale-tokens`, never `text-[12px]` / `text-[13px]`. It depends on `tester-own-client-reads` because the route it calls is created there. The stub gains the Tester callers named in spec `## Solution`, "Visual suite", for identity only; Contracts, Fleet and Requests come with the next ticket.

## Acceptance criteria

- [ ] Signed in as the stub's `visual-tester-session`, `/client` shows "Solstice Retail Group" in the header and "dana.whitfield@solsticeretail.example · Tester" in the viewer chip, and neither "Aurora Retail Group" nor "Nadia Okafor" appears in the header or chip.
- [ ] At a 390 px viewport the header still contains "Solstice Retail Group" in full.
- [ ] With `visual-tester-unlinked-session` (`/api/me/client` answers `404`), `/client` shows "Your login isn't linked to a Client yet" and "Ask your Manager to link your login to your Client before you can see your dashboard.", and no stat cards.
- [ ] With `visual-tester-failing-identity-session` (`/api/me/client` answers `500`), `/client` shows the single message "Couldn't load your dashboard — reload the page to try again" under a header that names no Client, and no stat cards.
- [ ] The four `client-*` goldens are deleted and recaptured from `visual-tester-session`, unmasked and stable on re-run, and their diff against `main` shows only the header and chip changed.

## Tests

- **Visual suite, non-golden (spec Testing decisions 4):** add a describe "the Client dashboard's identity states" to `frontend/tests/visual/surfaces.spec.ts`, beside the Agent's, with cases `tester-header-and-chip-name-their-own-client-and-login`, `tester-header-keeps-the-full-client-name-at-390px`, `unlinked-tester-shows-not-linked-message` and `failing-identity-shows-the-single-dashboard-error`.
- **Goldens:** `client-{desktop,mobile}-{light,dark}.png`, deleted first, then recaptured.
- **Component seam:** N/A here; the component tests for `ClientDashboardStats` arrive in `client-dashboard-fleet-and-requests`.

## Regression

- At risk: the `/client` URL and the Client Portal layout, `/client/fleet`, `/client/requests`, `/client/invoices` (same layout, same chip), the Agent dashboard's identity states, and the Manager dashboard.
- Existing tests expected to change: `frontend/tests/visual/stub-backend.mjs` gains the three Tester sessions and `GET /api/me/client` (existing Agent and Manager tokens answer as before); `frontend/tests/visual/surfaces.spec.ts` points the `client` surface at `visual-tester-session` and gains the describe above (every pre-existing case passes unmodified); the four `client-*` goldens are replaced. The Agent's `unlinked-agent-shows-not-linked-message` and its failing-identity case guard the pattern. `frontend/tests/e2e/change-password.spec.ts` (opens `viewer-menu-trigger` on `/client` as a real Tester) and the Tester-reaches-`/client` case in `frontend/tests/e2e/login.spec.ts` guard the `/client` chip and landing, both unmodified.

## Observability

A failed identity read is logged server-side with a label naming the endpoint and the response status, then thrown.
