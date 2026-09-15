# Remote Support Platform

A platform for a business that hires local support people in different countries to provide in-person mobile-testing support to a client's testers.

## Language

**Agent** (Local Support Agent):
A person hired remotely in a specific country who supplies smartphones and SIM cards to a Client's Testers and executes support tasks (topups, reboots, SIM swaps) on request. Submits a monthly invoice claiming salary and reimbursable expenses. An Agent record is created standalone by the Manager; it may separately be linked to one login (a User with role `AGENT`), the same relationship a Tester has to its Client but the other way around — the Agent exists first, the login link is optional and added later. `Agent.salaryAmount` is only ever the value supplied at creation — the *current* standing salary, and its full history, live in `AgentStandingAmount` (see below); `agent-standing-amounts-and-invoice-generation` writes both, in the same request, so the two never disagree at creation time.
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
A support action a Tester submits, or the Agent logs on the Tester's behalf: reboot, topup, SIM swap, smartphone/SIM provisioning, or repair. Tracked to completion (Submitted → In Progress → Completed, or Cancelled with a reason); carries no monetary amount by itself.
_Avoid_: Ticket, task.

**Proactive** (of a Request):
A Request the Agent logs directly, on a Tester's behalf, rather than one the Tester submitted themselves — starting at Submitted or immediately at Completed, the Agent's call. Distinct from "Tester-authored"; the two are queryable independently of who it's *for* (every Request, proactive or not, still names the Tester it's raised on behalf of). A proactively-logged Fee (fee-logging-and-provisioning ticket) is the same idea one level up: no pre-existing Request, so one is auto-created to keep the Fee traceable.
_Avoid_: Ad-hoc, walk-in.

**Fee**:
A billable line item an Agent logs against a Contract, always tracing back to the Request that caused it — enforced as a non-nullable foreign key, not just a service-layer rule, so there is no code path that creates one without a Request. A reboot or a like-for-like SIM swap never produces a Fee; a topup, a provisioning, or a repair does. Its `feeType` mirrors that same four-way subset of Request types (`FeeType`, deliberately narrower than the six-value `RequestType`, so a Reboot/SIM-Swap Fee can't even be constructed). A SIM swap that really did require provisioning a new physical SIM is not modeled as a Fee attached to the SIM Swap Request itself — it is logged as its own Provision SIM Fee (proactive if no Provision SIM Request already exists), alongside the original swap. Currency is always copied from its Contract at creation (the Agent never picks a different one). Carries an explicit `billingMonth` (the first day of the month, set from `createdAt` at creation time) rather than one derived from `createdAt` at query time — the stored column keeps "this Contract's Fees for month X" a plain equality filter for `client-invoice-generation`'s monthly aggregation, and is the column a future backdating feature (an Agent attributing a Fee logged a few days into a month to the prior month's invoice) would use, without a schema change.
_Avoid_: Charge, cost.

**Client Invoice**:
The monthly statement an Agent prepares for one Contract: its postpaid base amount plus that month's Fees, with carrier invoices attached as supporting files. Lifecycle: draft → sent → approved (client-invoice-submission-and-visibility ticket completes it — the Agent sends, only a Manager approves, and a sent/approved invoice is locked against further edits). One per Contract per calendar month, identified by a first-of-month `billingMonth`. Get-or-create on first view: an Agent opening "this Contract's Client Invoice for the current month" gets one created in `draft` automatically if none exists yet — there is no separate create step, the same "no separate create step" shape a proactive Fee's auto-created linking Request already established.

*Live while draft, frozen from sent onward*: while `draft`, the base amount and Fee lines are computed live (base amount from every currently-Active Postpaid SIM in the Fleet, Fee lines from that Contract's Fees for that `billingMonth`) on every read, per spec.md's "at the time of viewing" (client-invoice-generation ticket) — the Agent is still assembling it. The moment the Agent sends it, both are **snapshotted**: the base amount is copied onto the Client Invoice row, and the Fee-line membership is pinned into a `ClientInvoiceFeeSnapshot` join table (which Fees counted, not a copy of their fields — a Fee is never updated or deleted once logged, so pinning membership is enough). From `sent` onward every read serves that snapshot, never a fresh computation, so a Fee an Agent logs against the same Contract/month *after* sending can never silently change a total the Manager already approved or the Client was already shown. See `docs/adr/0001-client-invoice-snapshot-on-send.md` for the full reasoning. Rendered as PDF on demand from that snapshot, never stored as a static file, and only once `sent`/`approved` — a draft has nothing final to render yet.
_Avoid_: Bill.

**Carrier Invoice File**:
One file (typically a carrier-issued PDF) an Agent attaches to a Client Invoice draft as supporting documentation for its postpaid charges. Stored on the backend's local filesystem (client-invoice-generation ticket) — an opaque path only the backend ever resolves — with filename/content-type/size recorded as row metadata; not modeled as its own billing entity, matching spec.md's "opaque supporting documents, not modeled entities". A Client Invoice can carry several.
_Avoid_: Attachment (too generic — every Client Invoice file is specifically a carrier's own invoice).

**Agent Invoice**:
The monthly invoice an Agent sends to the Company Manager: full reimbursement of every Fee (postpaid base + additional) across all of the Agent's Contracts that month, plus salary, plus the Rollout Advance adjustment. One per Agent per calendar month, get-or-created on first access — the same shape Client Invoice established. Lifecycle: draft → sent → approved → paid (`agent-standing-amounts-and-invoice-generation` writes only `draft`; the next ticket adds the rest). While `draft` (the only status this ticket ever writes), every line is computed live on every read — there is no snapshot mechanism yet.

*Local Support Fees* is computed straight from each Contract's Fleet/Fees for the month (the same math Client Invoice's base amount + Fee total uses, extracted into a shared `ContractAmountService` both controllers call), **never** by reading a Client Invoice row or its frozen snapshot, and *not gated on that Contract's Client Invoice status at all* — a Contract whose Client Invoice is still `draft`, `sent`, or `approved` all count identically, and a Fee logged against a Contract *after* its Client Invoice was already sent (and so excluded from that Client Invoice's own frozen total) still counts here. This is deliberate: the Agent already fronted that money the moment it happened, regardless of how far the separate Client-facing paperwork has progressed through its own review — reading the two independently is simpler than reconciling different "did this count" rules for one number, and keeps Client Invoice amounts (read-only from here) entirely unaffected by anything on this side.

*Salary* and the two Rollout Advance lines resolve from the standing amount in effect for the invoice's own `billingMonth` (salary, new advance) and the month before it (advance repayment) — see Standing amount above.
_Avoid_: Payroll, payout.

**Rollout Advance**:
A standing per-Agent cash-flow advance set by the Company Manager to cover an Agent's fee fronting (see Standing amount). Appears on the Agent Invoice as two lines — repayment of the previous month's advance (negative, resolved for the month before the invoice's own) and the new advance for this month (positive, resolved for the invoice's own month) — netting to zero except in the one invoice cycle right after a standing-amount change takes effect (the month whose resolved amount differs from the month before it). Before a Manager has ever set one for a given Agent, both lines resolve to 0 rather than being omitted, so an Agent's very first invoice (or one before any advance was ever set) still shows both lines, just at zero.
_Avoid_: Float, retainer.
