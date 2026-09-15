# Remote Support Platform

A platform for a business that hires local support people in different countries to provide in-person mobile-testing support to a client's testers.

## Language

**Agent** (Local Support Agent):
A person hired remotely in a specific country who supplies smartphones and SIM cards to a Client's Testers and executes support tasks (topups, reboots, SIM swaps) on request. Submits a monthly invoice claiming salary and reimbursable expenses.
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
A support action a Tester submits, or the Agent logs on the Tester's behalf: reboot, topup, SIM swap, smartphone/SIM provisioning, or repair. Tracked to completion; carries no monetary amount by itself.
_Avoid_: Ticket, task.

**Fee**:
A billable line item an Agent logs against a Contract, always tracing back to the Request that caused it. A reboot or a like-for-like SIM swap never produces a Fee; a topup, a provisioning, or a repair does.
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
