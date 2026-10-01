---
id: approve-real-client-dashboard
type: approval
status: answered
blocks: [real-client-dashboard]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for `real-client-dashboard`?
`docs/features/real-client-dashboard/spec.md`.

In plain terms: when a Client's Tester signs in, their home page stops
showing made-up data. It shows their own Client's real name, their real
devices and SIMs, their real Requests, and the latest invoice for each
Contract.

These are choices made without you. Approving accepts them:

1. **The Tester is shown by their login (their email).** Testers have no
   separate display name in the app; every other Tester page already does
   this.
2. **"Active Fleet" counts** phones that are active or in repair, plus active
   SIM cards. Retired ones don't count.
3. **"Open Requests" counts** Requests waiting for Manager approval,
   submitted, or in progress. That was your answer.
4. **The invoice card shows each Contract's latest sent or approved
   invoice**, usually last month's. A draft is never shown to a Tester. To do
   that, two new read-only lookups are added that only return the Tester's
   own Client.
5. **The "View all" link is renamed "Open Invoices"**, because that page only
   shows the current month.
6. **The "Demo data — synthetic" footer disappears** from every page except
   the Manager dashboard, which is still demo until its own feature.
7. **Two named font sizes are added first**, as you approved for the styling
   rule. Nothing looks different.

## Recommendation

Approve. There's no database change, and every choice above is cheap to
change. Three walkthrough steps will be yours at the end.

## Blocks

`real-client-dashboard` stays a `draft`, and no tickets are cut until you
answer.

## Meanwhile

`manager-resets-a-password` is being ticketed and built.

## Answer

Approved (ok), human, 2026-10-01.
