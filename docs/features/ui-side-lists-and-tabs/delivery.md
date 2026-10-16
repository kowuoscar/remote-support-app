# Delivery report — ui-side-lists-and-tabs

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

The five UI changes you asked for on 2026-10-15, with your answers to the
clarifications. Merged as PR #31 (`249fd3e`), four tickets, then two fix
passes (the second authorized by you).

- **Carrier and type on invoice SIM lines (stories 1–6).** Each Postpaid SIM
  line reads its number, then "Verizon · Postpaid" (the SIM's current
  Carrier, muted, wrapping under the number when there is no room). A SIM
  with no Carrier reads "Postpaid" alone. On the Agent's Client Invoice (draft,
  sent, its own page, the one-column rows) and the Manager's invoice page. The
  Tester's Invoices page is unchanged. Ticket: `invoice-sim-line-carrier`.
- **Carriers as a side list, Agent and Manager (stories 7–16).** A
  "Carriers" column at 1280px and wider, the first selected, your pick kept in
  the address. Archived Carriers are always at the bottom, muted, marked
  "Archived", and open read-only. "Add carrier" sits above the column and the
  new Carrier is selected. The Manager's Country filter opens that Country's
  first Carrier. Below 1280px, a Carrier dropdown. Ticket:
  `carriers-side-list`.
- **Fleet side list and tabs (stories 17–30).** The Agent's Fleet has a
  "Clients" column; a Tester whose Client has several Contracts gets a
  "Contracts" column by Country, a one-Contract Tester keeps the chip. A
  "SIM Cards | Smartphones" tab switcher, SIM Cards first, each with its
  count, kept in the address and across Client picks, keyboard-operable.
  Every Fleet action works inside its tab. Ticket: `fleet-side-list-and-tabs`.
- **Fleet table layout, your choice A (stories 29–30).** At 1280px and wider
  the SIM Cards table fits beside the side list with no sideways scroll:
  Retire / Reactivate sit under the status badge (no "Change status" column).
  Plan and Carrier names are cut with "…" at their column's width, full name
  on hover. Below 1280px the table keeps its 720px minimum and scrolls inside
  its card, and numbers stay on one line. Fix passes 1 and 2.
- **The Manager's Contracts as one page (stories 31–43).** "New Contract"
  above a "Clients" column; picking a Client selects its first Agent's
  Contract; the "Agent" dropdown (shown even with one Agent), the Client name
  with Country and currency, the current-month invoice summary with "Open
  invoice", then the tabbed Fleet with Add SIM card / Add smartphone in their
  tabs. Only the selected Contract is read. Old `/manager/contracts/{id}`
  links land on that Contract. Ticket: `manager-contracts-one-page`.
- **A save survives a tab or Client switch.** Correcting a plan, then
  switching tabs or Clients, showed the old plan until a reload. Root cause:
  Next's router dropped the page refresh when the address was written. Fixed
  in fix pass 2, proven by a browser test that failed 10/10 before and passes
  10/10 after (`frontend/tests/e2e/fleet-correction-survives-navigation.spec.ts`).
  The old value still shows for about 0.2s after Save, then the row corrects.

## Acceptance walkthrough

Played by an agent on its own throwaway stack, never on yours.

1. Invoice SIM lines read "Verizon · Postpaid" / "Postpaid" for Agent and Manager, Tester unchanged — played — evidence: `evidence/step-1-run.json`
2. Carriers side list, archived last, selection in the address, add/archive, Country change, phone dropdown — played — evidence: `evidence/step-2-run.json`
3. Agent Fleet: Clients column, tabs with keyboard and address, plan correction and status change, long Plan name, phone (replayed after fix pass 2, with layout A and the save-then-switch race) — played — evidence: `evidence/step-3-replay2-race.json`
4. Tester Fleet with two Contracts and with one — played — evidence: `evidence/step-4-run.json`
5. Manager Contracts on one page — played — evidence: `evidence/step-5-run.json`
6. Carriers, the Agent's Fleet, the Manager's Contracts and the invoice lines on your desktop — yours
7. The same pages on your phone, as an Agent, a Tester with several Contracts and the Manager — yours

## Decisions taken alone

From the spec's `## Decisions taken`:

- The side list is one shared component; every side list breaks at 1280px
  (`xl`), with a dropdown below.
- Selection lives in the address on every side list page (`contract`,
  `carrier`, `tab`); the first row is selected unless the address names one.
- SIM Cards is the first and default tab; each tab shows its count; WAI-ARIA
  tabs with automatic activation, only the selected table rendered.
- `StatusFilterTabs` and the Review Queue filter are not moved onto the new
  tabs.
- The Tester's rows are labelled by Country; a one-Contract Tester keeps the
  chip.
- The "Every carrier in {Country} is archived" empty state is removed; a
  Carrier just created is selected.
- The Manager's Contracts page lists only Clients holding a Contract; the
  Agent picker is a native select; the search box, table and "N of M" count
  are removed; `/manager/contracts/{id}` redirects to `?contract={id}`.
- The invoice line's Carrier is read live, not snapshotted, and goes to every
  caller including the Tester's API.
- Plan and Carrier names are cut at their column's width (your layout A,
  replacing a 192px cap).
- Debt paid: "Two pills" and the stale `basePostpaidSims` comments.
- The orchestrator closed the ticket cut's last critic gap itself; two
  implementers' extra e2e tab steps and one race-fix wait were accepted and
  recorded.
- (after review) `scripts/run-e2e-isolated.sh` forwards its arguments to
  Playwright, so one spec can run through the isolated stack.

## Debt recorded

- `frontend/components/ui/contract-switcher.tsx` · Mysterious Name · it now picks Carriers and Clients too (F13)
- `frontend/lib/selection-address.ts` · Global Data · module-level `lastRefreshAt` (F26)
- `frontend/tests/visual` · Stale goldens · ten Fleet/Contracts goldens predate layout A (F33, F29)
- `frontend/components/fleet/sim-card-plan-control.tsx` · Editing state changed · plan picker no longer 192px minimum (F32)
- `frontend/components/fleet/sim-card-installed-in-control.tsx` · Clipped select · "Not i" while a row is in edit mode (F35)
- `DESIGN.md` · Self-contradicting reason · the Tables line's widths argue against its rule (F34)

## How to undo

```
git revert -m 1 249fd3e
```

Reverts code, not data — this feature has no migration.

## What happens next

`text-size-lint-rule` is next by the roadmap but held at your request until
you finish testing. The epic `side-lists-and-tabs` is closed separately (its
journeys replayed end to end on main), which loads the machine, so it waits
until you are not testing. A veto of a decision above becomes an open
question on a follow-up spec.
