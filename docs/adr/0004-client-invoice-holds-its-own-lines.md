# 4. A Client Invoice holds its own lines, editable while draft, and the Agent's pay follows them until approval

Status: accepted
Date: 2026-10-02
Feature: edit-client-invoice-lines
Ticket: serve-client-invoices-from-stored-lines, local-support-fees-follow-billed-client-invoice-lines

This ADR is written in two parts: the lines part (decided with the ticket that stores and serves
the lines) and the pay part (the Local Support Fees rule and its lifecycle, amending ADR 0002 and
ADR 0003, decided with `local-support-fees-follow-billed-client-invoice-lines`).

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

## Pay: Local Support Fees follow billed lines until the Agent Invoice is approved

The human's words (2026-10-01): "If an edit occur before approval it should update the agent own
monthly pay otherwise its a carry-over." This amends ADR 0002, which had the Agent Invoice ignore
Client Invoices.

**The rule.** Local Support Fees stay a sum over the Agent's Contracts for the month. Each
Contract's term is every line its Client Invoice bills, at its billed amount, plus anything of that
month the Client Invoice does not bill, at its computed amount:

- no Client Invoice: the computation (`ContractAmountService.totalForMonth`, as before);
- an invoice whose lines are not stored (never sent): its billed total, which is the computation
  with the Agent's edits;
- an invoice with stored lines: its billed total (so, on a sent-back draft, including its
  pre-filled late-Fee lines), plus the computed amount of every Fee of the month with no `FEE`
  line, plus, unless it has a `BASE_AMOUNT` line, the computed amount of every Postpaid SIM billing
  that month with no `POSTPAID_SIM` line.

The second part is the human's answer of 2026-10-02: a Fee logged after the Client Invoice was sent
counts at its logged amount. It keeps what ADR 0002 protects, a Fee the Agent fronted still reaches
their pay that month. A late Fee counts the same unbilled (at its amount) and as a pre-filled line
of a sent-back draft (at the same amount), so a send-back moves no pay.

The rule lives in one place, the new `ContractAmountService.payableAmountForMonth`, which reuses
`ClientInvoiceService.lines`, so the bill and the pay can never resolve a line two ways.
`AgentInvoiceService` calls it for the draft read and for the send snapshot.

**The Agent Invoice's lifecycle.** When a Client Invoice line is edited:

| Agent Invoice | Effect of the edit |
|---|---|
| `DRAFT` | Nothing written. It computes live and reads the new figure. |
| `SENT` | Its Local Support Fees move by exactly the edit's difference (new billed minus old billed, a pre-filled line's old amount being its pre-filled amount), in the edit's transaction, with an `agentInvoiceLocalSupportFeesFollowed` audit line. The human's answer of 2026-10-02. |
| `APPROVED`, `PAID` | Nothing moves. The difference is a carry-over for `invoice-adjustment`. |

The Agent Invoice's send snapshot freezes the rule's figure at that moment. A sent Agent Invoice
moves by the difference, not by a recomputation, so a Fee logged after its send does not slip in
and ADR 0003's freeze holds for everything but the edit. The Manager's override never touches Local
Support Fees, so the two never write the same column.

**Locks.** `AgentInvoiceRepository` gains locking finders. The Agent Invoice's send, approve,
mark-paid and override re-read the invoice under that lock inside `@Transactional` service methods.
`ClientInvoiceService.editLine` takes the Client Invoice's row lock first, writes the line, then
locks the Agent's invoice for that month, always, and holds both to commit. The order is always
Client Invoice then Agent Invoice and no Agent Invoice operation takes a Client Invoice lock, so
they cannot deadlock. An edit before an Agent Invoice send is computed into it; an edit after sees
`SENT` and moves it; an edit against an approve either lands first or sees `APPROVED` and moves
nothing, and the approve cannot overwrite the moved figure with a stale one.

**Alternatives rejected.** Keeping ADR 0002 as it was (rejected by the human). "Billed total only"
(drops a Fee logged after the Client Invoice's send, the case ADR 0002 was written for).
Recomputing a sent Agent Invoice instead of moving it by the difference (would let later Fees in).
Blocking the Agent Invoice's send until the Client Invoices are approved (couples the two
documents' timing; not asked for).

## Consequences of the pay rule

- An Agent Invoice still a draft, for a month whose Client Invoice was already sent, where the Fleet
  changed after that send, now counts what the Client Invoice billed (a SIM removed after the send
  still counts; a legacy invoice's frozen base amount is used) instead of the live Fleet. This is
  the human's rule applied.
- Client Invoice and Agent Invoice figures for a Contract and month agree on everything the Client
  Invoice bills; they differ only by what it does not bill.
