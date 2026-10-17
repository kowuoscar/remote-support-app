---
feature: carrier-invoice-object-storage
epic: carrier-invoices-per-sim
status: draft
date: 2026-10-17
---

<!-- sdlc:template spec 1 -->

# Carrier Invoice Files kept in an S3-compatible object store

## Problem

The epic `carrier-invoices-per-sim` starts here. The human asked on
2026-10-16 to "use an object storage for storing the files compatible with s3
such as minio". Today the bytes of a **Carrier Invoice File** sit on the
backend's local filesystem:

- `CarrierInvoiceFileStorage` writes `<root>/<client invoice id>/<file id>`
  under a directory set by `CARRIER_INVOICES_DIR` (default
  `./data/carrier-invoice-files`, relative to the backend's working directory).
  The row's `storage_path` holds that relative path.
- The compose backend has no volume for that directory. On the human's own
  stack (`rsa-prod`), every attached file is lost when the backend container
  is rebuilt. `docker-compose.yml` says so in a comment.
- Attaching writes the file and then saves the row, inside one transaction
  that holds the Client Invoice's row lock. A rollback after the write leaves
  the file behind with no row. The class's Javadoc claims the opposite. A slow
  disk, or a slow store, holds the lock that send and send-back wait on.
- The download header concatenates the uploaded name unescaped. A name with a
  quote or a non-ASCII letter yields a broken or misread header. Feature 2 of
  this epic will name every file "{Client} - {Month Year} - {Carrier} - {SIM
  number}", and Client names are not ASCII-only.
- The backend tests write real files into `backend/data`. A checkout holds
  about 1,700 leftover directories.
- Configuration is a scattered `@Value`, against coding standard Backend 14.

Settled by the human (2026-10-17), and not reopened here:

- The store is S3-compatible. The backend speaks the S3 API through the AWS
  SDK for Java v2, so any S3-compatible store works.
- **Production is Hetzner Object Storage** (S3-compatible; regions `fsn1`,
  `nbg1`, `hel1`; endpoints like `https://fsn1.your-objectstorage.com`). It
  is configured by environment only: endpoint, region, bucket, access key and
  secret. None of them is ever in the repo.
- **Locally, SeaweedFS** (Apache-2.0, one container serving the S3 API) stands
  in for it. It runs in docker compose with its own named volume, including the
  human's `rsa-prod` stack, in the backend tests through Testcontainers, and in
  both e2e backend scripts' throwaway stacks. MinIO is out: its free edition
  ended, and a pinned tag would never get a security fix.
- One private bucket. Object key `carrier-invoices/{tenant id}/{client invoice
  id}/{file id}` (choice A, ids, not readable names). The display name lives
  in the database and is given on download.
- **Objects carry no user metadata.** The Client, month, Carrier and SIM
  are not on the object. Which invoice or SIM a file belongs to lives only in
  its `carrier_invoice_files` row.
- No backfill: no Carrier Invoice File exists on the human's stack.
- Retention (feature 3) is done by the app, not a bucket lifecycle rule. The
  keys must let it delete one file.
- Nothing a user sees changes in this feature.

## Journeys

First feature of `docs/roadmap/carrier-invoices-per-sim.md`.

- **Bill the Client for the month** (`exists`, stays `exists`). Attaching and
  downloading carrier invoice files work exactly as before; only where the
  bytes live changes. Its proof spec,
  `client-invoice-submission-and-visibility.spec.ts`, and
  `client-invoice-generation.spec.ts` keep passing unedited. They now run
  against a real object store.

## Goals / Non-goals

Goals:

- Carrier Invoice File bytes are stored as objects in one private bucket of
  an S3-compatible store, keyed by ids, with no user metadata. They are read
  and written only by the backend.
- On the human's own stack, files survive any rebuild or restart of the
  backend container. SeaweedFS runs from `docker-compose.yml` with its own
  named volume.
- The same backend runs against Hetzner Object Storage in production, by
  environment alone.
- A failed attach leaves no object behind, and no object upload happens while
  the Client Invoice's row lock is held.
- A downloaded file keeps its exact name, whatever characters it holds.
- Backend tests and the isolated e2e stack run against a real SeaweedFS and
  leave nothing in the working tree.
- Storage configuration is one typed `@ConfigurationProperties` class.
- ADR 0007 records the storage choice.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No link to a SIM or a Carrier, no computed name, no form, no send gate, no
  removal of a file, no check mark.** Those are `carrier-invoice-per-sim`.
- **No retention, no scheduled job, no "Deleted on".** Those are
  `carrier-invoice-retention`. This feature adds the delete operation it needs
  for itself (undoing a failed attach), and nothing scheduled.
- **No migration of existing files and no backfill.** No row exists on the
  human's stack. No Flyway migration is added: the `storage_path` column keeps
  its name and type (Decisions taken).
- **No object metadata or tags**, user-defined or derived (Client, month,
  Carrier, SIM, filename). The store's own `Content-Type` on the object is not
  set from the upload either (Decisions taken).
- **No presigned URLs and no browser access to the store.** Downloads keep
  going browser → BFF → backend → store. The BFF routes, `forwardBinary` and
  every frontend file are unchanged.
- **No change to what an upload accepts**: still several files at once, any
  type, up to 20MB each, the uploaded name kept. Restricting types belongs to
  the per-SIM form.
- **No filesystem fallback** and no pluggable storage interface. The
  filesystem implementation, `CARRIER_INVOICES_DIR` and
  `app.file-storage.carrier-invoices-dir` are removed.
- **No streaming rewrite of downloads.** The backend still answers a download
  with the whole file in memory (Decisions taken).
- **No object-store probe in `/api/health`**, and no Actuator health
  contributor.
- **No Hetzner provisioning by this feature**: no bucket, credentials or
  deployment script for Hetzner, and no test, e2e run or acceptance step ever
  touches a Hetzner bucket. The production bucket is provisioned outside the
  app.
- **No backup job, no bucket versioning, no encryption settings, no
  SeaweedFS master, volume, filer or admin port exposed on a host port.**
- **No move to the `invoice` feature package.** The Client Invoices cluster
  has not moved (ADR 0006). New classes go in the existing layered packages.
- **The JWT `@Value`s are not converted.** Backend 14 is applied to the code
  this feature writes. `JwtService` is left as it is.

## User stories

Using the product, unchanged:

1. As an Agent, I want "Attach carrier invoice" on my Client Invoice draft to work as it does today (several files at once, any type, up to 20MB each, each listed under its own name), so that moving the storage costs me nothing.
2. As an Agent, I want to download a file I attached and get the same bytes under the same name and type, so that I can check what I sent.
3. As a Manager, I want to attach a file to any draft and download any invoice's files as today, so that my review is unchanged.
4. As a Tester, I want to download my Client's carrier invoice files from a sent or approved invoice as today, so that I can see the carrier's own bill.
5. As an Agent or a Manager, I want a file whose name holds accents, quotes or other non-ASCII characters (e.g. `Réf "Oct" 2026 – Orange.pdf`) to download under exactly that name, so that the computed names coming next in this epic keep their Client's spelling.
6. As an Agent, I want an attach that is refused or fails (the invoice was sent meanwhile, or the store is unavailable) to show today's "Couldn't attach one or more files. Try again." and leave no trace of the file, so that the list never shows a file that is not there and the store never keeps a file no invoice lists.
7. As an Agent, I want a large upload never to block the invoice's send or a Manager's send-back while it is being transferred, so that a slow upload does not freeze other work on the invoice.

Operating the stack:

8. As the human running my own stack, I want attached files kept when I rebuild or restart the backend container, so that an upgrade never loses a carrier invoice.
9. As the human, I want the files in a local SeaweedFS running from `docker-compose.yml`, with its own named volume, so that my `rsa-prod` stack gets it on the next rebuild with no extra step.
10. As the human, I want the store reachable only from inside the stack, with no host port and no public bucket, so that a carrier invoice is never served except through the app's access rules.
11. As the human, I want the backend to refuse to start when it cannot reach its bucket, with a log line that names the endpoint and bucket, so that a broken store is found at deploy time, not at the first upload.
12. As the human, I want an attach or download that hits an unavailable store to fail on its own with a clear log line, while the rest of the app keeps working, so that a store outage is visible and contained.
13. As the human, I want to point the backend at Hetzner Object Storage in production by environment alone (endpoint, region, bucket, access key, secret), with no credential in the repo, and moving between stores to be a configuration change plus a copy of the objects, so that neither SeaweedFS nor Hetzner locks me in.

For the team and the next features:

14. As the team, I want every object at `carrier-invoices/{tenant id}/{client invoice id}/{file id}`, with the key stored on the file's row, so that retention can delete one file by its row, and one Tenant's files stay under one prefix.
15. As the team, I want objects to carry no metadata about the Client, month, Carrier or SIM, so that the row stays the one source of truth and a correction never has to touch the store.
16. As the team, I want the backend integration tests and the isolated e2e suite to run against a real SeaweedFS, started and torn down like their Postgres, so that the store's real behaviour is tested and no file lands in the working tree.
17. As the team, I want the store's settings in one typed configuration class with dev-only defaults, so that Backend 14 holds and every environment variable is in one place.
18. As the team, I want ADR 0007 to record the store, the key shape, the SDK, the licences and what we rejected, so that the next reader knows why without reading this spec.
19. As the team, I want `CONTEXT.md`'s Carrier Invoice File entry to say where its bytes live, so that the glossary stops saying "local filesystem".

## Solution

### Prefactoring: debt paid on the way

- **Orphan on rollback.** The bytes are written before the transaction and
  removed if it fails (below). The wrong Javadoc on `CarrierInvoiceFileStorage`
  ("a failed write never leaves an orphaned DB reference… the controller
  writes the file before saving the row") is replaced.
- **An unescaped download header** is replaced by Spring's `ContentDisposition`
  builder (below).
- **`@Value` for storage** becomes a typed properties class (Backend 14).
- **Test residue in `backend/data`.** Nothing writes there any more. The
  `data/` entry and its comment leave `backend/.gitignore`. A leftover
  directory in a checkout can be deleted by hand; nothing reads it.

### Configuration

A new properties record, `ObjectStoreProperties`, bound to `app.object-store`,
in the `web` package beside the storage (the Client Invoices cluster is
unmoved, ADR 0006 / Backend 13):

```java
@ConfigurationProperties("app.object-store")
record ObjectStoreProperties(
    URI endpoint,          // OBJECT_STORE_ENDPOINT, dev default http://localhost:8333 (SeaweedFS S3)
    String region,         // OBJECT_STORE_REGION, default us-east-1
    String bucket,         // OBJECT_STORE_BUCKET, default remote-support-files
    String accessKey,      // OBJECT_STORE_ACCESS_KEY, dev-only default
    String secretKey,      // OBJECT_STORE_SECRET_KEY, dev-only default
    boolean pathStyleAccess,   // OBJECT_STORE_PATH_STYLE, default true (works with SeaweedFS)
    boolean createBucket)      // OBJECT_STORE_CREATE_BUCKET, default true: create a missing bucket at startup
```

`application.yml` gains the `app.object-store` block with `${ENV:default}`
values, and the same "dev-only default, override in every real environment"
comment the JWT secret carries. `app.file-storage` is removed.

The defaults match the local SeaweedFS only. The dev-only access key and
secret are not Hetzner's and never will be. In production on Hetzner Object
Storage, every value comes from the environment, for example:

| Variable | Hetzner production |
|---|---|
| `OBJECT_STORE_ENDPOINT` | `https://fsn1.your-objectstorage.com` (or `nbg1`, `hel1`) |
| `OBJECT_STORE_REGION` | the location, e.g. `fsn1` |
| `OBJECT_STORE_BUCKET` | the bucket provisioned in the Hetzner console |
| `OBJECT_STORE_ACCESS_KEY`, `OBJECT_STORE_SECRET_KEY` | the Hetzner S3 credentials, from the deployment's secrets |
| `OBJECT_STORE_PATH_STYLE` | whichever style the implementer confirms against Hetzner's docs |
| `OBJECT_STORE_CREATE_BUCKET` | `false`: the bucket is provisioned outside the app |

The `app.object-store` block's comment in `application.yml` names these
variables and says production sets all of them; it holds no Hetzner value.

### The S3 client

- **Dependency.** `software.amazon.awssdk:s3`, version managed by the AWS SDK
  v2 BOM (`software.amazon.awssdk:bom`) pinned to one exact 2.x release in
  `backend/pom.xml`. Apache-2.0. Main scope. Spring Cloud AWS is not added.
- **One `S3Client` bean**, built from `ObjectStoreProperties`, configured for
  a non-AWS store: endpoint override, static credentials, region, and the
  addressing style from `pathStyleAccess` (path-style by default, which
  SeaweedFS serves; virtual-hosted when it is off). It also gets:
  - request checksum calculation and response checksum validation set to
    "when required". Recent SDK 2.x releases send new default integrity
    checksums that some S3-compatible servers refuse.
  - a connection timeout of a few seconds and an overall call timeout of about
    30 seconds, so an unreachable store fails an attach quickly instead of
    hanging the request.
- **Only five S3 operations are used**: `PutObject`, `GetObject`,
  `DeleteObject`, `HeadBucket` and `CreateBucket` (tests also list objects and
  read tagging through their own client). The implementer checks each against
  Hetzner Object Storage's published list of supported S3 actions, and which
  addressing style Hetzner documents, and records both in ADR 0007. An
  operation Hetzner does not support is a stop: it goes back to the human.
- **Bucket at startup.** The backend always checks for the bucket at startup
  (`HeadBucket`). When it is absent and `createBucket` is true, the backend
  creates it. When it is absent and `createBucket` is false, or when the store
  cannot be reached, startup fails with one ERROR line naming the endpoint and
  the bucket (story 11). Compose, the tests and the e2e stacks leave the flag
  on. Production on Hetzner turns it off: the bucket is provisioned outside the
  app, and the app's credentials need no right to create one. The bucket is
  never made public.
- **Hetzner's reported occasional instability** needs nothing more: a failed
  call during a request is the 503 below, logged once, and the rest of the app
  keeps working (story 12). The SDK's default retries stand.

### `CarrierInvoiceFileStorage`, rewritten

It stays a concrete `@Component` in `web`, now over the S3 client. There is
no interface, because only one implementation exists and the tests use a
real SeaweedFS. Its whole surface:

```java
String keyFor(UUID tenantId, UUID clientInvoiceId, UUID fileId);
    // "carrier-invoices/" + tenantId + "/" + clientInvoiceId + "/" + fileId
void put(String key, MultipartFile file);   // streams the upload, size from the part
byte[] read(String key);                    // CarrierInvoiceFileMissingException when no such object
void delete(String key);                    // idempotent: deleting a missing key succeeds
```

- `put` sends no user metadata (`x-amz-meta-*`), no tags, and no
  `Content-Type` or `Content-Disposition` taken from the upload. The object
  is opaque bytes; its name, type, size and owner are on the row (story 15).
- Every store failure (`SdkException`, other than "no such key" on `read`)
  becomes `ObjectStoreUnavailableException`. That is a domain exception with
  `@ResponseStatus(SERVICE_UNAVAILABLE)` (Backend 7), logged once at ERROR
  with the operation and the key, never the credentials.
- `CarrierInvoiceFileMissingException` extends the existing
  `NotFoundException` (404), logged at WARN with the file id. A row that
  predates this feature, holding a filesystem path, lands here. No such row
  exists on the human's stack.

### Attaching and downloading: `CarrierInvoiceFileService`

A new non-transactional service in `web` owns the Carrier Invoice File
operations that touch the store. Both controllers
(`ClientInvoiceController`, `ClientInvoiceByIdController`) call it for upload
and download instead of `ClientInvoiceService`. The list of files is
unchanged.

**Attach**, in this order (Backend 3 and 8: the store call runs outside the
transaction that holds the row lock):

1. `requireNonEmpty` (unchanged).
2. Pick the file id. Compute the key with `keyFor(invoice's tenant, invoice
   id, file id)`. `put` the bytes. No lock is held.
3. Call `ClientInvoiceService.recordAttachedFile(found, fileId, key, metadata,
   principal)`. This is the old `attachFile` minus the storage call, still
   `@Transactional`: it re-reads the invoice under the row lock, requires
   `DRAFT` (409 otherwise) and open-to-Agent for an Agent, saves the row with
   `storage_path = key`, logs `AuditLog.created`, and returns the response.
4. If step 3 throws, for any reason including a failed commit, `delete(key)`
   and rethrow. If that delete fails too, log WARN with the key (a stray
   object, no row) and still rethrow the original exception.

A refused attach (a sent invoice) therefore uploads and then deletes. The UI
never offers an attach on a sent invoice, so that is rare. One path for every
failure is simpler than adding a pre-check.

**Download**: find the row by file id and invoice (404 as today), `read` the
stored key, and answer it. Unchanged:

- the status;
- the body, the whole file;
- the `Content-Type`, which is the type recorded on the row.

Two things change:

- **`Content-Disposition`** is built with `ContentDisposition.attachment()
  .filename(name, UTF_8)`, never by concatenation. A name is carried in
  `filename*` (RFC 6266 / RFC 5987), so every character survives (story 5),
  and a plain ASCII name downloads under the same name as today.
- **`X-Content-Type-Options: nosniff`** is added, because the type is the one
  the uploader's browser declared.

`ClientInvoiceService` loses its `CarrierInvoiceFileStorage` dependency, its
`attachFile` and its `downloadFile`. Its class Javadoc stops listing the
Carrier Invoice Files, except for the list read.

### Schema

No migration. `carrier_invoice_files.storage_path` keeps its name, and its
value is now the object key. Code always reads the key from the row and never
rebuilds it from ids, so a later key change touches no row. The entity's
Javadoc and the column's meaning in `CONTEXT.md` say "object key".

### docker-compose.yml

- A new **`seaweedfs`** service:
  - the `chrislusf/seaweedfs` image, pinned to one exact `<version>` tag the
    implementer confirms exists and is current, the same tag the tests and
    both e2e scripts use;
  - run as a single all-in-one server with its S3 gateway on (e.g. `server
    -dir=/data -s3`), its data in a new named volume `seaweedfs_data`;
  - S3 credentials for the local store configured the way the pinned version
    supports (an S3 config JSON mounted or generated in the container, or its
    environment; the implementer picks the mechanism). They are **dev-only
    defaults written in the compose file**, the way `POSTGRES_PASSWORD` is, and
    are never Hetzner's;
  - **no host port**: the S3 port (8333) is reachable only on the compose
    network, and no master, volume, filer or admin port is published;
  - a healthcheck the pinned image supports that proves the S3 gateway
    answers (e.g. an HTTP probe of port 8333 from inside the container).
- **`backend`** gains the `OBJECT_STORE_*` environment, pointing at
  `http://seaweedfs:8333` with the same dev-only credentials. It `depends_on`
  `seaweedfs` with `service_healthy`.
  - The comment saying uploads are lost on rebuild is replaced. Files now
    live in `seaweedfs_data` and survive a rebuild.
  - The demo-reset instructions also name `seaweedfs_data` when you want a
    clean store.
- `volumes:` gains `seaweedfs_data`.
- The human's `rsa-prod` project is `docker-compose.yml` plus an override that
  sets `SPRING_PROFILES_ACTIVE=prodlike`. It picks up the service and its
  volume (`rsa-prod_seaweedfs_data`) on its next `up --build`, with no change
  to the override (story 9). `rsa-prod` uses the local SeaweedFS, not
  Hetzner.

### The e2e scripts

- **`scripts/run-backend-for-e2e-isolated.sh`** starts a throwaway SeaweedFS
  the way it starts its throwaway Postgres:
  - `docker run -d --rm` on the pinned `chrislusf/seaweedfs` tag, with an
    exact name and a host port mapped to its S3 port, both passed in, and the
    same dev-only credentials as compose;
  - it waits until the S3 gateway answers;
  - it exports `OBJECT_STORE_ENDPOINT` and the credentials before
    `mvn spring-boot:run`;
  - `cleanup` stops the SeaweedFS container by its exact name, as it does
    Postgres.

  Two places pass the name and port through:
  - `scripts/run-e2e-isolated.sh` picks `E2E_ISOLATED_SEAWEEDFS_PORT` with
    `free_port` and names the container
    `remote-support-e2e-seaweedfs-$$-${RANDOM}`;
  - `frontend/playwright.e2e.isolated.config.ts` adds both to the script's
    arguments.
- **`scripts/run-backend-for-e2e.sh`** (the non-isolated path) does the same,
  with its own container name, a free port and a cleanup trap. It does not use
  the compose `seaweedfs`, which publishes no port.

### ADR 0007

`docs/adr/0007-carrier-invoice-files-in-an-s3-compatible-object-store.md`,
status `proposed`. The human's approval of this spec accepts it. It records:

- **Context**: the filesystem storage and its loss on rebuild, the human's
  request, and retention counting from approval.
- **Decision**:
  - the S3 API through AWS SDK v2, configured for non-AWS stores (checksums
    only when required, configurable addressing style);
  - Hetzner Object Storage in production, configured by environment only;
  - SeaweedFS locally (compose, `rsa-prod` included) and in tests and e2e
    stacks;
  - one private bucket;
  - keys by ids;
  - no object metadata;
  - the key stored on the row;
  - retention done by the app;
  - the browser never reaches the store;
  - the bucket checked at startup, created by the app only locally;
    provisioned outside the app on Hetzner;
  - the S3 operations used, checked against Hetzner's supported actions, and
    the addressing style Hetzner needs.
- **Licences**:
  - The AWS SDK v2 is Apache-2.0, and it is the only thing linked into the
    backend.
  - SeaweedFS is Apache-2.0 and runs as a separate, unmodified container that
    the backend reaches over HTTP. Nothing of it is linked or distributed
    with the app.
  - Hetzner Object Storage is a hosted service; no code of it is used.
  - The image tag is pinned. The tests start it through the Testcontainers
    core `GenericContainer` already on the test classpath; no new test
    dependency.
- **Consequences**:
  - Moving stores is configuration plus a copy of the bucket.
  - Local and production stores differ; behaviour specific to Hetzner is
    caught only by the human's production use, which is why the operations
    used are kept few and checked against Hetzner's list.
  - Upgrading the SeaweedFS image is a deliberate tag change.
  - Whole-file downloads are held in memory, capped at 20MB.
- **Rejected**:
  - MinIO: its free edition ended (no community images since October 2025,
    archived in 2026, images removed from Docker Hub in September 2026), so a
    pinned tag never gets a security fix;
  - Garage: AGPL-3.0, and a one-time layout and key setup at first start;
  - a volume for the filesystem directory: no S3, the human asked for
    object storage;
  - bytes in Postgres;
  - readable keys (B);
  - a bucket lifecycle rule (C), which cannot count from approval;
  - presigned URLs to the browser, which would bypass the access guards.

## Design direction

N/A — no user interface changes. The design slot is not engaged.

## Constraints

- Inherits every constraint of the `remote-support-mvp` and
  `send-a-client-invoice-back` specs on Client Invoices, except where this
  spec states otherwise.
- **HTTP contract unchanged.** Same routes, statuses, response bodies and
  `CarrierInvoiceFileResponse`. Additions only:
  - the RFC 6266 form of `Content-Disposition`;
  - `X-Content-Type-Options: nosniff`;
  - a 503 when the store is unavailable, where today an I/O failure is a 500.
- **Object key**: exactly `carrier-invoices/{tenant id}/{client invoice
  id}/{file id}`, lowercase canonical UUIDs, one private bucket.
- **No user metadata or tags on any object** (the human, 2026-10-17).
- **No store call inside a transaction holding a Client Invoice row lock**
  (Backend 8).
- **Main-scope dependency added**: the AWS SDK v2 `s3` artifact only (with
  what it pulls), Apache-2.0, pinned through its BOM. **Test scope**: nothing
  new; SeaweedFS starts through the Testcontainers core `GenericContainer`
  (there is no SeaweedFS module).
- **Images pinned**: `chrislusf/seaweedfs:<exact version>` in compose, in the
  tests and in both e2e scripts, in one tag. Never `latest`.
- **Store configuration by environment only.** No Hetzner endpoint, bucket,
  access key or secret is ever committed. The dev-only defaults in
  `application.yml`, compose and the scripts are for the local SeaweedFS.
- **S3 operations limited** to `PutObject`, `GetObject`, `DeleteObject`,
  `HeadBucket` and `CreateBucket` in main code, each supported by Hetzner
  Object Storage.
- **No migration.** The latest Flyway number is not touched.
- **Upload limit unchanged**: 20MB per file and per request.
- Every isolated run (tests, e2e, acceptance) uses its own throwaway SeaweedFS
  and Postgres. Nothing touches the human's compose stack, its ports
  3000/8080/5432, its volumes, or any Hetzner bucket. The e2e suite runs on an
  idle machine, and a failure is never called a flake without a controlled
  comparison.
- The backend never logs the store's credentials.

## Testing decisions

Tests assert external behaviour: HTTP status, headers and bytes, and what is
in the bucket, read through an S3 client. They never assert calls on the SDK
in an integration test. The store is real wherever a Spring context boots:
the human settled SeaweedFS in tests, and a fake would hide the S3 behaviour
that matters (checksums, path style, missing keys), as Backend 5 says of a
mocked database. No test reaches Hetzner.

1. **The test seam: one shared SeaweedFS, beside the shared Postgres.** A new
   test support class starts a singleton `GenericContainer` on the pinned
   `chrislusf/seaweedfs` tag once per JVM, with the S3 gateway on, dev-only
   credentials, its S3 port exposed and a wait strategy on that port. It is
   never stopped and is reaped by Ryuk, the way `IntegrationTest` starts
   Postgres. It publishes its endpoint and credentials to the context through
   `@DynamicPropertySource`. There is no Testcontainers SeaweedFS module and
   no `@ServiceConnection` for it.
   - Both contexts that boot use it: `IntegrationTest` and
     `DemoDataLoaderApiTest`, which has its own context under `demo`.
   - It also offers test helpers: list the keys under a prefix, read an
     object's bytes, and head an object's user metadata.
   - Prior art: `IntegrationTest`'s static `POSTGRES` and the class Javadoc
     on why it is never stopped.
2. **The HTTP seam: the existing Client Invoice API tests, unedited in what
   they assert.** `ClientInvoiceApiTest` (attach/list/download around lines
   494, 543, 611, 693), `ClientInvoiceByIdApiTest` (:197) and
   `ClientInvoiceSendBackApiTest` (:560, :618-657, :751) pass against SeaweedFS.
   Proving that is the point. New cases go in `ClientInvoiceApiTest`, at the
   same seam:
   - after an attach, exactly one object is at
     `carrier-invoices/{seeded tenant}/{invoice}/{file id}`, with the
     uploaded bytes and no `x-amz-meta-*`;
   - a download of a name with accents and a double quote answers a
     `Content-Disposition` whose `filename*` decodes to that exact name, and
     `nosniff`;
   - an attach to a sent invoice is 409 and leaves no object under that
     invoice's prefix. That is the undo of a failed attach;
   - a row whose object is missing (deleted through the test helper) is a 404
     on download.
3. **Unit tests (JUnit 5 + Mockito, Backend 6)** for the parts no real store
   can show on demand:
   - `CarrierInvoiceFileStorage` over a mocked `S3Client`: an `SdkException`
     becomes `ObjectStoreUnavailableException`; `NoSuchKey` on read becomes
     `CarrierInvoiceFileMissingException`; `put` sends no metadata;
     `keyFor` gives the exact shape.
   - `CarrierInvoiceFileService` over a mocked storage and a mocked
     `ClientInvoiceService`:
     - an exception from `recordAttachedFile` deletes the key and rethrows;
     - a failing delete still rethrows the original exception;
     - a store failure on `put` never calls `recordAttachedFile`.
4. **Startup**: a context-level test that `createBucket` creates a missing
   bucket. The shared test bucket starts absent, so every context boot
   exercises it. The bucket-startup logic (absent bucket with the flag off
   fails; present bucket is not recreated) is unit-tested over a mocked
   `S3Client`. A store unreachable at startup is shown in the acceptance
   walkthrough.
5. **End to end: the existing specs, unedited**, on the isolated stack with
   its throwaway SeaweedFS: `client-invoice-generation.spec.ts` (attach and
   download, :26, :80-88) and `client-invoice-submission-and-visibility.spec.ts`
   (:68). Prior art for the stack: `run-backend-for-e2e-isolated.sh`'s
   Postgres.
6. **Visual goldens**: unaffected. The stub backend serves `files: []`.
7. **`verify`** stays the command in `docs/agents/sdlc.json`, unchanged. After
   a full run, `backend/data` is not created.

## Decisions taken

- **Settled by the human (2026-10-17), answering this spec's former open
  question on the server: Hetzner Object Storage in production; SeaweedFS
  locally** (docker compose, `rsa-prod` included), in the integration tests
  and in both e2e backend scripts' throwaway stacks. Reason: the human works
  with Hetzner Object Storage ("I am working with hetzner object storage").
  MinIO is out because its free edition ended (no community images since
  October 2025, archived in 2026, images removed from Docker Hub in September
  2026), so a pinned tag never gets a security fix. SeaweedFS is Apache-2.0,
  maintained, and one container.
- **The startup `HeadBucket` runs whatever the flag; `createBucket` only
  decides whether a missing bucket is created or fails startup.** Reason: on
  Hetzner the flag is off, and story 11 still wants a broken store or a wrong
  bucket name found at deploy time.
- **`pathStyleAccess` stays a flag, default path-style, env
  `OBJECT_STORE_PATH_STYLE`.** Reason: SeaweedFS serves path-style without a
  wildcard DNS name; Hetzner's required style is set by environment once the
  implementer confirms it, with no code change.
- **The main code uses five S3 operations, each checked against Hetzner's
  supported actions, and an unsupported one goes back to the human.** Reason:
  local tests run on SeaweedFS, so Hetzner-only gaps would surface only in
  production; a small, checked surface is the guard.
- **No retry beyond the SDK's defaults for Hetzner's reported occasional
  instability.** Reason: a failed call is already a contained 503 with an
  ERROR line, and the user retries with today's "Try again." text.
- **The SeaweedFS S3 credential mechanism (config JSON or environment) is left
  to the implementer; the values are dev-only defaults in compose and the
  scripts.** Reason: it depends on the pinned version, and it never reaches
  production, where Hetzner's keys come from the environment.
- **The upload happens before the transaction, and a failed transaction
  deletes the object.** Reason: Backend 8 forbids an external call inside a
  transaction. A 20MB upload under the invoice's row lock would stall send and
  send-back. Uploading after commit would leave a row whose bytes may never
  arrive. Undoing a failed attach is the only order in which a reader never
  sees a row without its object.
- **A new non-transactional `CarrierInvoiceFileService` orchestrates; the
  locked write stays in `ClientInvoiceService.recordAttachedFile`.** Reason:
  Backend 3 keeps the transaction boundary on a service method, and a
  Spring proxy does not apply on a self-call. It also takes the file concerns
  out of a large service.
- **No pre-check before the upload; a refused attach uploads and then
  deletes.** Reason: one path for every failure, and that path is the one the
  409 test proves. The UI never offers an attach on a sent invoice.
- **`storage_path` keeps its name and now holds the object key, and the key is
  always read from the row.** Reason: no migration, the column was designed
  as an opaque handle, and a future key change touches no row.
- **No migration for old rows; a row whose object is missing answers 404 with
  a WARN.** Reason: the human has none ("nothing to backfill"). Only dev and
  test databases might hold one.
- **Objects carry no `Content-Type` from the upload either; the row's type is
  served.** Reason: the human ruled out object metadata. The row is already
  the source, and an object with a declared type invites serving it straight
  from the store.
- **`CarrierInvoiceFileStorage` stays one concrete class over `S3Client`, with
  no interface, and the filesystem implementation is deleted.** Reason: only
  one implementation is wanted, the tests use a real SeaweedFS, and an interface
  with one implementation adds nothing. Retention will call its `delete`.
- **Configuration: `ObjectStoreProperties` under `app.object-store`, env
  `OBJECT_STORE_*`, dev-only defaults pointing at a local SeaweedFS on
  `localhost:8333`.** Reason:
  Backend 14. The prefix names the store, not carrier invoices, because the
  key prefix already scopes them, so one bucket can serve a later file kind.
- **The bucket is named `remote-support-files` by default.** Reason: it holds
  the `carrier-invoices/` prefix, not only one kind. The name is a config
  value.
- **The bucket is ensured at startup, and startup fails when the store is
  unreachable.** Reason: a broken store is found at deploy time. Compose
  starts the backend only once SeaweedFS is healthy, and a flag turns the
  creation off for a pre-provisioned store (Hetzner).
- **`/api/health` does not probe the store.** Reason: it is the compose
  liveness check. Tying it to the store would take the whole app (and the
  frontend's start) down with a store outage that only touches attachments.
- **A store failure during a request is a 503 (`ObjectStoreUnavailableException`)
  with one ERROR line; the frontend shows its existing failure text.**
  Reason: Backend 7. It is visible and contained, and no copy changes.
- **Downloads stay whole-file `byte[]`.** Reason: the BFF's `forwardBinary`
  buffers the whole body anyway, and files are capped at 20MB. Streaming would
  change two controllers and the BFF for no gain today.
- **`Content-Disposition` is built by Spring's `ContentDisposition` with UTF-8,
  plus `nosniff`.** Reason: the per-SIM computed names will hold Client names
  with any characters. Concatenation breaks on a quote. The stored type is
  whatever the uploader declared.
- **SDK checksums "when required", short connect and call timeouts.** Reason:
  recent SDK 2.x defaults send checksums some S3-compatible servers reject,
  and an unreachable store should fail an attach in seconds.
- **SeaweedFS publishes no host port in compose; the e2e scripts start their
  own throwaway SeaweedFS.** Reason:
  - the human's stack keeps only its three known ports;
  - the store stays private (story 10);
  - the scripts already own their throwaway Postgres the same way.
- **Tests start SeaweedFS with the core Testcontainers `GenericContainer`.**
  Reason: there is no SeaweedFS module, and the core is already on the test
  classpath, so no test dependency is added.
- **One shared test SeaweedFS, wired by `@DynamicPropertySource`, used by
  `IntegrationTest` and `DemoDataLoaderApiTest`.** Reason: the singleton
  container is the suite's existing pattern, and those are the only two
  contexts that boot.
- **ADR 0007 is written by this feature, `proposed`, and accepted with the
  spec.** Reason: the epic left "an ADR for the storage choice" open. The
  choice is hard to reverse (the stored data's location and key contract) and
  the result of a trade-off.
- **`track` is `full`.** Reason: it adds a main-scope dependency, a compose
  service and a volume on the human's stack, and changes the e2e
  infrastructure.
- **Two tickets: the store first, the download name second.** Reason: the
  store swap must land with compose, scripts and the test seam together, or
  `verify` and the human's stack break between tickets. The header fix is
  independent and small. Both go in one PR.

## Open questions

None

## Acceptance walkthrough

The `[agent]` steps run against an isolated stack the agent starts and tears
down itself, never the human's compose stack, ports 3000/8080/5432, its
volumes, or any Hetzner bucket. The stack is:

- a throwaway Postgres and a throwaway SeaweedFS (`docker run --rm`, exact
  names, free ports, the pinned `chrislusf/seaweedfs` tag, dev-only
  credentials);
- the backend on the `demo` profile on a free port, with `OBJECT_STORE_*`
  pointing at that SeaweedFS;
- a production build of the frontend on a free port.

Logins are in the root `README.md`; Jordan is the demo Agent. "List the
bucket" means an S3 API listing against the throwaway SeaweedFS's S3 port
with its dev-only credentials (e.g. `aws s3api list-objects-v2
--endpoint-url http://localhost:<port> --bucket remote-support-files`).

1. [agent] Run `verify`. Show it green, and show that `backend/data` was not created. Show `docker ps` listing no SeaweedFS or Postgres container left from the run. (stories: 16, 17)
2. [agent] As Jordan, on the current-month draft at `/agent/client-invoices`, attach two files at once: a PDF named `Réf "Oct" 2026 – Orange.pdf` and a PNG. Show both listed under their names. Download each from the page, and show the saved name equals the original exactly and the bytes' SHA-256 equals the uploaded file's. (stories: 1, 2, 5)
3. [agent] List the bucket in SeaweedFS through the S3 API. Show exactly two objects, at `carrier-invoices/{tenant id}/{that invoice id}/{file id}`, whose file ids are the ones the invoice's file list returns. Head each and show no `x-amz-meta-*` and no tags. Query `carrier_invoice_files` and show each row's `storage_path` equals its key. (stories: 14, 15)
4. [agent] As the Manager, open that invoice's `/manager/invoices/client/{id}`, attach a third file and download one of Jordan's. As Jordan, send the invoice. As the Tester of that Client, download a file from the Invoices page. Show the same bytes and name. (stories: 3, 4)
5. [agent] Through the API, as Jordan, POST a file to the now-sent invoice. Show a 409, and the bucket still holding exactly the three objects under that invoice's prefix. (stories: 6)
6. [agent] Restart the backend process, then restart the SeaweedFS container (`docker restart`, the same container). Show every file still downloads with the same bytes. (stories: 8)
7. [agent] Stop the SeaweedFS container. Show:
   - attaching on a draft shows "Couldn't attach one or more files. Try again." and the backend logs one ERROR naming the store operation and no credentials;
   - the invoice page, its list of files and the Fleet page still load;
   - start SeaweedFS again and the attach works.

   Then, with SeaweedFS stopped, start the backend. Show it refuses to start with an ERROR naming the endpoint and the bucket. With SeaweedFS running, start the backend with `OBJECT_STORE_CREATE_BUCKET=false` and `OBJECT_STORE_BUCKET` set to a bucket that does not exist; show it refuses to start the same way and creates no bucket. (stories: 11, 12, 13)
8. [agent] Run `docker compose -f docker-compose.yml config` (read-only; nothing is started). Show:
   - a `seaweedfs` service on the pinned `chrislusf/seaweedfs` tag, with the `seaweedfs_data` volume, no `ports`, a healthcheck, and dev-only S3 credentials;
   - `backend` with `OBJECT_STORE_ENDPOINT=http://seaweedfs:8333` and a `depends_on` on `seaweedfs` being healthy;
   - `seaweedfs_data` declared.

   Grep the repository for `your-objectstorage.com` and show it appears only in docs, never with a key or secret. Show `application.yml`'s `app.object-store` block reading every value from `OBJECT_STORE_*`. Show ADR 0007, including the S3 operations checked against Hetzner's supported actions and the addressing style, and the amended Carrier Invoice File entry. (stories: 9, 10, 13, 17, 18, 19)
9. [human] On your own stack (`rsa-prod`), rebuild with your usual `up -d --build`; it now also starts the local SeaweedFS. Open a Client Invoice draft, attach a carrier invoice and download it. Then rebuild the backend alone (`up -d --build --force-recreate backend`), reopen the invoice, and download it again. Confirm it is the same file, under the same name. (stories: 1, 2, 8, 9)

## Execution order

Two tickets, one PR. The second depends on the first only because both touch
the download path in `CarrierInvoiceFileService`.

1. `carrier-invoice-files-in-object-store`. It covers:
   - the AWS SDK dependency and `ObjectStoreProperties`;
   - the `S3Client` bean and the bucket ensured at startup;
   - `CarrierInvoiceFileStorage` rewritten over S3, with the filesystem code,
     `app.file-storage` and the `backend/.gitignore` entry removed;
   - `CarrierInvoiceFileService` (attach with undo on failure, download) and
     `ClientInvoiceService.recordAttachedFile`;
   - `ObjectStoreUnavailableException` and `CarrierInvoiceFileMissingException`;
   - the shared test SeaweedFS (`GenericContainer`) for `IntegrationTest` and
     `DemoDataLoaderApiTest`;
   - the new `ClientInvoiceApiTest` cases for the key, no metadata, the 409
     undo and the missing object, and the unit tests;
   - the `seaweedfs` service, the `seaweedfs_data` volume and the backend env
     in `docker-compose.yml`;
   - throwaway SeaweedFS in both e2e backend scripts, `run-e2e-isolated.sh`
     and the isolated Playwright config;
   - the check of the S3 operations and addressing style against Hetzner
     Object Storage's documentation;
   - ADR 0007;
   - the declared `CONTEXT.md` terms and `ARCHITECTURE.md` entries.

   Labels: backend, infra. No dependency. (stories: 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
2. `carrier-invoice-file-download-name`: `Content-Disposition` built with
   `ContentDisposition` and UTF-8, `X-Content-Type-Options: nosniff`, and the
   `ClientInvoiceApiTest` case with an accented, quoted name. Labels: backend.
   Depends on `carrier-invoice-files-in-object-store`. (stories: 5)
