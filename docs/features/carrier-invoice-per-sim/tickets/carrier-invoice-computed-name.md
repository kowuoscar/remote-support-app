---
id: carrier-invoice-computed-name
title: Store and download each Carrier Invoice File under its computed name
status: in-progress
depends_on: [attach-carrier-invoice-per-sim]
labels: [backend]
stories: [3, 4, 5, 6, 25, 30]
---

## Context

`spec.md` `### The computed name: CarrierInvoiceFileName`, `### Attaching: the contract` step 4, `## Testing decisions` items 1 and 3. Depends on `attach-carrier-invoice-per-sim`, which stores the uploaded file's own name as a placeholder and provides the SIM link this name is built from.

`CarrierInvoiceFileName.of(clientName, billingMonth, carrierName, simNumber, uploadedFilename)` is a pure component in `web`. `recordAttachedFile` computes it once under the invoice's row lock and stores it in `filename`; it is never recomputed. The Tester's list and every download (Agent, Manager, Tester) already read `filename`, so they show the computed name with no further change.

`send-requires-every-carrier-invoice` and `remove-carrier-invoice-file` do not depend on this ticket: the gate and Remove read rows and SIM links, never the name, and their flows work with the placeholder name. Declare the `CONTEXT.md` Carrier Invoice File entry change (story 30) in the result; do not edit `CONTEXT.md`.

## Acceptance criteria

- [ ] An attached file is listed and downloads under `{Client} - {Month Year} - {Carrier} - {SIM number}{.ext}` for the Agent, the Manager and the Tester (e.g. `scan.PDF` on Verizon gives `Acme Corp - October 2026 - Verizon - +1-202-555-0102.pdf`), the extension kept lowercased when 1 to 10 ASCII letters or digits, else none.
- [ ] `/ \ : * ? " < > |` and control characters in the Client, Carrier or SIM number become `_`, whitespace runs collapse to one space, and a SIM with no Carrier omits that part.
- [ ] A name that would pass 255 characters shortens only the Client part.
- [ ] The name is fixed at attach: after the SIM's number is corrected, the listed and downloaded name is unchanged, and removing and re-attaching (once Remove exists) gives the corrected one.
- [ ] The Tester's Invoices page lists the files under their computed names.

## Tests

Seams per spec `## Testing decisions`: items 1 (HTTP) and 3 (unit).

- **`CarrierInvoiceFileName` unit test, table-driven:** plain; archived Carrier; no Carrier; each unsafe character; whitespace runs; extension kept lowercased, absent, rejected (`scan.final version`, `invoice`); 255-cut shortening only the Client; non-ASCII Client kept.
- **`ClientInvoiceByIdApiTest` (HTTP seam):** attach gives the computed name in the 201 body, the list and the download's `Content-Disposition`; a Client named `Acme/Labs "Test": North` gives `Acme_Labs _Test__ North - ...`; attach, correct the SIM's number through `PATCH /api/contracts/{contractId}/sim-cards/{simCardId}/number` (as `SimCardCorrectionApiTest` does), and the listed and downloaded names are unchanged.
- **Tester read:** a Tester's invoice response lists the computed name (`ClientInvoiceApiTest`, beside the existing list/download cases).
- **E2E:** `client-invoice-generation.spec.ts` asserts the computed name after attaching through the form. Prior art: `client-invoice-generation.spec.ts:26-88`.

## Regression

At risk: the download name, the file list on all three screens, the Tester's Invoices page.

Existing tests expected to change, each with the reason:

- `backend/src/test/java/com/remotesupport/backend/web/ClientInvoiceServiceAttachTest.java`: its `setUp` gives the mocked Contract a Client name and the SIM a number, because the service now reads them to compute the name; no assertion changes. Accepted by the orchestrator at merge.

- `ClientInvoiceByIdApiTest` and `ClientInvoiceApiTest`: assertions that expected the uploaded name (the placeholder set by `attach-carrier-invoice-per-sim`) now expect the computed name, and the new cases are added; every other pre-existing method keeps passing unmodified.
- `client-invoice-generation.spec.ts`: gains the computed-name assertion; its existing steps keep passing unmodified.
- `ClientInvoiceSendBackApiTest`: any assertion of the file's listed name moves to the computed name; every other pre-existing method keeps passing unmodified.

## Observability

N/A: a pure naming function evaluated at an existing operation; nothing new to log.
