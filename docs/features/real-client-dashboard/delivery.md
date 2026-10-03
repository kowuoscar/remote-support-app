# Delivery report — real-client-dashboard

<!-- sdlc:template delivery 1 -->

Merged to `main` as `16b7823` (PR #21) on 2026-10-02. The merge gate answered
`ok: true`: no blocking finding, harness passed, `verify` passed.

## What was built

A Tester's home page (`/client`) now shows their own Client's real figures
instead of demo data. `frontend/lib/demo/client.ts` is deleted.

- **Who you are** (`client-dashboard-identity`, `tester-own-client-reads`).
  The header names the Tester's Client ("Solstice Retail Group"). The viewer
  chip shows the Tester's username. A Tester linked to no Client sees a
  not-linked message. Only a Tester can read their own Client: a Manager,
  an Agent or another Client's Tester gets "not found".
- **Active Fleet and Open Requests** (`client-dashboard-fleet-and-requests`).
  - **Active Fleet:** Smartphones that are Active or In Repair, plus Active
    SIM Cards.
  - **Open Requests:** Requests at Pending Approval, Submitted or In
    Progress.
  - **If a read fails:** if any one Contract's read fails, that figure shows
    "—" with a "Couldn't load…" line, and the rest of the page still loads.
- **Latest invoices** (`client-dashboard-latest-invoices`). One row per
  Contract, showing the latest sent or approved Client Invoice: the month,
  the status and the total. A Fee logged after the invoice was sent doesn't
  move that total. An **Open Invoices** link goes to the Invoices page.
- **Enablers** (`type-scale-tokens`, `surface-demo-note-opt-in`). The 12px
  and 13px text sizes became the `text-label` and `text-label-sm` design
  tokens. The "demo data" footer now appears only on pages that ask for it.

## Acceptance walkthrough

1–12 (sign-in, figures, API checks, isolation between Clients, empty, failed
and degraded states, demo removal) — played — evidence:
`evidence/step-1-*` … `evidence/step-12-*`
13. Read the copy this feature adds: "Open Invoices", "No Contracts yet", the
    not-linked message and the "Couldn't load…" lines — yours
14. Sign in as Dana on the demo profile and match every figure against her
    Fleet, Requests and Invoices pages — yours

Full detail per step is in `acceptance.json`. Step 7's text names Jordan as
the one who sends the UK invoice, but in the demo data that Contract belongs
to Priya, so the runner played it as Priya. The code is right; only the step's
wording is wrong.

## Decisions taken alone

All are in the spec's `## Decisions taken`. Taken during build and review
**(after review)**:

- Every changed screenshot golden is deleted before it is recaptured.
  `maxDiffPixelRatio` would otherwise keep a stale one passing.
- The fix pass changed the Contract row on phones so that it wraps instead of
  cutting off. "Solstice Retail… — United States" was losing the country, the
  one part that tells two Contracts apart.
- The fix pass made the error page show exactly the spec's single title. To
  allow that, `EmptyState`'s description became optional.
- **You authorised one extra fix pass** (2026-10-02) for F22 (`min-w-40`
  instead of a hand-written width). You dismissed F23 and had the rule in
  `docs/agents/frontend.md` reworded: a golden may move with a code change
  that follows `DESIGN.md`, never on its own.

## Debt recorded

Twelve lines under `## frontend` in `docs/tech-debt.md`, tagged
`real-client-dashboard`. The main ones:

- dead code in `frontend/lib/status.ts` (F9);
- no timeout on the dashboard's backend reads (F8);
- no retry button on the error page (F12);
- no visual goldens for the empty, failed and degraded states (F15);
- the `readIdentity` flag argument (F24).

## How to undo

```
git revert -m 1 16b7823
```

This reverts code only; there is no migration. The two new read endpoints
disappear, and the dashboard goes back to demo data.

## What happens next

`real-manager-dashboard` is next in `real-dashboards`. Its definitions of
"billed" and "payout" will need your answers. The two approved invoice
features, `edit-client-invoice-lines` and `send-a-client-invoice-back`, go to
tickets next. `deactivate-a-login` waits for your approval.
