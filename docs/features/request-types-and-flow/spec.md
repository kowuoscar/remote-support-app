---
feature: request-types-and-flow
status: approved
date: 2026-09-18
---

# Request types and flow

> Decisions settled in the 2026-09-18 grilling session. The user then asked for
> the pipeline to be driven end to end, so the remaining open points were
> closed from those decisions; each is marked "Orchestrator call" below.
> Depends on `carrier-catalog`.

## Problem

Every Request type shares one blank form and one flow.

- **No details.** A Tester can only pick a type, so the Agent has to go back
  and ask which phone, which SIM, which topup or which model.
- **No approval.** Costly Requests — a new or replacement phone or SIM — start
  without anyone at the company agreeing to the spend.
- **The Fleet falls out of date.** Nothing records which SIM Card sits in
  which Smartphone, so a SIM Swap changes nothing. Phones carry a free-text
  "assigned to" that means nothing, and nothing says who owns a phone.

## Goals / Non-goals

**Goals**

- The Request types are Reboot, Topup, SIM Swap, Provision Smartphone,
  Provision SIM, Replace Smartphone, Replace SIM and Other. Repair is removed;
  existing Repair Requests and Fees become Other with the description "Repair".
- Each type asks for its own details at submission. Every type also takes an
  optional description, which is required for Other.
- Provision and Replace Requests wait at Pending Approval for the Company
  Manager, whoever raised them.
- Completing a Request changes the Fleet by itself where the type implies it.
- A SIM Card records the Smartphone it is Installed in; a Smartphone holds at
  most two.
- A Smartphone has an Owner, the Client or the company. The free-text
  "assigned to" is removed.

**Non-goals**

- A Tester cancelling their own Request.
- Return, Agent Stock and SIM Card cancellation (`returns-and-agent-stock`).
- The Manager editing a Request at approval. It is approve, or reject with a
  reason.
- Request states that differ by type beyond Pending Approval and Rejected.
- Retroactive rules: a Request that exists when this ships is never sent back
  to approval, and its missing details stay empty.
- A Manager creating Requests.

## User stories

1. As a Tester, I want the submit form to change with the Request type, so that I give the Agent what they need the first time.
2. As a Tester submitting a Reboot, I want to pick which Smartphone, so that the Agent reboots the right one.
3. As a Tester submitting a Topup, I want to pick which SIM Card, so that the right line is topped up.
4. As a Tester submitting a Topup, I want to pick one of that SIM Card's Carrier's Topup Options, so that the Agent buys what I need.
5. As a Tester submitting a Topup for a SIM Card whose Carrier has no Topup Options, I want to describe the topup instead, so that I'm never blocked.
6. As a Tester submitting a SIM Swap, I want to move one SIM Card into another Smartphone, so that a line follows the device I test on.
7. As a Tester submitting a SIM Swap, I want to exchange the SIM Cards of two Smartphones in one Request, so that I don't submit two.
8. As a Tester submitting a Provision Smartphone, I want to name the brand and model, so that I get the device I need.
9. As a Tester submitting a Provision SIM, I want to choose prepaid or postpaid, the Carrier and, for postpaid, the Postpaid Plan, so that the line matches my test plan.
10. As a Tester submitting a Provision SIM, I want to optionally name the Smartphone it goes into, so that it arrives installed.
11. As a Tester submitting a Replace Smartphone, I want to pick which Smartphone, and optionally a different model, so that the broken one is swapped out.
12. As a Tester submitting a Replace SIM, I want to pick which SIM Card, so that a dead SIM is replaced.
13. As a Tester submitting an Other Request, I want to describe what I need, so that support no type covers still gets done.
14. As a Tester, I want to add an optional description to any Request, so that I can give context.
15. As a Tester, I want to pick only Active units of the chosen Contract's Fleet, so that I can't target a retired phone.
16. As a Tester, I want to see that my Provision or Replace Request is Pending Approval, so that I know why nobody has started it.
17. As a Tester, I want to see a Rejected Request's reason, so that I know what to change before resubmitting.
18. As a Tester, I want my Fleet page to show which Smartphone each SIM Card is Installed in, and who owns each Smartphone, so that I know what I have.
19. As an Agent, I want each Request to show the details the Tester gave, so that I can act without asking.
20. As an Agent, I want to see Pending Approval Requests on my Contracts without being able to start them, so that I can plan.
21. As an Agent, I want to log any type proactively with the same details a Tester would give, so that the record is complete.
22. As an Agent logging a Provision or Replace Request proactively, I want it to go to Pending Approval, so that the Manager agrees before I spend.
23. As an Agent, I want logging a proactive Fee for a Provision or Replace type to be refused with a clear message, so that I log the Request instead.
24. As an Agent, I want a proactive Topup or Other Fee to keep working as today, so that small spends stay quick.
25. As an Agent completing a Provision Smartphone, I want no extra input, so that completion is one click and the phone appears in the Fleet as company-owned with the requested model.
26. As an Agent, I want to fill in a Smartphone's serial number later from the Fleet page, so that completion doesn't wait on it.
27. As an Agent completing a Provision SIM, I want to enter only the SIM number, so that the Carrier, flavor and Plan come from the approved Request.
28. As an Agent completing a Provision SIM that named a Smartphone, I want the new SIM Card installed in it, so that the Fleet is right immediately.
29. As an Agent completing a Replace Smartphone, I want the old Smartphone retired, the new one added with the same or the requested model, and its SIM Cards carried over, so that the Fleet is right immediately.
30. As an Agent completing a Replace SIM, I want to enter the new SIM Card's details, defaulted from the old one, so that the old one is retired and the new one takes its place in the same Smartphone.
31. As an Agent completing a SIM Swap, I want no extra input, so that the Installed-in links update instantly.
32. As an Agent completing a Topup, I want the Fee amount pre-filled from the requested Topup Option and linked to it, so that I don't retype it.
33. As an Agent, I want a Request that existed before this shipped to complete the way it did before, so that nothing in flight breaks.
34. As an Agent or Manager, I want to set or clear the Smartphone a SIM Card is Installed in from the Fleet page, so that existing SIM Cards can be linked.
35. As an Agent or Manager, I want a Smartphone to refuse a third SIM Card, so that the Fleet matches physical reality.
36. As a Company Manager, I want a Pending Requests page listing every Request at Pending Approval, longest-waiting first, so that I can clear them.
37. As a Company Manager, I want each pending Request to show its type, Client, Tester, Agent, requested details and how long it has waited, so that I can decide without opening anything else.
38. As a Company Manager, I want to approve a Request, so that it moves to Submitted and the Agent can start.
39. As a Company Manager, I want to reject a Request with a required reason, so that the Tester knows why.
40. As a Company Manager, I want a Replace Request to show which unit would be retired, so that I know what I'm approving.
41. As a Company Manager, I want a count of pending Requests on my dashboard, so that I notice them.
42. As a Company Manager, I want the Review Queue to stay invoices-only, so that money review isn't mixed with request approval.
43. As a Company Manager adding a Smartphone to a Fleet, I want to say whether the Client or the company owns it, so that ownership is recorded from the start.
44. As a Company Manager, I want approval and rejection in the audit log with who decided, so that decisions are traceable.
45. As a Company Manager, I want existing Repair Requests and Fees to read as Other with the description "Repair", so that history and invoices keep their meaning and totals.
46. As a developer, I want seed data covering every type, every status including Pending Approval and Rejected, Installed-in links and both Owners, so that local testing covers it at once.

## Solution

**Types.** `RequestType` becomes Reboot, Topup, SIM Swap, Provision
Smartphone, Provision SIM, Replace Smartphone, Replace SIM, Other. The
fee-capable subset (`FeeType`) is Topup, Provision Smartphone, Provision SIM,
Replace Smartphone, Replace SIM, Other. A migration rewrites Repair Requests
and Repair Fees to Other and sets the description "Repair" where a Request had
none. No amount, billing month or invoice snapshot changes.

**Details at submission.** A Request gains an optional description (required
for Other) and type-specific details, validated by one module that both the
Tester and the Agent-proactive paths call:

| Type | Required | Optional |
|---|---|---|
| Reboot | target Smartphone | — |
| Topup | target SIM Card; a Topup Option of that SIM Card's Carrier when the Carrier has an active one, otherwise a description | — |
| SIM Swap | one move (SIM Card → Smartphone), or an exchange: two SIM Cards installed in two different Smartphones | — |
| Provision Smartphone | requested model (brand and model, free text) | — |
| Provision SIM | flavor, Carrier, and a Postpaid Plan of that Carrier when postpaid | target Smartphone |
| Replace Smartphone | the Smartphone to replace | requested model (defaults to the old one's) |
| Replace SIM | the SIM Card to replace | — |
| Other | description | — |

Every unit named must be Active and in the Request's Contract. A Carrier, Plan
or Topup Option must be active and of the Contract's Country when the Request
is submitted. One archived afterwards stays valid for that Request: archiving
hides an entry from pickers, it never invalidates a record that already uses
it. (Orchestrator call.)

A SIM Swap is stored as one or two moves, each "this SIM Card goes into this
Smartphone". An exchange is two moves derived from the two SIM Cards' current
Smartphones. Submission checks that applying the moves leaves no Smartphone
with more than two SIM Cards; completion re-checks against the Fleet as it is
then, and refuses with a clear message if it no longer fits.

Provision no longer names a unit to replace — that is what Replace is for.
Historical Provision Requests keep the unit they replaced.

**Catalog for Testers.** Anyone who can view a Contract can read the active
Carrier catalog of that Contract's Country through a Contract-scoped read. The
Country-scoped catalog routes stay Agent and Manager only.

**Lifecycle.**

```
PENDING_APPROVAL -> SUBMITTED   (approve; Manager only)
PENDING_APPROVAL -> REJECTED    (reject, reason required; Manager only)
PENDING_APPROVAL -> CANCELLED   (Agent or Manager, reason required)
SUBMITTED        -> IN_PROGRESS | CANCELLED
IN_PROGRESS      -> COMPLETED | CANCELLED
COMPLETED, CANCELLED, REJECTED are terminal
```

Provision Smartphone, Provision SIM, Replace Smartphone and Replace SIM always
start at Pending Approval, whoever raised them; an Agent logging one
proactively can no longer start it at Submitted or Completed. Other types
start as today. The decision records who decided and when.

**Fees and approval.** A proactive Fee (no existing Request) is refused for
the four approval-required types; the Agent logs the Request instead. Topup
and Other keep the proactive Fee. A Fee can't be logged against a Request that
is Pending Approval, Rejected or Cancelled. Completing a Topup Request
pre-fills the Fee amount from the Request's Topup Option and links the Fee to
it; the amount stays editable.

**Manager approval.** A Manager-only Pending Requests page lists Requests at
Pending Approval across every Contract, longest-waiting first, with type,
Client, Tester, Agent, the requested details (for a Replace, the unit that
would be retired) and age. Approve and reject are addressed by the Request's
own identity, the way Review Queue actions are. The dashboard shows the count.
The Review Queue is unchanged.

**Fleet model.**

- Smartphone gains an Owner, Client or company. Existing Smartphones become
  company-owned (Orchestrator call: the business provisioned them); the
  Manager's add-Smartphone form asks, defaulting to company. A Smartphone
  created by a Provision or Replace Request is company-owned.
- Smartphone's "assigned to" is removed everywhere. Fleet visibility stays per
  Contract, so nobody loses sight of a unit.
- Smartphone's serial becomes optional. Agent and Manager can set it later from
  the Fleet page.
- SIM Card gains Installed in: at most one Smartphone, of the same Contract,
  Active; a Smartphone holds at most two. Agent and Manager can set or clear it
  from the Fleet page. Retiring a Smartphone clears the link on its SIM Cards;
  retiring a SIM Card clears its own.

**Fleet changes on completion**, in one module, extending today's provisioning
side-effect:

- Provision Smartphone: adds a company-owned Smartphone with the requested
  model and no serial. No Agent input.
- Provision SIM: the Agent enters the SIM number; Carrier, flavor and Plan come
  from the Request, and the monthly fee from the Plan as in `carrier-catalog`.
  The SIM Card is installed in the target Smartphone if one was named and it
  still has room; otherwise it is added uninstalled.
- Replace Smartphone: retires the named Smartphone, adds a company-owned one
  with the requested or the same model, and moves the old one's SIM Cards into
  it.
- Replace SIM: the Agent enters the new SIM Card's details, defaulted from the
  old one's; the old SIM Card is retired and the new one takes its Smartphone.
- SIM Swap: applies the moves. No Agent input.
- Reboot, Topup, Other: none.

A Request created before this feature has no details. Completing one falls
back to today's behaviour for its type, including the full SIM form for a
Provision SIM.

**Frontend.**

- Tester submit dialog and Agent log-Request dialog: one details section per
  type, each its own component, sharing unit pickers.
- Tester and Agent Request lists: a details summary per row, Pending Approval
  and Rejected in the status filter, the rejection reason shown like a
  cancellation reason.
- Agent completion step: per type as above.
- Manager: Pending Requests page in the navigation, approve and reject (with
  reason) controls, dashboard count card.
- Fleet tables (all roles): Owner and Installed-in columns, no "assigned to".
  Agent and Manager Fleet pages: set serial, set or clear Installed in.

**Seed data.** Requests of every type and every status, Installed-in links and
both Owners, kept minimal: the final demo-data pass rebuilds the whole story.

## Design direction

Conforms to the committed `DESIGN.md`. No new direction. Pending Approval uses
the warning Badge tone and Rejected the danger tone. New layouts reuse Card,
Table, Badge, Dialog, EmptyState and SurfacePage. The navigation gains a
Manager "Requests" item, recorded in `DESIGN.md` in the same commit as its
goldens.

Surfaces:

- Tester submit dialog, Agent log-Request dialog: **Operate**.
- Tester and Agent Requests lists, Agent completion step: **Operate**.
- Manager Pending Requests page and dashboard card: **Operate**.
- Fleet tables and Fleet page controls: **Operate**.

## Constraints

- Inherits every constraint in `remote-support-mvp` and `carrier-catalog`.
- Only a Manager approves or rejects. An Agent or Tester gets 403 on those
  routes and on the pending list; an unknown or other-tenant Request is 404.
- An Agent can never move a Request out of Pending Approval except to
  Cancelled.
- A Smartphone never holds more than two SIM Cards, enforced in the API.
- The Repair-to-Other migration changes no Fee amount, no billing month and no
  Client or Agent Invoice total, draft or frozen.
- No Request that exists at release changes status or gains required details.
- The Tester catalog read exposes only active entries of the Contract's own
  Country.

## Testing decisions

Same seams as `carrier-catalog`, agreed there. Tests assert external
behaviour.

- **Backend API tests** (MockMvc over Testcontainers, `IntegrationTest` base).
  Prior art: `RequestApiTest`, `FeeApiTest`, `SimCardCarrierApiTest`,
  `PostpaidSimPlanApiTest`, `SimCardCarrierMigrationTest`. Cover per-type
  validation on both the Tester and the Agent-proactive path, the lifecycle
  and who may make each transition, approval-required start status, the
  proactive-Fee refusal, each completion effect, the two-SIM limit, the legacy
  fallback, the Contract-scoped catalog read, and the Repair migration with
  invoice totals unchanged.
- **Playwright end-to-end.** Prior art: `tester-request-submission.spec.ts`,
  `agent-request-fulfillment.spec.ts`, `fee-logging-and-provisioning.spec.ts`.
  Cover: a Tester submits a Provision SIM, the Manager approves it, the Agent
  completes it with a number and the Fleet shows the SIM Card installed; a
  Manager rejects and the Tester sees the reason; a SIM Swap exchange updates
  both Smartphones; a Topup completes with the Option's amount.
- **Vitest component tests** where a form holds logic: the per-type details
  sections, the exchange picker, the approve and reject controls.
- **Visual goldens** for the Pending Requests page, per the frontend
  Definition of Done.

## Open questions

None

## Execution order

1. `other-replaces-repair` — Other type with a description on every Request; Repair migrated.
2. `smartphone-owner-and-optional-serial` — Owner, no "assigned to", serial optional and settable later. Parallel with 1.
3. `sim-installed-in-smartphone` — Installed-in link, two-SIM limit, Fleet controls. Blocked by 2.
4. `reboot-and-topup-details` — target units, Topup Option, Contract-scoped catalog read, per-type details structure. Blocked by 1. Parallel with 3.
5. `provision-request-details` — requested model, Carrier, flavor, Plan, target Smartphone; completion from the Request. Blocked by 3 and 4.
6. `replace-requests` — Replace Smartphone and Replace SIM. Blocked by 5.
7. `sim-swap-moves` — move and exchange, applied on completion. Blocked by 5. Parallel with 6.
8. `manager-approves-requests` — Pending Approval, Rejected, Pending Requests page, proactive rules. Blocked by 6 and 7.
