---
id: carrier-invoice-files-in-object-store
title: Keep Carrier Invoice File bytes in an S3-compatible object store that can never stop the backend starting
status: ready-for-agent
depends_on: []
labels: [backend, infra]
stories: [1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19]
---

## Context

The settings are bound to one typed `ObjectStoreProperties` (`app.object-store`, coding-standards Backend 14).

`spec.md` `### Prefactoring`, `### Configuration`, `### The S3 client`,
`### CarrierInvoiceFileStorage, rewritten`, `### Attaching and downloading`,
`### Schema`, `### docker-compose.yml`, `### The e2e scripts`, `### ADR 0007`,
`## Constraints` and `## Testing decisions` 1 to 3, 4 (the context tests) and 6.
The storage swap, compose, both e2e scripts and the test seam land together or
`verify` and the human's stack break between tickets.

The Carrier Invoice File's bytes move from the backend's filesystem to one
private bucket, written and read only by the backend. Nothing a user sees
changes while the store is up. Not in this ticket: the availability flag and
`filesAvailable` (`carrier-invoice-files-availability`), the download header
(`carrier-invoice-file-download-name`), the paused UI
(`carrier-invoice-files-paused`).

Constraint (spec `## Constraints`, Backend 3 and 8): no store call happens
inside a transaction holding a Client Invoice row lock. The upload runs before
`recordAttachedFile`, and a failed transaction deletes the object.

Startup in this ticket: a one-shot bucket check (`HeadBucket`, then
`CreateBucket` when absent and the flag is on) runs on `ApplicationReadyEvent`
and never fails startup; on failure it logs one ERROR naming the endpoint and
bucket. A store failure during attach or download is a 503
(`ObjectStoreUnavailableException`). `carrier-invoice-files-availability`
turns the one-shot check into `ObjectStoreAvailability`.

Scope notes:

- The implementer checks the five S3 operations and Hetzner's addressing style
  against Hetzner's published documentation and records both in ADR 0007; an
  unsupported operation is a stop that goes back to the human.
- The implementer picks and confirms the exact `chrislusf/seaweedfs` tag and
  the S3 credential mechanism for it; one tag in compose, the test support
  class and both e2e scripts.
- ADR 0007 is written with status `accepted`: the human's approval of the spec
  accepted it (spec, Decisions taken, first bullet). It is a new file, so this
  ticket writes it.
- Harness changes (`CONTEXT.md` Carrier Invoice File entry; `ARCHITECTURE.md`
  entries for the new classes) are declared in the result, not edited.

## Acceptance criteria

- [ ] After an Agent or a Manager attaches files to a draft, exactly one object per file exists in the bucket at `carrier-invoices/{tenant id}/{client invoice id}/{file id}`, holding the uploaded bytes, with no `x-amz-meta-*` and no tags, and each file's row `storage_path` equals that key. Downloading returns the same bytes, name and `Content-Type` as before, for the Agent, the Manager and the Tester.
- [ ] A send of the invoice issued while an attach's upload is still transferring is answered without waiting for that upload.
- [ ] An attach that is refused (a sent invoice answers 409) or fails leaves no object under that invoice's prefix and no row.
- [ ] A file whose object is missing downloads as 404; a store failure on attach or download answers 503 with one ERROR log line naming the operation and key and never the credentials.
- [ ] The backend starts and `/api/health` answers whatever the store's state or configuration: unreachable endpoint, wrong credentials, missing bucket with `OBJECT_STORE_CREATE_BUCKET=false`, an endpoint that is not a URL, a blank bucket or region. Each logs one ERROR naming the endpoint and the bucket (or the bad setting, never a credential value), and attach and download answer 503. With the flag on, a missing bucket is created at startup.
- [ ] `docker-compose.yml` starts a pinned `chrislusf/seaweedfs` service with a `seaweedfs_data` named volume, a healthcheck and no host port, and the backend points at it with `depends_on` `service_started`. Files survive a backend container rebuild. Both e2e backend scripts start and tear down their own throwaway SeaweedFS, and `client-invoice-generation.spec.ts` and `client-invoice-submission-and-visibility.spec.ts` pass unedited on it.
- [ ] Every storage setting (endpoint, region, bucket, access key, secret, path style, create bucket) is read from its `OBJECT_STORE_*` environment variable, with a dev-only default in `application.yml` and no Hetzner value in the repo; `CARRIER_INVOICES_DIR` and `app.file-storage` are gone, a full `verify` creates no `backend/data`, and `docs/adr/0007-carrier-invoice-files-in-an-s3-compatible-object-store.md` exists with status `accepted`, recording the decision, licences, consequences, rejected options, the operations checked against Hetzner and its addressing style.

## Tests

Seams per spec `## Testing decisions` items 1 to 4 and 6.

- **Test support (new):** a singleton `GenericContainer` on the pinned tag, started once per JVM beside `IntegrationTest`'s Postgres, publishing endpoint and credentials by `@DynamicPropertySource`, with helpers to list keys under a prefix, read an object's bytes and head its user metadata. Used by `IntegrationTest` and `DemoDataLoaderApiTest`.
- **HTTP seam, new cases in `ClientInvoiceApiTest`:** after an attach, exactly one object at the exact key with the uploaded bytes and no `x-amz-meta-*`; an attach to a sent invoice is 409 and leaves nothing under that prefix; a row whose object was deleted through the helper downloads as 404.
- **Concurrency (new test class, prior art `ClientInvoiceConcurrentSendTest`):** an attach whose `put` is held on a latch while a concurrent send of the same invoice completes; the send returns before the latch is released.
- **Context tests (prior art: the existing context tests under `IntegrationTest`):** a missing bucket is created at boot (the shared test bucket starts absent); a context booted with a malformed `OBJECT_STORE_ENDPOINT`, another with a blank bucket and another with a blank region each start, answer 503 on attach and download, and log one ERROR naming that setting; a context booted with `OBJECT_STORE_ENDPOINT` at a closed local port starts, answers 503 on attach and download, and logs one ERROR naming the endpoint and bucket; a context booted with `OBJECT_STORE_CREATE_BUCKET=false` and a bucket that does not exist starts, answers 503 on attach and download, logs one ERROR naming the endpoint and bucket, and creates no bucket; a probe refused for credentials logs one ERROR that contains neither the access key nor the secret.
- **Unit (JUnit 5 + Mockito), `CarrierInvoiceFileStorage` over a mocked `S3Client`:** `SdkException` becomes `ObjectStoreUnavailableException`; `NoSuchKey` on `read` becomes `CarrierInvoiceFileMissingException`; `put` sends no metadata; `keyFor` gives the exact shape; `delete` of a missing key succeeds.
- **Unit, `CarrierInvoiceFileService` over mocked storage and `ClientInvoiceService`:** an exception from `recordAttachedFile` deletes the key and rethrows; a failing delete still rethrows the original; a failing `put` never calls `recordAttachedFile`.
- **End to end:** the two named specs, unedited, on the isolated stack.
- **Manual proof (walkthrough steps 1 to 8):** compose config, restart persistence. Not an automated test.

## Regression

At risk: attaching, listing and downloading Carrier Invoice Files on all three screens; send, send-back and approve (their transaction and row lock); the demo seed under `DemoDataLoaderApiTest`; the isolated e2e startup path; the human's `rsa-prod` stack.

Existing tests expected to change, each justified:

- `ClientInvoiceApiTest.java` gains the new cases listed under Tests. Every pre-existing method keeps passing unmodified, and the attach, list and download cases (about lines 494, 543, 611, 693) now run against SeaweedFS.
- `ClientInvoiceByIdApiTest.java` (about line 197) and `ClientInvoiceSendBackApiTest.java` (about lines 560, 618-657, 751) must pass unedited.
- `IntegrationTest.java` is modified: it gains the shared SeaweedFS container and `@DynamicPropertySource`. `DemoDataLoaderApiTest.java` is modified the same way for its own `demo` context. Every pre-existing test method keeps passing unmodified.
- `client-invoice-generation.spec.ts` and `client-invoice-submission-and-visibility.spec.ts` must pass unedited.
- `frontend/playwright.e2e.isolated.config.ts`, `scripts/run-e2e-isolated.sh`, `scripts/run-backend-for-e2e-isolated.sh` and `scripts/run-backend-for-e2e.sh` are modified to add the throwaway SeaweedFS; all other e2e behaviour is unchanged.
- No other existing test may be edited.

## Observability

One ERROR per failed store call during a request (operation and key, no credentials); one WARN for a stray object when an undo delete fails (key); one WARN for a missing object (file id); one startup ERROR when the one-shot bucket check fails (endpoint and bucket) and one for a configuration that cannot build a client (the setting name only). Nothing is added to `/api/health`.
