---
id: approve-carrier-invoice-per-sim
type: approval
status: open
blocks: [carrier-invoice-per-sim]
created: 2026-10-17
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for attaching one carrier invoice per Postpaid SIM?
`docs/features/carrier-invoice-per-sim/spec.md`.

"Attach carrier invoice" opens a form: pick the Postpaid SIM line, pick the
file. It is stored as "{Client} - {Month Year} - {Carrier} - {SIM
number}.{ext}" (e.g. "Harbor Line - October 2026 - Verizon - 555-0101.pdf"),
and the SIM line shows a check mark, for the Agent and, read-only, the
Manager. Send stays disabled, with "2 Postpaid SIMs still need their carrier
invoice.", until every Postpaid SIM line has its file; the backend refuses
it too. An invoice with no Postpaid SIM is not gated.

Decisions taken that you may want to veto:

1. Remove is offered on any draft open to the Agent, not only after a
   send-back: otherwise a wrong file attached before the first send could
   never be replaced (one file per SIM).
2. While file storage is unavailable, an invoice whose Postpaid SIMs all have
   their file can still be sent (the check reads the database); a missing
   file still blocks.
3. The name is fixed when the file is attached; correcting the SIM number
   later does not rename it. The uploaded file's extension is kept;
   characters a file name cannot hold become "_".
4. Remove asks for a second click to confirm.

## Recommendation

Approve.

## Blocks

`carrier-invoice-per-sim`.

## Meanwhile

Nothing else in the epic can start before it.

## Answer
