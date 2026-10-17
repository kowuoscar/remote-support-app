---
id: send-requires-every-carrier-invoice
title: Refuse to send a Client Invoice while a Postpaid SIM line lacks its Carrier Invoice File
status: ready-for-agent
depends_on: [carrier-invoice-check-mark]
labels: [backend, frontend]
stories: [11, 12, 13, 14, 15, 17, 28, 29]
---

## Context

`spec.md` `### The send gate`, `### Demo data`, `### Frontend` (the send gate on the page), `## Testing decisions` items 1 (Sending), 2, 4, 6, 7. Depends on `carrier-invoice-check-mark` (which itself depends on `attach-carrier-invoice-per-sim`, so the chain gives this ticket `sim_card_id`, the frontend helper `missingCarrierInvoiceLines` the Send note reads, and the coded-409 advice). The direct gate on this edge is the shared four `agent-client-invoices-*` goldens and stub state that `carrier-invoice-check-mark` moves first; this ticket recaptures them again for the disabled Send and its note.

The gate reads database rows only, under the row lock send already takes, and never the store. It is the only change to send: approve and send-back do not look at files. Because the gate refuses every send with a Postpaid SIM line, this ticket also brings every existing sending test, the demo loader and every sending e2e spec under the rule through one helper each (spec Testing 4 and 6). Adds the new Reason `CARRIER_INVOICES_MISSING`. Frontend DoD in `docs/agents/frontend.md` applies.

## Acceptance criteria

- [ ] `POST /send` (both routes) on a draft with any Postpaid SIM line lacking its file answers 409 `{code: CARRIER_INVOICES_MISSING, message}` naming the count and the missing numbers; the invoice stays `DRAFT` and no line row is written.
- [ ] Send succeeds with every Postpaid SIM line covered, and with no Postpaid SIM line (none billing, or a legacy `BASE_AMOUNT` invoice); a resend after a send-back is gated on the stored lines; a file whose SIM no longer appears among the lines neither counts nor blocks.
- [ ] Send succeeds while file storage is unavailable when every line has its file, and is refused with the coded 409 when a file is missing.
- [ ] On the Agent's card, "Send Client Invoice" is disabled while a line lacks its file, with a `text-label-sm` `ink-mute` note linked by `aria-describedby`: "1 Postpaid SIM still needs its carrier invoice." or "{n} Postpaid SIMs still need their carrier invoice."; it is enabled with none missing or no Postpaid line.
- [ ] A send refused with `CARRIER_INVOICES_MISSING` (a SIM began billing after the page loaded) shows "A Postpaid SIM still needs its carrier invoice. Reload to see which."; any other failure keeps "Couldn't send. Try again."
- [ ] The demo's contract US-1 current-month invoice starts sent with one file per Postpaid SIM line; with the store unavailable at startup the backend still starts, logs one WARN and leaves that invoice a draft; the approved past invoices stay without files.
- [ ] An attach and a send racing on an invoice missing only that SIM's file never leave a sent invoice with a line missing its file.

## Tests

Seams per spec `## Testing decisions`: items 1 (Sending), 2 (concurrency), 4, 5, 6, 7.

- **`ClientInvoiceByIdApiTest` and `ClientInvoiceApiTest` (HTTP seam):** send with a missing line gives coded 409, still `DRAFT`, `linesStored` false, no line row, `{code, message}` on both routes; every line covered gives 200; no Postpaid SIM gives 200; a legacy `BASE_AMOUNT` invoice sends with 200; send after send-back gated on stored lines; a file whose SIM stopped billing neither counts nor blocks; send succeeds with `ObjectStoreAvailability.markUnavailable()` when every row exists. Prior art: `ClientInvoiceSendBackApiTest`'s coded-409 assertion.
- **`CarrierInvoiceFileAttachConcurrentSendTest`:** new case, attach versus send; either attach wins and send succeeds, or send gets the coded 409 and attach succeeds.
- **`DemoDataLoaderApiTest`:** a new case with a failing storage `put`: the loader completes, logs one WARN, and contract US-1's current-month invoice stays `DRAFT`; the existing case asserts one file per Postpaid SIM line on the sent invoice.
- **Test-support helper** `attachCarrierInvoicesForEveryPostpaidLine(invoiceId, agentToken)` beside `ObjectStoreTestSupport`, posting a tiny file per line through the real endpoint.
- **Frontend (`client-invoices-view.test.tsx`):** Send disabled with the count note (singular and plural), enabled with none missing or no Postpaid lines; a send refused with `CARRIER_INVOICES_MISSING` shows its text, another failure keeps the old text. `send-client-invoice-control.test.tsx` gains the `missingCarrierInvoices` cases (disabled with the note, describedby link, the coded error text).
- **E2E:** `helpers.ts` gains `attachCarrierInvoicesForEveryPostpaidSim(page)`; every spec that sends a Client Invoice with a Postpaid SIM calls it before "Send Client Invoice": `client-invoice-submission-and-visibility`, `edit-client-invoice-lines`, `send-a-client-invoice-back`, `client-invoices-side-list`, `manager-dashboard`, `client-dashboard` and `manager-invoice-review-queue`; `client-invoice-generation` asserts Send disabled until every line is covered and then enabled. The other e2e specs (including the Agent Invoice specs, which send no Client Invoice) are untouched.
- **Visual:** the four `agent-client-invoices-*` goldens are recaptured for the disabled Send with its note; no other golden moves.

## Regression

At risk: every flow that sends a Client Invoice (send, send-back, resend, approve, line edits, Agent Invoice, Review Queue, dashboards), the demo startup, and the e2e journey proofs.

Existing tests expected to change, each with the reason. Each backend file below sends a Client Invoice through `/client-invoice/send`, and the gate now refuses a send with a Postpaid SIM line and no file. Each calls `attachCarrierInvoicesForEveryPostpaidLine` before its send(s) (a no-op on a Contract with no Postpaid line); every pre-existing method keeps passing unmodified in what it asserts:

- `AgentInvoiceApiTest`, `CarrierInvoiceFileAttachConcurrentSendTest`, `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest`, `ClientInvoiceConcurrentSendTest`, `ClientInvoiceLineEditApiTest`, `ClientInvoiceLineEditRaceTest`, `ClientInvoiceSendBackApiTest`, `ClientInvoiceSendBackRaceTest`, `ClientInvoiceStoredLinesApiTest`, `LocalSupportFeesFollowClientInvoiceApiTest`, `MeClientApiTest`, `PostpaidSimPlanApiTest`, `ReviewQueueApiTest`, `SimCardCorrectionApiTest`, `TenantTotalsApiTest`: adds the helper call.
- Not touched: `AgentInvoiceByIdApiTest` and `LocalSupportFeesRaceTest` send only the Agent Invoice (`/api/agents/{id}/invoice/send`), which the gate does not cover.
- `ClientInvoiceApiTest`, `ClientInvoiceByIdApiTest` and `CarrierInvoiceFileAttachConcurrentSendTest` also gain the gate cases above; `DemoDataLoaderApiTest` gains the failing-put case.
- `frontend/components/agent/client-invoices-view.test.tsx` gains send-gate cases and its sending fixtures add covered lines; `frontend/components/agent/send-client-invoice-control.test.tsx` gains the `missingCarrierInvoices` cases and its existing cases pass unmodified (the prop defaults to 0). Pre-existing cases keep passing.
- E2E: `client-invoice-submission-and-visibility`, `edit-client-invoice-lines`, `send-a-client-invoice-back`, `client-invoices-side-list`, `manager-dashboard`, `client-dashboard` and `manager-invoice-review-queue` add the helper call before "Send Client Invoice"; `client-invoice-generation` gains the Send-disabled assertions. Their existing assertions do not change.
- The four `agent-client-invoices-*` goldens are recaptured with the note.

## Observability

The server logs the refused send at the existing conflict level with the invoice id, the count and the missing SIM numbers (the exception message); the demo loader logs one WARN when the store refuses its attach and leaves the invoice a draft.
