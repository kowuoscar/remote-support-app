---
id: carrier-invoice-files-availability
title: Tell the frontend whether file storage is available, re-checking the store only when an invoice page opens
status: done
depends_on: [carrier-invoice-files-in-object-store]
labels: [backend]
stories: [25]
---

## Context

`spec.md` `### File storage availability: ObjectStoreAvailability`,
`### What the frontend is told: filesAvailable`, `## Constraints` (no
background check) and `## Testing decisions` item 4. Depends on
`carrier-invoice-files-in-object-store`, which provides the S3 client, the
storage with its `ObjectStoreUnavailableException` and the one-shot startup
bucket check that this ticket replaces with `ObjectStoreAvailability.probe()`.

`ObjectStoreAvailability` (a `@Component` in `web`) holds a cached answer, is
probed once on `ApplicationReadyEvent`, and is re-probed only when a Client
Invoice response is built and the last check is over 30 seconds old:
asynchronous, one probe at a time, each call capped at about 3 seconds, on an
injected single-thread executor with an injected clock. No `@Scheduled` task,
timer or polling thread. `CarrierInvoiceFileStorage` calls `markUnavailable()`
before throwing for a store failure (never for a missing object). While
unavailable, attach and download answer 503 at once without calling the store.
`ClientInvoiceResponse` gains `filesAvailable`, read once per response (or per
list) from `availableForPage()`. Unblocks `carrier-invoice-files-paused`.

## Acceptance criteria

- [ ] Every Client Invoice response, for the Agent, the Manager and the Tester, carries `filesAvailable`: `true` while the store answers.
- [ ] While file storage is unavailable, an attach or a download answers 503 at once, without calling the store and without waiting on a timeout, and every other Client Invoice endpoint (lines, fees, PDF, send, send-back, approve) behaves as before.
- [ ] A failed upload or download makes the next Client Invoice response carry `filesAvailable: false`; a missing object (404) does not.
- [ ] After the store comes back, a Client Invoice page opened when the last check is over 30 seconds old is answered with the cached value and starts one check; the next response carries `filesAvailable: true`. Within 30 seconds of the last check no new check is made.
- [ ] While nobody opens an invoice page, the backend never calls the store; the log shows one ERROR on the move to unavailable (endpoint, bucket, cause, no credentials), one INFO on recovery, and nothing for a store that stays down.

## Tests

Seam per spec `## Testing decisions` item 4.

- **HTTP seam, `ClientInvoiceApiTest`:** a Client Invoice response has `filesAvailable: true` against the real SeaweedFS.
- **Context test:** a context booted with a malformed `OBJECT_STORE_ENDPOINT` starts and `isAvailable()` is false; likewise a blank bucket and a blank region, each with one ERROR naming the setting.
- **Unit, `ObjectStoreAvailability` over a mocked `S3Client`, a manual executor and a hand-moved clock:**
  - unreachable store, refused credentials, and an absent bucket with the flag off give unavailable, `probe()` never throws, and the ERROR on a refused-credentials probe contains neither the access key nor the secret;
  - an absent bucket with the flag on is created and it is available; a present one is not recreated;
  - stale value served before any probe runs, exactly one probe submitted, the next call returns the new value;
  - single flight: several `availableForPage()` calls while a probe is submitted submit no second one;
  - no refresh within 30 seconds, and `isAvailable()` never submits whatever the age;
  - recovery only by a successful probe; `markUnavailable()` flips it at once and does not reset the age of the last check;
  - one ERROR on the move to unavailable, none on a second failure, one INFO on recovery.
- **Unit, `CarrierInvoiceFileService` and `CarrierInvoiceFileStorage`:** while unavailable, attach and download throw `ObjectStoreUnavailableException` without calling the storage; a store failure calls `markUnavailable()`, a `NoSuchKey` does not.
- **Manual proof (walkthrough step 7):** the store going down and back, the two-minute silence. Not an automated test.

## Regression

At risk: every Client Invoice response shape (one new field) on the Agent, Manager and Tester endpoints; the attach and download gate; the startup bucket check from the previous ticket, now owned by `probe()`.

Existing tests expected to change:

- `ClientInvoiceApiTest.java` gains the `filesAvailable: true` case. Every pre-existing method keeps passing unmodified.
- `ClientInvoiceByIdApiTest.java` and `ClientInvoiceSendBackApiTest.java` must pass unedited; they read responses through JSON and ignore the new field.
- The context tests added by `carrier-invoice-files-in-object-store` for a missing bucket and for bad settings are adapted to assert through `ObjectStoreAvailability` where they used the one-shot check; their assertions keep the same meaning.
- The `CarrierInvoiceFileService` and `CarrierInvoiceFileStorage` unit test classes added by `carrier-invoice-files-in-object-store` gain the unavailable-gate and `markUnavailable()` cases, and their setup gains a mocked `ObjectStoreAvailability`; every pre-existing method keeps passing unmodified.
- No other existing test may be edited.

## Observability

One ERROR on the move to unavailable (endpoint, bucket, cause; never credentials) and one INFO on the move back; nothing logged while the state stays the same, so a store that stays down logs once.
