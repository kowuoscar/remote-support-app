---
id: carrier-invoice-files-paused
title: Show Carrier Invoice Files as paused, with Attach and Download disabled, while file storage is unavailable
status: ready-for-agent
depends_on: [carrier-invoice-files-availability]
labels: [frontend]
stories: [20, 21, 22, 23, 24]
---

## Context

`spec.md` `### Frontend: the paused files section`, `## Design direction`,
`## Testing decisions` items 5 and 7. Depends on
`carrier-invoice-files-availability` because it reads the `filesAvailable`
field that ticket adds to `ClientInvoiceResponse`.

Prefactors the three copies of the "Carrier invoice files" markup (the Agent's
`agent-client-invoice-card.tsx`, the Manager's `client-invoice-detail-view.tsx`,
the Tester's `invoices-view.tsx`) into one shared `CarrierInvoiceFilesSection`,
then adds the paused state to it. The normal state must stay pixel-identical,
including each caller's empty-list text and the Manager's `Download {filename}`
accessible name. No polling, no new fetch, no BFF route. The notice wording is
the spec's proposed text; use it as written.

Adds `filesAvailable` to `ClientInvoiceDetail` (`frontend/lib/api/types.ts:541`) and to the
visual stub backend (`tests/visual/stub-backend.mjs`, serving `true`). Adds one
line to `DESIGN.md` Components describing the paused files section, in this
ticket's commit. Satisfies the frontend DoD in `docs/agents/frontend.md`.

## Acceptance criteria

- [ ] With `filesAvailable` true, the Agent's card, the Manager's invoice detail and the Tester's Invoices page render exactly as today, including empty-list texts and accessible names; the visual goldens do not change.
- [ ] With `filesAvailable` false, on all three screens every file name is still listed with its paperclip, and each Download renders as non-interactive text with `aria-disabled="true"`, no `href` and the muted, not-allowed look, not focusable.
- [ ] With `filesAvailable` false and an attach control shown (a draft the viewer may attach to), "Attach carrier invoice" and its hidden file input are disabled, and one warning-tone paragraph under the heading reads "File storage is unavailable right now. Attaching and downloading are paused."
- [ ] With `filesAvailable` false and no attach control (the Tester, or a sent or approved invoice) with files listed, the paragraph reads "File storage is unavailable right now. Downloading is paused."; a section with no files and no attach control shows no notice. The notice is a plain paragraph, not `role="alert"`.
- [ ] Lines, fees, the PDF download, send, send-back and approve on these screens behave as before while paused, and the controls work again on a reload of the page once the response carries `filesAvailable: true`; the browser makes no extra request and sets no timer for availability.

## Tests

Seam per spec `## Testing decisions` item 5 (Vitest + Testing Library); prior art `client-invoices-view.test.tsx`'s "Attach carrier invoice" cases (about line 621).

- **New `CarrierInvoiceFilesSection` test beside the component:** `filesAvailable` true renders links and the enabled button; false lists every name, each Download `aria-disabled` with no `href`, the attach button disabled and the attach wording; false without an attach control gives the download-only wording; false with no files and no attach control gives no notice; the notice is not `role="alert"`.
- **`client-invoices-view.test.tsx` and `client-invoice-detail-view.test.tsx`:** one case each passing `filesAvailable: false` through the view, asserting the notice and a disabled Download (and a disabled Attach on the Agent's draft).
- **Visual:** `tests/visual` goldens unchanged with the stub serving `filesAvailable: true`; a changed golden is a defect.

## Regression

At risk: the files section on all three screens in the normal state, the Manager's `Download {filename}` accessible name, the Agent's attach flow and its failure text, and the visual goldens for Client Invoice pages.

Existing tests expected to change:

- `frontend/components/agent/client-invoices-view.test.tsx` and `frontend/components/manager/client-invoice-detail-view.test.tsx`: their fixtures gain `filesAvailable: true` and each gains one case with `filesAvailable: false`. Every pre-existing case keeps passing unmodified.
- `frontend/tests/visual/stub-backend.mjs` gains `filesAvailable: true` on its Client Invoice responses; no golden is recaptured.
- `frontend/components/manager/contracts-view.test.tsx`: its INVOICE fixture gains `filesAvailable: true`; every pre-existing case keeps passing unmodified.
- The Tester's `invoices-view.tsx` has no test of its own; it is covered through the new section test.
- `client-invoice-generation.spec.ts` and `client-invoice-submission-and-visibility.spec.ts` must pass unedited.

## Observability

N/A: a presentational state driven by a field already served; no new operation, and the browser logs nothing.
