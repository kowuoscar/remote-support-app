# 4. A Client Invoice holds its own lines, editable while draft, and the Agent's pay follows them until approval

Status: accepted
Date: 2026-10-02
Feature: edit-client-invoice-lines
Ticket: serve-client-invoices-from-stored-lines

This ADR is written in two parts. The lines part is here, with the ticket that stores and serves
the lines. The pay part (the Local Support Fees rule and its lifecycle, amending ADR 0002 and
ADR 0003) is added by `local-support-fees-follow-billed-client-invoice-lines`.

## Context

A Client Invoice was a base amount plus that month's Fees, computed live while draft and frozen at
send (ADR 0001). The computation is not what the Client is really billed: a Postpaid SIM's monthly
fee is fixed at provisioning, but the carrier's real bill rises with usage, and only the Agent
holds that figure. The human settled on 2026-10-01 that every line is editable and the computation
only pre-fills them. Editing means an invoice's amount can differ from its Fee's amount, so ADR
0001's freeze of Fee membership is no longer enough: the amounts themselves have to be stored.

## Decision

**A Client Invoice holds its own lines.** One `ClientInvoiceLine` row per line (`POSTPAID_SIM`,
`FEE`, or `BASE_AMOUNT` for an invoice sent before this change), with the billed `amount` and the
`computedAmount` the computation gave. A line is edited when the two differ. `ClientInvoice` gains
`linesStored`, the single rule every read follows:

- `linesStored` false (a draft never sent): the computation (a line per billing Postpaid SIM and per
  Fee of the month), with any stored row for the same SIM or Fee overriding its amount.
- `linesStored` true, `SENT` or `APPROVED`: the stored rows, exactly. Nothing is computed.
- `linesStored` true, `DRAFT` (an invoice sent back): the stored rows, exactly, never recomputed,
  plus one pre-filled line per Fee of the month that has no row. No line is added for a Postpaid SIM
  (the human's answer of 2026-10-02). A pre-filled line has no row until it is sent or edited.

**The send stores the lines.** One `@Transactional` operation in `ClientInvoiceService`, on the
invoice re-read under a row lock, so two concurrent sends leave one `SENT` invoice and one 409. It
stores a row for every line the invoice shows that has none and sets `linesStored`; on a draft that
already has stored lines it leaves every existing row alone, so the only rows a resend writes are
those of late-Fee lines, and no unique constraint can trip. It stops writing `snapshotBaseAmount`
and the Fee snapshot rows.

**ADR 0001's reason still holds.** From `sent` onward no figure moves; a Fee logged while the
invoice is `SENT` or `APPROVED` does not appear on it. The freeze now stores lines with amounts,
not Fee membership. `snapshot_base_amount` and `client_invoice_fee_snapshots` keep their data and
are no longer written or read.

## Consequences

- The V56 migration backfills every `SENT` and `APPROVED` invoice with one `BASE_AMOUNT` line plus a
  `FEE` line per snapshot row, so each reads the same lines, amounts and totals as before. Its base
  amount has no per-SIM breakdown, which it never had once sent.
- After the first edit, the stored lines are the only record of what an invoice billed. Going back
  to the computed model would lose every edited amount. That is the feature the human asked for.
- Alternative rejected: keeping the computation as the truth and storing only the edits. A sent
  invoice would then change when a Fee or SIM changed, which is what ADR 0001 forbids.
