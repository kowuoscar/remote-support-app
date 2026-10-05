---
id: agent-reaches-a-client-invoice-by-its-id
title: Let the Contract's Agent read, edit, attach to and send a Client Invoice by its id
status: in-progress
depends_on: [manager-sends-a-client-invoice-back, move-carrier-invoice-file-attach-into-the-client-invoice-service]
labels: [backend]
stories: [15, 17, 18, 19, 20, 24, 25, 28, 33]
---

## Context

Second slice of `spec.md` (`## Execution order`), the Agent's backend half; spec `## Solution`, "Backend: the Agent reaches an invoice by its id" (the route table and the "open to its Agent" predicate). Opens `GET /api/client-invoices/{id}`, `GET .../files`, `GET .../files/{fileId}` to the Contract's own Agent, and adds `POST .../files` (calling the attach operation the enabler moved into `ClientInvoiceService`), `PUT .../lines` (calling the same `editLine` as the current-month `PUT /api/contracts/{contractId}/client-invoice/lines`, same body and responses) and `POST .../send` (the same service send). Each check goes through `ClientInvoiceAccessGuard` against the invoice's Contract after the Tenant-scoped lookup. One predicate in `ClientInvoiceService`, open to its Agent (a `DRAFT` that is sent back, or of the current billing month, UTC), gates every Agent write; otherwise `409` with `{ "code": "PAST_MONTH_DRAFT_NOT_SENDABLE", "message": … }` carried by a `Reason` enum on its conflict exception as `AgentLoginConflictException` does. `SecurityConfig` narrows `/api/client-invoices/**` for exactly those routes, with approve and send-back keeping a Manager-only matcher placed before it. The Agent's by-id PDF stays refused. Modules: `web`, `security`, `dto`.

A resend by id stores the late-Fee lines through the existing send (`edit-client-invoice-lines`), and an edit moves a sent Agent Invoice through `AgentInvoiceService.followClientInvoiceEdit`; this ticket proves both through the real send-back from `manager-sends-a-client-invoice-back`.

## Acceptance criteria

- [ ] The Contract's own Agent reads a sent-back invoice of any month by id, with its reason, its files, and each file's download; another Agent of the Tenant or a Tester gets `403` on the by-id read, files, line edit, attach and send; a Manager gets `403` on `PUT .../lines` and `POST .../send`; the Agent's by-id PDF, approve and send-back get `403`.
- [ ] Every by-id route on another Tenant's invoice gets `404`, and no by-id route ever creates an invoice.
- [ ] The Agent edits a sent line, and a late-Fee line (a Fee of the month logged after the first send, shown with `edited` false and its logged amount), of a sent-back invoice by id, with `edit-client-invoice-lines`' validation and responses, and attaches a file by id.
- [ ] The Agent resends by id: `200`, `SENT`, the edited amounts, the frozen lines equal to the lines shown just before (both late Fees included, none duplicated or missing, no server error), a Fee logged after the resend not appearing; a past-month invoice (fixture) works the same and serves the edited amount and the attached file.
- [ ] The Agent's by-id send, attach and line edit of a past-month `DRAFT` never sent get `409` with code `PAST_MONTH_DRAFT_NOT_SENDABLE` and leave the invoice unchanged; a current-month draft never sent is accepted.
- [ ] A send-back and a resend leave a draft, a sent and an approved Agent Invoice's Local Support Fees unchanged, late Fee included; an edit by id on the sent-back invoice, of a sent line or a late-Fee line, moves a `SENT` Agent Invoice by exactly the difference with one `agentInvoiceLocalSupportFeesFollowed` audit line, and leaves an `APPROVED` one unchanged.

## Tests

- **HTTP API seam (spec Testing decisions 1):** `ClientInvoiceSendBackApiTest` (created by the blocker) gains `agent-reads-sent-back-invoice-and-files-by-id`, `other-agent-and-tester-get-403-on-every-agent-by-id-route`, `manager-gets-403-on-by-id-lines-and-send`, `agent-gets-403-on-by-id-pdf-approve-and-send-back`, `other-tenant-invoice-gives-404-on-every-by-id-route`, `agent-edits-sent-line-and-late-fee-line-by-id`, `agent-attaches-file-by-id`, `resend-by-id-freezes-lines-shown-including-late-fees`, `fee-after-resend-does-not-appear`, `past-month-sent-back-invoice-read-edit-attach-and-resend-by-id`, `past-month-never-sent-draft-is-409-with-code-and-unchanged`, `current-month-never-sent-draft-is-accepted`, `send-back-and-resend-leave-agent-invoice-local-support-fees-unchanged-in-draft-sent-and-approved`, `by-id-edit-moves-sent-agent-invoice-by-the-difference-and-not-approved-one`.

## Regression

- At risk: the current-month send, attach and line-edit routes (kept), the Manager's by-id read, files, PDF and approve, role enforcement in `SecurityConfig`, the `DRAFT` read that never creates an invoice, the Agent's pay.
- Existing tests expected to change: `backend/src/test/java/com/remotesupport/backend/web/ClientInvoiceByIdApiTest.java` method `agentsAndTestersAreForbiddenFromEveryByIdRouteEvenForTheirOwnContract` expects the Contract's own Agent to get `403` on the by-id read and files routes; this ticket opens those to that Agent on purpose, so only its own-Agent loop is narrowed to the PDF and approve routes; its Tester loop stays exactly as it is (the other-Agent refusal is covered by the new cases above). Every other method of that class keeps passing unmodified. `ClientInvoiceSendBackApiTest` gains the cases above and every pre-existing method keeps passing unmodified. `ClientInvoiceApiTest`, `ClientInvoiceLineEditApiTest`, `ClientInvoiceStoredLinesApiTest` and `LocalSupportFeesFollowClientInvoiceApiTest` pass unmodified.

## Observability

N/A — the by-id routes reuse the existing send, `editLine` and file audit lines (`agentInvoiceLocalSupportFeesFollowed` among them); no new event.
