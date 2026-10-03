# Delivery report — edit-client-invoice-lines

<!-- sdlc:template delivery 1 -->

Merged to `main` as `7737fa3` (PR #22) on 2026-10-03. The merge gate answered
`ok: true`: no blocking finding, harness passed, `verify` passed.

## What was built

While a Client Invoice is a draft, the Agent can set every line to what was
actually billed. The calculated amount only pre-fills a line.

- **Each invoice keeps its own lines** (`store-client-invoice-lines`,
  `serve-client-invoices-from-stored-lines`).
  - **The lines:** one per Postpaid SIM and one per Fee. They're stored when
    the invoice is sent, and every read, the PDF and the Review Queue show
    them from then on.
  - **Late Fees:** a Fee logged after the send doesn't change the sent
    invoice.
  - **Older invoices:** invoices sent before this change were copied across
    as they were (migration V56). Every amount, total, queue row and PDF
    reads exactly as before.
- **The Agent edits a line** (`agent-edits-a-client-invoice-line`,
  `agent-edits-lines-on-the-client-invoice-page`).
  - **Editing:** Edit, Save and Reset sit on each line of a draft. An edited
    line shows "Edited · computed $X", and Reset returns it to the
    calculated amount.
  - **Messages:** a refused amount keeps what was typed, with the server's
    message. A sent invoice can't be edited.
  - **Keyboard and phone:** everything works by keyboard alone and at phone
    width.
- **Pay follows the edits** (`local-support-fees-follow-billed-client-invoice-lines`).
  The Agent's Local Support Fees follow what was billed:

  | The Agent's own invoice | What an edit does to the Agent's pay |
  |---|---|
  | Draft | Shows the new figure. |
  | Sent | Moves by the edit's difference, with an audit line. |
  | Approved or paid | Nothing moves; the difference carries over to next month. |

  A Fee logged after the Client Invoice was sent still counts that month.
- **The Manager sees the edits** (`manager-sees-edited-client-invoice-lines`).
  The Manager's invoice page now lists each SIM with its billed amount and
  the "Edited · computed" note. The Client's Tester sees only the billed
  amounts.

## Acceptance walkthrough

1–19 (API, pay, audit, race tests, migration snapshot, Agent / Manager / Tester pages) — played — evidence: `evidence/step-1.txt` … `evidence/step-19-*`
20. Keyboard and 375px on both pages — played after the fix — evidence: `evidence/step-20-replay-*`
21. Full suites: 634 backend tests, 276 vitest, 79 e2e, 71 visual — played — evidence: `evidence/step-21-*`
22. A real carrier bill: set an over-plan SIM's line to the billed figure, check the Agent Invoice, review as the Manager — yours
23. Read ADR 0004 and the notes on ADRs 0001–0003: do they say what you settled? — yours

Full detail per step is in `acceptance.json`.

## Decisions taken alone

All are in the spec's `## Decisions taken`. Taken during build and review
**(after review)**:

- **Read transactions:** invoice reads, the PDF and the Review Queue run in a
  read-only transaction. Without one, stored lines would fail to load in
  production, and the tests couldn't see it.
- **Orphaned edits:** on a never-sent draft, an edited line for a SIM that
  has stopped billing stays as an edited line, and the Agent can reset it.
- **The rare race:** an edit and the Agent's own invoice send both lock the
  Agent's record. An edit can't be lost if the Agent Invoice is created and
  sent at the same moment.
- **Ticket wording:** the ticket critic's last objection was a wording slip
  with the fix given, so I applied it instead of asking you.
- **Shared code:** the edit marker, billed amount and announcement hook moved
  to `components/ui`, because both the Agent and the Manager pages use them.
- **Column header:** the Fee column header is "Billed", to match the SIM
  column.
- **You authorised one extra fix pass** (2026-10-03) for three keyboard and
  screen-reader issues (F21–F23):
  - focus stays put while a save runs;
  - Escape works from the whole editor;
  - same-day Fees of the same type are labelled "1 of 2" and "2 of 2".

## Debt recorded

Fifteen entries in `docs/tech-debt.md` are tagged `edit-client-invoice-lines`.
The main ones:
- the old snapshot columns are kept until you confirm you won't roll back;
- the Edit control's pending state should move into the shared button;
- the `@Lazy` cycle between two services;
- the ADR and spec text on lock order is stale.

A proposal to amend coding rule Frontend 13 is in the inbox.

## How to undo

```
git revert -m 1 7737fa3
```

Reverting is not code-only. Migration V56 stays applied. Its table and flag
are harmless once unused, and the old snapshot columns were never dropped.

The catch is invoices sent **while this was live**. Their send stored lines
instead of writing the old `snapshot_base_amount` and Fee snapshot rows. After
a revert, those invoices would read with no frozen base amount and no frozen
Fees. Before reverting, a one-off data fix would rebuild their snapshot from
`client_invoice_lines`:
- the sum of the per-SIM or `BASE_AMOUNT` lines becomes `snapshot_base_amount`;
- each `FEE` line becomes a snapshot row.

Agents' edits would also be lost from every draft.

## What happens next

`send-a-client-invoice-back` is next. Its eight tickets are cut and it builds
on this feature. `deactivate-a-login` has eight tickets ready too.
