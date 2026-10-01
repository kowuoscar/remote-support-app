# Delivery report — real-agent-dashboard

<!-- sdlc:template delivery 1 -->

Merged to `main` as `a6e73ec` (PR #19) on 2026-10-01. The merge gate answered
`ok: true` with no blocking finding, harness passed, `verify` passed. `verify`
was green on `main` after the merge too: 64 e2e passed.

## What was built

An Agent's home page no longer shows a made-up person. Every figure comes
from the database, and `frontend/lib/demo/agent.ts` is deleted.

- **Who the page is about (stories 1, 2, 9–11, 16, 17, 19, 21).** A new
  `GET /api/me/agent` returns the signed-in Agent's own name, country,
  currency and this month's standing salary and Rollout Advance. It takes no
  id: a Manager or Tester gets 404, and the Manager-only standing-amounts
  route is unchanged. The header reads "Jordan Ellis · United States", the
  chip "Jordan Ellis · Agent". A login not linked to an Agent sees the
  not-linked message. Any other failure goes to the dashboard's own
  `error.tsx`. Tickets `agent-own-record-read`,
  `agent-dashboard-identity-and-standing-amounts`.
- **Invoice figures (stories 3–6, 8, 20).** Local Support Fees and My Invoice
  status come from this month's Agent Invoice, read through the same
  get-or-create route My Invoice uses. The meta reads "Running total, all
  your Contracts" while Draft and "As sent on your invoice" once sent. Ticket
  `agent-dashboard-invoice-figures`.
- **Requests (stories 7, 12–15, 18, 22–24).** Open Requests counts Submitted
  and In Progress across the Agent's Contracts. Recent Requests lists the
  five most recently raised, each "raised <age>". A failed read shows "—"
  with "Couldn't load your Requests", never a quietly low count. Ticket
  `agent-dashboard-requests`.
- **Tests.** The visual stub now serves Agent data with dates relative to its
  own clock, so the `agent-*` goldens stop drifting a day per day; this pays
  the clock-drift debt. The new e2e spec is `tests/e2e/agent-dashboard.spec.ts`.

## Acceptance walkthrough

1. Header and chip show the signed-in Agent; no demo names anywhere — played — evidence: `evidence/step-1-agent-dashboard.yml`
2. Standing salary and Rollout Advance match `/api/me/agent` — played — evidence: `evidence/step-2-me-agent.txt`
3. Local Support Fees and status match this month's invoice — played — evidence: `evidence/step-3-invoice.txt`
4. A second Agent (Priya) sees only her own figures — played — evidence: `evidence/step-4-priya-dashboard.yml`
5. Open Requests counts Submitted + In Progress only — played — evidence: `evidence/step-5-requests-summary.txt`
6. Recent Requests: five newest, with the right rows; Open queue links — played — evidence: `evidence/step-6-recent-requests.yml`
7. A Tester's new Request appears on reload and raises the count — played — evidence: `evidence/step-7-after.yml`
8. Access: 404 / 401 / 403 as specified; a future salary change doesn't show yet — played — evidence: `evidence/step-8-access.txt`
9. The first visit creates the same Draft My Invoice opens; Review Queue unchanged — played — evidence: `evidence/step-9.txt`
10. Degraded and failed loads show "—"/"Couldn't load…", never 0 — played — evidence: `evidence/step-10-degraded.yml`
11. Unlinked login and an Agent with no Contracts — played — evidence: `evidence/step-11-unlinked.yml`
12. Demo file deleted; goldens recaptured unmasked; `verify` green — played — evidence: `evidence/step-12-verify.txt`
13. Confirm "recent" means recently raised, shown as "raised <age>" — yours
14. Read the new copy ("As sent on your invoice", "No Requests yet", the "Couldn't load…" lines) — yours
15. Sign in as Jordan on the demo profile; every figure is traceable, nothing belongs to anyone else — yours

The steps were played before the fix pass. That pass changed only layout,
error routing and test hygiene. The re-reviews confirmed it, and it was
re-verified (64 e2e passed, 45 visual).

## Decisions taken alone

All of these are in the spec's `## Decisions taken`. The ones you're most
likely to veto:

- "Recent" means recently raised (`createdAt`). Requests store no update
  time, and adding one needs a migration.
- Salary and Rollout Advance show the Agent's standing rates. They can differ
  from the invoice's lines after a Manager overrides the invoice.
- Opening the dashboard creates this month's draft invoice if none exists,
  as My Invoice already does.
- The new read gets its own controller, and `MeResponse` is not extended.

Taken during implementation and review **(after review)**:

- **The ticket cut was accepted after the critic's recheck failed on one
  line.** The line named a test that doesn't exist. I applied the critic's
  correction verbatim instead of escalating to you.
- **`agent-dashboard-invoice-figures` was re-merged after I added a missing
  `## Regression` line instead of rebuilding it.** The diff was 13 lines
  added, 0 removed.
- **I typed the design audit's dark-mode contrast failure (F11) as debt, not
  a blocker.** Its lines are unchanged by this branch, and the class is used
  in 4 places app-wide.
- **The fix pass moved the dashboard into a route group,
  `app/agent/(dashboard)/`, with its own `error.tsx`.** This satisfies
  Frontend rule 6. The URL is unchanged.
- **The fix pass dropped the name from the header (F17).** The spec reviewer
  caught it (F19, F20), and **you authorised one extra fix pass** on
  2026-10-01. The header was restored, and it now wraps on mobile through an
  opt-in `wrapSubtitle` prop that only this page sets.
- **The implementer removed the paid clock-drift entry from
  `docs/tech-debt.md`.** That is my file. I adopted the removal.

## Debt recorded

- `frontend/app/agent/(dashboard)/page.tsx` · Contrast below AA: the "Open queue" link in dark mode is 3.66:1, and the class is used app-wide (F11)
- `frontend/components/app-shell/surface-page.tsx` · A label that lies: the "Demo data — synthetic" footer still shows under real figures (F12)
- `frontend/components/agent/dashboard-stats.tsx` · Two money formats in one card (F18)
- `frontend/tests/e2e/helpers.ts` · Writes to the developer's database: the non-isolated e2e falls back to compose Postgres (F3)
- `backend/.../web` · Primitive Obsession: the current billing month is computed inline in about 7 controllers; `BillingMonth.current()` now exists (F9)

Removed as paid: the `frontend/lib/demo/agent.ts` clock-drift entry.

The standards reviewer also proposed amending two rules, Frontend 6
(`error.tsx`) and Frontend 8 (arbitrary sizes, which cites a
`tailwind.config` that doesn't exist). They are filed as `proposal` items in
`docs/inbox/`.

## How to undo

```
git revert -m 1 a6e73ec
```

This reverts code only. There is no migration. Drafts created by visiting
the dashboard are the same drafts My Invoice creates.

## What happens next

`real-client-dashboard` is next in `real-dashboards`, and it gets a spec
next. Two other features wait on your spec approval:
`send-a-client-invoice-back` and `manager-resets-a-password` (revised).

A veto of "recent = recently raised" would reopen it as a question on a
follow-up feature that adds an update time to Requests.
