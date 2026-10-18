---
id: carrier-invoice-files-show-deleted-on
title: Show "Deleted on {date}" where a deleted Carrier Invoice File's Download was
status: done
depends_on: [carrier-invoice-files-deleted-after-retention]
labels: [frontend]
stories: [1, 2, 3, 4, 5, 6, 18]
---

## Context

`spec.md` `### Frontend: the deleted file in CarrierInvoiceFilesSection`, `## Design direction`, `## Testing decisions` item 5. Depends on `carrier-invoice-files-deleted-after-retention` for the `deletedAt` field the API serves; without it the screens have nothing to show.

`CarrierInvoiceFileListItem` in `frontend/lib/api/types.ts` gains `deletedAt: string | null`. The shared `CarrierInvoiceFilesSection` takes it per file. A deleted file keeps its row, paperclip and name, and shows plain text `Deleted on {date}` (`formatDate`, as for `approvedAt`; `ink-mute`, `text-label-sm`, no icon, not a link, not focusable, no `aria-disabled`) in the Download slot, whether `filesAvailable` is true or false. The "File storage is unavailable" notice shows only when something is paused: the attach control is offered, or at least one file is not deleted. The three screens (Agent card, Manager detail, Tester Invoices page) only pass the field through. Adds one clause for the deleted row to `DESIGN.md`'s "Paused files section" in this ticket's commit. Frontend DoD in `docs/agents/frontend.md` applies. Story 1 (a live file stays downloadable) is the unchanged path, pinned by the existing cases.

## Acceptance criteria

- [ ] On the Agent's Client Invoice card, the Manager's invoice detail and the Tester's Invoices page, a deleted file stays listed by name with "Deleted on {date}" where Download was: no link, not focusable.
- [ ] A file not deleted still shows its Download link, and a paused one its disabled Download, exactly as before.
- [ ] A section whose files are all deleted shows no "File storage is unavailable" notice during an outage, and shows the same "Deleted on" rows as when storage is up. With one deleted and one live file while paused, the notice shows and only the live file's Download is paused.
- [ ] A Postpaid SIM line whose file is deleted keeps its "Carrier invoice attached" check mark on the Agent card and the Manager detail.
- [ ] On an editable draft, the Agent's Remove stays offered on a deleted file and removes it as on any file.
- [ ] No visual golden moves.

## Tests

Seams per spec `## Testing decisions` item 5 (Vitest + Testing Library).

- **`carrier-invoice-files-section.test.tsx`:** a deleted file lists its name and "Deleted on {date}" with no link; `filesAvailable` false with only deleted files shows no notice; one deleted and one live file while paused shows the notice and the live Download paused; the renderRemove slot still renders for a deleted file.
- **`client-invoices-view.test.tsx` (Agent):** a SIM line whose file has `deletedAt` set keeps "Carrier invoice attached"; Remove is offered on a deleted file of a draft.
- **`client-invoice-detail-view.test.tsx` (Manager):** the same check mark case, and "Deleted on" in place of Download.
- **Tester page:** the shared section is covered above; `frontend/components/client/invoices-view.tsx` only passes the field through and has no test file today, so no case is added unless one exists by implementation time.
- **Visual:** goldens unchanged, none added; the stub's files gain `deletedAt: null` only.

## Regression

At risk: the paused files section (notice rule), the Download slot in all three screens, the per-SIM check mark, the Remove control, and every Client Invoice golden.

Existing tests expected to change, each with the reason:

- `frontend/components/ui/carrier-invoice-files-section.test.tsx`: fixtures gain `deletedAt: null`, and it gains the cases above. Every pre-existing case keeps passing unmodified.
- `frontend/components/agent/client-invoices-view.test.tsx` and `frontend/components/manager/client-invoice-detail-view.test.tsx`: file fixtures gain `deletedAt: null`; each gains the cases above. Every pre-existing case keeps passing unmodified.
- `frontend/tests/visual/stub-backend.mjs`: `SIM_FILES` gains `deletedAt: null`, so the stub serves the API's new shape. No golden is recaptured; a golden that moves is a defect.
- `frontend/lib/api/types.test.tsx` is expected to stay unchanged; name it in the result if the type change forces an edit.
- The e2e specs (`client-invoice-submission-and-visibility.spec.ts` among them) run unedited.

## Observability

N/A: a presentational state of an existing row; nothing is logged and no new operation exists.
