# Delivery report — client-invoices-side-list

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

On a desktop, the Agent picks a Contract's Client Invoice from a column of
Clients beside the navigation, instead of a dropdown, as in your sketch.
Merged as PR #28 (`a1048a4`), five tickets.

- **The column (stories 1–9, 16, 17, 19, 21–23, 26).** At 1280px and wider,
  a 256px "Clients" column sits against the rail, stays under the top bar and
  scrolls on its own. One row per Contract, by Client name in alphabetical
  order, with the Country only when two Clients share a name. A click swaps
  the invoice in place and scrolls back to its top. Narrower screens keep
  today's dropdown, in the same order. `DESIGN.md`'s "dropdown, never a tab
  set" rule is amended for this page. Ticket: `contract-side-list-on-desktop`.
- **Status on every row (stories 18, 27, 28).** Each row shows this month's
  invoice status (Draft, Awaiting approval, Approved, Sent back); a row whose
  invoice failed to load shows none and explains when picked. Ticket:
  `side-list-row-status`.
- **The selected Client is kept in the address (stories 10–12, 20).**
  `?contract=<id>`: a reload, a shared link, or the invoice page's breadcrumb
  return to the same Client; clicks add no Back-button steps. Ticket:
  `selected-client-in-the-address`.
- **"Sent back" (stories 13–15, 24).** The "Sent back to you" list is first in
  the invoice area; a Client with a sent-back invoice of an earlier month gets
  a "Sent back" mark with an alert icon beside its status, never twice.
  Ticket: `sent-back-in-the-side-list`.
- **Long names on a phone (story 25).** The one-Contract chip cuts a long name
  with "…" and shows it in full on hover. Ticket: `switcher-chip-truncation`.

## Acceptance walkthrough

Steps 1–14 were played by an agent on an isolated demo stack and the stub
backend, all passed, evidence under `evidence/`. Steps 15 and 16 are yours
(see the inbox item).

## Decisions taken alone

Everything in the spec's `## Decisions taken`. Settled during this delivery:

- **(after review)** The "Sent back" mark carries an alert icon so it no
  longer looks like "Awaiting approval". A row whose own status is "Sent
  back" keeps the plain status badge, as on the invoice card.
- **(after review)** The test screenshots of this page use their own stub
  session, so the Agent dashboard's screenshots don't move.
- The requested test of the invoice page's fallback title wasn't written:
  that title lives in the page, not the view, and the spec didn't require it.

## Debt recorded

- `contract-side-list.tsx` · Two looks for Sent back · own-status "Sent back" keeps the dot
- `contract-side-list.tsx` · Unused directive · one eslint-disable warning
- Paid and removed: the sent-back table hiding the Country, the invoice page's silent title fallback, the one-Contract chip overflowing a phone.

## How to undo

```
git revert -m 1 a1048a4
```

Frontend only; no data or schema change.

## What happens next

Both features you asked for while testing are delivered. Implementation is
otherwise paused; the seven answered inbox items wait for `/sdlc`.
