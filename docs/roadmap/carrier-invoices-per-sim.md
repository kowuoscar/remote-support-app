---
id: carrier-invoices-per-sim
title: Carrier invoices attached per SIM, required before send, kept in object storage
status: in-progress
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

- [x] `carrier-invoice-object-storage` — Carrier Invoice Files are stored in an S3-compatible object store (Hetzner in production, SeaweedFS in compose and for tests), no existing files to move (the human, 2026-10-17); nothing a user sees changes.
- [x] `carrier-invoice-per-sim` — "Attach carrier invoice" opens a form that links the file to one of the invoice's Postpaid SIM lines (its number and Carrier) and stores it under a computed name "{Client} - {Month Year} - {Carrier} - {SIM number}"; the Agent cannot send the invoice until every Postpaid SIM line has its Carrier invoice; each Postpaid SIM line shows a check mark once its file is attached; after a send-back the Agent can remove a file.
- [ ] `carrier-invoice-retention` — a Carrier Invoice File is deleted from storage 30 days after its Client Invoice is approved, and the pages say so where the file was.

## Settled with the human (2026-10-17)

- **One file per Postpaid SIM.** "can one carrier invoice cover several sims
  of the same carrier? no".
- **Every SIM has a Carrier** ("A sim has always a carrier"): no
  Carrier-less case to design for.
- **No backfill.** "none presently so nothing to do to backfill": no Carrier
  Invoice File exists yet on the human's stack.
- **After a send-back the Agent can remove a file** ("yes the agent can remove
  a file"), then attach its replacement.
- **The link's purpose is the row's check mark.** "it's really to have in the
  row an icon (check mark) showing that for the sim number a file were
  uploaded." Each Postpaid SIM line shows a check mark once its file is
  attached.
- **Retention keeps the record (A).** After the 30 days the bytes are
  deleted; the invoice still lists the file by name with "Deleted on {date}"
  in place of Download, and the SIM line keeps its check mark (the human,
  2026-10-17: "A").
- **Object keys are ids (A).** `carrier-invoices/{tenant id}/{client invoice
  id}/{file id}` in one private bucket; the computed name lives in the
  database and is given on download (Content-Disposition). Deletion at
  retention is done by the app (a daily job that records "Deleted on"), not
  by a bucket lifecycle rule, which cannot count from approval. The backend
  speaks the S3 API (AWS SDK); SeaweedFS in compose with its own volume and
  in tests, Hetzner Object Storage in production (MinIO dropped: discontinued) (the human, 2026-10-17: "A", after options A ids / B readable keys /
  C lifecycle-on-approved-prefix).
- **No metadata on the stored object.** Which Client Invoice, month and SIM a
  file is for lives only in the database row; the object carries no
  client/month/carrier/SIM labels (the human, 2026-10-17: "no").

## Open at spec time

- The object store's deployment for the human's own stack, and an ADR for the
  storage choice.

## Later
