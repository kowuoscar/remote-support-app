# Journeys

<!-- sdlc:template journeys 1 -->

One business journey per level-3 heading, its state after an em dash:
`exists` (plays end to end today), `partial` (starts but does not finish, or
finishes with a manual step), `wanted` (not started). An epic brings one or
more journeys to `exists`, and closes only once the journey plays end to end
on `main` — proof, not a status flip.

The states below were reconstructed from the code at init (2026-09-21) and
confirmed with the human, each against the test that proves it.

### Sign in — exists

Any of the three roles signs in with an email and a password and lands on
their own console. An unauthenticated request to a protected route redirects
to the login page. Proof: `frontend/tests/e2e/login.spec.ts`.

The defect this journey carried is gone: the lookup was by username across
**all** tenants though usernames were unique only **within** one, so a username
held in two Tenants was refused 401, indistinguishable from a wrong password.
`globally-unique-usernames` made usernames unique across the whole deployment
(V55, `uq_users_username_global`), so the lookup resolves to at most one user.

### Set up the operating picture — exists

A Manager creates Clients, Agents and the Contracts that bind one Client to
one Agent, and an Agent gets a login at the moment they are created. Proof:
`frontend/tests/e2e/manager-entity-setup.spec.ts`,
`frontend/tests/e2e/create-agent-with-login.spec.ts`.

### Put resources in the field — exists

A Manager builds a Contract's Fleet of Smartphones and SIM Cards, and an
Agent keeps it accurate — changing a status, installing a SIM Card into a
Smartphone, setting a serial. A Tester sees their own Client's Fleet. Proof:
`frontend/tests/e2e/fleet-management.spec.ts`.

### Maintain the Carrier catalog — exists

An Agent maintains their country's Carriers with their Topup Options and
Postpaid Plans — adding, renaming, archiving, re-pricing — and a Manager
reads any country's catalog. Proof:
`frontend/tests/e2e/carrier-catalog.spec.ts`.

### Ask for support and get it done — exists

A Tester submits any of the eight Request types against their Contract, and
everyone at that Client sees it. The Agent progresses it from Submitted
through In Progress to Completed, cancels it with a reason, or logs one
proactively on a Tester's behalf. Proof:
`frontend/tests/e2e/tester-request-submission.spec.ts`,
`frontend/tests/e2e/agent-request-fulfillment.spec.ts`.

### Approve what costs money — exists

A Request type that commits a resource starts in Pending Approval, and only
a Manager moves it on — approving or rejecting it, with the Return type
carrying its own Disposition decision. Proof:
`ManagerApprovesRequestsApiTest`.

### Bill the work that was done — exists

An Agent logs Fees against a Contract, always tracing back to a Request,
including a Topup priced from a Carrier's Topup Option. Proof:
`frontend/tests/e2e/fee-logging-and-provisioning.spec.ts`.

### Take equipment back and reuse it — exists

A Return leaves the Fleet with a Manager-decided Disposition; a unit kept in
the Agent's Stock can later fulfil a Provision or a Replace, instead of a new
unit being bought. Proof: `frontend/tests/e2e/agent-stock.spec.ts`,
`StockFulfilmentApiTest`.

### Bill the Client for the month — exists

An Agent opens a Contract's Client Invoice for the month, which drafts itself
from the postpaid base amount and that month's Fees, attaches the carrier
invoice files, and sends it. Its numbers freeze at send (ADR 0001). The
Client's Testers see and download it; the Manager approves it. Proof:
`frontend/tests/e2e/client-invoice-submission-and-visibility.spec.ts`.

### Get the Agent paid for the month — exists

An Agent opens their own monthly invoice — Local Support Fees across all
their Contracts, their standing salary, and the two Rollout Advance lines —
and sends it. The Manager overrides a line if needed, approves it, and marks
it paid. Proof:
`frontend/tests/e2e/agent-standing-amounts-and-invoice-generation.spec.ts`.

### Work through what is waiting — exists

A Manager opens one Review Queue of every invoice awaiting them, of either
type and any month, longest-waiting first, and acts on each from there.
Proof: `frontend/tests/e2e/manager-invoice-review-queue.spec.ts`.

### See where things stand on arrival — partial

Each role's home page should answer "what needs me today?" from their real
data. Today it largely does not.

The Manager's dashboard shows a real Review Queue and a real pending-request
count beside **fabricated** money and counts (`billedThisMonthUSD: 41280`,
`payoutThisMonthUSD: 22940` in `frontend/lib/demo/manager.ts`). The Agent's
and the Client's dashboards are **entirely** fabricated, down to the name in
the page header — a real Agent sees another person's name, salary and
Rollout Advance.

To reach `exists`: the Agent and Client dashboards need only to be wired to
endpoints that already exist; the Manager's two money aggregates
(billed this month, payout this month) need new backend work. Brought to
`exists` by the `real-dashboards` epic.

### Send an invoice back for correction — wanted

A Manager who finds something wrong with a sent invoice can return it to the
Agent with a reason, instead of the only options being to approve it or to
leave it sitting. Today both invoice lifecycles move strictly forward.

### Look back at finished invoices — wanted

A Manager can browse invoices that have left the Review Queue — approved
Client Invoices, approved and paid Agent Invoices — filtered by month,
Contract and Agent. Today a finished invoice is reachable only by its id.

### Keep a login working over time — wanted

A user changes their own password; a Manager resets the password of an Agent
or a Tester who is locked out; a login is deactivated when a person leaves,
without destroying the Agent record and the invoice history hanging off it.
Today a password can never be changed by anyone.

### Operate a second tenant safely — exists

A second customer company can be operated in the same deployment without a
user of one signing in to the other. Tenants continue to be created directly
in the database; this journey is about correctness, not about a SuperAdmin
surface.

Brought to `exists` by the `tenant-scoped-sign-in` epic. No username can be
held in two Tenants — creation is refused on both the Agent and the Tester
path, and V55's `uq_users_username_global` enforces it in the database — so
the sign-in lookup can no longer match more than one row. Proof:
`GlobalUsernameIndexMigrationTest`, `CrossTenantUsernameAgentCreationApiTest`,
`AgentLoginApiTest`, `TesterUsernameConflictApiTest`, with
`SecondTenantSignInApiTest` proving legitimate second-Tenant operation still
works. Every remaining single-tenant assumption is named and dispositioned in
`docs/features/globally-unique-usernames/delivery.md`.
