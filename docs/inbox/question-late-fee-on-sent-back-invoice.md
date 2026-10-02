---
id: question-late-fee-on-sent-back-invoice
type: question
status: open
blocks: [send-a-client-invoice-back, edit-client-invoice-lines]
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

When a Manager sends an invoice back, can a Fee of that month that was logged
**after** the first send appear on it?

Example: the September invoice is sent on 25 September. On the 27th, a $10
Topup Fee for September is logged. On 1 October the Manager sends the invoice
back. Does the $10 Topup now show up on it, so the Client is billed for it in
September?

Right now the two specs disagree. The epic promised "the Agent adds what was
missing (a file, a late Fee of that month)". The editing spec fixes the list
of lines at the first send, so the $10 would wait for next month's
adjustment.

## Recommendation

**Yes.** The late Fee appears on the sent-back invoice as a new pre-filled
line, and every line it was sent with stays exactly as sent.
- **It keeps the epic's promise:** the Client is billed in the Fee's own month.
- **Nothing reviewed moves:** no figure the Manager already reviewed changes.
- **Pay is unaffected:** the Agent's pay is the same either way.

It changes one rule in the editing spec.

## Blocks

`send-a-client-invoice-back` and `edit-client-invoice-lines` both stay
`draft`.

## Meanwhile

`real-client-dashboard` is being built.

## Answer
