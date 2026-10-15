# Delivery report — client-invoices-layout-fixes

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

The three layout points you raised on the Agent's Client Invoices page.
Merged as PR #30 (`28d9673`), two tickets. The last two of the three planned
tickets were combined into one by your choice.

- **Side list rows on one line (stories 1–5).** The Client name is on the
  left, cut with "…" when long and shown in full on hover. The status chip is
  on the right of the same line, with space between. The Country, shown only
  when two Clients share a name, sits on a second line under the name. The
  "Sent back" mark sits on a second line, right-aligned under the status, or
  on the first line when there is no status. Screen readers hear the row as
  before. Ticket: `side-list-row-one-line`.
- **Edit beside the amount on desktop (stories 6–9).** In the Billed column,
  the amount and Edit (and Reset on an edited line) are on one line, and
  "Edited · computed …" sits under them, like correcting a SIM number in the
  Fleet. Editing shows the field, Save and Cancel on one line, with any
  refusal below. Amounts, the Billed header and the note share one right
  edge. Ticket: `invoice-lines-layout`.
- **One column below 1280px (stories 10–22).** Postpaid SIM Cards and Fee
  lines become the rows of your mockup:
  - line 1: the number or type on the left, the amount on the right;
  - line 2: the details on the left (cancellation note, "Logged {date}",
    "Edited · computed …"), with Edit and Reset on the right;
  - then a Fee's description.

  Nothing scrolls sideways at any width, phones included. The lines stay
  tables for screen readers. A sent invoice shows the same rows with no
  buttons and keeps the edited notes. The Manager's invoice page is
  unchanged. Ticket: `invoice-lines-layout`.

## Acceptance walkthrough

Played by an agent on its own throwaway stack, never on yours.

1. Side list name and chip on one line at 1440, column 256px — played — evidence: `evidence/step-1-geometry.txt`
2. 60-character name cut with "…", full in its title — played — evidence: `evidence/step-2-geometry.txt`
3. Same-name Clients show the Country on line 2 — played — evidence: `evidence/step-3-geometry.json`
4. Status plus "Sent back" mark; the mark alone on line 1 — played — evidence: `evidence/step-4-mark-geometry.json`
5. Amount and Edit on one line at 1280; no sideways scroll at 1280 and 1366 (replayed after the fix) — played — evidence: `evidence/step-5-replay-geometry.json`
6. Keyboard: Edit, field/Save/Cancel on one line, refusal, Escape — played — evidence: `evidence/step-6-keyboard-log.txt`
7. One-column rows at 1279/1024/768/390/375, notes on the left (replayed after the fix) — played — evidence: `evidence/step-7-replay-geometry.json`
8. Editing at 390, refusal, save, Reset, 64-character number — played — evidence: `evidence/step-8-log.txt`
9. Sent invoice and past-month draft: no buttons, notes kept — played — evidence: `evidence/step-9-log.jsonl`
10. Table semantics at 1440 and 390 (Chromium; WebKit not installed) — played — evidence: `evidence/step-10-chromium-390.yml`
11. Manager's invoice page identical to `main` — played — evidence: `evidence/step-11-compare.txt`
12. `DESIGN.md` amendments, only the four goldens moved, verify green (replayed) — played — evidence: `evidence/step-12-replay-verify.txt`
13. Side list and desktop Edit, side by side with the Fleet — yours
14. The invoice lines on your phone — yours

## Decisions taken alone

Everything in the spec's `## Decisions taken`. Settled during this delivery:

- **(by you)** The last two tickets became one, `invoice-lines-layout`, so
  the full test suite ran once instead of twice.
- **(after review)** The invoice lines use their own small table file,
  `invoice-line-table.tsx`. It is built from the shared table parts, except
  the outer table, whose fixed 720px minimum width can't be lifted.
- **(after review)** The 720px minimum doesn't come back on desktop either.
  The spec assumed the invoice area is at least 736px wide at 1280px, but it
  is 692px, so the lines scrolled sideways and clipped Cancel and the note.
  They now fit at every width.
- **(by you, after review)** `DESIGN.md` says so in one sentence (F18).

## Debt recorded

- `contract-side-list.tsx` · Name cut hard · two badges leave a few letters of the name; full name on hover only (F12)
- `edit-client-invoice-line-control.tsx` · iOS zoom on focus · 12px field; shared with the Fleet fields (F13)
- Paid: "Columns hidden on phones", "Bare field on mobile", "Unused directive".

One test failure during the first change's merge is not explained. The
Agent Stock test of a Tester submitting a request failed once, then passed
on a rerun of the same code. It is probably machine load, but not proven.

## How to undo

```
git revert -m 1 28d9673
```

Frontend and docs only; no data or schema change.

## What happens next

Three features now wait for your walkthrough, which is the limit, so no
new feature starts until you accept one. Next: the delivery speed-ups you
agreed to, then the seven answered inbox items through `/sdlc`.
