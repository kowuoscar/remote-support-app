---
id: approve-ui-side-lists-and-tabs
type: approval
status: open
blocks: [ui-side-lists-and-tabs]
created: 2026-10-15
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for your five UI fixes?
`docs/features/ui-side-lists-and-tabs/spec.md`. Every point you confirmed
today is built in as you said it: "Free · Postpaid" on invoice lines; a
Carriers side list; the Fleet side list and "SIM Cards | Smartphones" tabs;
long Plan names cut with "…"; and the Manager's Contracts page as one page,
with the Agent dropdown, the invoice summary and the tabbed Fleet.

These are choices made without you; approving accepts them:
1. **Carriers:** archived Carriers are always in the list, at the bottom,
   marked "Archived". "Show archived" now only shows the selected Carrier's
   archived Plans and Topup Options.
2. **Manager's Contracts:** the search box and the table go, and the Clients
   side list replaces them. "{n} in this tenant" stays. The old address of a
   Contract's page now opens this page with that Contract selected.
3. **Contracts page buttons:** "Add SIM card" and "Add smartphone" become
   plain buttons instead of filled pills. Only one primary button per page,
   as the design rules want.
4. **Tester with several Contracts:** the side list rows show the Country,
   since every row is the same Client.
5. **Tabs:** SIM Cards is shown first, and each tab shows its count, e.g.
   "SIM Cards 12".
6. **Kept on reload:** the selected Carrier, Client and tab stay in the
   page address, so a reload or a shared link opens the same view.
7. **Invoice Carrier:** the line shows the SIM's current Carrier. A Tester
   who opens the same data sees it too; they already see Carriers on their
   Fleet.

How it's built: one PR, four tickets. The invoice Carrier line is built
first and alone. Then the shared side list on Carriers, then Fleet, then
Contracts. Your steps are 6 and 7 of the walkthrough, on your desktop and
your phone.

## Recommendation

Approve. Read the user stories, the non-goals, the decisions taken and the
walkthrough; the solution is yours to skip.

## Blocks

`ui-side-lists-and-tabs`.

## Meanwhile

`text-size-lint-rule` is held at your request until you've tested these
fixes.

## Answer
