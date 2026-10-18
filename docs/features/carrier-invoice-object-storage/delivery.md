# Delivery report — carrier-invoice-object-storage

<!-- sdlc:template delivery 1 -->

Prose, not machine-read — `findings.json` and `acceptance.json` carry the
data this report narrates. Filed once, at delivery, alongside the inbox item
of type `acceptance` that points here.

## What was built

The first feature of the epic `carrier-invoices-per-sim`. Merged as PR #32
(`9838cd6`): four tickets, one fix pass, then a second one authorized by you
(uploads streamed again).

- **Files live in an S3-compatible object store (stories 1–10, 14–19).**
  Carrier Invoice Files no longer go to the backend's disk. Each file is
  stored under `carrier-invoices/{tenant id}/{client invoice id}/{file id}`
  in one private bucket, with no metadata or tags on the object; the
  database keeps the file name, and the download uses it (accents and other
  non-ASCII characters intact). Uploads are streamed, never held whole in
  memory. Production uses Hetzner Object Storage, configured only from the
  environment (`OBJECT_STORE_*`); docker compose (your `rsa-prod` included),
  the tests and the e2e scripts use SeaweedFS. ADR 0007 records the choice.
  Tickets: `carrier-invoice-files-in-object-store`,
  `carrier-invoice-file-download-name`.
- **Storage trouble never stops the app (stories 11–13, 20–25).** The
  backend starts whatever state the store is in: a wrong address, a missing
  bucket, a bad key or an unreachable store each log one ERROR naming the
  endpoint and bucket, never the secret. While the store is unavailable, the
  files section on the Agent's card, the Manager's invoice page and the
  Tester's Invoices page lists the file names with Download (and the Agent's
  Attach) disabled and a warning line. Everything else on the invoice works.
  The store is checked at startup, then only when an invoice page is opened
  and the last check is over 30 seconds old; nothing runs while nobody opens
  a page. It comes back by itself, at worst on the second page load. A
  store that fails mid-upload answers "Couldn't attach… Try again." and pauses
  the files section. Tickets: `carrier-invoice-files-availability`,
  `carrier-invoice-files-paused`.

## Acceptance walkthrough

Played by an agent on its own throwaway stack, never on yours.

1. `verify` green, goldens unchanged, no `backend/data`, no container left — played — evidence: `evidence/` (step 1)
2. Agent attaches two files and downloads them, same bytes; quotes come back as `%22` (accepted by you) — played (replayed after the fix pass) — evidence: `evidence/step-2-downloads-rerun.txt`
3. Bucket holds exactly the two objects at the expected keys, no metadata — played — evidence: `evidence/` (step 3)
4. Agent attaches a third file; Manager and Tester download it, same bytes and names — played (replayed) — evidence: `evidence/step-4-tester-downloads-rerun.txt`
5. Attach to a sent invoice refused with 409, nothing stored — played — evidence: `evidence/` (step 5)
6. Files survive a backend and a SeaweedFS restart — played — evidence: `evidence/` (step 6)
7. The store goes down and comes back; misconfigurations start cleanly — played (replayed) — evidence: `evidence/step-7a-backend-log-rerun.txt`
8. docker compose config: SeaweedFS service, volume, env — played — evidence: `evidence/` (step 8)
9. On `rsa-prod`: attach, rebuild the backend, download the same file — yours

## Decisions taken alone

From the spec's `## Decisions taken`, besides what you settled:

- The one-shot startup check became an on-demand check (your amendment):
  stale-while-revalidate at 30 seconds, one probe at a time, capped at 3
  seconds.
- One shared `CarrierInvoiceFilesSection` replaces the three copies of the
  files section (prefactoring).
- Downloads stay whole-file in memory; uploads are streamed.
- Content-Disposition carries the stored name (UTF-8 `filename*`) with
  `X-Content-Type-Options: nosniff`; objects carry no Content-Type of their
  own.
- The AWS SDK's HTTP client needed newer `httpclient5`/`httpcore5` than
  Spring Boot manages; the override is in `backend/pom.xml` and the ADR.
- Four tickets in one pull request; the orchestrator closed the ticket
  cut's last critic gaps.
- (at review, settled by you) The Manager does not attach; a quote in an
  uploaded name may come back as `%22`.

## Debt recorded

- `CONTEXT.md` · extra "Object store" term (F7)
- `backend/pom.xml` · app-wide httpclient5/httpcore5 override (F8)
- `CarrierInvoiceFileStorage` / `ObjectStoreAvailability` · duplicated status expression and timeout lambda (F11)
- `scripts/run-backend-for-e2e*.sh` · duplicated SeaweedFS setup (F12)
- `ObjectStoreConfig.configurationProblem` · Feature Envy (F13)
- `ClientInvoiceService.toResponse` / `ObjectStoreAvailability` · flag arguments (F14)
- `CarrierInvoiceFileService` · builds HTTP headers outside the controller (F15)
- `CarrierInvoiceFileStorageTest` · three near-identical failure tests (F16)
- `carrier-invoice-files-section.tsx` · paused Download span's ARIA (F17)
- `agent-client-invoice-card.tsx` · disabled Attach far from its notice (F18)
- `frontend/tests/visual` · no golden for the paused state (F20)
- `CarrierInvoiceFileStorageUnreachableStoreTest` · test kind Backend 6 doesn't name (F26)

## How to undo

```
git revert -m 1 9838cd6
```

Reverts code, not data — this feature has no migration. Files attached
after delivery stay in the bucket and would no longer be read (their
`storage_path` holds an object key, not a disk path).

## What happens next

`carrier-invoice-per-sim` is next in the epic: one carrier invoice per
postpaid SIM, attached through a form under the computed name, a check mark
on the SIM row, and no send without them. Your `rsa-prod` is rebuilt so it
starts the local SeaweedFS.
