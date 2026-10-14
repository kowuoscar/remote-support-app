# Delivery report — real-manager-dashboard

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

The Manager's home page stops showing demo figures. Merged as PR #26
(`cb4b7a8`), seven tickets.

- **Who is signed in (stories 1, 28–33).** A Manager now has an optional
  Manager name, stored on their login (`users.name`, V60, no data written).
  The Dashboard chip reads "Yazan Abuhassan · Manager", or the login while no
  name is set, or plain "Manager" if `/api/me` fails. Every Manager page's
  account menu has "Change name" above "Change password"; the dialog opens
  with the current name, refuses a blank name before sending and a name over
  100 characters from the server, and updates the chip without signing out.
  Agent and Tester menus are unchanged; other Manager pages keep plain
  "Manager". Tickets: `manager-name-api` (backend `GET /api/me` name,
  `PUT /api/me/name`, `ManagerNameService`), `manager-changes-own-name`
  (dialog, menu item, two route handlers).
- **Real counts (stories 2–5, 18–20, 26).** Clients, Agents and Contracts are
  the Tenant's real list lengths, with "In this tenant" under Clients; a failed
  list shows "—" with "Couldn't load your …". The grid renders at once, with no
  fake skeleton. Ticket: `manager-dashboard-identity-and-counts`.
- **Real money (stories 6–17, 21–23, 27).** A new Manager-only
  `GET /api/tenant-totals` sums this billing month's sent or approved Client
  Invoices ("Billed this month") and approved or paid Agent Invoices ("Payout
  this month") per currency, from the invoices' own totals. Each card shows one
  line per currency, never one sum; a plain `0` when nothing counts; "—" with
  "Couldn't load this month's totals" on failure. Tickets: `tenant-totals-read`,
  `manager-dashboard-money`.
- **Demo code gone (stories 24, 25).** `frontend/lib/demo/`, the "Demo data"
  footer, `useSimulatedLoad`, `StatCardSkeleton` and seven demo status maps are
  deleted. Tickets: `delete-demo-status-maps`, `delete-frontend-demo-fixtures`.

## Acceptance walkthrough

1. Sign in as the unnamed Manager; chip, no demo figures, no skeleton — played — evidence: `evidence/step-1.txt`
2. Account menu; Change name dialog, blank and too-long refusals, rename, reload — played — evidence: `evidence/step-2a.txt`
3. `/manager/invoices` chip and menu; Agent and Tester menus and 403s — played — evidence: `evidence/step-3a.txt`
4. Counts equal the list API lengths — played — evidence: `evidence/step-4.txt`
5. `GET /api/tenant-totals` billed equals the sent invoice's total — played — evidence: `evidence/step-5.txt`
6. Payout plain `0` while sent; grows on approve, unchanged once paid — played — evidence: `evidence/step-6.txt`
7. Two currencies, two lines — played — evidence: `evidence/step-7.txt`
8. Send back drops the GBP line, resend restores it — played — evidence: `evidence/step-8.txt`
9. A Fee after send leaves Billed unchanged; override counts in Payout — played — evidence: `evidence/step-9.txt`
10. Loading the page creates no invoice rows — played — evidence: `evidence/step-10.txt`
11. Agent/Tester 403, no token 401; other-Tenant backend tests green — played — evidence: `evidence/step-11a.txt`
12. Stub degraded and empty sessions — played — evidence: `evidence/step-12-degraded.txt`
13. Demo code, text sizes, migration, goldens, verify — played — evidence: `evidence/step-13a.txt`
14. Read the copy this feature adds — yours
15. Sign in as the Manager against the demo profile; rebuild the figures; two-currency card and long name at phone width — yours
16. Adjustment treatment matches `invoice-adjustment` (spec comparison; live form once that feature lands) — played — evidence: `evidence/step-16.txt`

## Decisions taken alone

Everything in the spec's `## Decisions taken`. The entries settled during this
delivery:

- **(during implementation)** `/api/tenant-totals` amounts serialise as JSON
  numbers, like every other amount in the API, not as strings.
- **(after review)** The "Change name" item is opted in by a context that the
  shell sets for the Manager console, not by a prop on every Manager page.
  You kept this on 2026-10-14; it looks the same.
- **(after review, by you)** The failed-card messages stay exactly as the spec
  wrote them; the fixer's links and "reload to retry" were removed.
- The ticket cut split the Manager-name work into a backend ticket and a
  screen ticket, after the independent critic found it too large.
- The fix pass recaptured goldens apart from the code that moved them; an
  empty commit (`e14310d`) names the code change behind each group. The tester
  login dialog goldens record main's earlier row-spacing change (`5c9b701`).

## Debt recorded

- `frontend/tests/e2e/manager-dashboard.spec.ts` · Indirect evidence · counts read from page subtitles, not the list API
- `frontend/components/app-shell/change-name-dialog.tsx` · autoFocus on mobile · mirrors ChangePasswordDialog
- `frontend/components/app-shell/change-name-dialog.tsx` · Invisible success · off-Dashboard save shows nothing
- `frontend/components/app-shell/change-name-dialog.tsx` · No client-side length cap · code-point vs UTF-16 counting
- `frontend/app/manager/page.tsx` · No loading state · seven reads, no Suspense
- `frontend/components/manager/dashboard-stats.tsx` · Two unavailable states · Pending cards' bare "—"
- `frontend/components/app-shell/viewer-menu.tsx` · Primitive Obsession · name and role parsed from the label
- `DESIGN.md` · Stale description · § Menu doesn't list Change name

## How to undo

```
git revert -m 1 cb4b7a8
```

Reverts code, not data. V60 only adds a nullable column; a name a Manager has
set stays in `users.name` after the revert, unused.

## What happens next

Implementation is paused at your request, so the loop does not start another
feature. Two rules came out of review as worth revisiting: Frontend 3 (is an
event-triggered client read allowed?) and the golden rule (what to do when a
fix pass recaptures goldens left stale by an earlier pushed commit). Vetoing a
decision above reopens it as an open question on a follow-up spec.
