---
id: carrier-invoice-check-mark
title: Show a check mark on each Postpaid SIM line whose Carrier Invoice File is attached
status: ready-for-agent
depends_on: [attach-carrier-invoice-per-sim]
labels: [backend, frontend]
stories: [7, 24]
---

## Context

`spec.md` `### Reading` (`carrierInvoiceFileId` on the line), `### Frontend` (the check mark, the stub), `## Design direction` (Agent card, Manager detail), `## Testing decisions` items 1, 5, 7. Depends on `attach-carrier-invoice-per-sim` for the SIM-linked file rows.

`ClientInvoiceBaseSimLineResponse` gains `carrierInvoiceFileId` (nullable UUID, always serialised; `toResponse` reads the invoice's files once and maps them by SIM; the Tester's response carries it too and the Tester's page shows no SIM lines). One shared `CarrierInvoiceCheck` renders the mark on the Agent's `AgentClientInvoiceCard` and the Manager's `ClientInvoiceDetailView`. The visual stub serves the first line with a file and the second without, with that one file under a name string of the stub's own. Adds the `DESIGN.md` check-mark line in this ticket's commit. Frontend DoD in `docs/agents/frontend.md` applies.

`send-requires-every-carrier-invoice` and `remove-carrier-invoice-file` depend on this ticket: Remove's effect is shown through the mark, and the send ticket recaptures the same four goldens this ticket moves, so the two cannot run in parallel without a binary-file conflict.

## Acceptance criteria

- [ ] On the Agent's invoice card, a Postpaid SIM line whose file is attached shows an `IconCheckCircle` named "Carrier invoice attached" after its "{Carrier} · Postpaid" label; a line without its file shows nothing extra.
- [ ] On the Manager's invoice detail, the same mark shows read-only on each Postpaid SIM line with its file, with no Attach and no Remove.
- [ ] The Client Invoice response serves `carrierInvoiceFileId` on each Postpaid SIM line: the id of the file linked to that SIM, or `null`; a file whose SIM has no line sets no line's field.
- [ ] The row layout and the `xl` area layout of the card do not move; the four `agent-client-invoices-{desktop,mobile}-{light,dark}.png` goldens show one line with its mark and one without, and a file listed.

## Tests

Seams per spec `## Testing decisions`: items 1, 5, 7.

- **`ClientInvoiceByIdApiTest` (HTTP seam):** after an attach the line's `carrierInvoiceFileId` equals the file id and an uncovered line's is `null` (key present); a file whose SIM has no line sets no line's field.
- **Frontend:** `client-invoices-view.test.tsx` (Agent's card, through the view): a covered line shows "Carrier invoice attached", an uncovered one does not. `client-invoice-detail-view.test.tsx` (Manager): the mark shows, and there is no Remove and no Attach.
- **E2E:** `client-invoice-submission-and-visibility.spec.ts` asserts the Manager's check mark; `client-invoice-generation.spec.ts` asserts the Agent's mark after attaching. Prior art: `client-invoice-generation.spec.ts:26-88`.
- **Visual:** the four goldens are recaptured in the same commit as the stub and the component; no other golden moves, and one that does is a defect.

## Regression

At risk: the Agent's card and the Manager's detail layouts (debt :17, two copies of the layout), the Client Invoice response consumers, all Client Invoice goldens.

Existing tests expected to change, each with the reason:

- `ClientInvoiceByIdApiTest`: gains the `carrierInvoiceFileId` cases; every pre-existing method keeps passing unmodified.
- `frontend/components/agent/client-invoices-view.test.tsx`, `frontend/components/manager/client-invoice-detail-view.test.tsx` and `frontend/components/manager/contracts-view.test.tsx` (and any fixture building a Client Invoice SIM line): fixtures gain `carrierInvoiceFileId`; the first two gain the cases above. Every pre-existing case keeps passing unmodified.
- `client-invoice-submission-and-visibility.spec.ts` and `client-invoice-generation.spec.ts` gain the mark assertions; their existing steps keep passing unmodified.
- `frontend/tests/visual/stub-backend.mjs` and the four `agent-client-invoices-*` goldens are recaptured together.

## Observability

N/A: a field already derivable from stored rows and a presentational mark; no new operation and nothing is logged.
