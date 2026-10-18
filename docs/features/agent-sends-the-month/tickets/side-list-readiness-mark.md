---
id: side-list-readiness-mark
title: Mark each Client Invoices side list row ready or with its missing files
status: in-progress
depends_on: [cards-defer-to-the-month-bar]
labels: [frontend]
stories: [21, 22, 23, 35]
---

## Context

`spec.md` `**The side list.**` under `### Frontend`, `## Design direction` (the readiness mark, the `DESIGN.md` Side list Row line), `## Testing decisions` items 5 (view cases, accessible name order), 6 (`clientInvoiceRowName`) and 7 (`client-invoices-side-list-rows.spec.ts`). Depends on `cards-defer-to-the-month-bar` because it shares the Agent page's view, test file, stub data, goldens and `helpers.ts` with it; running them in parallel would conflict on all four.

`SideListItem` (`frontend/components/ui/side-list.tsx`, rows in `side-list-rows.ts`) gains `status?: ReactNode`, line 2 left, after the Country when one is shown, last in DOM order (accessible name reads name, Country, status badge, "Sent back" mark, readiness). On a row whose current-month invoice is a `DRAFT` (never sent, sent back, or late): no file missing shows `IconCheckCircle` (`h-3`, `success`) then "Ready"; otherwise "1 file missing" / "{n} files missing" in `ink-mute`. Nothing on a sent, approved or failed-to-load row. The count is `missingCarrierInvoiceLines(invoice).length`. `DESIGN.md`'s Side list Row paragraph gains the readiness line given in the spec, in this ticket's commit.

Harness declarations for the result (the merger applies them), `CONTEXT.md`: the Agent's month (every Client Invoice of the Agent for a billing month), the month send (the one action that first-sends them together, all or nothing), a ready Client Invoice (`DRAFT` with every Postpaid SIM line covered), and the Client Invoice entry saying a first send goes through the month send. Frontend DoD in `docs/agents/frontend.md` applies.

## Acceptance criteria

- [ ] Each Client Invoices side list row whose current-month invoice is a `DRAFT` (never sent, sent back, or late) shows "✓ Ready" or "{n} files missing" ("1 file missing") on line 2 under the Client name, after the Country when one is shown ("Kenya · ✓ Ready"); the status badge and the "Sent back" mark are unchanged.
- [ ] A row whose invoice is sent or approved, or failed to load, shows no readiness mark.
- [ ] After an attach or a remove, the row's mark and the bar's count follow once the page refreshes.
- [ ] A row's accessible name reads name, Country, status badge, "Sent back" mark, then the readiness text, and the long name stays on one truncated line beside its badge; the readiness sits on line 2, so the row is two lines high.
- [ ] The `agent-client-invoices-*` goldens show both readiness marks (one "✓ Ready", one "n files missing"); `client-invoices-side-list-rows.spec.ts` (bounding-box cases, no goldens) gains a case with a readiness mark beside a Country and a "Sent back" mark; no other golden moves.
- [ ] End to end, a fresh Agent's rows read "{n} files missing" and "✓ Ready" as files are attached and removed, and a sent-back row reads "Sent back" with its readiness.

## Tests

Seams per spec `## Testing decisions` items 5, 6, 7.

- **`client-invoices-view.test.tsx` (existing, extended):** the mark for a ready draft, for 1 and n missing files, beside a Country; its absence on sent, approved and failed rows; the accessible-name order; the mark following the refreshed invoice after an attach or a remove (the view re-rendered with the new invoice).
- **`side-list.tsx` / `side-list-rows.ts`:** covered through the view tests above; the `status` slot is rendered by the shared row, which has no test file of its own today, so a case is added there only if one exists by implementation time.
- **`agent-sends-the-month.spec.ts` (existing from the previous ticket, extended e2e):** the side list marks on a fresh Agent.
- **Visual:** `client-invoices-side-list-rows.spec.ts` new bounding-box case; `agent-client-invoices-*` recaptured.

## Regression

At risk: the shared side list rows used by Carriers and Client Invoices (`carriers-view.tsx` uses `SideListItem`), the row's one-line fit with a long name, every locator that reads a row's accessible name, and the Agent page goldens.

Existing tests expected to change, each with the reason:

- `frontend/components/agent/client-invoices-view.test.tsx`: gains the mark cases. Every pre-existing case keeps passing unmodified except those that assert a row's exact accessible name, which now include the readiness text.
- `frontend/tests/e2e/helpers.ts`: `clientInvoiceRowName` accepts the readiness text after the status and "Sent back" parts.
- `frontend/tests/e2e/client-invoices-side-list.spec.ts`: every exact Draft locator (lines ~53, 54, 142, 275, 304, and the `\s*Draft$` strip at ~46) and the exact `Sent back` ones now include the readiness text, and the one-line-high assertion (~283) becomes "the long name stays on one truncated line beside its badge; the row is two lines high".
- `frontend/tests/e2e/agent-sends-the-month.spec.ts`: gains the marks case. Pre-existing cases keep passing unmodified.
- `frontend/tests/visual/client-invoices-side-list-rows.spec.ts`: gains one bounding-box case; its first case changes (the exact "Aurora Retail Group Draft" locator at line ~35 and the one-line height check at ~38 now account for the readiness line 2); the other two cases are unmodified. It holds no goldens.
- `frontend/tests/visual/surfaces.spec.ts`: the exact "Aurora Retail Group Draft" locator at line ~306 gains its readiness text; no other change.
- `frontend/tests/visual/stub-backend.mjs` is expected to stay unchanged (the previous ticket serves a ready and a missing-file invoice); name any forced edit.
- Goldens `agent-client-invoices-{desktop,mobile}-{light,dark}.png` recaptured. The Carriers goldens and `carriers-view` tests must stay unchanged.

- Accepted by the orchestrator at merge: `frontend/tests/visual/stub-backend.mjs` gains the session `visual-agent-invoices-unsent-marked-session` (needed for the Country + readiness + Sent back bounding-box case; existing sessions unaffected), and the desktop goldens `client-invoice-attach-dialog-open-desktop-*`, `client-invoice-files-paused-desktop-*`, `client-invoice-remove-confirm-open-desktop-*` are retaken because their captures include the side list.

## Observability

N/A: a presentational mark derived from data the page already loads; nothing is logged.
