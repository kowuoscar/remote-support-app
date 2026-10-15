# Delivery report — agent-corrects-the-fleet

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

The corrections move from the Tester to the Agent, as you asked at
acceptance: the Manager, on any Contract, and the Agent, on their own
Contracts, correct a mistyped SIM number, a SIM's Postpaid plan, a
Smartphone's model and its owner. The Tester's Fleet is view-only. Merged as
PR #29 (`0ba080e`), three tickets.

- **The serial Edit matches the corrections (stories 21, 27).** The serial
  Edit is now the same quiet button as the correction Edits, named "Edit
  serial, {serial}" for screen readers; this pays F21. A phone with no serial
  now shows "Edit" where it showed "Set serial" (recorded as debt, F4).
  Ticket: `serial-edit-matches-corrections`.
- **The Agent corrects their own Fleet (stories 1–20, 25–28).** An "Edit"
  beside each Smartphone's model and owner, each SIM's number, and each
  Postpaid SIM's plan, on the Agent's Fleet page, beside the status, serial
  and "Installed in" controls. Same rules as the Manager's: audit-logged with
  old and new value, actor and role; owner in both directions, refused while
  a Return naming the phone is open; a plan change moves unsent drafts and
  never a sent, approved or paid invoice. Another Agent's Contract is `403`.
  The plan control points to `/agent/carriers` when the Carrier has no active
  plan. `PRODUCT.md`, `CONTEXT.md` and `docs/journeys.md` say so. Ticket:
  `agent-corrects-own-fleet`.
- **The Tester's Fleet is read-only (stories 22–24).** No Edit anywhere on the
  Tester's Fleet; the four correction endpoints answer a Tester `403`.
  Corrections a Tester made before stay as they are. Ticket:
  `tester-fleet-read-only`.

## Acceptance walkthrough

Played by an agent on an isolated demo stack (`rsa-acc`, torn down after),
never on your stack.

1. Priya corrects a SIM number, kept trimmed after a reload — played — evidence: `evidence/step-1-after-reload.yml`
2. Priya corrects a Postpaid plan; both drafts move by the price difference — played — evidence: `evidence/step-2-plan-control-open.yml`
3. No active plan points to the Carriers page; a Prepaid SIM has no plan control — played — evidence: `evidence/step-3-plan-control-no-active.yml`
4. Jordan's plan correction leaves a sent invoice and a sent Agent Invoice unchanged — played — evidence: `evidence/step-4-client-invoice-after.json`
5. Jordan corrects model and owner; recorded Request models unchanged; Return follows the new owner — played — evidence: `evidence/step-5-recorded-model.txt`
6. Owner change refused on an open Return; another Agent's Contract is `403` — played — evidence: `evidence/step-6-open-return-refused.yml`
7. Empty, too-long, cancel, unchanged and backend-down cases — played — evidence: `evidence/step-7-requests.txt`
8. Dana and Marco see plain text and no control; Tester `PATCH`es are `403` — played — evidence: `evidence/step-8-dana-api.txt`
9. Manager corrections still work; audit lines with role; serial Edit is the quiet button — played — evidence: `evidence/step-9-audit-lines.txt`
10. Keyboard and 375px, copy, goldens, no migration, verify green — played — evidence: `evidence/step-10-verify.txt`
11. Correct the four things as an Agent on your stack — yours
12. Confirm the Tester's Fleet has nothing to edit — yours
13. The Agent's draft bills the corrected plan; the Manager's corrections still work — yours

Noticed while playing, not failures: an archived Carrier plan can't be
restored (there is no restore action, so step 3 used new plans); a sent
invoice's API data reports the SIM's live `monthlyFeeAmount` while its
billed `amount` stays unchanged, which is by design.

## Decisions taken alone

Everything in the spec's `## Decisions taken`. Settled during this delivery:

- **(during implementation)** The Tester tests that checked an Edit no longer
  shows were merged: four became two.
- **(after review)** The six review smells went to debt, none fixed: five
  are small and one (F6) is in lines this feature didn't change.

## Debt recorded

- `smartphone-serial-control.tsx` · Lost visible label · no-serial phone shows "Edit" not "Set serial" (F4)
- `smartphone-serial-control.tsx` · Inline edit not shared · no focus return, no Escape (F5)
- `smartphone-serial-control.tsx` · Off-scale text size · pre-existing text-[12px] (F6)
- `CONTEXT.md` · Repeated clause · "audit-logged" twice (F3)
- `SimCardCorrectionApiTest.java` · Duplicated Code · Return-to-stock helper (F1)
- `SmartphoneCorrectionApiTest.java` · Duplicated Code · other-Agent setup and audit capture (F2)
- Paid: F21, the serial Edit's coloured chip.

## How to undo

```
git revert -m 1 0ba080e
```

Reverts code, not data. No migration was added; values an Agent corrected
stay as corrected, and the audit log keeps the old ones.

## What happens next

`client-invoices-layout-fixes` is next: the side list's status on the name's
line, an invoice line's amount and actions on one line on desktop, and one
column below 1280px. If you veto a decision above, it reopens as a question
on the next spec.
