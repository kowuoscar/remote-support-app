# Delivery report — correct-a-fleet-unit

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

A Manager, on any Contract, and any Tester, on their own Client's Contracts,
can correct a mistyped unit already in the Fleet. Merged as PR #27
(`bc76fe2`), five tickets.

- **Smartphone model and owner (stories 3, 4, 7, 8, 11, 22–24).** Inline
  Edit → Save/Cancel on the Manager's Contract page and the Tester's Fleet
  page. An owner change is refused while a Return naming the phone is open
  (`409 SMARTPHONE_ON_OPEN_RETURN`); a Return raised afterwards follows the
  corrected owner. Tickets: `correct-a-smartphone-model`,
  `correct-a-smartphone-owner`.
- **SIM number (stories 1, 5).** Trimmed, at most 64 characters, shown live
  everywhere it appears, including a sent invoice's SIM line. No duplicate
  check, as at creation (recorded as debt). Ticket: `correct-a-sim-card-number`.
- **Postpaid plan (stories 2, 6, 14–20).** Only the SIM's Carrier's active
  plans; the fee shows before Save and is re-copied from the plan's price.
  An unsent draft Client Invoice and the draft Agent Invoice move by the price
  difference; stored, sent, sent-back or approved Client Invoices and sent,
  approved or paid Agent Invoices do not. Ticket: `correct-a-postpaid-plan`.
- **Everything else.** One audit event per changed field with old and new
  value, actor and role; an unchanged save is a no-op. `PRODUCT.md`'s Tester
  line now says a Tester corrects these four things; `CONTEXT.md` gained
  Correction and narrowed Tester, Owner and Carrier. The four existing Fleet
  route handlers moved to `forwardJson` first
  (`fleet-route-handlers-forward-json`).

## Acceptance walkthrough

Steps 1–12 were played by an agent on an isolated demo stack, all passed,
evidence under `evidence/`. Steps 13 and 14 are yours (see the inbox item).

## Decisions taken alone

Everything in the spec's `## Decisions taken`. Settled during this delivery:

- **(during implementation)** Saving the owner already held is a no-op even
  while a Return is open.
- **(during implementation)** A SIM whose plan is archived opens the plan
  control with nothing chosen; Save waits for a pick.
- **(during implementation)** A Tester's plan control has no Carriers-page
  pointer, since a Tester has no Carriers page.
- **(after review)** A request with no owner is refused as `400 OWNER_INVALID`
  ("Choose Client or Company."); the spec listed no code for it.
- **(after review)** Each correction Edit button keeps the visible text "Edit"
  but is named with its unit for screen readers ("Edit number, +44…",
  "Edit owner of Pixel 8", …).
- **(after review)** The correction Edit buttons are the quiet `ghost`
  variant, not the soft-indigo chip, so the table isn't a wall of colour.
- **(by you, at the gate)** F20 (two pill buttons on the Contract page,
  pre-existing) and F21 (the older serial Edit is still the coloured chip)
  went to debt instead of being fixed.

## Debt recorded

- `smartphone-serial-control.tsx` · Inconsistent Edit · F21
- `contract-fleet-view.tsx` · Two pills · F20
- `smartphone-owner-control.tsx` · Repeated name · owner Edit names repeat for same-model phones
- `CorrectionErrors.java` · Divergent Change · older endpoints' 400 body is now a ProblemDetail (no screen reads it)
- `SmartphoneController.java` · Duplicated Code · F9
- `SmartphoneCorrectionService.java` · Duplicated Code · F10
- `InvalidCorrectionException.java` · Primitive Obsession · F11
- `SimCardCorrectionService.java` · Middle Man · F12

## How to undo

```
git revert -m 1 bc76fe2
```

Reverts code, not data. No migration was added; corrected values stay as
corrected, and the audit log keeps the old ones.

## What happens next

`client-invoices-side-list` is next, built the same way.
