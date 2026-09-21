---
feature: manager-invoice-review-queue
status: accepted
date: 2026-09-17
---

# Manager invoice Review Queue

## Problem

The Company Manager has no working place to find and act on invoices waiting for them. The Manager's Invoices page and the Dashboard's "Pending approvals" card show hardcoded sample data, and "Review" opens a modal whose Approve button does nothing. The review that actually works is scattered across other pages: a Client Invoice is approved from its Contract's page and an Agent Invoice from its Agent's page. The Manager has to already know which Contract or Agent to open, and neither page tells them what is waiting.

Worse, every invoice view and action only reaches the **current month's** invoice. A Client Invoice an Agent sent on the 30th that the Manager hasn't approved by the 1st becomes unreachable, and the same goes for an approved Agent Invoice not marked paid before the month rolls over. It can never be approved or paid from the app again.

## Goals / Non-goals

**Goals**

- The Manager's Invoices page lists the real **Review Queue** (CONTEXT.md): every Client Invoice in `sent` and every Agent Invoice in `sent` or `approved`, from any Contract, Agent and billing month, longest waiting first.
- Each row opens a dedicated invoice detail page showing the full invoice, where the Manager takes the action it is waiting for:
  - Client Invoice: approve.
  - Agent Invoice: override and approve, then mark paid.
- Invoices are addressed by their own identity, so a past month's invoice can be opened and acted on exactly like the current one.
- The Dashboard's "Pending approvals" card shows the real Review Queue.
- Review actions exist in exactly one place, the detail page. The Contract and Agent pages keep a compact summary that links to it.

**Non-goals**

- No change to either invoice lifecycle or its locks. `approved` stays a final, immutable Client Invoice (ADR 0001). An Agent Invoice still goes sent → approved → paid, with override only while `sent` (ADR 0003).
- No rejection or "send back to Agent" action. There is no un-approve and no un-send.
- No invoice history or archive view of already-final invoices. The queue shows only what is waiting.
- No change to the Agent's or Tester's invoice views, or to the Agent's current-month build and send flow.
- The Dashboard's other figures (billed and payout this month, entity counts) stay as they are.
- No new visual direction.

## User stories

1. As a Company Manager, I want the Invoices page to list every invoice waiting on me, so that I don't have to visit each Contract and Agent to find work.
2. As a Company Manager, I want the Review Queue to include invoices from past billing months, so that an invoice sent just before month end is never stranded.
3. As a Company Manager, I want the Review Queue ordered longest waiting first, so that I clear the oldest work before newer submissions.
4. As a Company Manager, I want to filter the Review Queue to Client Invoices or Agent Invoices, so that I can batch one kind of review.
5. As a Company Manager, I want each Review Queue row to show the type, what it is for (Contract or Agent), billing month, amount and how long it has been waiting, so that I can triage before opening it.
6. As a Company Manager, I want an empty state when nothing is waiting, so that I know the queue is clear rather than broken.
7. As a Company Manager, I want to open a Client Invoice from the Review Queue on its own page, so that I can review it in full rather than in a cramped modal.
8. As a Company Manager, I want the Client Invoice detail page to show the base amount, every Fee line, the total, its status and timestamps, so that I review exactly what the Agent sent.
9. As a Company Manager, I want to download the attached Carrier Invoice Files from the Client Invoice detail page, so that I can check the postpaid charges against them.
10. As a Company Manager, I want to download the Client Invoice PDF from its detail page, so that I see what the Client sees.
11. As a Company Manager, I want to approve a sent Client Invoice from its detail page, so that it becomes the final, immutable accounting record for that Contract's month.
12. As a Company Manager, I want to approve a Client Invoice from a past billing month, so that month rollover never blocks approval.
13. As a Company Manager, I want the Client Invoice detail page to show its final state right after I approve (status updated, no actions left), so that I can see the approval took effect.
14. As a Company Manager, I want to open an Agent Invoice from the Review Queue on its own page, so that I can review it in full.
15. As a Company Manager, I want the Agent Invoice detail page to show Local Support Fees, Salary, both Rollout Advance lines, the total, its status and timestamps, so that I review exactly what the Agent sent.
16. As a Company Manager, I want to override the Salary or new-advance line on a sent Agent Invoice from its detail page, so that I can apply a one-off adjustment before approving.
17. As a Company Manager, I want to approve a sent Agent Invoice from its detail page, so that it moves to awaiting payment.
18. As a Company Manager, I want an approved Agent Invoice to stay in the Review Queue until I mark it paid, so that I don't lose track of payments I still owe.
19. As a Company Manager, I want to mark an approved Agent Invoice paid from its detail page, so that its status reflects the payment made outside the app.
20. As a Company Manager, I want to override, approve and mark paid Agent Invoices from past billing months, so that month rollover never blocks payroll.
21. As a Company Manager, I want the Agent Invoice detail page to update in place after each action, so that I can see the result without navigating.
22. As a Company Manager, I want a "Back to invoices" link on every detail page, so that I can return to the queue and see the invoice gone once it is final.
23. As a Company Manager, I want an invoice to leave the Review Queue once it is final (Client Invoice approved, Agent Invoice paid), so that the queue only shows open work.
24. As a Company Manager, I want a clear error when an action is no longer valid (for example, another Manager already approved it), so that I refresh rather than assume it worked.
25. As a Company Manager, I want the Dashboard's Pending approvals card to show the real count and the oldest waiting invoices, each linking to its detail page, so that I can jump straight into review from the dashboard.
26. As a Company Manager, I want the Contract page to show a compact summary of its current month's Client Invoice (month, status, total) with a link to its detail page, so that I can still reach it from the Contract.
27. As a Company Manager, I want the Agent page to show a compact summary of its current month's Agent Invoice (month, status, total) with a link to its detail page, so that I can still reach it from the Agent.
28. As a Company Manager, I want a detail page for an invoice that is not waiting on me (a draft, or already final) to render read-only with no actions, so that links from the Contract or Agent summary always work.
29. As an Agent or Tester, I must not be able to see the Review Queue or reach the Manager's invoice detail pages or actions, so that approval stays a Manager-only power.
30. As an Agent, I want my own invoice pages and send flow to keep working exactly as before, so that this change doesn't disrupt month-end.

## Solution

**Why by id, not "owner + current month".** The current routes address "this Contract's/Agent's invoice for the current month" and get-or-create it. That shape can't express a past month, which the Review Queue needs. Two alternatives were rejected. Adding a month parameter to every existing route spreads a date concern across routes the Agent's own flow doesn't need. Keeping current-month-only would silently drop waiting invoices from the queue. Addressing an invoice by its own id serves any month and never creates anything, which is right for a Manager reading or acting on an invoice that already exists.

**Backend: Review Queue.** A new Manager-only read returns the Review Queue for the caller's tenant as one list mixing both invoice types. Each item carries:
- `kind`: Client Invoice or Agent Invoice
- the invoice `id`
- `status`
- `billingMonth`
- a subject: the Contract's Client and Agent names for a Client Invoice, the Agent's name for an Agent Invoice
- the owning Contract or Agent id
- `currency`
- `totalAmount`, read from the frozen snapshot, since every queue member is at least `sent`
- `waitingSince`: `sentAt` for a `sent` invoice, `approvedAt` for an approved Agent Invoice

The list is sorted by `waitingSince` ascending. Membership is exactly the CONTEXT.md definition. The filter by type is client-side over this one list.

**Backend: invoice by id (expand).** New Manager-only routes keyed by invoice id. They never get-or-create and return 404 for an id outside the caller's tenant.
- *Client Invoice by id:* read (same response shape as today's Client Invoice read), Carrier Invoice File list and download, PDF, approve. Approve keeps today's rules: only `sent` → `approved`, 409 otherwise.
- *Agent Invoice by id:* read (same response shape as today's Agent Invoice read), override, approve, mark paid. Each keeps today's transition and override rules, returning 409 on an invalid transition.

The response shapes, the snapshot-on-send reads and the transition rules are shared with the existing current-month routes. The by-id routes differ only in how they find the invoice, not in what they do with it.

**Frontend (migrate).**
- **Invoices page:** lists the Review Queue from the new endpoint. It keeps the All / Client Invoices / Agent Invoices filter and the empty state, and each row links to its detail page. The modal and the sample-data queue are deleted.
- **Detail pages:**
  - `/manager/invoices/client/<invoiceId>` renders the full Client Invoice with today's Manager Client Invoice presentation (lines, files, PDF, status badge and timestamps), plus Approve while `sent`.
  - `/manager/invoices/agent/<invoiceId>` renders the full Agent Invoice with today's Agent Invoice presentation, plus override and Approve while `sent` and Mark paid while `approved`.
  - Both have a breadcrumb or "Back to invoices" link. After a successful action the page refreshes in place and shows the new status with its actions gone. A 409 or other failure shows an inline error and changes nothing.
  - An invoice in a status with no Manager action renders read-only.
- **Existing controls:** the approve, override and mark-paid controls take the invoice id instead of the Contract or Agent id.
- **Contract and Agent pages:** the full review sections are replaced by a compact current-month invoice summary (month, status badge, total) with an "Open invoice" link to the detail page.
- **Dashboard:** the Pending approvals card reads the Review Queue: the count and the first four items, each linking to its detail page.

**Backend (contract).** Once nothing calls them, delete the Manager's current-month action routes: Client Invoice approve, and Agent Invoice override, approve and mark paid, all addressed by Contract or Agent. The current-month read, send and file-attach routes stay, because the Agent's and Tester's own flows use them. The Manager's Contract and Agent pages also keep using the current-month reads for their summaries.

## Design direction

Conforms to the committed `DESIGN.md`. No new direction, and no `DESIGN.md` change is expected. It was pinned in the MVP against the Stripe reference (`remote-support-mvp` spec, Design direction). New layouts reuse existing tokens and components: Card, Table, Badge, Money, EmptyState, Breadcrumb and SurfacePage.

Surfaces:

- Review Queue (Manager Invoices page): **Operate**. The Manager triages and picks the next invoice to act on.
- Client Invoice detail page: **Operate**. The Manager reviews and approves.
- Agent Invoice detail page: **Operate**. The Manager reviews, overrides, approves and marks paid.
- Contract and Agent invoice summaries, Dashboard card: **Operate**. Entry points into the detail pages.

## Constraints

- Inherits every constraint in the `remote-support-mvp` spec: stack, tenant scoping, explicit currency, no payment integration.
- The Review Queue, every by-id invoice route and every detail page are **Manager-only**. Agent and Tester get 403 from the API, and the frontend Manager guard applies to the pages.
- By-id routes resolve only within the caller's tenant. An id from another tenant, or one that doesn't exist, is 404, never 403, so existence doesn't leak.
- By-id routes **never create** an invoice.
- Final statuses stay final: a Client Invoice `approved` and an Agent Invoice `paid` accept no further transition (409). An Agent Invoice override is accepted only while `sent` (409 otherwise).
- Amounts shown for a `sent`/`approved`/`paid` invoice always come from its snapshot (ADR 0001, ADR 0003), never a live recomputation.
- The Review Queue is ordered by `waitingSince` ascending: `sentAt` for `sent`, `approvedAt` for an approved Agent Invoice. Ties are broken by invoice id for a stable order.
- The Dashboard card shows at most 4 Review Queue items, plus the total count. If the Review Queue can't be loaded, only the card shows an unavailable state; the rest of the Dashboard still renders. This is what lets the backend-free visual suite render the page.
- After the contract step, no Manager action route addressed by Contract id or Agent id may remain.

## Testing decisions

Tests assert external behaviour only: HTTP responses, rendered accessibility tree and user-visible output. They never assert repository calls or component internals.

1. **Backend: HTTP API seam (primary).** Spring Boot integration tests against real controllers and PostgreSQL via Testcontainers. Prior art: `ClientInvoiceApiTest`, `AgentInvoiceApiTest` (`IntegrationTest` support class). Covers:
   - Review Queue membership per status for both types.
   - Past-month invoices included.
   - Ordering by `waitingSince`, including an approved Agent Invoice ordered by `approvedAt`.
   - Snapshot totals.
   - By-id reads and every by-id action for current and past months.
   - 409 on invalid transitions and override outside `sent`.
   - 404 for unknown or other-tenant ids.
   - 403 for Agent and Tester on every new route.
   - By-id reads never creating an invoice.
   - After the contract step, the removed routes no longer exist.
2. **Frontend: Playwright e2e against the real backend (golden path).** Prior art: `client-invoice-submission-and-visibility.spec.ts` and `agent-invoice-submission-and-approval.spec.ts`, which migrate to this flow. The journey: the Agent sends → the Manager sees the invoice in the Review Queue → opens the detail page → approves (Client Invoice), or overrides and approves, then marks paid (Agent Invoice) → sees the final state → returns to the queue and the invoice is gone. It also covers the Contract and Agent summary "Open invoice" link and a Dashboard card row opening a detail page. Business rules already covered at the API seam are not re-tested here.
3. **Frontend: component tests (new seam).** Vitest with jsdom and Testing Library, run with `npm test`. No prior art in the repo, so this feature sets the convention. Client components only; async server components stay covered by e2e. Covers:
   - Review Queue view: type filter, empty state, row fields, and each row linking to the right detail page.
   - Detail views: which actions render per kind and status, including a read-only draft or final invoice, and the final-state rendering after a successful action.
   - Approve, override and mark-paid controls: the confirm step, the pending state, and the inline error on 409/500, with a mocked `fetch`.
4. **Visual goldens.** The existing backend-free suite (`tests/visual/surfaces.spec.ts`). The Dashboard's Pending approvals card becomes backend-driven, so it is masked in the manager goldens, which are re-baselined in the same commit. No new goldens: the surfaces (Manager, Agent, Client consoles) are unchanged.

## Open questions

None

## Execution order

1. `component-test-harness` — Vitest + Testing Library seam, first tests on the existing invoice action controls
2. `client-invoice-review-page` — Review Queue endpoint and page; Client Invoice by-id routes, detail page and approve
3. `agent-invoice-review-page` — Agent Invoices join the queue; by-id override/approve/paid and detail page
4. `dashboard-review-queue-card` — Dashboard card reads the real Review Queue (parallel with 3)
5. `invoice-summaries-on-contract-and-agent-pages` — Contract/Agent pages show a summary linking to the detail page; e2e migrated
6. `remove-current-month-manager-invoice-actions` — delete the Manager's current-month action routes
