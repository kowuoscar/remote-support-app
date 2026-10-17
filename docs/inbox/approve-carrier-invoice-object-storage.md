---
id: approve-carrier-invoice-object-storage
type: approval
status: open
blocks: [carrier-invoice-object-storage]
created: 2026-10-17
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec `docs/features/carrier-invoice-object-storage/spec.md`?
Read the user stories, the non-goals, the decisions taken and the
walkthrough; the solution is yours to skip.

What it does: Carrier invoice files move from the backend container's disk
into an S3 bucket: a local SeaweedFS in docker compose (your `rsa-prod`
included, with its own volume, so uploads survive rebuilds), in the tests and
in the throwaway stacks; Hetzner Object Storage in production, set by
environment only. Keys are `carrier-invoices/{tenant id}/{client invoice
id}/{file id}`, objects carry no labels. Nothing changes on screen, except
that a file name with accents or quotes now downloads under its exact name.

Choices to note:

1. The backend refuses to start when it can't reach its bucket, with a log
   line naming it. On your stack it waits for SeaweedFS to be healthy first.
2. An attach that fails or is refused leaves no file behind in the bucket.
3. A store outage fails only the attach or download, with a clear error; the
   rest of the app keeps working. `/api/health` doesn't check the store.
4. In production the bucket is created on Hetzner outside the app; locally
   the app creates it.
5. ADR 0007 records the choice, Hetzner and SeaweedFS, and why MinIO and
   Garage were not chosen; your approval accepts it.
6. Two tickets in one PR: the storage move, then the safe download name.

## Recommendation

Approve.

## Blocks

`carrier-invoice-object-storage`: no tickets until approved.

## Meanwhile

`text-size-lint-rule` stays held at your request.

## Answer
