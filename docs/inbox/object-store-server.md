---
id: object-store-server
type: question
status: answered
blocks: [carrier-invoice-object-storage]
created: 2026-10-17
---

<!-- sdlc:template inbox-item 1 -->

## Question

You asked for S3-compatible storage "such as minio". MinIO's free edition has
ended: no community images since October 2025, the project archived in 2026,
and the `minio/minio` images removed from Docker Hub in September 2026. A
pinned MinIO version would never get a security fix. Which server should
store the Carrier invoice files?

- **SeaweedFS**: Apache-2.0 licence, actively maintained, one container.
- **Garage**: AGPL-3.0 licence, actively maintained, light; a one-time setup
  step at first start.
- **MinIO's last free release**, from a mirror: works today, no updates ever.

## Recommendation

SeaweedFS. The app talks plain S3 whichever you pick, so switching later is a
compose change plus copying the files.

Spec: `docs/features/carrier-invoice-object-storage/spec.md`, Open
questions 1.

## Blocks

`carrier-invoice-object-storage`: the spec stays a draft until answered.

## Meanwhile

Nothing else runs: `text-size-lint-rule` is held at your request.

## Answer

SeaweedFS (the human, 2026-10-17), as the local stand-in in docker compose (rsa-prod included), tests and throwaway stacks. Production runs on Hetzner Object Storage ("I am working with hetzner object storage"); the app reaches either through the S3 API, configured by environment (endpoint such as https://fsn1.your-objectstorage.com, region, bucket, keys; credentials never in the repo).
