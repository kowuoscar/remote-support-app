---
id: cards-defer-to-the-month-bar
title: Take the first Send off the Client Invoice cards and keep Resend and the late Contract's Send
status: ready-for-agent
depends_on: [month-bar-sends-the-month, first-send-only-through-the-month]
labels: [frontend]
stories: [15, 16, 17, 18, 19, 20, 34]
---

## Context

`spec.md` `**The card** (AgentClientInvoiceCard)` under `### Frontend`, `## Design direction` (the card), `## Testing decisions` items 5 (`client-invoices-view`, `send-client-invoice-control`) and 6 (every other spec keeps Jordan; the new `agent-sends-the-month.spec.ts`). Depends on `month-bar-sends-the-month` (the bar the card's line points at, and the fields) and on `first-send-only-through-the-month` (the server rule that makes a never-sent card's Send wrong, and Jordan's month sent in the e2e database by the global setup).

What lands in `AgentClientInvoiceCard` on an editable draft: `sendableOnItsOwn` true shows `SendClientInvoiceControl`, labelled "Resend Client Invoice" / "Confirm resend" when the draft is sent back and "Send Client Invoice" / "Confirm send" otherwise (a late Contract), still gated by its own carrier invoices; `sendableOnItsOwn` false shows no Send, keeps the "{n} Postpaid SIMs still need their carrier invoice." note as its own line under Attach and adds under it "Sent with the month, from the bar above." (`text-label-sm`, `ink-mute`). `SendClientInvoiceControl` reads `MONTH_NOT_SENT` as "Send this month from the bar above. Reload to see it." The by-id page uses the same card and shows no bar. Attach, Remove and line edits are unchanged.

The e2e side: Jordan's month is sent by the global setup, so every existing spec's new Contract is a late one and keeps "Send Client Invoice"; specs that resend after a send-back click "Resend Client Invoice"; `attachCarrierInvoicesForEveryPostpaidSim` waits for the card, not for the Send button; `helpers.ts` documents that Jordan's month is sent before the specs run. A new `agent-sends-the-month.spec.ts` (fresh Agent, same pattern as the journey proof) proves a send-back is resent alone with the bar's date unchanged and a Contract added after the send is sent alone. Frontend DoD in `docs/agents/frontend.md` applies.

## Acceptance criteria

- [ ] While the month is unsent, no card shows a Send; each editable never-sent card shows its missing-files note and, under it, "Sent with the month, from the bar above."; the by-id page's card does the same and shows no bar.
- [ ] A sent-back draft's card shows "Resend Client Invoice" (confirm "Confirm resend"), disabled with its note while a Postpaid SIM lacks its file and enabled otherwise; confirming resends that invoice alone, and the bar still reads "Sent to Manager on {same date}".
- [ ] A Contract added after the month was sent shows "Send Client Invoice" (confirm "Confirm send") gated by its own carrier invoices, and sends that invoice alone.
- [ ] A `MONTH_NOT_SENT` refusal from a card's Send reads "Send this month from the bar above. Reload to see it."; the other texts of the control are unchanged.
- [ ] Attach, Remove and line edits on a draft work as before; the `agent-client-invoices-*` goldens show the card without Send, and no golden outside the set named in `month-bar-sends-the-month` moves.
- [ ] The whole existing e2e suite passes: specs that resend after a send-back click "Resend Client Invoice", every other spec's late Contract keeps "Send Client Invoice", and the new `agent-sends-the-month.spec.ts` passes.

## Tests

Seams per spec `## Testing decisions` items 5, 6, 7.

- **`client-invoices-view.test.tsx` (existing, extended):** no Send on a card while unsent, with the missing-files note and "Sent with the month, from the bar above."; "Resend Client Invoice" on a sent-back card; "Send Client Invoice" on a late one; Attach and Remove still offered.
- **`send-client-invoice-control.test.tsx` (existing, extended):** the Resend wording ("Resend Client Invoice", "Confirm resend"), the late wording, the `MONTH_NOT_SENT` text, and the unchanged `CARRIER_INVOICES_MISSING` text.
- **`agent-sends-the-month.spec.ts` (new e2e):** fresh Agent; send-back resent alone from its card with the bar's date unchanged; a Contract added after the send sent alone; no Send on any card before the month is sent.
- **Visual:** the Agent page goldens recaptured; the stub's unsent session already serves `sendableOnItsOwn` false (previous ticket), and the four-state session serves it true on the sent-back draft (the Cedar card), so that card shows "Resend Client Invoice" in `client-invoice-files.spec.ts`-style captures.

## Regression

At risk: the Agent's Client Invoice card (Attach, Remove, line edit, status note focus after a send), the send-back flow, every e2e spec that sends a Client Invoice, and the Agent page goldens.

Existing tests expected to change, each with the reason:

- `frontend/components/agent/client-invoices-view.test.tsx`: gains the card cases above. Every pre-existing case keeps passing unmodified except those that clicked a never-sent card's Send, which now set `sendableOnItsOwn` true on their fixture (a late Contract) and assert the same behaviour.
- `frontend/components/agent/send-client-invoice-control.test.tsx`: gains the Resend wording and `MONTH_NOT_SENT` cases. Every pre-existing case keeps passing unmodified.
- `frontend/tests/e2e/helpers.ts`: `attachCarrierInvoicesForEveryPostpaidSim` waits for the card, not the Send button; the doc comment records that Jordan's month is sent before the specs run.
- `frontend/tests/e2e/send-a-client-invoice-back.spec.ts`: its resend click becomes "Resend Client Invoice" and its confirm click "Confirm resend" (line ~141) (the first send is a late Contract's and keeps its label).
- `frontend/tests/e2e/client-invoices-side-list.spec.ts`: its two resends after a send-back click "Resend Client Invoice" and then "Confirm resend" (lines ~235, ~362).
- `frontend/tests/e2e/client-invoice-submission-and-visibility.spec.ts`: gains an assertion that no card shows a Send while the fresh Agent's month is unsent; its other assertions are unchanged.
- Goldens `agent-client-invoices-*` and, where their capture includes the card header or its note, `client-invoice-attach-dialog-open-*`, `client-invoice-files-paused-*`, `client-invoice-remove-confirm-open-*`, recaptured.
- The other e2e specs that send a Jordan Contract (`client-dashboard`, `client-invoice-generation`, `edit-client-invoice-lines`, `manager-dashboard`, `manager-invoice-review-queue`, `agent-invoice-submission-and-approval`) stay unedited: their Contracts are late, so the card's label is unchanged. If one needs an edit anyway, name it in the result.

## Observability

N/A: wording and visibility of existing controls; the server's audit and coded refusals are unchanged and nothing new is logged.
