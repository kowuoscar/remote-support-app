---
feature: client-invoices-layout-fixes
epic: agent-client-invoices-side-list
status: approved
date: 2026-10-15
---

<!-- sdlc:template spec 1 -->

# Client Invoices layout fixes: one-line side list rows, inline line actions, one-column lines below 1280px

## Problem

The human tested `client-invoices-side-list` by hand on 2026-10-15 and gave
three pieces of feedback on the Agent's Client Invoices page:

> - The side list, the chip should be on the same line not below with a space between.
> - The edit button (desktop) is below the amount, it should be on the side following the same design when updating sim/smartphone/carriers
> - The billed amount and edit button are not showing (mobile)

What the Agent runs into today:

- **The side list.** Each row puts the Client name on one or two lines and
  the status badge on a separate line below it. Each row is taller than it
  needs to be, and the statuses don't form a column the eye can run down.
- **Desktop invoice lines.** In the Billed column, the amount comes first,
  then the "Edited · computed" note, then the Edit and Reset buttons on a
  third line. The Edit button is the soft-indigo chip. In the Fleet, a
  correction is the value with a quiet Edit beside it, and the field, Save and
  Cancel open on one line. The two pages fix a mistake in two different ways.
- **Phones and narrow windows.** The Postpaid SIM Cards and Fee lines are
  720px-wide tables inside a sideways scroller. On a phone, only the first
  column shows. The billed amount and Edit are off-screen, and nothing hints
  that they are there (`docs/tech-debt.md`, "Columns hidden on phones"). On a
  phone, the Agent cannot see what a line bills or correct it.

Two follow-ups were answered "Ok" by the human. A long name that doesn't fit
beside the chip is cut with "…" and shown in full on hover. On desktop, the
Fleet correction layout applies: the value, then Edit; while editing, the
field, Save and Cancel on one line. For phones the human asked: "On mobile,
propose a layout (one column, row = information and actions), that avoid
scrolling on the side. Show me an exemple before confirmation". The layout was
shown and confirmed with "Ok, go ahead". It applies below 1280px, the side
list's own breakpoint, and the human accepted that too. The confirmed
mockups:

```
 Postpaid SIM Cards
 ________________________________
|                                |
| +44 7700 900123         £25.00 |
|                         [Edit] |
|________________________________|
|                                |
| +44 7700 900456         £30.00 |
| Edited · computed £25.00       |
|                 [Edit] [Reset] |
|________________________________|
|                                |
| +44 7700 900789         £25.00 |
| Cancelled 3 Oct — still billed |
| through this month      [Edit] |
|________________________________|

 Fee lines
 ________________________________
|                                |
| Topup                   £10.00 |
| Logged 12 Oct           [Edit] |
| Customer asked for extra data  |
|________________________________|
|                                |
| Delivery                 £8.00 |
| Logged 14 Oct           [Edit] |
|________________________________|

 A row being edited (both lists)
 ________________________________
|                                |
| +44 7700 900456                |
| [ 30.00      ]  [Save][Cancel] |
| ! Enter an amount of zero or   |
|   more, with at most two ...   |
|________________________________|
```

"A sent invoice (no Edit): the amount alone on the right." This means no
buttons.

The desktop row that was shown:

```
 _____________________________________________________
| NUMBER              |                       BILLED  |
|_____________________|_______________________________|
| +44 7700 900456     | £30.00   [Edit] [Reset]       |
|                     | Edited · computed £25.00      |
|_____________________|_______________________________|
```

## Journeys

This is the second and last feature of
`docs/roadmap/agent-client-invoices-side-list.md`, the line
`client-invoices-layout-fixes`.

- **Bill the Client for the month** (`exists`, stays `exists`). The journey
  does what it did before. This feature makes its line corrections usable on a
  phone, which until now they were not in practice, and makes the desktop
  page read like the rest of the app. Its proof spec,
  `client-invoice-submission-and-visibility.spec.ts`, keeps passing unchanged.

## Goals / Non-goals

Goals:

- Each side list row puts the Client name on the left and the status chip on
  the right, on one line, with space between. A name that does not fit is cut
  with "…", and the full name shows on hover.
- On a desktop, an invoice line's amount and its actions (Edit, and Reset
  when edited) sit on one line in the Billed column. The "Edited · computed"
  note sits under them. Editing opens the field, Save and Cancel on one line,
  with any error below, in the Fleet correction controls' visual language
  (ghost Edit).
- Below 1280px, the Postpaid SIM Cards and Fee lines are a one-column list,
  as in the confirmed mockup. Nothing on the page scrolls sideways, at phone
  widths included.
- A sent invoice, or a closed past-month draft, shows the same layouts with
  no buttons. Its "Edited · computed" notes stay.
- `DESIGN.md` is amended in the same commits. Three places reverse earlier
  decisions:
  - its Layout rule that tables scroll sideways rather than reflow (line 166);
  - the Contract side list's row rule (lines 226-227, the Row bullet);
  - the predecessor specs' decisions, listed under `## Solution`, "What this
    reverses".

Non-goals. Each is something a reasonable agent would otherwise build:

- **No change to the Manager's Client Invoice detail view**
  (`client-invoice-detail-view`). It already lists lines at every width, and
  the human raised nothing about it. `BilledAmount`, which it shares, keeps
  its current shape.
- **No change to any other table in the app.** The shared `Table` keeps its
  720px minimum and sideways scroll everywhere else: Requests, Fleet, the
  Testers table, the Client's invoices, and the "Sent back to you" table on
  this very page. Only the invoice card's two line tables reflow.
- **No card-wide redesign.** The card's header (month, badge, Attach file,
  Send, Download PDF), summary, hint line and Carrier invoice files list stay
  as they are, apart from fitting the width. They already wrap.
- **No change to the side list's width, breakpoint, order, selection or
  scroll behaviour, or to the Contract Switcher.** The row's layout changes
  and nothing else does.
- **No new status, mark or label.** `clientInvoiceBadge`'s labels and tones
  and the "Sent back" mark are unchanged. "Two looks for Sent back" (debt)
  stays open (see Decisions taken).
- **No change to how a line is edited.** The endpoint, validation messages,
  Reset-as-save-computed, focus handoffs, Escape, in-flight `aria-busy` and
  the announcement are all unchanged. Only the layout and the button variants
  move.
- **No new accessible names for Edit and Reset.** They stay "Edit" and
  "Reset", described by the line's name, as today (see Decisions taken).
- **No phone-only breakpoint.** The one-column lines start below 1280px, not
  640px (answered by the human).
- **No backend, API, schema or migration change.**

## User stories

1. As an Agent on a desktop, I want each Client row in the side list to show the Client name on the left and its status chip on the right of the same line, with space between, so that the column reads as names with their statuses lined up down the right edge.
2. As an Agent, I want a Client name too long for its line cut with "…" and shown in full on hover, so that every row stays one line and I can still read the whole name.
3. As an Agent with two Contracts for Clients of the same name, I want the Country on a second line under the name, so that I can still tell the two rows apart.
4. As an Agent whose Client has a sent-back invoice from an earlier month, I want that row to still carry the "Sent back" mark, shown once, beside the month's status, or, when the row also shows a status, on a second line right-aligned under it (answered 2026-10-15), so that the column still tells me who needs correcting.
5. As an Agent using a screen reader, I want each side list link to read exactly as today (the name, the Country when shown, the status, and "Sent back" when marked), so that the new layout changes nothing I hear.
6. As an Agent on a desktop correcting a draft, I want each line's billed amount and its Edit on one line in the Billed column, with Reset beside Edit when the line was edited, so that the action sits beside the amount it corrects, as it does in the Fleet.
7. As an Agent on a desktop, I want the "Edited · computed {amount}" note directly under an edited line's amount and actions, so that I see at a glance what the line would have billed.
8. As an Agent on a desktop, I want Edit to open the amount field, Save and Cancel on one line in the Billed column, with any refusal shown below, so that correcting a billed amount looks and feels like correcting a SIM number in the Fleet.
9. As an Agent on a desktop, I want the amounts lined up down the Billed column whether a row has Edit, Edit and Reset, or no buttons, so that I can compare the amounts down the column.
10. As an Agent on a screen narrower than 1280px, phones included, I want each Postpaid SIM Card as one row: the number on the left and the amount on the right on its first line; below, the line's details on the left (a cancellation note, the "Edited · computed" note) and Edit, and Reset when edited, on the right, so that I see and correct every SIM line without scrolling sideways.
11. As an Agent below 1280px, I want each Fee line as one row: its type on the left and the amount on the right; below, "Logged {date}" (and the "Edited · computed" note when edited) on the left and its actions on the right; then its description, when it has one, so that the whole Fee reads in one column.
12. As an Agent below 1280px, I want a row I am editing to show the line's name on top, then the field, Save and Cancel on one line, then any refusal below, so that I always see which line I am changing.
13. As an Agent on any screen narrower than 1280px, I want the page never to scroll sideways and the line lists never to scroll inside themselves, so that a line's amount and Edit are always in view.
14. As an Agent looking at a sent invoice, at any width, I want the same layouts with no Edit or Reset, and each edited line's "Edited · computed" note kept, so that I can still see what I corrected before sending.
15. As an Agent opening a past month's draft that was never sent, I want it laid out like a sent invoice, with no buttons, so that a closed invoice never offers an action.
16. As an Agent using a keyboard, I want editing to behave as today at every width: Edit puts focus in the field, Escape and Cancel return it to Edit, a refused Save keeps it in the field, and the focus ring is visible at each stop, so that the new layout costs me nothing.
17. As an Agent opening a single invoice's page (from "Sent back to you", or any other month), I want its lines laid out the same way, so that the invoice looks the same however I reach it.
18. As a Manager, I want my Client Invoice detail page unchanged, so that a fix on the Agent's page never moves mine.
19. As an Agent using a screen reader, I want the SIM and Fee lines announced as tables at every width, each with its column headers and one row per line, so that the layout change does not change what I hear.
20. As an Agent with a long SIM number, a long Fee type or a long description, I want the text to wrap inside the row while the amount stays whole on the right, so that a long value never pushes the amount or Edit out of view.
21. As the team, I want `DESIGN.md` to state where invoice lines reflow below 1280px, how a side list row is laid out, and that invoice line actions follow the Fleet correction controls, so that the written rules stay true.
22. As the team, I want the Agent Client Invoices goldens, at desktop and phone, to show a draft invoice with every line shape (plain, edited, cancelled SIM; a Fee with and without a description; an edited Fee), so that a regression in either layout is caught.

## Solution

### Prefactoring: debt paid on the way

- **"Columns hidden on phones"** (`agent-client-invoice-card.tsx`). Paid by
  the one-column lines below `xl`, which is the core of this feature.
- **"Bare field on mobile"** (`edit-client-invoice-line-control.tsx`). Paid
  by the layout rather than by a visible label. Below `xl`, the row being
  edited keeps the line's name (number or Fee type) on the line directly
  above the field, and at `xl` the name is in the same table row. The field
  keeps its `sr-only` "Billed amount for {line} ({currency})" label.
- **"Unused directive"** (`contract-side-list.tsx`). The row markup in that
  file is rewritten anyway, so the lint warning is resolved by making the
  directive necessary or removing it. Lint stays clean.

Debt on the touched files that stays open, with the reason:

- **"Pending state duplicated outside Button".** Paying it means giving the
  shared `Button` a focusable busy mode, which changes every dialog in the
  app (two more debt entries).
- **"Effect with no dependency array".** The focus logic is correct, pinned
  by tests, and not moved by a layout change.
- **"Two looks for Sent back".** Unifying the two needs a change to
  `clientInvoiceBadge`, which moves the invoice card's badge.
- **"Duplicated invoice layout".** The Manager view is out of scope. The two
  move closer, since both now list lines on narrow screens.

### The side list row

`ContractSideList` keeps its props, links, order, selection, click handling
and arrival scroll. Only its row layout changes:

```
line 1:  [Client name, one line, cut with "…"] ........ [status chip]
line 2:  [Country, muted — only on a name collision]    [mark — see Q1]
```

- **Line 1.** The name is on the left and takes the free width. It never
  wraps, and when it is too long it is cut with an ellipsis. Its `title` is
  the full name, as today. The status `Badge` sits on the right of the same
  line, at the row's right inset, with at least an 8px gap. The badge never
  shrinks; the name gives way.
- **Line 2** appears only when there is something to put on it. On the left
  is the Country (`text-label-sm`, `ink-mute`), on the rare name collision.
  On the right, under the status, is the "Sent back" mark when the row also
  has a status badge (recommended answer to open question 1). A row with no
  status (its invoice failed to load) and a mark puts the mark on line 1 in
  the status's place.
- **Accessible name unchanged.** The DOM order stays name, Country, status,
  mark, with the same space text nodes. Line placement is done by CSS grid
  placement, not by reordering, so every link reads exactly as today. The
  `side-list` e2e and component name assertions (`"{Client} Draft"`,
  `"Yarrow Labs Sent back"`) stay true.

### The line amount control, at `xl` and wider

`EditClientInvoiceLineControl` keeps its interface, its states and its
behaviour. Its display and editing branches are re-laid out in the Fleet
correction controls' language (`SimCardNumberControl`, `correct-a-fleet-unit`
delivery: "the correction Edit buttons are the quiet `ghost` variant"):

```
display (draft):   £30.00  [Edit] [Reset]       ← one line
                   Edited · computed £25.00     ← only when edited
editing:           [ 30.00 ] [Save] [Cancel]    ← one line
                   ! refusal message            ← only on a refusal
sent / closed:     £30.00
                   Edited · computed £25.00     ← only when edited
```

- **Variants.** Edit becomes `ghost` (was `row`), and Reset stays `ghost`.
  Save becomes `row` (was `secondary`) and Cancel stays `ghost`. This matches
  the Fleet: a quiet Edit, a soft-indigo Save, a quiet Cancel. Save now comes
  before Cancel, as in the Fleet and the human's wording. The field is the
  Fleet's compact height (28px, matching the `sm` buttons), `tnum`,
  right-aligned, 112px wide.
- **Alignment.** The Billed cell stays right-aligned. In a draft, the actions
  slot keeps one width on every row, enough for Edit and Reset, so every
  amount's right edge lines up down the column (the Tabular Everywhere Rule's
  "columns of numbers always align"). The note and a refusal sit under the
  line, right-aligned. On a sent or closed invoice there is no actions slot,
  and the amount is right-aligned as today.
- **Unchanged.** The button names and their `aria-describedby` (the line's
  name, plus the error on Reset), the `role="alert"` refusal with its icon,
  the busy pattern, the focus targets, Escape and the `output` announcement
  all stay as they are.

### The invoice lines below `xl`: one markup that reflows

The card's two line tables become one responsive structure. At `xl` it looks
and reads like today's table. Below `xl` the same elements reflow into the
mockup's one-column rows. Each line is rendered **once**: there is no second
hidden copy for the other width.

- **Semantics are pinned.** The table, row groups, header cells, rows and
  cells carry explicit ARIA roles (`table`, `rowgroup`, `columnheader`,
  `row`, `cell`). A CSS display change can then never strip the table
  semantics, which some browsers do to restyled table elements. The header
  row stays in the accessibility tree at every width and is visually hidden
  below `xl`.
- **Width.** Below `xl` the line tables drop the shared `Table`'s 720px
  minimum. They keep the hairline wrapper and the row dividers, which is the
  mockup's boxed list. The 720px minimum comes back at `xl`, where the
  invoice area is at least 736px wide.
- **Each row below `xl` is a two-column grid**, a flexible left track and an
  `auto` right track, laid out by named areas:

  ```
  Postpaid SIM row                    Fee row
  "what     amount"                   "what     amount"
  "details  actions"                  "details  actions"
                                      "desc     desc"
  editing: "what" / "editor" (full width) / "error" (full width), then the
  row's own details (cancellation note; Logged date and description)
  ```

  - `what`: the SIM number, or the Fee type. It wraps (`break-words`).
  - `amount`: the billed amount. It never wraps.
  - `details`: stacked, in this order:
    - the SIM's cancellation note, or the Fee's "Logged {date}";
    - then the "Edited · computed" note when the line was edited.
  - `actions`: Edit, then Reset. They align to the last line of `details`
    and drop to a line of their own, right-aligned, when the two don't fit
    side by side (the mockup's edited row).
  - `desc`: the Fee's description, full width. It is left out when the Fee
    has none: no "—" placeholder below `xl`.
  - A row with no details puts its actions alone on line 2, on the right
    (the mockup's first row).

  The cells and the control's parts are placed into these areas with
  `display: contents` or `subgrid`, whichever the implementer proves keeps
  the accessibility tree intact (Constraints). At `xl` the same elements are
  table cells again, and the note and actions go back into the Billed cell as
  in the previous section.
- **Copy below `xl`.** The column headers are hidden, so the Fee's date reads
  "Logged {date}". The word "Logged" is shown below `xl` only; at `xl` the
  column header says it. The SIM's cancellation note keeps its wording.
- **The editing row below `xl`.** The amount, the note and the actions give
  way to the editor line: the field takes the free width, with Save and
  Cancel on its right, and a refusal below it, full width. The row's own
  details stay under it, so the Agent keeps the context of the line.
- **Read-only.** A sent invoice or a closed past-month draft renders the same
  rows with no `actions` area. The amount stays on the right of line 1, and
  the note stays in `details`. The note's presence is not a decision of this
  spec. `CONTEXT.md`, Client Invoice: "a line that was edited keeps showing
  'Edited · computed {amount}' on every surface".
- **Where it lives.** The responsive line structure is local to
  `AgentClientInvoiceCard`, built from the shared table parts plus class and
  role overrides. It is not a new `components/ui` table variant: it has one
  user, and every other table keeps scrolling. `LineAmount` composes `Money`
  and `EditedLineNote` itself on the read-only path, so that below `xl` the
  note can move to `details`. `BilledAmount` is left exactly as the Manager
  view uses it.
- **Both Agent pages get it.** The card serves `AgentClientInvoicesView` and
  `AgentClientInvoicePageView` (by id), so both change together.

### The rest of the card below `xl`

No redesign. The header already wraps (`flex-wrap`), the summary is a
two-column grid below `sm`, and the Carrier invoice files list truncates
filenames. The constraint that nothing scrolls sideways covers the whole
card. Anything found overflowing at 375px is fixed in place, with the
smallest change, and named in the ticket's result.

### `DESIGN.md`

Amended in the same commit as each visual change (`docs/agents/frontend.md`,
"Drift vs evolution"):

- **Layout (line 166).** The rule becomes: "Tables scroll horizontally on
  narrow viewports (`min-w-[720px]` inside an `overflow-x-auto` wrapper)
  rather than reflowing to cards — except the Agent's Client Invoice lines
  (Postpaid SIM Cards, Fee lines), which below `xl` reflow to a one-column
  list of rows (what and amount, then details and actions), keeping their
  table semantics (client-invoices-layout-fixes spec)."
- **Contract side list, Row bullet (lines 226-227).** The two-line clamp and
  the meta line below the name are replaced:
  - the name is on one line, cut with "…", with the full name in `title`;
  - the status badge is on the right of the same line;
  - a second line appears only for the Country on a collision and for the
    mark placement decided by open question 1;
  - the accessible name order is unchanged.
- **Buttons.** One sentence on correction controls: an inline correction
  (Fleet units, Client Invoice lines) is the value with a `ghost` `sm` Edit
  beside it. Editing is the field, a `row` Save and a `ghost` Cancel on one
  line, with the refusal below.

### What this reverses

Each reversal is stated here so the next reader does not restore the old
rule:

- **`DESIGN.md` line 166** (tables never reflow): amended, as above.
- **`DESIGN.md` lines 226-227** (two-line name, meta line below): amended, as
  above.
- **`client-invoices-side-list` spec**, Non-goals, lines 105-107: "No change
  to the invoice card itself … its sections, actions and phone layout (debt:
  columns hidden at 375px) stay as they are". Reversed for the line sections'
  layout and the action buttons. Its story 22 and its decision "A row stacks
  the name above a meta line" are reversed by stories 1-3 here.
- **`edit-client-invoice-lines` spec**, Solution, lines 506-511: each line
  "gets an **Edit** row action", with a "**Reset** row action". The actions
  stay, but their look is no longer the soft-indigo `row` chip: Edit and Reset
  are `ghost` and Save is `row`, as in the Fleet. The order and placement
  change as above.

## Design direction

The surface is **Operate**: the Agent corrects and sends one invoice after
another, at a desk or on a phone. The page keeps `DESIGN.md`'s Financial
Control Room world, with no token added and no new colour. The confirmed
mockups in `## Problem` are the reference. Design review and the human's
walkthrough steps judge the result against them, and against the Fleet page's
correction controls side by side.

- **Side list.** The rows get calmer and denser: one line of `text-sm
  font-medium` name, with a pill status badge on the right edge. The badges
  form a ragged-left, flush-right column. The selected row keeps the
  soft-indigo fill, and its badges keep their own tones. The 8px `px-3 py-2`
  row rhythm holds.
- **Billed column, desktop.** The amount stays the strongest thing in the
  cell (`Money`, `tnum`). The ghost Edit and Reset are quiet text buttons that
  only fill on hover, so a long draft is no longer "a wall of colour"
  (`correct-a-fleet-unit` delivery). The soft-indigo appears only on Save,
  while a line is being edited. The note stays `text-label-sm`, `ink-mute`.
- **Lines below `xl`.** A hairline-bordered `rounded-xl` box with hairline
  dividers between rows, as the table wrapper is today, with the same
  `px-4 py-3` cell padding. Line 1 carries the weight: what (`font-medium`,
  `ink`) and the amount. Line 2 is secondary (`text-label-sm`, `ink-mute`
  details). The actions are the same ghost buttons, right-aligned. The Fee
  description is `ink-secondary` body text. No row is a card of its own, no
  row has a shadow (the Flat-At-Rest Rule), and no row is coloured.
- **Touch.** The `sm` buttons are 28px tall. Below `xl` the actions keep at
  least 8px between them, and each button's hit area is at least 28×28px.

## Constraints

- Inherits every constraint of the `remote-support-mvp` spec and of
  `client-invoices-side-list`. No backend, API, schema or migration change.
- **The breakpoint is `xl`, `min-width: 80rem` (1280px)**, the side list's
  own. At 1280px and wider, the line tables are tables. Below 1280px, they are
  one-column rows. No other breakpoint is added for this feature.
- **No sideways scroll.** At 375, 390, 768, 1024 and 1279px wide,
  `document.documentElement.scrollWidth` equals `window.innerWidth` on
  `/agent/client-invoices` and on `/agent/client-invoices/{id}`. Each line
  list's own wrapper has `scrollWidth` equal to its `clientWidth`. A SIM
  number of 64 characters or a 500-character Fee description wraps inside its
  row.
- **Visually hidden content cannot escape.** Any `sr-only` element in the line
  tables (the header row below `xl`, the field label) has a positioned
  ancestor inside the wrapper. `docs/tech-debt.md` records a page that scrolls
  sideways because of exactly this, on the Testers table.
- **One markup.** Every line is rendered once in the DOM. No element is
  duplicated and hidden per breakpoint, except the word "Logged" and the
  description's "—", which are shown at one width only. With no CSS applied
  (jsdom), there is one `row` per line plus the header row, one amount per
  line and one note per edited line.
- **Semantics at every width.** In Chromium (and WebKit when installed), the
  accessibility snapshot at 390 and at 1440 shows, for each list:
  - a table with its column headers;
  - one row per line, each with its cells;
  - the Edit and Reset buttons inside their row.

  If a placement technique (`display: contents`, `subgrid`) loses any of
  these in either browser, that technique is not used.
- **Side list.** The column stays 256px. A row's line 1 is one line high
  whatever the name's length. A name never wraps, and a status badge is never
  cut or pushed outside the row.
- **The Manager's view is untouched.** `client-invoice-detail-view` and
  `BilledAmount` have no diff. The Manager's detail page renders
  pixel-identically.
- **Tokens only** for new and changed lines: `text-label-sm`, `text-label`,
  the spacing scale and the two hairline tokens. No `text-[12px]` /
  `text-[13px]` is added (Frontend rule 8). An arbitrary value carries its
  reason in a comment.
- **Goldens.** Exactly the four `agent-client-invoices-{desktop,mobile}-{light,dark}`
  goldens move, each in the commit of the code change that moves it, with its
  `DESIGN.md` amendment. No other golden changes.
- e2e and visual runs use an isolated stack on ports other than
  3000/8080/5432 (`docs/agents/implementer-notes.md`). No test writes to the
  user's compose stack. The e2e suite is run on an idle machine, and a failure
  is never called a flake without a controlled comparison.

## Testing decisions

Tests assert external behaviour only: the accessibility tree, text, focus,
requests made, and, at real widths in a browser, where things sit. They never
assert class names or button variants. No new seam is added: each behaviour is
tested at the highest seam that can see it.

1. **Component: the existing `client-invoices-view.test.tsx` and
   `edit-client-invoice-line-control.test.tsx`.** These use Vitest and
   Testing Library, with `stubFetch` / `stubPendingFetch` and `mockRouter`
   from `tests/component`. jsdom applies no CSS, so these tests pin the
   markup contract that makes "one markup" true, not the layout. Their
   existing cases keep passing unedited. The control's tests assert by
   button name, textbox name, alert role and focus, and none of these
   change. New cases:
   - a draft with one plain SIM, one edited SIM, one cancelled SIM and two
     Fees has exactly one `row` per line plus one header row per table; each
     amount appears once in its row, and "Edited · computed" appears once
     per edited line;
   - in the editing state the form's controls come in the order field, Save,
     Cancel, with the refusal after them;
   - a Fee row contains "Logged" and its date, and a Fee with no description
     renders no description text beyond the desktop "—";
   - a sent invoice and a closed past-month draft keep each edited line's
     note and have no button in any line row (the existing "no-edit-or-reset-
     on-sent-invoice, yet the marker stays" case already covers part of
     this);
   - side list: the existing link-name cases (`"{Client} Draft"`, the
     collision Country, `"Yarrow Labs Sent back"`) pass unedited, which
     proves the accessible name order did not move.
2. **End to end: Playwright against the real backend, in the existing
   specs.** The default e2e viewport is 1280 wide, so every existing step
   sees the desktop table and keeps its `row`/`cell` queries. The new steps
   check geometry with bounding boxes, in the style of the existing
   `scrollWidth` and `toBeInViewport` checks.
   - **Prior art:** `edit-client-invoice-lines.spec.ts:147-150` (the Manager
     view at 375), `client-invoices-side-list.spec.ts:112-122` (the phone path
     and no sideways scroll) and `send-a-client-invoice-back.spec.ts:89-105`
     (switching viewport mid-test).
   - **`client-invoices-side-list.spec.ts` gains:**
     - at 1440, a row's name and status badge share a line (their boxes
       overlap vertically);
     - the badge's right edge is within the row's right inset;
     - a Client renamed to 60 characters stays one line high, with the
       name's `title` equal to the full name and the name element
       overflowing (`scrollWidth > clientWidth`);
     - the column is still 256px.
   - **`edit-client-invoice-lines.spec.ts` gains:**
     - at 1280: a SIM line's amount and Edit share a line, and its note sits
       below them; after Edit, the field, Save and Cancel share a line; a
       refused `-5` shows the alert below that line;
     - at 390: for the SIM and Fee rows, the number or type and the amount
       share line 1; Edit is in the viewport (`ratio: 1`); an edit and a
       Reset work at that width; there is no sideways scroll;
     - after send, at 390: no Edit or Reset, and the note is visible.

     The existing `/^(Edit|Reset)$/` count-0 assertion is unchanged.
3. **Visual goldens: the existing stubbed suite.** The surface is
   `agent-client-invoices` at 1440 and 390, light and dark. The stub's
   first-selected Contract, Aurora Retail Group (a Draft), gets lines:
   - a plain Postpaid SIM;
   - an edited Postpaid SIM;
   - a cancelled Postpaid SIM;
   - a Fee with a description;
   - an edited Fee with no description.

   Its totals are made consistent with those lines. The four goldens are
   recaptured. The desktop pair moves for the side list rows and the Billed
   column, and the mobile pair for the one-column lines. The fixtures already
   include a row with both a status and the "Sent back" mark, so the hard
   side list case is in the golden. Goldens are not in the `verify` gate
   (debt), so the walkthrough runs the visual suite explicitly.
4. **The accessibility tree at real widths** is checked once, in the
   walkthrough, with `playwright-cli snapshot` in Chromium (and WebKit when
   installed). It is not added as a permanent test, because the component
   test pins the markup and the e2e queries by role at 1280.

## Decisions taken

- **A sent invoice keeps the "Edited · computed" note at every width; "the
  amount alone" means no buttons.** Reason: `CONTEXT.md` already settles it
  ("keeps showing … on every surface"), the human's line is about buttons
  ("no Edit"), and the note is the Agent's only trace of a correction after
  sending.
- **The one-column lines are one markup restyled by CSS grid, with explicit
  ARIA table roles, not a table plus a hidden list.** Reason: a second copy
  doubles every line in jsdom, makes the existing `row` and text queries
  ambiguous, and leaves two layouts to drift apart. Explicit roles keep the
  table semantics whatever the display. The placement technique is the
  implementer's, bounded by the semantics constraint.
- **Edit and Reset keep the accessible names "Edit" and "Reset", described by
  the line's name (`aria-describedby`), not a Fleet-style `aria-label`
  ("Edit number, {number}").** Reason: each is already distinguishable by its
  description. The unit tests (including F23's per-line descriptions) and the
  e2e pin these names. The human asked for the Fleet's look, not its naming.
  Renaming is a cheap later change if wanted.
- **The Manager's invoice detail view, and the shared `BilledAmount`, are not
  touched.** Reason: the human's feedback is about the Agent's page. The
  Manager view already lists its lines at every width and passes its own
  375px check.
- **Edit and Reset are `ghost`, Save is `row` and Cancel is `ghost`, in the
  order field, Save, Cancel.** Reason: this is the Fleet correction controls'
  exact set (`SimCardNumberControl`), which the human named, and the recorded
  Fleet decision that correction Edits are quiet.
- **The in-flight busy pattern (`aria-disabled` + `aria-busy`, local spinner)
  is kept, not swapped for the Fleet's `loading`.** Reason: `loading` uses
  native `disabled`, which drops keyboard focus (debt on `Button`). The
  invoice control's pattern is the better one, and its tests (F21) pin it.
- **The amount field is the Fleet's compact 28px height, 112px wide at `xl`,
  and takes the free width below `xl`.** Reason: it sits on one line with the
  `sm` buttons. The mockup draws it wide on a phone.
- **In a draft, the actions slot keeps one width on every row at `xl`.**
  Reason: amounts then line up down the Billed column whether a row has Edit
  alone or Edit and Reset (Tabular Everywhere Rule). The human's desktop row
  draws the amount first, then the buttons.
- **The refusal sits under the editor, right-aligned in the Billed cell at
  `xl` and full-width below `xl`, keeping its alert icon.** Reason: the
  mockup draws "! Enter an amount…" under the editor. The icon matches the
  mockup and the existing `role="alert"` tests.
- **Below `xl`, a row's `details` stack the line's own detail (cancellation
  note, or "Logged {date}"), then the "Edited · computed" note, with the
  actions aligned to their last line.** Reason: this is the mockup's rows
  generalised to a line that is both cancelled and edited, or a Fee that is
  edited, which the mockup does not draw.
- **Below `xl`, a row being edited keeps the line's own details under the
  editor.** Reason: the mockup's edited row has no details to show. Keeping
  the cancellation note or a Fee's date and description while editing keeps
  the reason for the correction in view.
- **"Logged" is visible below `xl` only, and the "—" for a missing
  description shows at `xl` only.** Reason: at `xl` the column headers name
  the date and description. Below `xl` the headers are hidden, and the
  mockup shows "Logged 12 Oct" and no line for a missing description.
- **The 720px table minimum applies only at `xl` on these two tables, and the
  shared `Table` is not changed.** Reason: every other table keeps its
  sideways scroll by `DESIGN.md`. At `xl` the invoice area is at least 736px.
- **The responsive line structure lives in the invoice card, not as a
  `components/ui` variant.** Reason: it has one user. It can be promoted if a
  second table needs to reflow.
- **On a Country collision, the Country goes on the side list row's second
  line, under the name.** Reason: the name line has no room left beside the
  status, and the collision is rare. This keeps the row's one-line shape for
  everyone else.
- **The side list's accessible names keep today's order (name, Country,
  status, mark); only CSS grid placement moves the parts.** Reason: what a
  screen reader hears doesn't change, and the existing name assertions keep
  proving it.
- **Debt paid: "Columns hidden on phones", "Bare field on mobile" (by
  layout) and "Unused directive". Debt left open: "Pending state duplicated
  outside Button", "Effect with no dependency array", "Two looks for Sent
  back", "Duplicated invoice layout".** Reason: debt is paid when a feature
  touches its module and the fix stays inside it. Each entry left open would
  move shared or out-of-scope code (see `## Solution`).
- **Testing stays on the existing seams.** The component tests pin the
  one-markup contract, the existing e2e specs gain geometry steps at 1280 and
  390, and the existing goldens are recaptured with richer stub lines.
  Reason: each is the highest existing seam that can see its behaviour.
  Layout is visible only in a real browser.
- **Three tickets in a line, not in parallel.** Reason: all three move the
  same four goldens, and the second and third edit the same control.

- **(answered by the human, 2026-10-15: "ok")** When a row has both its month's status and the "Sent back" mark, the status stays on the name's line, on the right, and the mark goes on a second line, right-aligned under it. Reason: every row's status sits in the same place down the column and the name keeps some room.

## Open questions

None.

## Acceptance walkthrough

The `[agent]` steps run against an isolated stack with the backend on the
`demo` profile: its own fresh database, seeded by `DemoDataLoader`, never the
user's compose stack. Logins are in the root `README.md`. Jordan is the demo
Agent with several Contracts. Before step 5, the agent sets up one of
Jordan's draft invoices, through the Manager's and Agent's own screens or the
e2e helpers' API calls, with:

- three Postpaid SIM Cards, one of them cancelled this month and still
  billed;
- one Topup Fee with a description;
- one Fee with no description.

Jordan then edits one SIM and one Fee. The `[human]` steps are played by the
human on their own stack, set up and then left to them, at their desktop and
on their phone.

1. [agent] At 1440×900, as Jordan, open `/agent/client-invoices`. In the "Clients" column, show that every row's Client name and status badge sit on one line (boxes overlapping vertically), the name on the left and the badge against the row's right inset with a gap between, and the column 256px wide. Show from the accessibility snapshot that each link's name is unchanged, e.g. "{Client} Draft". (stories: 1, 5)
2. [agent] As the Manager, rename one of Jordan's Clients to a 60-character name. As Jordan, reload and show the row still one line high, the name cut with "…", its `title` the full name, and its badge whole and inside the row. (stories: 2)
3. [agent] As the Manager, create a Client with the same name as one of Jordan's Clients and a Contract for Jordan. Show the two rows of that name carry the Country on a second line under the name, the status still on line 1, and no other row has a second line except one carrying the mark (step 4). (stories: 3)
4. [agent] Send one Contract's invoice, then as the Manager send back an earlier month's invoice of that Contract, or use the visual stub's Agent session, where that Contract reads "Awaiting approval" plus the mark. Show the row lays out the status and the "Sent back" mark as answered in open question 1, "Sent back" once, with the name still visible. Show that a Contract whose own invoice for this month was sent back shows a single "Sent back" chip on line 1. (stories: 4, 5)
5. [agent] At 1280×800, open the prepared draft. In Postpaid SIM Cards and Fee lines, show each Billed cell has the amount and Edit on one line. Show the edited lines have amount, Edit and Reset on one line with "Edited · computed {amount}" directly under them, and that the amounts' right edges line up down the column across rows with and without Reset. Show the table still has its Number / Billed and Type / Description / Logged / Billed headers. (stories: 6, 7, 9)
6. [agent] Using the keyboard only, Tab to a SIM line's Edit and press Enter. Show the field, Save and Cancel on one line in that cell, in that order, with focus in the field. Type `-5` and press Enter: show the refusal under that line, `-5` still in the field, and focus in it. Press Escape: show focus back on Edit. Edit again to `31.40` and save: show the new amount, the note, and focus on Edit. Show a visible focus ring at each stop. Open the Fleet page in a second tab and capture a SIM number's Edit and its open editor next to the invoice's, for step 13. (stories: 8, 16)
7. [agent] At 1279×800, 1024×768, 768×1024, 390×844 and 375×800, show the lines as one-column rows (stories: 10, 11, 13):
   - SIM rows: the number left and the amount right on line 1; the cancellation note or edited note on the left with Edit (and Reset) on the right below.
   - Fee rows: the type and amount on line 1, then "Logged {date}" with Edit, then the description when present, and nothing (no "—") for the Fee with none.
   - At each width, show `document.documentElement.scrollWidth` equals `window.innerWidth` and each line list's `scrollWidth` equals its `clientWidth`, and every amount and Edit `toBeInViewport({ ratio: 1 })` once scrolled to vertically. (stories: 10, 11, 13)
8. [agent] At 390×844, edit the Fee with no description. Show the type on top, then the field, Save and Cancel on one line, a refused `abc` showing its message below them, then a valid save updating the amount and adding the note under "Logged {date}". Reset it and show the note gone. Give a SIM a 64-character number through the Manager's Fleet correction and show it wraps inside its row with the amount whole on the right and no sideways scroll. (stories: 11, 12, 16, 20)
9. [agent] Send the invoice. At 1440 and at 390, show no Edit or Reset anywhere on the card, the amounts in place, and every edited line's "Edited · computed" note still shown. Open a past month's never-sent draft by its id (or the stub's equivalent) and show the same read-only rows. Open a sent-back invoice from "Sent back to you" and show its own page lays out its lines the same way at 1440 and at 390. (stories: 14, 15, 17)
10. [agent] With `playwright-cli snapshot` at 1440 and at 390, in Chromium and, when installed, WebKit, show each line list as a table with its column headers, one row per line with its cells, and each Edit and Reset inside its row, described by the line's name. Show no line, amount or note appears twice. (stories: 19)
11. [agent] As the Manager, open the same invoice's detail page at 1440 and at 375. Show it renders as on `main` (screenshot comparison against a `main` build) and that `git diff main` shows no change to `client-invoice-detail-view` or `BilledAmount`. (stories: 18)
12. [agent] Show the `DESIGN.md` diff amends Layout (line 166), the Contract side list's Row bullet and Buttons, each in the commit of its visual change. Run the full visual suite and show only the four `agent-client-invoices-*` goldens changed, now showing the lines, the side list rows and both chips. Run the component tests named in `## Testing decisions`, then `docs/agents/sdlc.json`'s `verify` green on an idle machine. (stories: 21, 22)
13. [human] On your desktop, on your own stack, open the Agent's Client Invoices page. Confirm the side list rows read as you asked (name left, chip right, space between) and a long name is cut with "…" and shows in full on hover. Then open a draft and the Fleet page side by side, and confirm the invoice's Edit beside the amount, and its field, Save and Cancel on one line, follow the same design as correcting a SIM number. (stories: 1, 2, 4, 6, 7, 8, 9)
14. [human] On your phone, signed in as an Agent on your stack, open a draft Client Invoice. Confirm the Postpaid SIM Cards and Fee lines match the mockup you confirmed, that you can see every amount and Edit without scrolling sideways, and that editing a line puts the field, Save and Cancel on one line under its name. Then send it, or open a sent one, and confirm there are no buttons and your edited notes are still there. (stories: 10, 11, 12, 13, 14)

## Execution order

Three tickets, in a line: each moves the same four goldens, and the second and
third edit the same control.

1. `side-list-row-one-line`: the side list row is laid out as one line (name left, cut with "…", `title` full; status right), with a second line for the Country on a collision and for the mark per open question 1. Accessible names are unchanged. Includes the `DESIGN.md` Row bullet amendment, the "Unused directive" fix, the `client-invoices-side-list.spec.ts` geometry steps, and the recaptured desktop goldens. Labels: frontend. No dependency. (stories: 1, 2, 3, 4, 5)
2. `invoice-line-actions-inline`: at `xl`, the line amount control in the Fleet correction language. The amount and ghost Edit (and Reset) are on one line, the note under them, aligned amounts; the editor's field, `row` Save and ghost Cancel are on one line, the refusal below. Includes the `DESIGN.md` Buttons sentence, the stub's draft lines for Aurora, the component cases on order and the read-only note, the `edit-client-invoice-lines.spec.ts` steps at 1280, and the recaptured desktop goldens. Labels: frontend. Depends on `side-list-row-one-line`. (stories: 6, 7, 8, 9, 14, 15, 16, 18)
3. `invoice-lines-one-column-below-xl`: below `xl`, the Postpaid SIM Cards and Fee lines reflow as one markup into the mockup's rows (what and amount; details and actions; description), with editing laid out per the mockup. Explicit table roles, no 720px minimum below `xl`, "Logged" and "—" per width, nothing scrolling sideways on either Agent invoice page. Includes the `DESIGN.md` Layout amendment, the "Columns hidden on phones" and "Bare field on mobile" debt, the component one-markup cases, the `edit-client-invoice-lines.spec.ts` steps at 390, and the recaptured mobile goldens. Labels: frontend. Depends on `invoice-line-actions-inline`. (stories: 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22)
