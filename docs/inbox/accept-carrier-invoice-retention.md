---
id: accept-carrier-invoice-retention
type: acceptance
status: open
blocks: []
created: 2026-10-18
---

<!-- sdlc:template inbox-item 1 -->

## Question

Are carrier invoice files now deleted 30 days after approval, with the
invoice saying so where each file was? Report:
`docs/features/carrier-invoice-retention/delivery.md`.

Agents played walkthrough steps 1–9 and 11 and kept evidence; step 10 waits
for `month-closes-on-the-fifth`. Step 12 is yours:

12. On your own stack (`rsa-prod`), now rebuilt with retention: about a
    minute after the backend starts, `docker compose -p rsa-prod logs backend`
    shows no retention error. Approve a Client Invoice that has carrier
    invoice files and download one: it works. On or after the 31st day after
    that approval, open the invoice and confirm each file shows
    "Deleted on {date}" where Download was, each SIM line keeps its check
    mark, and you are happy with how that reads.

## Recommendation

Accept the first half now (no error, download works); the second half needs
the 30 days to pass. Anything off becomes a small follow-up ticket.

## Blocks

Nothing.

## Meanwhile

The epic `carrier-invoices-per-sim` is complete; the loop moves on.

## Answer
