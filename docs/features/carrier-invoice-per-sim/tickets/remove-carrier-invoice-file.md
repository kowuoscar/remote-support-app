---
id: remove-carrier-invoice-file
title: Let the Agent remove a Carrier Invoice File from a draft open to them
status: in-progress
depends_on: [carrier-invoice-check-mark]
labels: [backend, frontend]
stories: [18, 19, 20, 21, 22, 23]
---

## Context

`spec.md` `### Removing`, `### Frontend` (Remove), `## Design direction` (Remove) and `## Testing decisions` items 1 (Removing), 3, 5, 6. Depends on `carrier-invoice-check-mark` (and through it `attach-carrier-invoice-per-sim`) for the SIM-linked file rows, `carrierInvoiceFileId` (a removal must visibly take the check mark off the line) and the attach form and the `CarrierInvoiceFilesSection`. It does not depend on the send gate: the note's count is updated by the gate ticket's component, and this ticket only asserts the line's check mark and the attach form.

The route is by id only; access is `ClientInvoiceAccessGuard.requireCanSend` (Contract's own Agent). The row is deleted under the lock, the object after the commit (Backend 8); a failed object delete is a WARN and the answer is still 204. Adds `AuditLog.carrierInvoiceFileRemoved`. Adds the `DESIGN.md` Remove line and the "removing" paused-notice word in this ticket's commit. Frontend DoD in `docs/agents/frontend.md` applies.

## Acceptance criteria

- [ ] As the Contract's Agent, `DELETE /api/client-invoices/{invoiceId}/files/{fileId}` on a current-month draft never sent, or on a sent-back draft, answers 204; the row is gone and its object is no longer in the bucket.
- [ ] The API refuses with 403 for a Manager, a Tester and another Agent, 404 for an unknown file, 409 for a sent or approved invoice, 409 `PAST_MONTH_DRAFT_NOT_SENDABLE` for a past month's draft never sent, and 503 at once, leaving the row, while file storage is unavailable.
- [ ] On the Agent's card each file of an editable draft has a quiet "Remove" (accessible name "Remove {filename}") that opens an inline "Remove this file?" with "Cancel" and a danger-toned "Remove"; confirming removes the file from the list, takes the check mark off its SIM line and puts the line back in the attach form.
- [ ] A failed removal shows "Couldn't remove the file. Try again." Sent and approved invoices, and the Manager's and Tester's views, show no Remove.
- [ ] While `filesAvailable` is false, Remove is disabled like Attach and Download, and the Agent's notice reads "File storage is unavailable right now. Attaching, removing and downloading are paused."; the Manager's and Tester's wording is unchanged.
- [ ] After a send-back, the Agent can remove a file, attach its replacement and resend (once the gate exists, the resend is the end of the e2e flow).

## Tests

Seams per spec `## Testing decisions`: items 1 (Removing), 3, 5, 6.

- **HTTP seam, `ClientInvoiceByIdApiTest`:** 204 on a current-month draft and on a sent-back draft with the row gone and `keysUnder` no longer listing the key; 409 on a sent invoice and 409 on an approved invoice; 409 `PAST_MONTH_DRAFT_NOT_SENDABLE` on a never-sent past-month draft; 403 for Manager, Tester and another Agent; 404 unknown file; 503 with the row kept while unavailable. Prior art: the existing attach and download cases.
- **`CarrierInvoiceFileService` unit test:** the object is deleted only after `removeFile` returns; a failing delete logs WARN and does not throw; an exception from `removeFile` deletes nothing; while unavailable, `remove` never calls storage.
- **Frontend:** `client-invoices-view.test.tsx`: Remove opens its confirm and sends a `DELETE`, a failure shows its text, Remove is disabled while paused with the new notice. `carrier-invoice-files-section.test.tsx`: the action slot renders, and the paused wording names removing only when Remove is offered. `client-invoice-detail-view.test.tsx`: no Remove.
- **E2E:** `send-a-client-invoice-back.spec.ts` adds removing a file after the send-back, attaching its replacement and resending.

## Regression

At risk: the Carrier Invoice Files section on all three screens in the normal and paused states, the Manager's `Download {filename}` name, the BFF `files/[fileId]` route's existing GET, and Attach.

Existing tests expected to change, each with the reason:


- Accepted by the orchestrator at merge: `frontend/components/agent/client-invoices-view.test.tsx` case `pauses-attach-and-download-with-a-notice-when-file-storage-is-unavailable` renders an editable draft with a file, where Remove is offered, so its expected notice moves to "…Attaching, removing and downloading are paused." as the spec's Frontend "Remove" section requires; nothing else in that case changes.

- `ClientInvoiceByIdApiTest` and `CarrierInvoiceFileServiceTest` (or the existing service test file) gain the cases above; every pre-existing method keeps passing unmodified.
- `frontend/components/manager/client-invoice-detail-view.test.tsx` gains one case (no Remove on the Manager's detail); every pre-existing case keeps passing unmodified.
- `frontend/components/agent/client-invoices-view.test.tsx` and `carrier-invoice-files-section.test.tsx` gain Remove and wording cases; every pre-existing case keeps passing unmodified, including the existing paused wording where Remove is not offered.
- `send-a-client-invoice-back.spec.ts` gains the replacement flow; its existing steps keep passing unmodified.
- No golden should move (Remove is covered by component tests and the walkthrough); one that does is a defect.

## Observability

A removal writes `AuditLog.carrierInvoiceFileRemoved(invoiceId, fileId, simCardId, actor, tenant)`; a failed object delete after the commit logs WARN "stray object, no row refers to it" with the key.
