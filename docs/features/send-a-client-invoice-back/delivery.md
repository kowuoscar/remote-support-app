# Delivery report — send-a-client-invoice-back

<!-- sdlc:template delivery 1 -->

Merged to `main` as `2923a37` (PR #24) on 2026-10-06. The merge gate answered
`ok: true`: no blocking finding, harness passed, `verify` passed.

## What was built

A Manager can send a sent Client Invoice back to its Agent with a reason. The
Agent corrects it and sends it again.

- **The Manager sends back** (`manager-sends-a-client-invoice-back`,
  `manager-send-back-control-on-the-client-invoice-page`).
  - **Where:** a **Send back** button sits beside Approve on a sent invoice. It
    opens a short reason form, and Approve is hidden while the form is open.
  - **What happens:** the invoice becomes a draft again with exactly the lines
    it was sent with. Nothing is recalculated. It leaves the Review Queue.
  - **Who sees the reason:** only the Manager and the Agent, never the Tester,
    the PDF or the logs.
  - **Race with approval:** if a send-back and an approval happen at the same
    moment, exactly one of them wins.
- **The Agent corrects and resends** (`agent-reaches-a-client-invoice-by-its-id`,
  `agent-client-invoice-card-addresses-the-invoice-by-id`,
  `agent-opens-a-client-invoice-on-its-own-page`,
  `agent-lists-the-client-invoices-sent-back-to-them`,
  `agent-sees-sent-back-invoices-on-the-client-invoices-page`).
  - **Finding it:** "Sent back to you" on the Client Invoices page lists each
    sent-back invoice with its reason. **Open** leads to the invoice's own
    page, which shows the notice and names the Client.
  - **Correcting it:** the Agent edits lines, attaches files and resends. A Fee
    of that month logged after the first send appears as a new line.
  - **After resending:** the invoice goes straight back into the Review Queue.
    The Manager sees "Previously sent back on {date}: {reason}".
  - **Old drafts:** a past-month draft that was never sent opens read-only.
  - **New wording:** the Agent's send confirmation now says "Only the Manager
    can send it back to you."
- **Pay:** sending back and resending move nothing on their own. An edit
  follows the line-editing rule:
  - the Agent's own invoice still a draft: it shows the new figure;
  - the Agent's own invoice sent: it moves by the edit's difference;
  - approved or paid: the difference carries over to next month.
- **Prefactor** (`move-carrier-invoice-file-attach-into-the-client-invoice-service`):
  attaching a carrier file now lives in the shared invoice service.

## Acceptance walkthrough

1–18 (send-back, refusals, late Fees, resend, Review Queue, permissions,
Tester and PDF, audit log, pay, Manager form, stale tab, Agent's section and
page, read-only past draft, full suites, keyboard at 1280 and 375px) — played —
evidence: `evidence/step-1.txt` … `evidence/step-18-replay-mobile.txt`
19. Send back a real invoice with the reason you'd really write, and read it as
    the Agent would — yours
20. Read ADR 0005 and ADR 0001's new note — yours
21. Send back a real last-month invoice whose SIM line doesn't match the
    carrier bill, correct it as the Agent, attach the file and resend — yours

Full detail per step is in `acceptance.json`.

## Decisions taken alone

All are in the spec's `## Decisions taken`. Taken during build and review
**(after review)**:

- **Migration V59, not the reserved V57.** `deactivate-a-login` shipped V58
  first, and Flyway refuses a lower version on a database that already has
  V58.
- **Test exceptions.** Four existing tests had to change, because this spec's
  own design forced it. The spec now names them.
- **Ticket wording fix.** The ticket critic's last objection was a wording
  slip with the fix supplied, so I applied it rather than ask you.
- **Two disputed findings, settled by the reviewer who raised them:**
  - **Agent's resend-button focus:** it predates this feature but sits on its
    keyboard path, so it blocked the merge and was fixed.
  - **Dark-mode red button contrast:** it affects the whole app, so it went to
    the debt log.
- **New wording the spec didn't decide:**
  - a sent-back past-month invoice says "that month's Fees — open to your
    corrections";
  - the buttons say "Sending back…" and "Sending…" while busy;
  - the not-found page says "It doesn't exist or isn't one of yours."

  The spec reviewer found that none of these contradicts the spec.
- **Contract switcher width fix.** A long client name had made the switcher
  wider than a phone screen. The fix stopped that without the switcher
  stretching across the page on desktop.

## Debt recorded

Entries tagged `send-a-client-invoice-back` in `docs/tech-debt.md`:
- the Agent and Manager invoice layouts largely duplicate each other;
- the old per-contract routes now have no callers;
- the Manager's page still labels a sent-back invoice "Draft";
- a past-month draft that was never sent is a dead end for the Agent;
- the red button's dark-mode contrast;
- the invoice tables on a phone show only their first column;
- the app has no shared Textarea component.

## How to undo

```
git revert -m 1 2923a37
```

Migration V59 stays applied, and its two nullable columns are then unused.
Invoices sent back while this was live stay as drafts. The Agent can still
send a current-month one from the current-month page, but a past-month one
could then only be fixed in the database.

## What happens next

The next feature in the invoice epic is `invoice-adjustment`. The
`login-lifecycle` epic is ready to close.
