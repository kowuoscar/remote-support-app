# 5. A Manager may send a Client Invoice back to draft

Status: accepted
Date: 2026-10-05
Feature: send-a-client-invoice-back
Ticket: manager-sends-a-client-invoice-back

## Context

A Client Invoice's lifecycle only moved forward (`DRAFT -> SENT -> APPROVED`). ADR 0001 froze its
numbers at send so a figure a Manager has reviewed can never move quietly. That left a Manager who
finds a wrong or missing line on a sent invoice with two choices, approve it or leave it waiting,
and no way to hand it back to the Agent with a reason. ADR 0004 gave every sent invoice its own
stored lines, which the Agent can edit while the invoice is a draft.

## Decision

**One backward edge, `SENT -> DRAFT`, taken by a Manager's send-back with a required reason.**
`ClientInvoiceStatus.canTransitionTo` gains it; `APPROVED` stays terminal. The send-back runs in
`ClientInvoiceService.sendBack`, under the Client Invoice row lock the send, the line edit and the
approval also take, so a send-back and an approval of the same invoice run one after the other and
the second gets 409. The invoice records `sentBackAt` and `sentBackReason` (the latest only, kept
through the resend and overwritten by the next send-back) and loses its `sentAt`. An invoice is
"sent back" when it is a `DRAFT` carrying a `sentBackAt`.

**ADR 0001's reason still holds.** The freeze protects every figure while a Manager is reviewing it
(`sent`) or has approved it (`approved`). A send-back is the Manager deliberately handing that
review back, and the resend is a new review of new frozen numbers.

**Nothing is recomputed and nothing is cleared.** The invoice returns to draft carrying its stored
lines and `linesStored`; the Agent edits them as on any draft (ADR 0004). A send-back writes no
line row. A Fee of that month logged after the first send appears as a new pre-filled line, stored
at the resend, and every line the invoice was sent with stays exactly as sent. No line is added for
a Postpaid SIM added after the send (the human's answer of 2026-10-02).

**A send-back and a resend move no pay.** A late Fee counts at its logged amount whether it is
unbilled, a pre-filled line or stored at that amount, so the Agent's Local Support Fees term is the
same before and after either. An edit on a sent-back invoice reaches the Agent's pay by ADR 0004's
rule, unchanged.

**`approved` stays terminal.** An amount found wrong after approval is corrected by the Manager on
the following month's invoice (`invoice-adjustment`); an approved invoice is never reopened.

**The reason is a note from the Manager to the Agent,** not part of the statement. A Tester never
sees it (the response fields are null for a Tester and the PDF never prints them), a Tester never
sees a draft, and the reason's text is never logged. The audit line is the existing generic
`STATUS_CHANGE` from `SENT` to `DRAFT`.

## Consequences

- The migration is additive: nullable `sent_back_at` and `sent_back_reason varchar(1000)` on
  `client_invoices`. It is `V59`, because `V58` shipped first and Flyway rejects an out-of-order
  version on a database that already has a later one.
- The Review Queue and the Dashboard card need no change: membership is `SENT`, so a send-back
  leaves them and a resend rejoins with `waitingSince` equal to the new `sentAt`.
- Only the Client Invoice is covered. The Agent Invoice's half, and the matching note on ADR 0003,
  belong to `send-an-agent-invoice-back`.
- Alternatives rejected: recomputing the lines on send-back (moves figures the Manager reviewed);
  clearing `linesStored` (would trip the line table's unique constraints on resend and lose edited
  amounts); reopening an approved invoice (ADR 0001's final record).
