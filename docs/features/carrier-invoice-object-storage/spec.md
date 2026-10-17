---
feature: carrier-invoice-object-storage
epic: carrier-invoices-per-sim
status: approved
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

Amended by the human at the approval gate (2026-10-17): "Let's not block the
backend startup if the object storage configuration is failing. Aside from
that, upload and download should be deactivated on the frontend." Settled
from it:

- **The backend always starts**, whatever the store's state or configuration:
  unreachable endpoint, wrong credentials, missing bucket, malformed or missing
  settings. At startup it still checks the bucket (and creates it when the
  create-bucket flag is on), and on failure logs one clear ERROR naming the
  endpoint and the bucket.
- **The backend knows whether file storage is available and tells the
  frontend.** It re-checks the store only when an invoice page is opened,
  never in the background (the human, 2026-10-17: "Let's go with the
  alternative when invoice pages are opened"), so it recovers without a
  restart. A failed upload or download also marks it unavailable. An attach
  or download that reaches the backend while it is unavailable still answers
  503: the server stays authoritative.
- **While file storage is unavailable**, wherever files are attached or
  downloaded (the Agent's Client Invoice card, the Manager's invoice detail,
  the Tester's Invoices page), "Attach carrier invoice" and every file's
  Download are disabled, one short notice explains why, and the file names
  are still listed. The controls come back by themselves once the store is
  back: the first page opened after that may still show them paused, the
  next load shows them working. Send, send-back and approve are not affected
  by this feature.
- Apart from that paused state and the exact download name, nothing a user
  sees changes in this feature.

## Journeys

First feature of `docs/roadmap/carrier-invoices-per-sim.md`.

- **Bill the Client for the month** (`exists`, stays `exists`). Attaching and
  downloading carrier invoice files work exactly as before; only where the
  bytes live changes. Its proof spec,
  `client-invoice-submission-and-visibility.spec.ts`, and
  `client-invoice-generation.spec.ts` keep passing unedited. They now run
  against a real object store. While the store is unavailable, the journey's
  file steps pause (Attach and Download disabled, with a notice); every other
  step of it (lines, fees, send, send-back, approve, the PDF) carries on.

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
- The backend starts whatever the store's state, reports whether file
  storage is available, and recovers by itself when invoice pages are opened
  after the store comes back, with no background check and no restart.
- While file storage is unavailable, the three screens that show carrier
  invoice files list them by name with Attach and Download disabled and one
  notice, instead of offering controls that fail.
- Backend tests and the isolated e2e stack run against a real SeaweedFS and
  leave nothing in the working tree.
- Storage configuration is one typed `@ConfigurationProperties` class.
- ADR 0007 records the storage choice.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No link to a SIM or a Carrier, no computed name, no form, no send gate, no
  removal of a file, no check mark.** Those are `carrier-invoice-per-sim`.
  Note for that feature: its send gate (a file per SIM before send) must
  decide what happens while file storage is unavailable. This feature leaves
  send, send-back and approve exactly as they are, store up or down.
- **No live update of the paused state.** No polling, server push or timer in
  the browser: the controls follow the availability the page was loaded with,
  and change on a later load or refresh (Decisions taken).
- **No background availability check.** No `@Scheduled` task, timer or
  polling thread re-checks the store: nothing runs while nobody opens an
  invoice page (the human, 2026-10-17). This rule is about availability
  only. It does not decide how `carrier-invoice-retention` runs its daily
  deletion, which is that feature's choice.
- **No new status endpoint and no BFF route for availability.** It rides on
  the Client Invoice response every one of the three screens already loads.
- **No paused state anywhere else.** The Fleet, the PDF download, the
  dashboards and every other control are untouched by the store's state. A
  single file whose object is missing (404) is not "storage unavailable" and
  keeps today's behaviour.
- **No retention, no deletion job, no "Deleted on".** Those are
  `carrier-invoice-retention`, which is free to add its own daily job. This
  feature adds the delete operation it needs for itself (undoing a failed
  attach), and nothing scheduled.
- **No migration of existing files and no backfill.** No row exists on the
  human's stack. No Flyway migration is added: the `storage_path` column keeps
  its name and type (Decisions taken).
- **No object metadata or tags**, user-defined or derived (Client, month,
  Carrier, SIM, filename). The store's own `Content-Type` on the object is not
  set from the upload either (Decisions taken).
- **No presigned URLs and no browser access to the store.** Downloads keep
  going browser → BFF → backend → store. The BFF routes and `forwardBinary`
  are unchanged. The frontend changes only for the paused state.
- **No change to what an upload accepts**: still several files at once, any
  type, up to 20MB each, the uploaded name kept. Restricting types belongs to
  the per-SIM form.
- **No filesystem fallback** and no pluggable storage interface. The
  filesystem implementation, `CARRIER_INVOICES_DIR` and
  `app.file-storage.carrier-invoices-dir` are removed.
- **No streaming rewrite of downloads.** The backend still answers a download
  with the whole file in memory (Decisions taken).
- **No object-store probe in `/api/health`**, and no Actuator health
  contributor. The availability probe below is internal to the backend.
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
11. As the human, I want the backend to start whatever the store's state or configuration (unreachable, wrong credentials, missing bucket, bad settings), logging one ERROR that names the endpoint and the bucket, so that a store problem never takes the whole app down and is still found in the log at deploy time.
12. As the human, I want an attach or download that hits an unavailable store to fail on its own with a clear log line, while the rest of the app keeps working, so that a store outage is visible and contained.
13. As the human, I want to point the backend at Hetzner Object Storage in production by environment alone (endpoint, region, bucket, access key, secret), with no credential in the repo, and moving between stores to be a configuration change plus a copy of the objects, so that neither SeaweedFS nor Hetzner locks me in.

For the team and the next features:

14. As the team, I want every object at `carrier-invoices/{tenant id}/{client invoice id}/{file id}`, with the key stored on the file's row, so that retention can delete one file by its row, and one Tenant's files stay under one prefix.
15. As the team, I want objects to carry no metadata about the Client, month, Carrier or SIM, so that the row stays the one source of truth and a correction never has to touch the store.
16. As the team, I want the backend integration tests and the isolated e2e suite to run against a real SeaweedFS, started and torn down like their Postgres, so that the store's real behaviour is tested and no file lands in the working tree.
17. As the team, I want the store's settings in one typed configuration class with dev-only defaults, so that Backend 14 holds and every environment variable is in one place.
18. As the team, I want ADR 0007 to record the store, the key shape, the SDK, the licences and what we rejected, so that the next reader knows why without reading this spec.
19. As the team, I want `CONTEXT.md`'s Carrier Invoice File entry to say where its bytes live, so that the glossary stops saying "local filesystem".

When file storage is unavailable:

20. As an Agent, I want my Client Invoice card to keep listing the files by name while file storage is unavailable, with "Attach carrier invoice" and each file's Download disabled and one short notice saying why, so that I don't try an attach that can only fail and I know it is not my doing.
21. As a Manager, I want the same on an invoice's detail view (names listed, Attach and Download disabled, the notice), so that my review shows what is attached even while the files can't be fetched.
22. As a Tester, I want my Client's invoice on the Invoices page to keep listing its carrier invoice files with Download disabled and the notice, so that I can see the files exist and that they will come back.
23. As an Agent, a Manager or a Tester, I want Attach and Download to work again by themselves once file storage is back, at worst on my second page load after it returns, without anyone restarting anything, so that an outage ends by itself.
24. As an Agent or a Manager, I want everything else on the invoice (lines, fees, the PDF, send, send-back, approve) to work as usual while file storage is unavailable, so that a store outage only pauses files.
25. As the human, I want the backend to re-check the store only when an invoice page is opened and its last check is more than 30 seconds old, in the background of that request so the page never waits on the store, and to mark it unavailable as soon as an upload or download fails, so that nothing runs while nobody uses the app, the screens follow the store's real state one reload late at worst, and an outage is logged once.

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
- **Three copies of the files section** (Agent card, Manager detail, Tester
  Invoices page) become one shared `CarrierInvoiceFilesSection`, so the paused
  state is written and tested once (below).

### Configuration

A new properties record, `ObjectStoreProperties`, bound to `app.object-store`,
in the `web` package beside the storage (the Client Invoices cluster is
unmoved, ADR 0006 / Backend 13):

```java
@ConfigurationProperties("app.object-store")
record ObjectStoreProperties(
    String endpoint,       // OBJECT_STORE_ENDPOINT, dev default http://localhost:8333 (SeaweedFS S3); parsed when the client is built, never at binding
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
- **Bucket at startup, never fatal.** The backend always checks for the bucket
  at startup (`HeadBucket`). When it is absent and `createBucket` is true, the
  backend creates it. When it is absent and `createBucket` is false, or when
  the store cannot be reached or refuses the credentials, the backend **logs
  one ERROR line naming the endpoint and the bucket and starts anyway**, with
  file storage marked unavailable (story 11). A setting that cannot even build
  a client (an endpoint that is not a URL, a blank bucket or region) is the
  same: one ERROR naming the setting, never its value when it is a
  credential, and file storage stays unavailable until a restart with a
  fixed configuration. Nothing about the store can fail the Spring context.
  Compose, the tests and the e2e stacks leave the flag on. Production on
  Hetzner turns it off: the bucket is provisioned outside the app, and the
  app's credentials need no right to create one. The bucket is never made
  public.
- **Hetzner's reported occasional instability** needs nothing more: a failed
  call during a request is the 503 below, logged once, marks file storage
  unavailable until the next successful check (triggered by the next invoice
  page opened once the last check is 30 seconds old), and the rest of the app
  keeps working (stories 12, 25). The SDK's default retries
  stand.

### File storage availability: `ObjectStoreAvailability`

A new `@Component` in `web`, beside the storage, owns one fact: is file
storage available right now. Its surface:

```java
boolean isAvailable();          // the cached answer; never calls the store, never starts a check
boolean availableForPage();     // the cached answer at once; starts one asynchronous probe when the last check is stale
void markUnavailable();         // called by CarrierInvoiceFileStorage on a store failure
void probe();                   // ensure the bucket (HeadBucket, then CreateBucket when absent and createBucket is on)
```

- **No background schedule.** There is no `@Scheduled` task, no timer and no
  polling thread. Nothing calls the store while nobody opens an invoice page
  (the human, 2026-10-17).
- **Once at startup.** `probe()` runs once on `ApplicationReadyEvent`, so the
  context is up whatever it finds; it cannot fail startup, and on failure it
  logs the one ERROR of story 11. Until it completes, the cached answer is
  unavailable.
- **On demand, stale-while-revalidate.** Wherever a Client Invoice response
  is built (the Agent's card, the Manager's detail, the Tester's Invoices
  page), `availableForPage()` is read. It returns the cached answer
  immediately. When the last completed check is more than **30 seconds** old
  and no check is in flight, it also submits one `probe()` to a small
  single-thread executor the component owns, and returns without waiting.
  The page is built from the value it was given and never waits on the store.
  - **Single flight**: an in-flight flag (compare-and-set) ensures at most one
    probe runs at a time. A page opened while one is running starts nothing.
  - **Capped**: the probe's call carries its own short timeout (about 3
    seconds), so a hung store ties up the executor's one thread for at most
    that long.
  - "Last check" is the time the last probe finished, success or failure.
    `markUnavailable()` does not touch it.
  - The executor and the clock are injected, so unit tests run the probe on
    the calling thread and move time by hand. The executor is shut down with
    the context.
- **Consequence, stated plainly.** The page that triggers a check shows the
  value from before it. Right after the store comes back, or goes down
  without a failed transfer, the first page opened may still show the
  previous state; the next reload shows the right one. Within 30 seconds of
  the last check, no new check is made, so a reload in that window shows the
  same state again.
- **Logging on transitions only.** The move to unavailable logs one ERROR
  naming the endpoint, the bucket and the cause (never the credentials); the
  move back logs one INFO. A store that stays down while pages keep opening
  logs once, not once per check.
- **A failed request marks it at once.** Every `ObjectStoreUnavailableException`
  raised by `CarrierInvoiceFileStorage` first calls `markUnavailable()`. A
  missing object (`NoSuchKey`, 404) does not: one lost file is not an outage.
- **Recovery is the probe's job.** Only a successful probe sets it back to
  available. Since the probe also creates a missing bucket when the flag is
  on, a store that comes up empty after the backend (a fresh SeaweedFS) is
  recovered too.
- Attach and download read `isAvailable()` for their 503 gate and start no
  check: the page they come from already did.

### What the frontend is told: `filesAvailable`

`ClientInvoiceResponse` gains one field, `filesAvailable: boolean`, filled
from `availableForPage()` wherever the response is built (read once per
response, or once per list of responses). It is the same value on
every Client Invoice and does not depend on the invoice's status. The
Agent's Client Invoices page, the Manager's invoice detail and the Tester's
Invoices page all render from this response already, server-side, so each
page load carries the availability of that moment with no extra request
(Decisions taken). `CarrierInvoiceFileResponse` and the file-list endpoints
are unchanged.

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
  with the operation and the key, never the credentials. Before it is thrown,
  `ObjectStoreAvailability.markUnavailable()` is called.
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

1. `requireNonEmpty` (unchanged). If `isAvailable()` is false, throw
   `ObjectStoreUnavailableException` (503) at once, without calling the store
   or waiting on a timeout.
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

**Download**: find the row by file id and invoice (404 as today). If
`isAvailable()` is false, answer 503 at once. Otherwise `read` the stored key
and answer it. Unchanged:

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

### Frontend: the paused files section

The three views draw the same "Carrier invoice files" section today, each
with its own copy of the markup. This feature extracts it into one shared
component, `CarrierInvoiceFilesSection` (prefactoring), used by
`AgentClientInvoiceCard`, the Manager's `ClientInvoiceDetailView` and the
Tester's `InvoicesView`. It takes the files, how to build a file's download
address, the empty-list text (each caller keeps its own: "No carrier invoice
files attached yet." for the Agent, "No carrier invoice files attached." for
the others), `filesAvailable`, and an optional attach control. In the
available state each caller renders exactly what it renders today, including
the Manager's `Download {filename}` accessible name.

When `filesAvailable` is false:

- **The file list still shows every name**, with its paperclip, as today (the
  names come from the database).
- **Each Download is disabled, not a dead link**: it renders as non-interactive
  text with the same icon and label, `aria-disabled="true"`, no `href`,
  `text-ink-mute`, `cursor-not-allowed`. It is not focusable and cannot be
  opened in a new tab.
- **"Attach carrier invoice" is disabled** where it is offered today (a draft
  the viewer may attach to): `AttachCarrierInvoiceFileControl` takes a
  `disabled` prop, passed to the existing `Button`'s `disabled`, so it shows
  the secondary button's existing disabled look (`disabled:opacity-50`,
  `cursor-not-allowed`), and the hidden file input is disabled too.
- **One notice**, under the section heading and above the list:
  `IconAlertTriangle` (`h-3.5 w-3.5`) and one line of `text-label-sm` in the
  `warning` tone, the same shape as the attach control's inline error but
  warning, not danger, because nothing the viewer did failed. It is a plain
  paragraph, not `role="alert"`: it is there on load, not announced as an
  event. Its text names only what the section offers:
  - with the attach control shown: "File storage is unavailable right now.
    Attaching and downloading are paused."
  - otherwise (the Tester; any sent or approved invoice): "File storage is
    unavailable right now. Downloading is paused."
  - a section with no files and no attach control shows no notice, since
    nothing is paused there.
- The controls come back on a page load or refresh after the store is back:
  the first one may still show them paused, the next shows them working
  (story 23). The section never polls.

Nothing else on the three pages reads `filesAvailable`. The `ClientInvoice`
type in `lib/api/types.ts` gains the field. The visual-test stub backend
serves `filesAvailable: true`, so no golden changes.

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
  `seaweedfs` with `service_started`, not `service_healthy`: compose starts
  the store first, but never holds the backend back on the store's health
  (the human's amendment); an invoice page opened once the store is up
  triggers the check that picks it up.
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
  - the backend never refuses to start over the store: it logs the failure,
    reports file storage as unavailable to the frontend, and recovers by
    itself (the human's amendment, 2026-10-17);
  - availability is checked once at startup and then only on demand, when an
    invoice page is opened and the last check is more than 30 seconds old,
    asynchronously, one check at a time; there is no background schedule
    (the human, 2026-10-17). The cost: right after the store changes state,
    the first page opened may still show the previous one;
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
  - A broken store configuration is found in the log and on screen (files
    paused), not by a failed deploy.
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
  - presigned URLs to the browser, which would bypass the access guards;
  - failing startup when the store is unreachable (this spec's first draft),
    turned down by the human;
  - a background check of the store every 30 seconds (this spec's second
    draft), turned down by the human for a check when invoice pages are
    opened.

## Design direction

Design slot: `impeccable`. `DESIGN.md` is the world, and this is a quiet
state, not a new pattern: no new token, no new component style, no new
colour.

- The paused section looks like today's section with its controls dimmed:
  the secondary button's existing disabled treatment for "Attach carrier
  invoice", and the Download label in `ink-mute` without its indigo link
  colour or underline on hover. The names stay `ink`: what is attached is
  still true.
- The notice reuses the inline message shape already in the files section
  (the attach control's `IconAlertTriangle` plus a 12px line), in the
  `warning` tone, the system's tone for "waiting on something", never
  `danger`, which says the viewer's action failed. One line, no card, no
  banner, no dismiss button.
- Voice: plain, short, no apology, no blame, no instruction to do anything,
  since the viewer can do nothing about it: "File storage is unavailable right
  now. Attaching and downloading are paused." ("Downloading is paused." where
  nothing can be attached.)
- In the normal state every pixel is unchanged; the visual goldens prove it.
- `DESIGN.md` gains one line under Components describing the paused files
  section, so the next file control follows it.

## Constraints

- Inherits every constraint of the `remote-support-mvp` and
  `send-a-client-invoice-back` specs on Client Invoices, except where this
  spec states otherwise.
- **HTTP contract unchanged.** Same routes, statuses, response bodies and
  `CarrierInvoiceFileResponse`. Additions only:
  - the RFC 6266 form of `Content-Disposition`;
  - `X-Content-Type-Options: nosniff`;
  - a 503 when the store is unavailable, where today an I/O failure is a 500;
  - `filesAvailable: boolean` on `ClientInvoiceResponse`.
- **The backend starts whatever the store's state or configuration** (the
  human, 2026-10-17). Nothing about the store may fail the Spring context or
  hold compose's backend back.
- **No background availability check** (the human, 2026-10-17): no
  `@Scheduled` task, timer or polling thread for it. The probe runs once at
  startup and otherwise only when a Client Invoice response is built and the
  last check is more than 30 seconds old; asynchronously, one at a time, each
  call capped at about 3 seconds; it logs only on a change of state. This
  constrains availability only, not `carrier-invoice-retention`'s deletion
  job.
- **The browser never polls** for availability.
- **No new design token**; the normal state of every page is pixel-identical.
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
4. **Startup and availability.**
   - A context-level test that `createBucket` creates a missing bucket. The
     shared test bucket starts absent, so every context boot exercises it.
   - `ClientInvoiceApiTest` asserts `filesAvailable: true` on a Client
     Invoice response against the real SeaweedFS (the HTTP seam).
   - `ObjectStoreAvailability` is unit-tested over a mocked `S3Client`, an
     injected manual executor (it records submitted tasks and runs them only
     when the test says) and a hand-moved clock. No scheduler exists, so
     none is mocked, and no real time is waited on:
     - an unreachable store, refused credentials, or an absent bucket with
       the flag off make it unavailable, and `probe()` never throws;
     - an absent bucket with the flag on is created and it is available; a
       present bucket is not recreated;
     - **stale value served, refresh triggered**: with the last check more
       than 30 seconds old, `availableForPage()` returns the cached value
       before any probe runs, and exactly one probe is submitted; once it
       runs, the next call returns the new value;
     - **single flight**: several `availableForPage()` calls while a probe is
       submitted but not finished submit no second one;
     - **no refresh within 30 seconds**: with the last check younger than 30
       seconds, `availableForPage()` submits nothing; `isAvailable()` never
       submits anything, whatever the age;
     - **recovery**: unavailable (a failed probe or `markUnavailable()`), then
       a successful probe, gives available; nothing else does;
     - `markUnavailable()` flips it at once and does not reset the age of the
       last check;
     - one ERROR on the move to unavailable, none on a second failure, one
       INFO on recovery.
   - A context test boots with a malformed `OBJECT_STORE_ENDPOINT` and shows
     the context starts and `isAvailable()` is false. Prior art: the existing
     context tests under `IntegrationTest`.
   - `CarrierInvoiceFileService`'s unit tests add: while unavailable, attach
     and download throw `ObjectStoreUnavailableException` without calling the
     storage; and `CarrierInvoiceFileStorage`'s add: a store failure calls
     `markUnavailable()`, a `NoSuchKey` does not.
   - A store that is down or comes back is shown end to end in the acceptance
     walkthrough (step 7), not in the e2e suite: stopping the throwaway
     SeaweedFS mid-suite would disturb the other specs sharing it.
5. **Frontend component tests (Vitest + Testing Library)** for the paused
   state, at the highest seam that renders it:
   - `CarrierInvoiceFilesSection`, in a new test beside it: with
     `filesAvailable` false, every name is listed, each Download is
     `aria-disabled` with no `href`, the attach control's button is disabled,
     and the notice reads the attach wording; without an attach control it
     reads the download-only wording; with no files and no attach control
     there is no notice; with `filesAvailable` true, today's links and button.
   - The existing view tests (`client-invoices-view.test.tsx`,
     `client-invoice-detail-view.test.tsx`) each gain one case passing
     `filesAvailable: false` through the view, proving the wiring; their
     fixtures gain `filesAvailable: true` and otherwise pass unedited.
   - Prior art: `client-invoices-view.test.tsx`'s "Attach carrier invoice"
     cases (around line 621).
6. **End to end: the existing specs, unedited**, on the isolated stack with
   its throwaway SeaweedFS: `client-invoice-generation.spec.ts` (attach and
   download, :26, :80-88) and `client-invoice-submission-and-visibility.spec.ts`
   (:68). Prior art for the stack: `run-backend-for-e2e-isolated.sh`'s
   Postgres.
7. **Visual goldens**: unchanged, none added. The stub backend serves
   `files: []` and now `filesAvailable: true`, so every page renders as
   today; a golden that changes is a defect. The paused state is covered by
   the component tests and the walkthrough.
8. **`verify`** stays the command in `docs/agents/sdlc.json`, unchanged. After
   a full run, `backend/data` is not created.

## Decisions taken

- **Spec approved by the human, 2026-10-17** (`docs/inbox/approve-carrier-invoice-object-storage.md`, "Ok"), after their amendments (no startup block, files paused in the UI, on-demand availability check). This accepts ADR 0007 as described here; ticket 1 writes it `accepted`.

- **Settled by the human (2026-10-17), answering this spec's former open
  question on the server: Hetzner Object Storage in production; SeaweedFS
  locally** (docker compose, `rsa-prod` included), in the integration tests
  and in both e2e backend scripts' throwaway stacks. Reason: the human works
  with Hetzner Object Storage ("I am working with hetzner object storage").
  MinIO is out because its free edition ended (no community images since
  October 2025, archived in 2026, images removed from Docker Hub in September
  2026), so a pinned tag never gets a security fix. SeaweedFS is Apache-2.0,
  maintained, and one container.
- **Settled by the human (2026-10-17), amending this spec at the approval
  gate: the backend never refuses to start over the store, and the frontend
  disables upload and download while the store is unavailable.** Reason: the
  human's words, "Let's not block the backend startup if the object storage
  configuration is failing. Aside from that, upload and download should be
  deactivated on the frontend."
- **The startup `HeadBucket` runs whatever the flag; `createBucket` only
  decides whether a missing bucket is created or reported.** Reason: on
  Hetzner the flag is off, and story 11 still wants a broken store or a wrong
  bucket name found in the log at deploy time.
- **Settled by the human (2026-10-17), replacing this spec's 30-second
  background check: availability is a cached flag, checked once at startup
  and then only when an invoice page is opened, stale-while-revalidate.**
  The human: "Let's go with the alternative when invoice pages are opened."
  No `@Scheduled` task, timer or polling thread. Building a Client Invoice
  response reads the cached value at once and, when the last check is more
  than 30 seconds old, starts one asynchronous probe (single flight, capped
  at about 3 seconds). Reason: nothing runs while nobody uses the app, and a
  page load still never waits on the store. The 30-second window bounds the
  store traffic to one capped check per 30 seconds however many pages open,
  and the log to one line per change of state. The price, accepted: the page
  that triggers a check shows the state from before it, so right after the
  store recovers, or goes down without a failed transfer, the first page
  opened may still show the previous state, and the next reload (once the
  last check is more than 30 seconds old) is correct. Changing the number is
  one constant.
- **The asynchronous probe runs on a single-thread executor the component
  owns, with the executor and clock injected; not Spring's `@Async`.**
  Reason: no `@EnableAsync` switch for one task, single flight is a flag the
  component holds, and the unit tests drive both the executor and time by
  hand.
- **The probe is the bucket ensure itself (`HeadBucket`, then `CreateBucket`
  when absent and the flag is on).** Reason: one code path for startup and
  recovery, and a store that comes back empty is recovered too. It adds no S3
  operation to the five.
- **The probe's call is capped at about 3 seconds.** Reason: the startup
  check and the executor's one thread must never sit on a hung store for the
  30-second call timeout; a check in flight blocks the next one.
- **Only the probe sets it available; any `ObjectStoreUnavailableException`
  sets it unavailable at once; a missing object does not.** Reason: one
  failure is enough to stop offering controls that will fail, and recovery
  through the probe alone keeps one source of "it's back". One lost object is
  not an outage.
- **Log ERROR on the move to unavailable and INFO on the move back, nothing in
  between.** Reason: an outage during which pages keep opening would
  otherwise log an error on every check.
- **While unavailable, attach and download answer 503 at once, without
  calling the store.** Reason: the server stays authoritative for a page
  loaded before the outage, and the request fails in milliseconds, not after a
  30-second timeout.
- **A configuration that cannot build a client leaves file storage
  unavailable for the life of the process, with one ERROR naming the
  setting.** Reason: the human's "missing config" case must not fail startup.
  Configuration is read at startup only, so recovering from it needs a restart
  anyway. The endpoint is bound as a string so that binding itself cannot
  fail.
- **The flag reaches the frontend as `filesAvailable` on
  `ClientInvoiceResponse`, not through a status endpoint.** Reason: the three
  screens already render from that response, server-side, so it adds no
  request, no BFF route and no client fetch, and the flag arrives with the
  page it governs. A status endpoint would need a new BFF route and a second
  fetch on three pages. The field is per response, not per file, because
  availability is the store's, not a file's.
- **Controls re-enable on a later page load or refresh, with no polling.**
  Reason: the human asked for the controls to be deactivated, not watched; an
  Agent who reloads gets the current state (one reload late at worst), and
  polling would add a timer and a client fetch to three pages for an outage
  that should be rare.
- **Compose's backend `depends_on` SeaweedFS with `service_started`, not
  `service_healthy`.** Reason: waiting on the store's health would block the
  backend's startup, which the human ruled out. Start order is kept, and the
  check triggered by the first invoice page opened catches up.
- **Proposed notice wording, for the human to veto: "File storage is
  unavailable right now. Attaching and downloading are paused." where the
  attach control is shown, and "File storage is unavailable right now.
  Downloading is paused." elsewhere; no notice where nothing is paused.**
  Reason: `DESIGN.md`'s plain voice; it says what is wrong and what is
  paused, blames no one, and asks nothing of a user who can do nothing about
  it. A Tester is never told about attaching, which they never do.
- **The notice uses the warning tone and the existing inline message shape
  (`IconAlertTriangle` + 12px line), as a plain paragraph, not an alert.**
  Reason: no new token or pattern; warning means "waiting on something",
  danger would say the viewer's own action failed, and a state present on
  load is not an event to announce.
- **A paused Download is non-interactive text (`aria-disabled`, no `href`),
  not a link to a 503.** Reason: the human asked for it deactivated; a link,
  even styled grey, can still be opened in a new tab and fail.
- **The three files sections become one shared `CarrierInvoiceFilesSection`.**
  Reason: the paused state is then written and tested once, and each caller
  keeps its own empty text and accessible names, so the normal state does not
  move a pixel.
- **The paused state is not an e2e spec; it is shown in the walkthrough.**
  Reason: the isolated e2e specs share one throwaway SeaweedFS, and stopping it
  mid-suite would make the other specs' failures depend on timing. Component
  tests and the backend unit tests cover each half.
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
  ERROR line; the files section pauses until a page opened after the store
  answers again triggers a successful check, and the user retries after a
  reload or two.
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
- **The bucket is ensured at startup, and a failure is logged, never fatal.**
  Reason: a broken store is found in the deploy's log without taking the app
  down (the human's amendment). A flag turns the creation off for a
  pre-provisioned store (Hetzner).
- **`/api/health` does not probe the store.** Reason: it is the compose
  liveness check. Tying it to the store would take the whole app (and the
  frontend's start) down with a store outage that only touches attachments.
- **A store failure during a request is a 503 (`ObjectStoreUnavailableException`)
  with one ERROR line; the frontend shows its existing failure text for that
  attempt, and the paused state from the next page load.** Reason: Backend 7.
  It is visible and contained, and the failure text is unchanged.
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
  service and a volume on the human's stack, changes the e2e infrastructure,
  and now touches three screens.
- **Three tickets in one PR: the store with its availability, the download
  name, the paused files section.** Reason: the availability probe is the
  startup bucket check rewritten, so it lands with the store swap, which must
  already land with compose, scripts and the test seam together or `verify`
  and the human's stack break between tickets. The header fix is independent
  and small. The frontend change is a different layer with its own tests and
  only needs the `filesAvailable` field; folding it into the store ticket
  would make one ticket span backend, infra and three screens.

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

1. [agent] Run `verify`. Show it green, the visual goldens unchanged, and that `backend/data` was not created. Show `docker ps` listing no SeaweedFS or Postgres container left from the run. (stories: 16, 17)
2. [agent] As Jordan, on the current-month draft at `/agent/client-invoices`, attach two files at once: a PDF named `Réf "Oct" 2026 – Orange.pdf` and a PNG. Show both listed under their names. Download each from the page, and show the saved name equals the original exactly and the bytes' SHA-256 equals the uploaded file's. (stories: 1, 2, 5)
3. [agent] List the bucket in SeaweedFS through the S3 API. Show exactly two objects, at `carrier-invoices/{tenant id}/{that invoice id}/{file id}`, whose file ids are the ones the invoice's file list returns. Head each and show no `x-amz-meta-*` and no tags. Query `carrier_invoice_files` and show each row's `storage_path` equals its key. (stories: 14, 15)
4. [agent] As the Manager, open that invoice's `/manager/invoices/client/{id}`, attach a third file and download one of Jordan's. As Jordan, send the invoice. As the Tester of that Client, download a file from the Invoices page. Show the same bytes and name. (stories: 3, 4)
5. [agent] Through the API, as Jordan, POST a file to the now-sent invoice. Show a 409, and the bucket still holding exactly the three objects under that invoice's prefix. (stories: 6)
6. [agent] Restart the backend process, then restart the SeaweedFS container (`docker restart`, the same container). Show every file still downloads with the same bytes. (stories: 8)
7. [agent] The store goes down and comes back (stories: 11, 12, 13, 20, 21, 22, 23, 24, 25). For this step the throwaway SeaweedFS keeps its data across a stop (started without `--rm`, removed by name at the end).
   - With the page open on a new draft as Jordan, `docker stop` SeaweedFS and attach a file. Show "Couldn't attach one or more files. Try again." and one ERROR in the backend log naming the store operation, with no credentials.
   - Reload. As Jordan on the draft, as the Manager on its `/manager/invoices/client/{id}`, and as the Tester on an earlier sent invoice's Invoices page, show the files section listing every file name, "Attach carrier invoice" disabled (Jordan, Manager), each Download disabled and not a link, and the notice: "File storage is unavailable right now. Attaching and downloading are paused." (Jordan, Manager) or "File storage is unavailable right now. Downloading is paused." (Tester). Screenshot each.
   - Show lines, fees, the PDF and the Fleet page still work, and that a direct API attach and download answer 503 at once.
   - Leave the stack untouched for two minutes with SeaweedFS still stopped and no page open. Show the backend log gains no line in that time: nothing checks the store while nobody opens a page.
   - `docker start` SeaweedFS. Reload Jordan's draft once (at least 30 seconds after the last reload of an invoice page, so the last check is stale; wait out the rest of the 30 seconds only if needed): it may still show the paused state. Reload again: Attach and Download are enabled; reload the Manager's and the Tester's pages: enabled too. A download returns the same bytes and an attach works. Show one INFO line for the recovery and no repeated ERROR lines in between.
   - Stop SeaweedFS again without attaching anything. Reload Jordan's draft once (again with the last check at least 30 seconds old): it may still show the controls enabled. Reload again: paused, with one ERROR in the log. `docker start` SeaweedFS.
   - Stop the backend. Start it with `OBJECT_STORE_ENDPOINT` at a free port where nothing listens. Show it starts (`/api/health` answers), logs one ERROR naming that endpoint and the bucket, and the files sections are paused. Start a fresh throwaway SeaweedFS on that port; reload once (the last check at least 30 seconds old), then again, and show Attach works and the bucket was created by the backend.
   - With SeaweedFS running, start the backend with `OBJECT_STORE_CREATE_BUCKET=false` and `OBJECT_STORE_BUCKET` set to a bucket that does not exist, then with a wrong secret key, then with `OBJECT_STORE_ENDPOINT=not a url`. Each time show it starts, logs one ERROR naming the endpoint and the bucket (or the bad setting), never the secret, creates no bucket, and the files sections are paused. (stories: 11, 12, 13, 20, 21, 22, 23, 24, 25)
8. [agent] Run `docker compose -f docker-compose.yml config` (read-only; nothing is started) (stories: 9, 10, 13, 17, 18, 19). Show:
   - a `seaweedfs` service on the pinned `chrislusf/seaweedfs` tag, with the `seaweedfs_data` volume, no `ports`, a healthcheck, and dev-only S3 credentials;
   - `backend` with `OBJECT_STORE_ENDPOINT=http://seaweedfs:8333` and a `depends_on` on `seaweedfs` with `service_started` (not `service_healthy`);
   - `seaweedfs_data` declared.

   Grep the repository for `your-objectstorage.com` and show it appears only in docs, never with a key or secret. Show `application.yml`'s `app.object-store` block reading every value from `OBJECT_STORE_*`. Show ADR 0007, including the S3 operations checked against Hetzner's supported actions and the addressing style, and the amended Carrier Invoice File entry. (stories: 9, 10, 13, 17, 18, 19)
9. [human] On your own stack (`rsa-prod`), rebuild with your usual `up -d --build`; it now also starts the local SeaweedFS. Open a Client Invoice draft, attach a carrier invoice and download it. Then rebuild the backend alone (`up -d --build --force-recreate backend`), reopen the invoice, and download it again. Confirm it is the same file, under the same name. (stories: 1, 2, 8, 9)

## Execution order

Four tickets, one PR. `carrier-invoice-file-download-name` and
`carrier-invoice-files-availability` depend on the first only;
`carrier-invoice-files-paused` depends on the availability ticket, whose
`filesAvailable` field it reads.

1. `carrier-invoice-files-in-object-store`: the AWS SDK dependency,
   `ObjectStoreProperties`, the `S3Client` bean (no store setting can fail
   startup), a one-shot startup bucket check that never fails startup,
   `CarrierInvoiceFileStorage` over S3 (503 on a store failure), the
   attach-with-undo `CarrierInvoiceFileService`, the shared test SeaweedFS,
   compose, both e2e scripts, ADR 0007 and the declared `CONTEXT.md` and
   `ARCHITECTURE.md` entries. Labels: backend, infra. No dependency.
   (stories: 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
2. `carrier-invoice-file-download-name`: `Content-Disposition` built with
   `ContentDisposition` and UTF-8, `X-Content-Type-Options: nosniff`, and the
   `ClientInvoiceApiTest` case with an accented, quoted name. Labels: backend.
   Depends on `carrier-invoice-files-in-object-store`. (stories: 5)
3. `carrier-invoice-files-availability`: `ObjectStoreAvailability` (cached
   value, on-demand stale-while-revalidate check, single flight, no
   scheduler), the 503 at once while unavailable, and `filesAvailable` on
   `ClientInvoiceResponse`. Labels: backend. Depends on
   `carrier-invoice-files-in-object-store`. (stories: 25)
4. `carrier-invoice-files-paused`: the shared `CarrierInvoiceFilesSection`
   used by the Agent's card, the Manager's detail and the Tester's Invoices
   page; the paused state (names listed, Attach and Download disabled, the
   notice); the `disabled` prop on `AttachCarrierInvoiceFileControl`;
   `filesAvailable` in `lib/api/types.ts`, the test fixtures and the visual
   stub backend; its component tests; the one-line `DESIGN.md` entry. Labels:
   frontend. Depends on `carrier-invoice-files-availability`. (stories: 20,
   21, 22, 23, 24)
