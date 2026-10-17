---
id: carrier-invoice-file-download-name
title: Download a Carrier Invoice File under its exact name, whatever characters it holds
status: ready-for-agent
depends_on: [carrier-invoice-files-in-object-store]
labels: [backend]
stories: [5]
---

## Context

`spec.md` `### Attaching and downloading` (the two changes under "Download"),
`## Testing decisions` item 2. Today the download header concatenates the
uploaded name unescaped, so a quote or a non-ASCII letter yields a broken or
misread header, and the computed names of the next features hold Client names
that are not ASCII-only.

Depends on `carrier-invoice-files-in-object-store` because the download path
lives in the new `CarrierInvoiceFileService` that ticket creates; both would
edit the same method. Plain ASCII names keep downloading under the same name.

## Acceptance criteria

- [ ] Downloading a file named `Réf "Oct" 2026 – Orange.pdf` answers a `Content-Disposition: attachment` whose `filename*` (RFC 6266, UTF-8) decodes to exactly that name, and the same for the Agent, the Manager and the Tester.
- [ ] Downloading a plain ASCII name yields the same saved name as before.
- [ ] Every file download carries `X-Content-Type-Options: nosniff`; the body and the `Content-Type` recorded on the row are unchanged.

## Tests

Seam per spec `## Testing decisions` item 2 (HTTP, `ClientInvoiceApiTest`).

- Attach a file named with accents, a double quote and an en dash, download it: `filename*` decodes to the exact name; `nosniff` present; bytes and `Content-Type` equal the upload's.
- Download a plain `invoice.pdf`: the decoded name is `invoice.pdf`.
- Download as the Tester of a sent invoice carries the same header.

## Regression

At risk: file downloads on all three screens and through the BFF's `forwardBinary` (unchanged), which passes the header through.

Existing tests expected to change: `ClientInvoiceApiTest.java` gains the cases above. Every pre-existing method keeps passing unmodified, including those that read the download header for plain names (about lines 543 and 611); if one asserts the old header form verbatim, say so in the result instead of editing it silently. `client-invoice-generation.spec.ts` (download steps, about lines 80-88) must pass unedited. No other existing test may be edited.

## Observability

N/A: a response header on an existing endpoint; no new operation to log.
