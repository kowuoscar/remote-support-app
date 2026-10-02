---
id: local-support-fees-follow-billed-client-invoice-lines
title: Make the Agent's Local Support Fees follow billed Client Invoice lines until approval
status: ready-for-agent
depends_on: [agent-edits-a-client-invoice-line]
labels: [backend]
stories: [14, 26, 27, 28, 29, 30, 31, 32]
---

## Context

Fourth slice of `spec.md` (`## Execution order`). Implements "Local Support Fees: the new rule" and its lifecycle table, amending ADR 0002 and 0003, as the human answered on 2026-10-02. Modules: `web`, `repository`, `logging`.

- A new `ContractAmountService` operation, "a Contract's payable amount for a month", reusing `ClientInvoiceService`'s line resolution; `AgentInvoiceService.computeLocalSupportFees` calls it in the draft read and the send snapshot. `totalForMonth` stays.
- `AgentInvoiceRepository` gains a locking finder; Agent Invoice send, approve, mark-paid and override become `@Transactional` and re-read under it. Lock order is always Client Invoice then Agent Invoice.
- `editLine` step 7: after writing the line, lock that month's Agent Invoice of the Contract's Agent; if `SENT`, add the edit's difference (new billed minus old billed, where a pre-filled line's old amount is its pre-filled amount) to its Local Support Fees and write `AuditLog.agentInvoiceLocalSupportFeesFollowed`. `DRAFT` writes nothing; `APPROVED` and `PAID` move nothing.
- ADR 0004's pay part, and the dated notes on ADR 0002 and 0003.

The one edge where unedited pay can read differently from today is in spec `## Constraints` (story 32).

## Acceptance criteria

- [ ] With no edits anywhere, Local Support Fees equal today's figure for a Contract with no Client Invoice, with a never-sent draft, and with a sent invoice.
- [ ] After an edit on a never-sent draft, a draft Agent Invoice reads the edited figure, and a reset returns it to the computation; an Agent Invoice sent afterwards freezes the edit.
- [ ] Editing a line of a Client Invoice while the Agent Invoice is `SENT` moves its Local Support Fees by exactly the edit's difference, writes one `agentInvoiceLocalSupportFeesFollowed` audit line, and a Fee logged after the Agent Invoice's send does not slip in.
- [ ] Editing while the Agent Invoice is `APPROVED` or `PAID` leaves its Local Support Fees unchanged and writes no follow line; the Manager's override of a sent Agent Invoice still changes only Salary and new advance, and a later edit leaves those as overridden.
- [ ] A Fee logged after the Client Invoice's send counts at its logged amount; once a sent-back fixture shows it as a pre-filled line the figure is unchanged, and editing that line moves it by the edit's difference; a Postpaid SIM the invoice does not bill counts at its computed amount, except on an invoice with a `BASE_AMOUNT` line, which counts its base amount with no per-SIM additions.
- [ ] An edit racing the Agent Invoice's send, or its approve, leaves Local Support Fees equal to the rule's value for the final states: the edit counted exactly once against a send, and not at all after an approve.

## Tests

- **HTTP API seam (spec Testing decisions 3):** new `LocalSupportFeesFollowClientInvoiceApiTest`. Cases: `no-edits-equals-todays-figure-for-no-invoice-draft-and-sent`, `edit-on-never-sent-draft-moves-draft-agent-invoice-and-reset-restores`, `agent-invoice-sent-after-edit-includes-it`, `edit-after-agent-invoice-sent-moves-by-exact-difference-with-one-audit-line-and-late-fee-does-not-slip-in`, `edit-after-approve-or-paid-moves-nothing`, `late-fee-after-client-invoice-sent-counts-at-logged-amount-and-unchanged-when-prefilled-then-moves-when-edited`, `approved-client-invoice-pays-billed-total-plus-unbilled`, `legacy-base-amount-line-adds-no-per-sim-lines`, `manager-override-changes-only-salary-and-new-advance-and-survives-an-edit`.
- **Races (spec item 4, committing tests cleaning up their own rows):** new `LocalSupportFeesRaceTest`, cases `edit-versus-agent-invoice-send-counts-edit-once` and `edit-versus-agent-invoice-approve-counts-edit-once-or-not-at-all`.

## Regression

- At risk: the Agent Invoice's draft read, send, approve, mark-paid and the Manager's override (now transactional and locked), the Local Support Fees figure on the Agent Invoice page and its detail page, the edit route from the previous ticket (step 7 added to it).
- Existing tests expected to change: none expected. `AgentInvoiceApiTest`, `AgentInvoiceByIdApiTest`, `ClientInvoiceLineEditApiTest` pass unedited. An assertion failing only because a Fleet changed after a Client Invoice's send is the Constraints edge: it is changed only with that edge named in the commit and listed here.

## Observability

`AuditLog.agentInvoiceLocalSupportFeesFollowed(agentInvoiceId, clientInvoiceId, oldAmount, newAmount, actor, tenant)` for each move of a sent Agent Invoice (story 30), shaped like `agentInvoiceOverridden`.
