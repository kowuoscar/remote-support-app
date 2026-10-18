# 7. Carrier Invoice Files are kept in an S3-compatible object store

Status: accepted
Accepted: by the human, 2026-10-17, with the approval of the `carrier-invoice-object-storage` spec ("Ok")
Date: 2026-10-17
Feature: carrier-invoice-object-storage
Ticket: carrier-invoice-files-in-object-store

## Context

A Carrier Invoice File's bytes lived on the backend's filesystem (`CARRIER_INVOICES_DIR`, a directory
inside the container's writable layer). A rebuilt backend container lost them, and a second backend
instance could not have read them. The human asked for object storage, works with Hetzner Object
Storage, and counts file retention from the approval of this spec (the `carrier-invoice-retention`
epic, which will call `CarrierInvoiceFileStorage.delete`).

## Decision

- **The S3 API, through the AWS SDK for Java v2** (`software.amazon.awssdk:s3`, versions managed by the
  SDK's BOM, pinned to one exact release in `backend/pom.xml`), configured for non-AWS stores: endpoint
  override, static credentials, request checksums and response checksum validation only "when required"
  (recent SDK 2.x releases send default integrity checksums some S3-compatible servers refuse), and an
  addressing style chosen by configuration.
- **Hetzner Object Storage in production, configured by environment only.** No endpoint, bucket, access
  key or secret of any hosted store is committed. `application.yml` carries dev-only defaults for the
  local store; production sets every `OBJECT_STORE_*` variable.
- **SeaweedFS locally** (docker compose, the human's `rsa-prod` stack included), in the backend
  integration tests and in both e2e backend scripts' throwaway stacks, as the unmodified
  `chrislusf/seaweedfs` image pinned to one exact tag (`4.48` when this was written), never `latest`.
  Its S3 identity is configured with `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` in the container's
  environment (confirmed on 4.48: the server logs "Added admin identity from AWS environment variables").
  Compose publishes no host port for it.
- **One private bucket**, `remote-support-files` by default, holding the `carrier-invoices/` prefix; the
  prefix scopes the kind, so one bucket can serve a later file kind. The bucket is never made public.
- **Keys by ids**: `carrier-invoices/{tenant id}/{client invoice id}/{file id}`, lowercase canonical
  UUIDs. **The key is stored on the row** (`carrier_invoice_files.storage_path`, no migration) and always
  read from the row, never rebuilt from ids.
- **No object metadata**: no `x-amz-meta-*`, no tags, no `Content-Type` or `Content-Disposition` taken
  from the upload. The object is opaque bytes; name, type, size and owner are on the row.
- **Retention is done by the app**, not by a bucket lifecycle rule (which cannot count from approval).
- **The browser never reaches the store.** The backend writes and reads; no presigned URL is issued.
- **Write before the row, undo on failure.** `CarrierInvoiceFileService` (not transactional) uploads, then
  `ClientInvoiceService.recordAttachedFile` saves the row under the invoice's row lock, and a failure for
  any reason deletes the object again. No store call happens inside a transaction holding that lock
  (coding standards, Backend 8), so a slow upload never stalls a send.
- **Availability is checked at startup and then on demand, and never fails startup.** `HeadBucket` runs
  on `ApplicationReadyEvent`; when the bucket is absent and `OBJECT_STORE_CREATE_BUCKET` is on it is
  created (compose, tests and e2e leave the flag on), and when it is off (Hetzner: the bucket is
  provisioned outside the app and the app's credentials need no right to create one) or the store is
  unreachable or refuses the credentials, the backend logs **one ERROR naming the endpoint and the
  bucket and starts anyway**. A setting that cannot build a client (an endpoint that is not a URL, a
  blank bucket or region) is one ERROR naming the setting, never a credential value. After startup the
  store is checked again only on demand: when an invoice page is opened and the last check is more than
  30 seconds old, asynchronously, one check at a time; there is no background schedule. A failed
  transfer also marks the store unavailable at once. Logging is on transitions only (one ERROR going
  down, one INFO coming back). The cost: right after the store changes state, the first page opened may
  still show the previous one. Attaching and downloading answer 503 while unavailable. Nothing about the
  store can fail the Spring context, and `/api/health` does not probe the store.
- **A store failure during a request is a 503** (`ObjectStoreUnavailableException`) with one ERROR line
  naming the operation, the key, the endpoint and the bucket and never the credentials; a missing object is a 404 with a WARN.

### The S3 operations used, checked against Hetzner

Main code uses exactly five operations: `PutObject`, `GetObject`, `DeleteObject`, `HeadBucket` and
`CreateBucket` (tests also list objects and read tagging through their own client against SeaweedFS).

Checked on 2026-10-17 against Hetzner's published "List of supported actions"
(<https://docs.hetzner.com/storage/object-storage/supported-actions/>). The page says Hetzner offers
"basic compatibility with the standard Amazon S3 actions" and lists the *notable exceptions*; it is not
an enumerated whitelist, so "supported" below means "offered and not among the listed exceptions":

| Operation | Hetzner |
|---|---|
| `PutObject` | offered, not among the exceptions. |
| `GetObject` | offered, not among the exceptions. |
| `DeleteObject` | offered, not among the exceptions (the listed exception is conditional PUT/DELETE on *versioned* buckets, which we do not use). |
| `HeadBucket` | offered, not among the exceptions. |
| `CreateBucket` | offered with limits: only the headers `x-amz-bucket-object-lock-enabled` and `x-amz-acl` (`private`, `public-read`), and only `LocationConstraint` in `CreateBucketConfiguration`. The SDK sets neither header and sends only the location constraint, so the call is inside the limits. Production runs with `OBJECT_STORE_CREATE_BUCKET=false` anyway. |

No operation is unsupported, so nothing goes back to the human. Not used, and listed by Hetzner as
unsupported or limited: object tagging on buckets, `GetObjectAttributes`, `CopyObject` across buckets,
bucket policy `NotAction`, server-side encryption other than SSE-C (we set none).

### Addressing style Hetzner documents

Hetzner documents **virtual-hosted style** (the bucket in the host name), not path style:

- the endpoint includes the location: `fsn1.your-objectstorage.com`, `nbg1.your-objectstorage.com`,
  `hel1.your-objectstorage.com` (Overview, "Available endpoints"); the region value is that location;
- its S3 tool guides configure `mc` with `--path "off"` and `s3cmd` with the DNS-style template
  `%(bucket)s.fsn1.your-objectstorage.com`, and publish bucket URLs as
  `https://<bucket-name>.<location>.your-objectstorage.com/<file-name>`
  (<https://docs.hetzner.com/storage/object-storage/getting-started/using-s3-api-tools/>).

So production sets `OBJECT_STORE_PATH_STYLE=false`; SeaweedFS serves path style without wildcard DNS, so
the default stays `true`. A request signed for one style is refused by the other, so a wrong value is a
503 at the first attach and a startup ERROR, not silent corruption.

Production variables, for example Falkenstein: `OBJECT_STORE_ENDPOINT=https://fsn1.your-objectstorage.com`,
`OBJECT_STORE_REGION=fsn1`, `OBJECT_STORE_BUCKET=<the bucket provisioned in the console>`,
`OBJECT_STORE_ACCESS_KEY` / `OBJECT_STORE_SECRET_KEY` from the deployment's secrets,
`OBJECT_STORE_PATH_STYLE=false`, `OBJECT_STORE_CREATE_BUCKET=false`.

Amended 2026-10-17 (`carrier-invoice-retention`): retention runs as a daily scheduled job, plus once after
startup. It deletes the objects of files whose Client Invoice was approved at least 30 days earlier, keeps
the row with `deleted_at`, and never probes or sets availability. A failed run stops, and the next run
retries.

## Licences

- The AWS SDK for Java v2 is Apache-2.0 and is the only thing linked into the backend. Its Apache HTTP
  client needs a newer Apache HttpComponents than Spring Boot 3.3 manages, so `backend/pom.xml` overrides
  `httpclient5.version` and `httpcore5.version` to the versions the SDK release is built against (also
  Apache-2.0); the S3 client is their only user in this app.
- SeaweedFS is Apache-2.0 and runs as a separate, unmodified container the backend reaches over HTTP.
  Nothing of it is linked or distributed with the app.
- Hetzner Object Storage is a hosted service; no code of it is used.
- The image tag is pinned. The tests start it through the Testcontainers core `GenericContainer`
  already on the test classpath; no new test dependency.

## Consequences

- Moving stores is configuration plus a copy of the bucket.
- Local and production stores differ; behaviour specific to Hetzner is caught only by the human's
  production use, which is why the operations used are kept to five and checked against Hetzner's list.
- Upgrading the SeaweedFS image is a deliberate change of one tag in four places (compose, the test
  support class and both e2e backend scripts).
- Whole-file downloads are held in memory, capped at 20MB by the upload limit.
- A broken store configuration is found in the log, and (with the availability ticket) on screen, not by
  a failed deploy.
- SeaweedFS creates a missing bucket on the first write, Hetzner does not; that is why the startup
  check, not the write, decides whether the bucket is right.
- The SDK's default retries and the call timeouts (10s per attempt, 30s per call) are the only
  resilience; Hetzner's reported occasional instability is a contained 503 with an ERROR line.

## Rejected

- **MinIO**: its free edition ended (no community images since October 2025, archived in 2026, images
  removed from Docker Hub in September 2026), so a pinned tag never gets a security fix.
- **Garage**: AGPL-3.0, and a one-time layout and key setup at first start.
- **A volume for the filesystem directory**: no S3, and the human asked for object storage.
- **Bytes in Postgres.**
- **Readable keys** (names in the key): a name can hold any character and changes with the computed
  per-SIM names to come.
- **A bucket lifecycle rule**, which cannot count from approval.
- **Presigned URLs to the browser**, which would bypass the access guards.
- **Failing startup when the store is unreachable** (the spec's first draft), turned down by the human.
- **A background check of the store every 30 seconds** (the spec's second draft), turned down by the
  human for a check when invoice pages are opened.
