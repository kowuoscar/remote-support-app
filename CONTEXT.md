# Remote Support Platform

A platform for a business that hires local support people in different countries to provide in-person mobile-testing support to a client's testers.

## Language

**Agent** (Local Support Agent):
A person hired remotely in a specific country who supplies smartphones and SIM cards to a Client's Testers and executes support tasks (topups, reboots, SIM swaps) on request. Submits a monthly invoice claiming salary and reimbursable expenses. Signs in with exactly one login (a User with role `AGENT`), which the Manager creates together with the Agent in a single step — email and temporary password, the same way a Tester's login is created — so no newly created Agent is ever unable to sign in. An Agent created before that rule existed may still lack a login; the Manager gives it one from the Agent's page, and it never gets a second. `Agent.salaryAmount` is only ever the value supplied at creation — the *current* standing salary, and its full history, live in `AgentStandingAmount` (see below); `agent-standing-amounts-and-invoice-generation` writes both, in the same request, so the two never disagree at creation time.
_Avoid_: Rep, field agent, support worker.

**Standing amount** (of an Agent's salary or Rollout Advance):
A Manager-set value — standing monthly salary or standing Rollout Advance — that auto-populates every future Agent Invoice until changed again (`agent-standing-amounts-and-invoice-generation` ticket, spec.md user stories 5-6). Modeled as an append-only history, `AgentStandingAmount` rows of `(agent, amountType, amount, effectiveMonth, setBy, setAt)`, rather than a mutable column: a change a Manager makes today must never alter the invoice for the month already in progress, only the one after it. Resolving "the amount in effect for month X" = the most recent row (by `effectiveMonth`, then `setAt` as a same-month tiebreaker) with `effectiveMonth <= X`. Every Agent has at least one `SALARY` row from the moment it's created (the initial salary, effective that same calendar month); `ROLLOUT_ADVANCE` has none at all until a Manager sets one for the first time, and resolves to 0 until then — an Agent's Rollout Advance is opt-in cash-flow support, not something every Agent has from day one.
_Avoid_: Salary/advance history (too implementation-flavored — "standing amount" is the general term covering both).

**Company Manager**:
The internal role that manages Clients, contracts, Agents and Resources, and approves Agent invoices and payroll for the support business.
_Avoid_: Admin, Ops manager.

**Client**:
The company whose Testers receive local support. The MVP serves a single Client.
_Avoid_: Customer, account.

**Tester**:
An individual user belonging to a Client, with their own login, who submits task requests and views the fleet assigned to them.
_Avoid_: End user, client user.

**Primary Contact**:
A single Tester per Client, flagged by the Manager as the one to reach for account-level questions. A visible designation, not an extra permission — every Tester at a Client still sees all of that Client's data. Modeled as a boolean flag on Tester (`isPrimaryContact`), enforced to at most one per Client, rather than a separate `primaryContactId` on Client — the flag lives on the entity the designation is actually about.
_Avoid_: Admin, owner.

**Contract**:
The billing and operational relationship between one Client and one Agent. A Client can hold several Contracts (e.g. one per country/Agent); an Agent can hold several Contracts (typically with different Clients in their own country). Each Contract owns its own Fleet.
_Avoid_: Engagement, account.

**Fleet**:
The set of Smartphones and SIM Cards provisioned under a single Contract.
_Avoid_: Inventory, devices.

**Postpaid SIM** / **Prepaid SIM**:
The two SIM Card billing flavors. A Postpaid SIM carries a fixed monthly fee that rolls into its Contract's base amount. A Prepaid SIM carries no monthly fee.
_Avoid_: Plan type.

**Request**:
A support action a Tester submits, or the Agent logs on the Tester's behalf. Its **Request type** is one of: Reboot, Topup, SIM Swap, Provision Smartphone, Provision SIM, Replace Smartphone, Replace SIM, Return, or Other. Every type shares one lifecycle — Submitted → In Progress → Completed, or Cancelled with a reason — preceded, for approval-required types, by Pending Approval. What varies by type is the details it requires at submission, whether it can carry a Fee, what completing it changes in the Fleet, and whether it needs approval. Carries no monetary amount by itself.
_Avoid_: Ticket, task.

**Replace Smartphone** / **Replace SIM** (Request types):
A Request to swap out one named unit of a Contract's Fleet for a new one; completing it retires the named unit and adds its replacement. Distinct from **Provision**, which only ever means a net-new unit. No reason is required.
_Avoid_: Provision-with-replacement.

**Other** (Request type):
A free-text Request, described by its submitter, for support no other type covers — e.g. an Agent's repair work. Submittable by a Tester or an Agent, and can carry a Fee. Replaces the former Repair type.
_Avoid_: General, Repair, misc.

**Pending Approval** (of a Request):
The starting status of every approval-required Request — Provision Smartphone, Provision SIM, Replace Smartphone, Replace SIM, and any Return holding a company-owned unit — whoever raised it, Tester or Agent. Only the Company Manager moves it on: approving puts it at Submitted (choosing the Disposition, for a Return), rejecting ends it at Rejected. The Agent can see it but cannot start it.

**Pending Requests**:
The Requests currently at Pending Approval, across every Contract, waiting on the Company Manager — ordered longest-waiting first. Separate from the Review Queue, which stays invoices-only: approving a Request is agreeing to a spend before it happens, reviewing an invoice is checking money after it did.
_Avoid_: Approval queue, request inbox.

**Rejected** (of a Request):
The terminal status of a Request the Company Manager declined at approval, with a required reason. Distinct from Cancelled: Rejected means "the Manager said no", Cancelled means "no longer needed".
_Avoid_: Denied, declined.

**Owner** (of a Smartphone or SIM Card):
A Smartphone is owned either by the Client or by the company; a SIM Card is always company-owned. A Smartphone reached through a Provision or Replace Request is always company-owned. Units are part of a Contract's Fleet, never assigned to an individual Tester.

**Installed in** (of a SIM Card):
The Smartphone a SIM Card currently sits in, if any. A Smartphone holds at most two SIM Cards. A SIM Swap Request moves one SIM into another Smartphone, or exchanges the SIMs of two Smartphones.

**Agent Stock**:
The company-owned Smartphones and SIM Cards an Agent holds that belong to no Contract's Fleet — kept after a Return for use with a future Client. An Agent may fulfil a Provision or Replace Request from their Stock instead of acquiring a new unit.
_Avoid_: Inventory, spare pool.

**Disposition** (of a returned unit):
What happens to a unit leaving a Fleet through a Return. A Client-owned Smartphone is always posted back to the Client. For a company-owned Smartphone the Company Manager chooses: posted back to the company, or kept in the Agent's Stock. For a SIM Card: cancelled, or kept in the Agent's Stock. A cancellation carries the effective cancellation date the Agent records; a Postpaid SIM's base amount counts a billing month in full — no part-month amount — whenever that month's first day is on or before the cancellation date, and drops out from the month after (cancelled-sim-billed-through-its-month ticket).

**Carrier**:
A mobile operator in one country, offering **Topup Options** (for a Topup Request) and **Postpaid Plans** (for a Postpaid SIM), each with a name and a price in that country's currency. Maintained by the Agents of that country, who know their local carriers' offers; archived rather than deleted once in use. Every SIM Card added to a Fleet names an active Carrier of its Contract's country; a SIM Card from before the catalog may name none, and one whose Carrier was archived later still shows it. Every Postpaid SIM added since the catalog names an active Plan of its own Carrier, and its monthly fee is copied from that Plan's price when it's provisioned — never typed in, and never re-read afterwards, so a later price change touches neither SIMs already on the plan nor any Client Invoice. A Postpaid SIM from before the catalog names no Plan and keeps the fee it was given. A Postpaid Plan's price is the SIM's monthly fee; a Topup Option's price is the suggested amount of the resulting Fee, which the Agent may adjust.
_Avoid_: Operator, provider, network.

**Proactive** (of a Request):
A Request the Agent logs directly, on a Tester's behalf, rather than one the Tester submitted themselves — starting at Submitted or immediately at Completed, the Agent's call. Distinct from "Tester-authored"; the two are queryable independently of who it's *for* (every Request, proactive or not, still names the Tester it's raised on behalf of). A proactively-logged Fee (fee-logging-and-provisioning ticket) is the same idea one level up: no pre-existing Request, so one is auto-created to keep the Fee traceable.
_Avoid_: Ad-hoc, walk-in.

**Fee**:
A billable line item an Agent logs against a Contract, always tracing back to the Request that caused it — enforced as a non-nullable foreign key, not just a service-layer rule, so there is no code path that creates one without a Request. A Reboot, SIM Swap or Return never produces a Fee; a Topup, Provision Smartphone/SIM, Replace Smartphone/SIM, or Other can. Its fee type mirrors exactly that fee-capable subset of Request types, so a Fee for a non-fee-capable type can't even be constructed. A SIM swap that really did require provisioning a new physical SIM is not modeled as a Fee attached to the SIM Swap Request itself — it is logged as its own Provision SIM Fee (proactive if no Provision SIM Request already exists), alongside the original swap. Currency is always copied from its Contract at creation (the Agent never picks a different one). A Topup Fee may name the Topup Option it was bought from — optional, allowed on no other fee type, and only for an active Option of an active Carrier in the Contract's Country. The Option records where the Fee came from, never what it costs: the amount is whatever the Agent submitted, so re-pricing the Option leaves every existing Fee alone. Carries an explicit `billingMonth` (the first day of the month, set from `createdAt` at creation time) rather than one derived from `createdAt` at query time — the stored column keeps "this Contract's Fees for month X" a plain equality filter for `client-invoice-generation`'s monthly aggregation, and is the column a future backdating feature (an Agent attributing a Fee logged a few days into a month to the prior month's invoice) would use, without a schema change.
_Avoid_: Charge, cost.

**Client Invoice**:
The monthly statement an Agent prepares for one Contract: its postpaid base amount plus that month's Fees, with carrier invoices attached as supporting files. Lifecycle: draft → sent → approved (client-invoice-submission-and-visibility ticket completes it — the Agent sends, only a Manager approves, and a sent/approved invoice is locked against further edits). One per Contract per calendar month, identified by a first-of-month `billingMonth`. Get-or-create on first view: an Agent opening "this Contract's Client Invoice for the current month" gets one created in `draft` automatically if none exists yet — there is no separate create step, the same "no separate create step" shape a proactive Fee's auto-created linking Request already established. Only that current-month view creates: a Manager opening a Client Invoice from the Review Queue reaches it by its own identity, for any billing month, and never creates one. The Manager's approval is likewise addressed by the invoice's own identity and nothing else — there is no "approve this Contract's invoice for the current month" action; "this Contract's current month" is the Agent's and the Client's way in, never the Manager's way to act.

*Live while draft, frozen from sent onward*: while `draft`, the base amount and Fee lines are computed live (base amount from every currently-Active Postpaid SIM in the Fleet, plus a cancelled Postpaid SIM still billing through its cancellation month — see "Disposition" — Fee lines from that Contract's Fees for that `billingMonth`) on every read, per spec.md's "at the time of viewing" (client-invoice-generation ticket; cancelled-sim-billed-through-its-month ticket) — the Agent is still assembling it. The moment the Agent sends it, both are **snapshotted**: the base amount is copied onto the Client Invoice row, and the Fee-line membership is pinned into a `ClientInvoiceFeeSnapshot` join table (which Fees counted, not a copy of their fields — a Fee is never updated or deleted once logged, so pinning membership is enough). From `sent` onward every read serves that snapshot, never a fresh computation, so a Fee an Agent logs against the same Contract/month *after* sending can never silently change a total the Manager already approved or the Client was already shown. See `docs/adr/0001-client-invoice-snapshot-on-send.md` for the full reasoning. Rendered as PDF on demand from that snapshot, never stored as a static file, and only once `sent`/`approved` — a draft has nothing final to render yet.
_Avoid_: Bill.

**Carrier Invoice File**:
One file (typically a carrier-issued PDF) an Agent attaches to a Client Invoice draft as supporting documentation for its postpaid charges. Stored on the backend's local filesystem (client-invoice-generation ticket) — an opaque path only the backend ever resolves — with filename/content-type/size recorded as row metadata; not modeled as its own billing entity, matching spec.md's "opaque supporting documents, not modeled entities". A Client Invoice can carry several.
_Avoid_: Attachment (too generic — every Client Invoice file is specifically a carrier's own invoice).

**Agent Invoice**:
The monthly invoice an Agent sends to the Company Manager: full reimbursement of every Fee (postpaid base + additional) across all of the Agent's Contracts that month, plus salary, plus the Rollout Advance adjustment. One per Agent per calendar month, get-or-created on first access — the same shape Client Invoice established. Only that current-month view creates: a Manager opening an Agent Invoice from the Review Queue reaches it by its own identity, for any billing month, and never creates one. Every Manager action on it — override, approve, mark paid — is addressed the same way, by the invoice's own identity; only the Agent's own view/build and send are addressed as "this Agent's invoice for the current month". Lifecycle: draft → sent → approved → paid, every transition set manually by the Manager (`agent-standing-amounts-and-invoice-generation` wrote only `draft`; `agent-invoice-submission-and-approval` completes send/approve/paid and the Manager's per-invoice override).

*Local Support Fees* is computed straight from each Contract's Fleet/Fees for the month (the same math Client Invoice's base amount + Fee total uses, extracted into a shared `ContractAmountService` both controllers call), **never** by reading a Client Invoice row or its frozen snapshot, and *not gated on that Contract's Client Invoice status at all* — a Contract whose Client Invoice is still `draft`, `sent`, or `approved` all count identically, and a Fee logged against a Contract *after* its Client Invoice was already sent (and so excluded from that Client Invoice's own frozen total) still counts here. This is deliberate: the Agent already fronted that money the moment it happened, regardless of how far the separate Client-facing paperwork has progressed through its own review — reading the two independently is simpler than reconciling different "did this count" rules for one number, and keeps Client Invoice amounts (read-only from here) entirely unaffected by anything on this side.

*Salary* and the two Rollout Advance lines resolve from the standing amount in effect for the invoice's own `billingMonth` (salary, new advance) and the month before it (advance repayment) — see Standing amount above.

*Live while `draft`, frozen from `sent` onward*: the same shape Client Invoice established (ADR 0001), one step further — all **four** line items freeze together at once, since there is no single "base amount" here the way Client Invoice has one. Sending copies the resolved Local Support Fees, Salary and both Rollout Advance lines onto the `AgentInvoice` row's own `snapshot*` columns; every read from `sent` onward serves those columns, never a fresh computation, so neither a Fee logged afterwards nor a standing-amount change (even one made mid-cycle, bypassing the normal next-month-effective rule) can move a total the Manager is reviewing or has already approved. See `docs/adr/0003-agent-invoice-snapshot-on-send-and-per-invoice-override.md`.

*Manager override, at approval time*: while an invoice is `sent` (not `draft`, not once `approved`/`paid`), a Manager may overwrite the frozen Salary line and/or the frozen Rollout Advance **new-advance** line directly on that one invoice's snapshot, recalculating its total. This never writes to `AgentStandingAmount` — the standing rate, and therefore every other invoice past or future, is completely unaffected; it is a one-invoice edit, not a rate change. Only the *new advance* line is overridable, never the *repayment* line: repayment settles an amount already fixed and communicated on the Agent's prior invoice, so there is nothing left for the Manager to decide about it this month, whereas the new advance is the forward-looking figure for next month's cash-flow support the Manager is actively approving right now.
_Avoid_: Payroll, payout.

**Review Queue**:
The invoices currently waiting on a Company Manager action, across every Contract and Agent and any billing month: each Client Invoice in `sent` (awaiting approval) and each Agent Invoice in `sent` (awaiting review/approval) or `approved` (awaiting mark paid). Ordered longest-waiting first — by when the invoice entered the status it is waiting in. An invoice leaves the Review Queue when it reaches its final status (Client Invoice `approved`, Agent Invoice `paid`).
_Avoid_: Pending approvals (marking an Agent Invoice paid is not an approval), inbox.

**Rollout Advance**:
A standing per-Agent cash-flow advance set by the Company Manager to cover an Agent's fee fronting (see Standing amount). Appears on the Agent Invoice as two lines — repayment of the previous month's advance (negative, resolved for the month before the invoice's own) and the new advance for this month (positive, resolved for the invoice's own month) — netting to zero except in the one invoice cycle right after a standing-amount change takes effect (the month whose resolved amount differs from the month before it). Before a Manager has ever set one for a given Agent, both lines resolve to 0 rather than being omitted, so an Agent's very first invoice (or one before any advance was ever set) still shows both lines, just at zero.
_Avoid_: Float, retainer.
