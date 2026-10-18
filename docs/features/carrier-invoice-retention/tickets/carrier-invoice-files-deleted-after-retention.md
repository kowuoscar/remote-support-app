---
id: carrier-invoice-files-deleted-after-retention
title: Delete a Carrier Invoice File's bytes 30 days after its Client Invoice is approved
status: done
depends_on: []
labels: [backend]
stories: [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20]
---

## Context

`spec.md` `### Schema`, `### The rule`, `### The job: CarrierInvoiceRetention`, `### Download, list and response`, `### ADR 0007, dated note`, `## Constraints`, `## Testing decisions` items 1 to 4. Flyway: list `backend/src/main/resources/db/migration` and take the next free number (V62 on today's `main`).

The backend half of the feature, demoable through the API and the bucket: a daily job (`CarrierInvoiceRetention.runOnce()`, plus one run about a minute after startup) deletes the objects of files whose Client Invoice was approved at least 30 days earlier, marks `deleted_at`, and keeps the row. `CarrierInvoiceFileResponse` gains `deletedAt`, and downloading a deleted file answers `410 CARRIER_INVOICE_FILE_DELETED` before the availability gate. Settings under `app.carrier-invoice-retention` (`enabled`, `cron`). The 30 days and the batch of 500 are constants. Not in this ticket: any frontend change (`carrier-invoice-files-show-deleted-on`).

Harness declarations for the result (the merger applies them): `ARCHITECTURE.md` entries for the migration, `CarrierInvoiceRetention`, `CarrierInvoiceRetentionProperties`, the scheduling configuration class and `CarrierInvoiceFileDeletedException`; `CONTEXT.md`: a Carrier Invoice File's bytes are kept 30 days after approval, then deleted while the row stays. ADR 0007 gains the dated note from the spec, edited in this ticket.

## Acceptance criteria

- [ ] `runOnce()` on a Client Invoice approved 30 days and 1 minute ago deletes each of its files' objects from the Object store and reports them as deleted. `GET /api/client-invoices/{id}` then lists each file with `deletedAt` set and its name, type and size unchanged. A second `runOnce()` changes nothing and reports 0. A file whose object is already missing is marked deleted with no error.
- [ ] A file on an invoice approved 30 days minus 1 minute ago is kept: its object stays and its download works. A file on a draft, sent or sent-back invoice is never deleted, however far back its `uploadedAt` and the billing month are moved.
- [ ] Downloading a deleted file answers `410` with `{ code: "CARRIER_INVOICE_FILE_DELETED" }`, also while file storage is marked unavailable (not `503`). Every file not yet deleted downloads, attaches, sends and approves as before.
- [ ] When the store is down during a run, no file is marked deleted, the run stops at the first failure without attempting later files, and the run writes one ERROR (the storage's) and one WARN ("Carrier invoice retention stopped: object store unavailable; {deleted} deleted, {remaining} still due; next run retries"). A file is marked deleted only after its object's deletion succeeded; the next run retries the files left.
- [ ] A run with nothing due writes no log line above DEBUG and makes no store call. A run that deleted n > 0 files writes one INFO "Carrier invoice retention deleted {n} files". A run takes at most 500 due files, oldest approval first. The job never probes availability nor sets file storage available.
- [ ] With the defaults the cron `0 0 3 * * *` in UTC is registered, and one run is scheduled about one minute after startup, both on the scheduling thread without delaying or failing startup. `CARRIER_INVOICE_RETENTION_ENABLED=false` registers neither, and `CARRIER_INVOICE_RETENTION_CRON` moves the cron. `runOnce()` works whatever `enabled` says.
- [ ] ADR 0007 carries the dated retention note under Decision, and the `CONTEXT.md` and `ARCHITECTURE.md` entries are declared in the result.

## Tests

Seams per spec `## Testing decisions`: items 1 to 4.

- **`CarrierInvoiceRetentionTest` (new, `IntegrationTest`, real bean, shared SeaweedFS, bucket read through `ObjectStoreTestSupport`):** due file (object gone, `deletedAt` in the by-id response, name/type/size unchanged, download `410`, `deleted = 1`); 30 days minus 1 minute kept and downloadable; draft, sent and sent-back invoices with `uploadedAt` and `billingMonth` moved back months never deleted; an object removed through the helper beforehand marked deleted without error; a second run `deleted = 0`. Backdate `approvedAt` through the repository (prior art `ClientInvoiceByIdApiTest.moveToPastMonth`). The 500 cap is not played here.
- **`CarrierInvoiceRetentionUnitTest` (new, JUnit 5 + Mockito, Backend 6):** `ObjectStoreUnavailableException` on the second of three files (first marked, second and third not, third never attempted, `stoppedByStore`, exactly one WARN, log capture as in `ObjectStoreAvailabilityTest`); nothing due (no store call, nothing above DEBUG); the query is asked for at most 500; `ObjectStoreAvailability.probe()` never called; exactly one INFO when n > 0.
- **`ClientInvoiceApiTest` (HTTP seam):** a row with `deletedAt` set through the repository downloads as `410` with the code; with the store marked unavailable still `410`, not `503`.
- **`CarrierInvoiceRetentionTriggersTest` (new, context test):** `enabled=false` registers no scheduled task; `enabled=true` registers the cron `0 0 3 * * *` in UTC (read from `ScheduledTaskHolder`); `cron=0 * * * * *` registers that cron instead. The startup run is shown in the walkthrough, not waited on.
- **Migration:** exercised by every `IntegrationTest` boot; no separate test.

## Regression

At risk: the Client Invoice response consumers (`CarrierInvoiceFileResponse.of`, `ClientInvoiceResponse.files`), download, attach, send and approve for files not yet deleted, and the whole suite firing a retention trigger (hence `enabled=false` in tests).

Existing tests expected to change, each with the reason:

- `backend/src/test/java/com/remotesupport/backend/support/IntegrationTest.java`: its `@DynamicPropertySource` also sets `app.carrier-invoice-retention.enabled=false` so no trigger fires during the suite. Additive; no existing method changes.
- `backend/src/test/java/com/remotesupport/backend/web/ClientInvoiceApiTest.java`: gains the two `410` cases. Every pre-existing method keeps passing unmodified.
- `ClientInvoiceByIdApiTest`, `CarrierInvoiceFileServiceTest`, `ClientInvoiceServiceAttachTest`, `DemoDataLoaderApiTest` and `CarrierInvoiceTestSupport` read the file response or build files; the new field is nullable, so none changes (the merger refuses any unlisted test-file change). `DemoDataLoaderApiTest` and `DemoDataLoaderStoreDownTest` boot with retention enabled, but nothing is ever due in their databases (past approved demo invoices carry no files), so they stay unedited. Set `enabled=false` in `IntegrationTest`'s dynamic properties, not in the shared `ObjectStoreTestSupport.register`, which is not listed.
- No frontend, e2e or golden file changes in this ticket; the existing e2e specs run unedited.

## Observability

Per run: nothing when nothing is due; one INFO "Carrier invoice retention deleted {n} files" when n > 0; each deleted file a DEBUG line with its id and key; on a store failure the storage's one ERROR plus one WARN from the job. Never the store's credentials. Each row's `deleted_at` is the durable record.

Accepted by the orchestrator at merge: `sdlc-test-guard` flagged `enabled = false` in the new `CarrierInvoiceRetentionScheduling.java` and the new `CarrierInvoiceRetentionTriggersTest.java`. Both are the feature flag `app.carrier-invoice-retention.enabled`, not a skip marker, in new files whose tests run.
