# Remote Support Platform

A platform for a business that hires local support people in different countries to provide in-person mobile-testing support to a client's testers.

## Language

**Agent** (Local Support Agent):
A person hired remotely in a specific country who supplies smartphones and SIM cards to a Client's Testers and executes support tasks (topups, reboots, SIM swaps) on request. Submits a monthly invoice claiming salary and reimbursable expenses. An Agent record is created standalone by the Manager; it may separately be linked to one login (a User with role `AGENT`), the same relationship a Tester has to its Client but the other way around — the Agent exists first, the login link is optional and added later.
_Avoid_: Rep, field agent, support worker.

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
The monthly statement an Agent prepares for one Contract: its postpaid base amount plus that month's Fees, with carrier invoices attached as supporting files. Lifecycle: draft → sent → approved. Visible read-only to the Client once sent.
_Avoid_: Bill.

**Agent Invoice**:
The monthly invoice an Agent sends to the Company Manager: full reimbursement of every Fee (postpaid base + additional) across all of the Agent's Contracts that month, plus salary, plus the Rollout Advance adjustment. Lifecycle: draft → sent → approved → paid.
_Avoid_: Payroll, payout.

**Rollout Advance**:
A standing per-Agent cash-flow advance set by the Company Manager to cover an Agent's fee fronting. Appears on the Agent Invoice as two lines — repayment of the previous month's advance and the new advance for next month — netting to zero unless the Manager has just approved a change.
_Avoid_: Float, retainer.
