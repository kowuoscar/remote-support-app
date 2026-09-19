---
feature: returns-and-agent-stock
status: implemented
date: 2026-09-18
---

# Returns and Agent Stock

> Decisions settled in the 2026-09-18 grilling session. The user then asked for
> the pipeline to be driven end to end, so the remaining open points were
> closed from those decisions; each is marked "Orchestrator call" below.
> Depends on `request-types-and-flow`.

## Problem

A unit leaves a Client's Fleet only when someone retires it by hand, and no
Request records why it left. A company-owned phone or SIM that could serve
another Client has nowhere to go: retiring it loses it. And when a Postpaid
SIM is cancelled with the carrier, nothing records when the cancellation takes
effect, so nobody can say which months the Client still owes.

## Goals / Non-goals

**Goals**

- A **Return** Request names the Smartphones and SIM Cards leaving a
  Contract's Fleet.
- Each returned unit gets a **Disposition**: a Client-owned Smartphone is
  posted back to the Client; for a company-owned Smartphone the Manager chooses
  posted back to the company or kept in the Agent's Stock; for a SIM Card the
  Manager chooses cancelled or kept in the Agent's Stock.
- A Return holding any company-owned unit waits at Pending Approval, where the
  Manager picks the Dispositions.
- A cancelled SIM Card carries the effective cancellation date the Agent
  records, and a Postpaid SIM is billed through the month of that date.
- **Agent Stock** holds the company-owned units an Agent keeps, outside every
  Contract, and a Provision or Replace Request can be fulfilled from it.

**Non-goals**

- Part-month billing of a cancelled SIM Card.
- Billing or reimbursing a Postpaid SIM while it sits in Stock. It is in no
  Contract, so no Client Invoice counts it, and the Agent Invoice is computed
  per Contract. The Manager should cancel a postpaid line rather than stock it
  if that matters; the approval screen says so. (Orchestrator call — flagged in
  the recap as a business gap to decide later.)
- Moving Stock between Agents, or adding to Stock other than through a Return.
- A Fee on a Return (postage is not modelled).
- Changing a Disposition after approval. Cancel the Request and submit again.

## User stories

1. As a Tester, I want to submit a Return naming one or more Smartphones and SIM Cards of a Contract, so that what I no longer need leaves my Fleet with a record of why.
2. As a Tester, I want to pick only Active units of that Contract, so that I can't return something already gone.
3. As a Tester, I want a Return of only Client-owned Smartphones to need no approval, so that sending my own phones back isn't held up.
4. As a Tester, I want to see that a Return holding company units is Pending Approval, so that I know why it hasn't started.
5. As a Tester, I want to see each unit's Disposition once decided, so that I know whether to expect a parcel label or a collection.
6. As an Agent, I want to log a Return proactively with the same details, so that the record is complete when a Tester tells me in person.
7. As an Agent, I want each Return to show every unit and its Disposition, so that I know what to post, cancel or keep.
8. As an Agent completing a Return, I want to record the effective cancellation date of each SIM Card being cancelled, so that billing stops at the right month.
9. As an Agent, I want completing a Return to update the Fleet by itself: posted and cancelled units retired, kept units moved to my Stock, SIM Cards uninstalled from a returned Smartphone.
10. As an Agent, I want a Stock page listing the Smartphones and SIM Cards I hold, so that I know what I can reuse.
11. As an Agent completing a Provision Smartphone or Replace Smartphone, I want to pick a Smartphone from my Stock instead of adding a new one, so that kept units get reused.
12. As an Agent completing a Provision SIM or Replace SIM, I want to pick a matching SIM Card from my Stock, so that a kept line gets reused.
13. As an Agent, I want completion to work as before when I pick nothing from Stock, so that Provision Smartphone stays one click.
14. As a Company Manager, I want a Return holding a company-owned unit to appear in Pending Requests, so that I decide what happens to company property.
15. As a Company Manager, I want to choose each company-owned unit's Disposition while approving, and be unable to approve until all are chosen, so that the Agent never guesses.
16. As a Company Manager, I want a Client-owned Smartphone in the same Return to show as "posted back to the Client" with nothing to choose, so that the screen is honest about what I control.
17. As a Company Manager, I want a reminder on a Postpaid SIM that keeping it in Stock keeps the carrier charging with no Client to bill, so that I choose knowingly.
18. As a Company Manager, I want to see every Agent's Stock, so that I know what the company holds where.
19. As a Company Manager, I want a cancelled Postpaid SIM to stay on its Contract's Client Invoice through the month of its cancellation date and drop out after, so that the Client pays for what the carrier charged.
20. As a Company Manager, I want sent and approved Client Invoices never to move because of a Return, so that approved money stays approved.
21. As a Company Manager, I want Dispositions, cancellations and Stock movements in the audit log, so that every unit's path is traceable.
22. As a developer, I want seed data with a completed Return of each Disposition, a unit in Stock, a Request fulfilled from Stock and a cancelled Postpaid SIM, so that local testing covers it at once.

## Solution

**Return type.** `RequestType` gains Return. It never carries a Fee. It
requires at least one unit: Active Smartphones and SIM Cards of the Request's
Contract, each at most once. A description is optional. The details module
from `request-types-and-flow` validates it on both the Tester and the
Agent-proactive path.

**Approval.** A Return holding at least one company-owned unit — any SIM Card,
or a company-owned Smartphone — starts at Pending Approval, whoever raised it.
A Return holding only Client-owned Smartphones starts at Submitted (or
Completed, for an Agent logging it proactively).

**Disposition**, one per returned unit:

| Unit | Disposition | Who sets it |
|---|---|---|
| Client-owned Smartphone | Posted to Client | fixed at submission |
| Company-owned Smartphone | Posted to company, or Kept in Stock | Manager, at approval |
| SIM Card | Cancelled, or Kept in Stock | Manager, at approval |

Approving a Return requires a Disposition for every company-owned unit in the
same action; without them the approval is refused. Rejecting works as for any
Request. The Pending Requests page shows the units and the pickers, and a note
on a Postpaid SIM that Stock keeps the carrier charging.

**Completion.** Completing a Return applies every unit's Disposition at once:

- Posted to Client, Posted to company: the Smartphone is retired.
- Cancelled: the Agent gives the effective cancellation date for each such
  SIM Card; completion is refused without one. The SIM Card is retired and
  keeps the date.
- Kept in Stock: the unit leaves the Contract and joins the Stock of the
  Contract's Agent.
- A returned SIM Card is uninstalled. SIM Cards installed in a returned
  Smartphone that are not themselves in the Return stay in the Fleet,
  uninstalled.
- A unit no longer Active at completion refuses the completion with a clear
  message.

**Agent Stock.** A unit in Stock belongs to no Contract and is held by exactly
one Agent. It is company-owned, shows on no Fleet, counts on no invoice, and
can't be the target of any Request. The Agent sees their own Stock on a Stock
page; the Manager sees every Agent's, filterable by Agent. Stock is read-only
apart from fulfilment: units enter through a Return and leave through a
Provision or Replace.

**Fulfilment from Stock.** When completing a Provision Smartphone or Replace
Smartphone, the Agent may pick a Smartphone from their own Stock; it joins the
Contract as Active instead of a new one being added. When completing a
Provision SIM or Replace SIM, the Agent may pick a SIM Card from their own
Stock that matches the Request: same Carrier and flavor, and for postpaid the
same Postpaid Plan (Orchestrator call: the Manager approved that Plan). The
SIM Card keeps its number and monthly fee. Everything else about completion —
installing into the target Smartphone, retiring the replaced unit — is
unchanged. Picking nothing completes as in `request-types-and-flow`.

**Billing a cancelled Postpaid SIM.** A Contract's base amount for billing
month X counts every Active Postpaid SIM of the Contract, plus every Postpaid
SIM of the Contract cancelled with an effective date on or after the first day
of X. No part-month amounts. It drops out from the month after. The shared
Contract amount computation changes once, so the Client Invoice and the Agent
Invoice's Local Support Fees stay in step. Frozen snapshots are untouched
(ADR 0001, ADR 0003). A SIM Card kept in Stock leaves the base amount at once.

**Frontend.**

- Tester submit dialog and Agent log-Request dialog: a Return details section
  with a multi-select of the Contract's Active units.
- Requests lists: each Return lists its units and their Dispositions.
- Manager Pending Requests: Disposition pickers inside the approve control.
- Agent completion step: cancellation dates for a Return; a "from my Stock"
  picker for Provision and Replace.
- Agent Stock page and Manager Stock page (Agent filter), in the navigation.

**Seed data.** Kept minimal; the final demo-data pass rebuilds the story.

## Design direction

Conforms to the committed `DESIGN.md`. No new direction. The navigation gains
an Agent "Stock" item and a Manager "Stock" item, recorded in `DESIGN.md` in
the same commit as the goldens they move. All surfaces are **Operate**: the
Return details section, the Disposition pickers, the completion step, the two
Stock pages.

## Constraints

- Inherits every constraint in `remote-support-mvp`, `carrier-catalog` and
  `request-types-and-flow`.
- Only a Manager sets a Disposition, and only while approving.
- A unit is in exactly one place: one Contract's Fleet, or one Agent's Stock.
- An Agent fulfils only from their own Stock; a Tester never sees Stock.
- No sent or approved Client Invoice, and no sent, approved or paid Agent
  Invoice, changes total because of a Return, a cancellation or a Stock move.
- A cancellation date may be in the past or the future.

## Testing decisions

Same seams as the two earlier features.

- **Backend API tests.** Prior art: the `request-types-and-flow` API tests,
  `ClientInvoiceApiTest`, `AgentInvoiceApiTest`. Cover Return validation on
  both paths, start status by ownership mix, approval refused without every
  Disposition, each completion effect, cancellation date required, stale unit
  at completion, the Stock read per role and tenant, fulfilment matching rules
  and own-Stock-only, the base amount across the cancellation month boundary
  for draft invoices, and frozen invoices unchanged.
- **Playwright end-to-end.** A Tester returns a company-owned Smartphone and a
  SIM Card → the Manager keeps the phone and cancels the SIM → the Agent
  completes with a date → the Fleet loses both and the Stock page shows the
  phone. Then the Agent fulfils a Provision Smartphone from Stock.
- **Vitest component tests**: the Return unit multi-select, the Disposition
  pickers inside the approve control, the Stock picker.
- **Visual goldens** for the two Stock pages.

## Open questions

None

## Execution order

1. `return-client-owned-smartphones` — Return type and details; the no-approval path; completion retires and uninstalls.
2. `manager-decides-return-disposition` — approval with Dispositions; Posted to company and Cancelled with its date. Blocked by 1.
3. `cancelled-sim-billed-through-its-month` — base amount counts a cancelled Postpaid SIM through its cancellation month. Blocked by 2.
4. `agent-stock` — Kept in Stock Disposition; Agent and Manager Stock pages. Blocked by 2. Parallel with 3.
5. `fulfil-from-stock` — Provision and Replace completion from the Agent's Stock. Blocked by 4.
