---
id: accept-carrier-invoice-object-storage
type: acceptance
status: open
blocks: []
created: 2026-10-17
---

<!-- sdlc:template inbox-item 1 -->

## Question

Do carrier invoice files now attach and download the same way, kept in the
object store? Report:
`docs/features/carrier-invoice-object-storage/delivery.md`.

Agents played walkthrough steps 1–8 and kept evidence. Step 9 is yours:

9. On your own stack (`rsa-prod`), now rebuilt with the local SeaweedFS,
   open a Client Invoice draft as the Agent, attach a carrier invoice and
   download it. Then rebuild the backend alone
   (`up -d --build --force-recreate backend`), reopen the invoice and
   download it again. Confirm it is the same file, under the same name.

## Recommendation

Accept if the file comes back the same; anything off becomes a small
follow-up ticket.

## Blocks

Nothing.

## Meanwhile

The loop starts `carrier-invoice-per-sim`.

## Answer
