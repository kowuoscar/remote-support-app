# Delivery report — agent-sends-the-month

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

The first feature of the epic `client-invoices-sent-by-the-month`. Merged as
PR #35 (`f815851`): five tickets, one fix pass and a short second one (taken
under your "implements this in full without stop").

- **The month send (stories 4–6, 25–32).** `POST /api/me/client-invoice-months/{YYYY-MM}/send`
  sends every current-month Client Invoice of the Agent's Contracts together,
  all or nothing. It first creates any draft nobody had opened, so the month is
  complete; an invoice with nothing to bill goes at $0. It refuses with a coded
  409 and writes nothing when an invoice is not ready (naming which), when the
  Contracts changed since the page loaded, when the month was already sent, or
  for another month. Every invoice gets one shared send time and one audit
  line records the month send. A new column `first_sent_at` (migration V63,
  backfilled) marks the month as sent; there is no new month record.
- **First send only through the month (stories 25, 30, 31, 33, 34).** A
  never-sent invoice's own Send answers `MONTH_NOT_SENT` while the month is
  unsent. A sent-back invoice, or the invoice of a Contract added after the
  month was sent, still sends on its own. A month that already held an
  invoice sent before this update counts as sent and finishes one by one.
- **The month bar (stories 1–3, 7–14, 24).** At the top of the Agent's Client
  Invoices page: "October 2026 · 1 of 2 ready · Send all 2 to Manager",
  disabled with its reason until every invoice is ready, then a two-step
  confirm, then "Sent to Manager on {date}".
- **Cards defer to the bar (stories 15–20).** While the month is unsent a card
  has no Send and reads "Sent with the month, from the bar above." A sent-back
  card shows "Resend Client Invoice"; a late Contract's card keeps "Send Client
  Invoice".
- **Side list marks (stories 21–23, 35).** Each draft row reads "✓ Ready" or
  "{n} files missing" on its second line.
- **Demo data:** a fresh demo database sends Jordan's month with a file on
  every Postpaid SIM line and leaves Priya's unsent.

## Acceptance walkthrough

Played by agents on their own throwaway stacks, never on yours.

1. `verify` green, goldens as listed — played — evidence: `evidence/step-1.txt`
2. A fresh Agent's bar, rows and cards; an Agent with no Contract sees no bar — played — evidence: `evidence/step-2.txt`
3. Every refusal, nothing written — played — evidence: `evidence/step-3.txt`
4. Attach and remove move the marks and the count — played — evidence: `evidence/step-4.txt`
5. Contracts changed since the page loaded; an empty month sent at $0 — played — evidence: `evidence/step-5.txt`
6. Confirm and send: one shared time, audit line — played — evidence: `evidence/step-6.txt`
7. Already sent; two sends at once give one 200 — played — evidence: `evidence/step-7.txt`
8. One Contract; an invoice no longer ready at confirm — played — evidence: `evidence/step-8.txt`
9. Testers see the sent invoices; the Manager sees four rows — played (replayed after the fix pass) — evidence: `evidence/step-9.txt`
10. Send back one, resend it alone, the bar's date unchanged — played — evidence: `evidence/step-10.txt`
11. A Contract added later sends on its own — played — evidence: `evidence/step-11.txt`
12. Bar at 390px and 1024px, no sideways scroll — played — evidence: `evidence/step-12.txt`
13. Fresh demo database; storage down at start — played — evidence: `evidence/step-13.txt`
14. Migration test, CONTEXT.md and DESIGN.md — played — evidence: `evidence/step-14.txt`
15. On `rsa-prod` — yours

## Decisions taken alone

From the spec's `## Decisions taken`, besides what you settled (resend on its
own, UI A1, advance approval):

- The month is sent once any of its invoices has been sent (`first_sent_at`),
  so a month begun one by one before the update finishes one by one.
- No new month record; the page builds the bar from the invoices it loads.
- The send carries the invoice ids the page showed, so a Contract added
  meanwhile refuses with "Your Contracts changed".
- The single-invoice routes stay, narrowed by `MONTH_NOT_SENT`.
- (at ticket cut) The isolated e2e stack sends Jordan's month once in its
  global setup, so every existing spec's Contract is a late one.
- (at review) A pushed goldens-only commit is answered by an empty follow-up
  commit naming its code, as on real-manager-dashboard.

## Debt recorded

- `docs/agents/frontend.md` · what to do with a pushed goldens-only commit is
  unwritten (F3)
- `docs/agents/coding-standards.md` · Backend 3 doesn't name the
  non-transactional get-or-create exception

All other review findings were fixed.

## How to undo

```
git revert -m 1 f815851
```

The revert does not undo migration V63 (`first_sent_at` on
`client_invoices`). The column is nullable and harmless to the old code.

## What happens next

`manager-reviews-by-agent`, the epic's second feature, starts now: an Agents
side list on the Manager's Invoices page, each Agent's month as a list,
"Approve the N left to review" and "Next invoice →". Your `rsa-prod` is
rebuilt with the month send. Your October counts as sent already, because
invoices were sent one by one before the update; use an Agent with nothing
sent this month, or November, to see the bar unsent.
