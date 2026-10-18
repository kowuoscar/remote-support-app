# Delivery report — carrier-invoice-per-sim

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

The second feature of the epic `carrier-invoices-per-sim`. Merged as PR #33
(`6414dee`): five tickets, one fix pass, then two more authorized by you
(three small visual fixes, then the SIM number kept on one line).

- **One carrier invoice per Postpaid SIM line (stories 1–4, 7–9, 26).**
  "Attach carrier invoice" opens a form listing the invoice's Postpaid SIM
  lines that still lack a file, as `{number} · {Carrier}`. Each file is linked
  to its SIM (migration V61, `sim_card_id`, one file per SIM per invoice).
  Attach is disabled once every line has its file, and absent when the
  invoice has no Postpaid SIM line. Ticket: `attach-carrier-invoice-per-sim`.
- **The computed name (stories 5, 6, 10, 11).** The file is stored as
  `{Client} - {Month Year} - {Carrier} - {SIM number}.{ext}`, fixed at
  attach. The uploaded extension is kept, lowercased, and unsafe or control
  characters become `_`. Only the Client part is cut to fit 255 characters.
  Ticket: `carrier-invoice-computed-name`.
- **The check mark (stories 21, 22, 24, 25).** Each Postpaid SIM line with
  its file shows a green check after its Carrier label, on the Agent's card
  and on the Manager's detail. Ticket: `carrier-invoice-check-mark`.
- **No send without every file (stories 12–14, 16, 17).** Send is disabled
  with a count note, and the server refuses a stale send with 409
  `CARRIER_INVOICES_MISSING`. The check reads the database only, so a store
  outage doesn't block a fully covered invoice. Ticket:
  `send-requires-every-carrier-invoice`.
- **Remove a file (stories 15, 18–20, 23).** On any draft open to its Agent,
  the Agent removes a file with an inline two-step confirm. Remove is paused
  with the rest while the store is unavailable. Ticket:
  `remove-carrier-invoice-file`.
- **Demo data (stories 27, 28, 30).** A fresh demo database attaches a
  carrier invoice for each Postpaid SIM of its sent invoice. With the store
  down it logs one WARN and leaves that invoice a draft.

## Acceptance walkthrough

Played by an agent on its own throwaway stack, never on yours.

1. `verify` green on an idle machine — played — evidence: `evidence/step-1-verify.log`
2. Your example set up: three Postpaid SIMs, one Prepaid; Send disabled with the count — played — evidence: `evidence/step-2.txt`
3. Attach through the form under the computed name — played — evidence: `evidence/step-3-download.log`
4. API: attach without a SIM or to a Prepaid SIM is 400, a second file for one SIM is 409, a send with files missing is 409 naming them — played — evidence: `evidence/step-4-api.txt`
5. Remaining lines attached; bucket and rows match — played — evidence: `evidence/step-5-bucket-db.txt`
6. A stale send refused when a fourth SIM appears — played — evidence: `evidence/step-6.log`
7. Manager sees the check marks, read-only — played — evidence: `evidence/step-7-api.txt`
8. Send-back, remove, re-attach, resend — played — evidence: `evidence/step-8.log`
9. Remove on a never-sent draft; refused on a past month — played — evidence: `evidence/step-9-past.txt`
10. A corrected SIM number keeps its check and the stored name; unsafe characters in a Client name become `_` — played — evidence: `evidence/step-10-acme.log`
11. Prepaid-only month: no Attach, send works — played — evidence: `evidence/step-11.log`
12. Store down: a fully covered invoice still sends; files paused — played — evidence: `evidence/step-12.log`
13. A file whose SIM left the lines stays listed and doesn't block — played — evidence: `evidence/step-13.log`
14. Fresh demo database attaches its files; store-down start — played — evidence: `evidence/step-14b.txt`
15. On `rsa-prod`: attach real carrier PDFs, send, send back, remove, resend — yours

## Decisions taken alone

From the spec's `## Decisions taken`, besides what you settled:

- Remove works on any draft open to its Agent, including one never sent,
  so a wrong file can always be replaced.
- The send check reads rows only; a store outage doesn't block a covered
  invoice.
- The name is computed once at attach and never recomputed; the month reads
  `October 2026`; a SIM with no Carrier leaves that part out.
- No preview of the computed name in the form; no file-type restriction.
- The check mark shows on the Manager's detail too; nothing changes on the
  Tester's page.
- Remove deletes the row first, then the object; a failed object delete
  only logs a WARN.
- `sim_card_id` is nullable, so files from before this feature keep working.
- (after review) The demo loader checks the store itself and attaches
  through the normal attach path, outside any transaction.
- (at review, settled by you) Tests that boot the demo data are exempt from
  the shared-test-base rule (coding standards Backend 6).

## Debt recorded

- `carrier-invoice-files-section.tsx` · at 390px a long computed file name
  wraps inside its SIM number (seen at the last re-review; not a finding)

All smells raised in review were fixed.

## How to undo

```
git revert -m 1 6414dee
```

The revert does not undo migration V61 (`sim_card_id` on
`carrier_invoice_files`). The column is nullable and harmless to the old
code, so it can stay.

## What happens next

`carrier-invoice-retention` is next in the epic: files deleted 30 days after
the invoice's approval, shown as "Deleted on {date}". You answered its open
question (no "Kept until"), so its spec now goes to you for approval. Your
`rsa-prod` is rebuilt with this feature.
