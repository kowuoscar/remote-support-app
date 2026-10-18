---
id: month-bar-sends-the-month
title: Show the Agent's month bar and send the whole month from it
status: ready-for-agent
depends_on: [send-the-month-api]
labels: [frontend]
stories: [1, 2, 3, 7, 8, 9, 10, 11, 12, 13, 14, 24, 34]
---

## Context

`spec.md` `### Frontend` (Types, The month bar), `## Design direction` (the month bar, the sent bar, the `DESIGN.md` entry), `## Testing decisions` items 5 (control, view, helper, fixtures), 6 (the journey proof) and 7. Depends on `send-the-month-api` for the route and for `monthSentAt` / `sendableOnItsOwn` on the invoice response.

What lands: `ClientInvoiceDetail` gains `monthSentAt: string | null` and `sendableOnItsOwn: boolean`; a `clientInvoiceMonth(invoices)` helper (its own file under `frontend/lib/api/`) returns the `unsent | sent | incomplete` state; a BFF route `frontend/app/api/me/client-invoice-months/[month]/send/route.ts` mirrors the backend path through `forwardJson`; the new client component `SendClientInvoiceMonthControl` is first in the `SideListLayout`'s `lead` on the Agent Client Invoices page (above "Sent back to you", so above the Contract Switcher below `xl`), absent when the Agent has no Contract. `DESIGN.md` gains the "Month bar" entry in this ticket's commit (Rule 7 of the issue tracker). The e2e helper `createAgentWithLogin` is added to `helpers.ts` and the journey proof `client-invoice-submission-and-visibility.spec.ts` moves to a fresh Agent.

Not in this ticket (`cards-defer-to-the-month-bar`): the card still shows its own Send here; removing it and the "Sent with the month" line are the next ticket. A card's Send on a never-sent draft is refused by the server once `first-send-only-through-the-month` merges; this ticket's journey proof sends only through the bar. The stub backend's existing `visual-agent-invoices-session` (`INVOICE_STATES`: DRAFT, SENT, APPROVED, sent-back DRAFT) is, under this feature, a SENT month: it keeps its four states and gains `monthSentAt` set on every invoice, with `sendableOnItsOwn` true on the sent-back draft and on Aurora's DRAFT (a late Contract's draft in a sent month, so its card keeps "Send Client Invoice") and false elsewhere. A NEW session, `visual-agent-invoices-unsent-session`, serves two DRAFT invoices (one ready, one missing a file), `monthSentAt` null and `sendableOnItsOwn` false, and is what the month-bar golden captures: `frontend/tests/visual/surfaces.spec.ts` points its `agent-client-invoices` entry at it. Frontend DoD in `docs/agents/frontend.md` applies.

## Acceptance criteria

- [ ] On the Agent's Client Invoices page with an unsent month, the bar reads "{Month Year} · {ready} of {total} ready" with "Send all {total} to Manager" ("Send 1 to Manager" for one Contract); while any invoice is not ready the button is disabled and the line "{n} invoices still need their carrier invoices." ("1 invoice still needs its carrier invoices.") is linked to it by `aria-describedby`; with every invoice ready it is enabled.
- [ ] Clicking the enabled button opens the confirm "Sends all {n} Client Invoices to the Manager and the Clients, and locks their numbers. Only the Manager can send one back to you." with Back (focus returns to the button) and "Confirm send all {n}" ("Confirm send" for one; "Sending…" while pending); confirming posts `{invoiceIds}` of the invoices the page loaded and then the refreshed page shows the sent bar.
- [ ] A refused send shows, under the confirm, `role="alert"`: "An invoice is no longer ready. Reload to see which." (`CARRIER_INVOICES_MISSING`), "Your Contracts changed since this page loaded. Reload and try again." (`MONTH_CHANGED`), "This month was already sent. Reload to see it." (`MONTH_ALREADY_SENT`), "The month has changed. Reload to see this month's invoices." (`NOT_THE_CURRENT_MONTH`), "Couldn't send. Try again." for any other code, and the existing "Couldn't reach the server…" line on a network failure.
- [ ] When any loaded invoice has `monthSentAt` the bar reads "{Month Year} · Sent to Manager on {date}" with a success check and no button, and keeps that date when a sent-back or late invoice waits on its own Send.
- [ ] When an invoice failed to load the bar reads "{Month Year} · Couldn't load every invoice." with the button disabled and "Reload the page to send the month." as its note; an Agent with no Contract sees no bar and the existing empty state.
- [ ] At 390px and at 1024px the bar sits above the Contract Switcher and wraps without horizontal scroll. The `agent-client-invoices-*` goldens, captured from `visual-agent-invoices-unsent-session`, show the bar "1 of 2 ready" disabled with its note; goldens captured from the four-state session now show the sent bar (`client-invoice-attach-dialog-open-*`, `client-invoice-files-paused-*`, `client-invoice-remove-confirm-open-*` move only where their capture includes the bar); no other golden moves.
- [ ] End to end, a fresh Agent with a login holding two Contracts (one with a Postpaid SIM, one with nothing) sees "1 of 2 ready" and the disabled button, attaches the carrier invoice, sends the month through the bar and sees "Sent to Manager on {today}"; the Tester then sees the invoice and the Manager reviews it, as the journey proof did before.

## Tests

Seams per spec `## Testing decisions` items 5, 6, 7 (Vitest + Testing Library; Playwright e2e on the isolated stack; the visual stub).

- **`send-client-invoice-month-control.test.tsx` (new):** each state's text; the disabled button and its `aria-describedby` note; one-Contract wording; the confirm's focus on open and on Back; the request's URL and `{invoiceIds}` body; each error code's text and the network line; pending state (`aria-busy`, repeat activation ignored).
- **`client-invoice-month.test.ts` (new, table-driven):** unsent counts (`ready` counts `DRAFT` invoices with no missing Postpaid line), sent (any `monthSentAt`), incomplete (a null invoice), the month label from `billingMonth`.
- **`client-invoices-view.test.tsx` (existing, extended):** the bar from the loaded invoices (unsent, sent, incomplete, absent with no Contracts), its place before "Sent back to you".
- **E2E `client-invoice-submission-and-visibility.spec.ts` (journey proof, rewritten) with `createAgentWithLogin`** (prior art `create-agent-with-login.spec.ts` and `readRevealedPasswordAndClose`).
- **Visual:** the Agent page goldens are recaptured (delete the stored files first, per the note in `playwright.config.ts`).

## Regression

At risk: the Agent Client Invoices page layout and its goldens, the `lead` area of `SideListLayout`, `SendClientInvoiceControl` (untouched here), and every e2e spec that opens the Agent's Client Invoices page (the bar must not break their locators: "Send Client Invoice" and "Confirm send" stay unique outside the confirm of a click on the bar).

Existing tests expected to change, each with the reason:

- `frontend/components/agent/client-invoices-view.test.tsx`: fixtures gain `monthSentAt` and `sendableOnItsOwn`, and it gains the bar cases. Every pre-existing case keeps passing unmodified.
- `frontend/components/manager/contracts-view.test.tsx` and `frontend/components/manager/client-invoice-detail-view.test.tsx`: their `ClientInvoiceDetail` fixtures gain the two new required fields (type check). No case changes.
- `frontend/tests/visual/stub-backend.mjs`: the new unsent session and the two fields on every served invoice, as described in Context. Goldens named above are recaptured in this commit.
- `frontend/tests/visual/surfaces.spec.ts`: the `agent-client-invoices` entry's session becomes `visual-agent-invoices-unsent-session`; nothing else changes (its failing-session case at line ~295 reads the four-state session and is unmodified in code).
- Visual specs that read the four-state session and so may see the sent bar in their captures: `client-invoice-files.spec.ts` and `client-invoices-side-list-rows.spec.ts` (bounding-box cases, no goldens). Neither file's code changes in this ticket; any golden of theirs whose capture includes the bar is recaptured and named in the result.
- `frontend/tests/e2e/helpers.ts`: gains `createAgentWithLogin`. Additive; every existing helper keeps its behaviour.
- `frontend/tests/e2e/client-invoice-submission-and-visibility.spec.ts`: the journey proof moves to a fresh Agent and sends through the bar (the spec's own stated change); its Tester and Manager steps keep their assertions.
- Goldens `agent-client-invoices-{desktop,mobile}-{light,dark}.png` and, only where the capture includes the bar, `client-invoice-attach-dialog-open-*`, `client-invoice-files-paused-*`, `client-invoice-remove-confirm-open-*`.
- `frontend/lib/api/types.test.tsx` and every other e2e spec stay unedited; name any forced edit in the result.

## Observability

N/A: a presentational control over an existing server action; the server writes the audit line (`send-the-month-api`) and the control logs nothing.
