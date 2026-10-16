---
id: carrier-invoices-per-sim
title: Carrier invoices attached per SIM, required before send, kept in object storage
status: proposed
journeys: [bill-the-client-for-the-month]
---

<!-- sdlc:template epic 1 -->

## Intent

Raised by the human on 2026-10-16 while testing on a production-like stack,
right after accepting `ui-side-lists-and-tabs`. In their words:

> Make sure agent can't send a client invoice to the manager without the
> related carrier invoices.
>
> Example: A contract with a client who have 3 postpaid and 1 prepaid.
> The bare minimum before sending the invoice is that 3 carrier invoices are
> attached.
>
> I would expect when attaching an invoice to link it to the related sim
> number, carrier and then store it with a computed name (client - month
> year - carrier - sim number), the action button should handle opening a
> form to do that
>
> Constraints: use an object storage for storing the files compatible with s3
> such as minio
> file retention policy: 30 days after being approved.

What the code does today (explorer, 2026-10-16):

- A Carrier Invoice File belongs to a Client Invoice only: no link to a SIM
  Card or a Carrier (`CarrierInvoiceFile.java`, table `carrier_invoice_files`).
  "Attach carrier invoice" opens the file picker straight away, several files
  at once, any type, up to 20MB, keeping the uploaded file's own name.
- Bytes live on the backend's local filesystem
  (`CarrierInvoiceFileStorage`, `CARRIER_INVOICES_DIR`). The compose backend
  has no volume for them, so they are lost when the container is rebuilt.
- Send checks nothing about files: an invoice can go with none. Approve
  checks none either. `approved_at` is recorded at approval.
- The invoice's SIM lines are the Contract's Postpaid SIM Cards billing that
  month, each with its number and Carrier. Prepaid SIMs have no line.
- No object storage and no scheduled job exist anywhere yet.

## Journeys

- **Bill the Client for the month** → stays `exists`. Attaching becomes one
  file per Postpaid SIM line through a form, and send requires them all.

## Features

Ordered by dependency; cut and sized at spec time.

- [ ] `carrier-invoice-object-storage` — Carrier Invoice Files are stored in an S3-compatible object store (MinIO in compose and for tests), existing files moved over; nothing a user sees changes.
- [ ] `carrier-invoice-per-sim` — "Attach carrier invoice" opens a form that links the file to one of the invoice's Postpaid SIM lines (its number and Carrier) and stores it under a computed name "{Client} - {Month Year} - {Carrier} - {SIM number}"; the Agent cannot send the invoice until every Postpaid SIM line has its Carrier invoice; the Manager sees which SIM each file is for.
- [ ] `carrier-invoice-retention` — a Carrier Invoice File is deleted from storage 30 days after its Client Invoice is approved, and the pages say so where the file was.

## Open at spec time

- One file per Postpaid SIM, as the example reads, or may one Carrier
  invoice cover several SIMs of the same Carrier?
- A Postpaid SIM with no Carrier (from before the catalog): is a file still
  required, and what goes in the name's Carrier part?
- Existing files, not linked to any SIM: what happens to them, and to an
  unsent draft that holds them?
- Sent back: do the files stay linked, can the Agent replace or remove one?
- Retention: is the database record kept (the name, "deleted on …") or
  removed with the bytes? Does it apply to every tenant alike?
- The object store's deployment for the user's own stack, and an ADR for the
  storage choice.

## Later
