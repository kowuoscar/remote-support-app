---
id: carrier-invoice-object-storage-gate
type: question
status: answered
blocks: [carrier-invoice-object-storage]
created: 2026-10-17
---

<!-- sdlc:template inbox-item 1 -->

## Question

The merge gate is blocked by one finding, F25, raised at re-review. The fix
for the store-down crash (F21) now reads each upload whole into memory (up
to the 20 MB cap) before sending it to file storage, where the spec says the
upload is streamed. Everything else is confirmed and walkthrough steps 2, 4
and 7 now pass. Do you accept uploads held in memory, or want them streamed?

## Recommendation

Fix it: one more fixer pass that streams the upload again in a form the
storage client can retry (it reopens the upload on each attempt), keeping
the 503 on a store outage. *Reason:* it is a small, local change, and
streaming keeps memory flat when several Agents upload at once. Accepting
the buffering is also defensible: 20 MB per upload, few concurrent users.

Findings: `docs/features/carrier-invoice-object-storage/findings.json` (F25).

## Blocks

`carrier-invoice-object-storage`.

## Meanwhile

The feature is blocked; PR #32 stays draft.

## Answer

Fix it (2026-10-17): authorises one more fixer pass for F25.
