---
feature: correct-a-fleet-unit
epic: fleet-corrections
status: approved
date: 2026-10-14
---

<!-- sdlc:template spec 1 -->

# Correct a Smartphone or SIM Card in the Fleet

## Problem

Once a Smartphone or SIM Card is in a Contract's Fleet, almost nothing about
it can be changed. The Manager and the Contract's own Agent can set a
Smartphone's serial and a SIM Card's "Installed in". Only the Agent's Fleet
page offers a status change. Nothing else can be changed by anyone. Testing a
fresh, production-like stack on 2026-10-14, the human found that a mistyped
SIM number has no fix in the product. Neither has a SIM put on the wrong
Postpaid plan, a Smartphone entered with the wrong model, or a Client's own
phone left on the "Company" default owner. Today the only way out is to
retire the unit and add it again. That loses its history and its Requests,
and does not even work for a Smartphone (a Manager cannot retire one from
their page).

The human asked for this (2026-10-14, verbatim): "Manager should be able to
edit SIM cards, mistypes of sim number or postpaid plan, same for smartphone
mistype and owner. Tester should be able to do the same."

The Tester part changes a role. `PRODUCT.md` says a Client Tester "views their
Client's Fleet", and asks for every change through a Request. The backend says
the same: `FleetAccessGuard` refuses every Fleet write by a Tester. Its
Javadoc reads "a Tester never may".

Each of the four corrections reaches further than its own row:

- **A Postpaid plan sets money.** A Postpaid SIM's monthly fee is copied from
  its Plan's price when the SIM is added, and never re-read afterwards. The
  fee feeds the Contract's base amount on its Client Invoice, and through the
  payable amount it feeds the Agent's Local Support Fees.
- **The SIM number and the Smartphone model are read live** wherever the unit
  appears: Fleet tables, Requests, Agent Stock, returned units, and a Client
  Invoice's per-SIM lines, sent or approved ones included.
- **The owner decides a Return.** A Return of a Company-owned Smartphone needs
  the Manager's approval and Disposition. A Client-owned one is posted back to
  the Client, with no approval.
- **Nothing stops two SIM Cards from having the same number.** No database
  constraint and no rule refuses it, at creation or anywhere else.

## Journeys

Advances `docs/roadmap/fleet-corrections.md`. This is the epic's only feature.

- **Put resources in the field** (`exists`, extended). Today the journey reads
  "A Manager builds a Contract's Fleet … an Agent keeps it accurate … A Tester
  sees their own Client's Fleet." This feature adds: "A Manager or a Tester
  corrects a mistyped SIM number, a SIM's Postpaid plan, a Smartphone's model
  or its owner." The proof stays `frontend/tests/e2e/fleet-management.spec.ts`,
  which gains the correction tests. The orchestrator rewrites the journey's
  paragraph at delivery.

## Goals / Non-goals

Goals:

- A Manager, on any Contract of the Tenant, can correct four things: a SIM
  Card's **number**, a Postpaid SIM's **Postpaid plan**, a Smartphone's
  **model**, and a Smartphone's **owner**.
- A Tester can make the same four corrections on any Contract of their own
  Client. Every Tester can, not only the Primary Contact.
- Correcting the plan re-copies the new plan's price into the SIM's monthly
  fee. Every invoice still computed live then reflects it. A stored invoice
  line never moves.
- Each correction is refused with a clear message when it would break a rule:
  a blank or over-long value, a plan the SIM's Carrier does not offer, an
  archived plan, a plan on a Prepaid SIM, or an owner change while a Return of
  that Smartphone is open.
- Each correction is audit-logged with who made it, the old value and the new
  one, the way a serial change is.
- `PRODUCT.md`'s Tester line, the glossary and the journey say what a Tester
  can now do.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No Carrier correction and no Postpaid ↔ Prepaid flip** (decided by the human, 2026-10-14).
- **No correction by an Agent** (decided by the human, 2026-10-14). The Agent's Fleet page and
  its controls stay as they are.
- **No status control on the Manager's Contract page** (decided by the human, 2026-10-14). The
  backend already allows a Manager to change status. This feature does not add
  the control.
- **No new Tester power beyond the four corrections.** A Tester still cannot
  change a status, a serial or "Installed in", and cannot add a unit.
- **No uniqueness rule on SIM numbers.** A corrected number is checked the way
  creation checks a number: non-blank and within length, nothing more
  (`## Decisions taken`). Adding a rule would need a decision about the data
  already stored.
- **No rewrite of a stored Client Invoice line or a frozen Agent Invoice
  figure.** A correction never reaches a sent, approved or sent-back invoice's
  amounts. To fix one, use what already exists: the Manager's send-back, then
  the Agent's line edit. Once `invoice-adjustment` lands, an adjustment does it.
- **No rewrite of recorded history.** A Request's requested model, and the
  model a Replace Smartphone Request recorded for the unit it replaced, keep
  what they recorded.
- **No correction of a unit in Agent Stock.** Only a unit on a Contract's
  Fleet is reached.
- **No serial correction for a Tester**, and no change to how the serial works
  for anyone.
- **No model catalog.** The model stays free text.
- **No notification** to the Agent, the Manager or the Client when a unit is
  corrected. The audit log is the only record.
- **No change history shown in the product.**
- **No bulk correction.** One field of one unit at a time.

## User stories

1. As a Company Manager, I want to correct a SIM Card's number from the Contract's Fleet, so that a mistyped number is fixed without retiring the SIM and adding it again.
2. As a Company Manager, I want to correct a Postpaid SIM's Postpaid plan, choosing from the active plans of the SIM's own Carrier and seeing the new monthly fee before I save, so that the SIM is billed on the plan it is really on.
3. As a Company Manager, I want to correct a Smartphone's model, so that a mistyped model is fixed everywhere the phone appears.
4. As a Company Manager, I want to correct a Smartphone's owner between Client and Company, so that a Client's own phone is not treated as the company's, and the reverse.
5. As a Client Tester, I want to correct a SIM Card's number on my Client's Fleet, so that I can fix a mistake I spot without raising a Request.
6. As a Client Tester, I want to correct a Postpaid SIM's Postpaid plan on my Client's Fleet, seeing the new monthly fee before I save, so that the SIM is billed on the plan we actually have.
7. As a Client Tester, I want to correct a Smartphone's model on my Client's Fleet, so that our phones are recorded as they are.
8. As a Client Tester, I want to correct a Smartphone's owner on my Client's Fleet, so that our own phones are recorded as ours.
9. As a Client Tester, I want the correction available on every Contract of my Client, whether or not I am the Primary Contact, so that any of us can fix what we see.
10. As the company, I want a Tester refused when correcting a unit of another Client's Contract, and an Agent refused when correcting any unit, so that each role acts only within what it was given.
11. As the company, I want a Tester still unable to change a unit's status, serial or "Installed in", so that the Tester's new power is exactly the four corrections asked for.
12. As a Manager or a Tester, I want a blank number or model, or one longer than its limit, refused at the field with what I typed kept, so that I can fix it in place.
13. As a Manager or a Tester, I want surrounding spaces dropped from a corrected number or model, so that a stray space does not create a value that looks the same but differs.
14. As a Manager or a Tester, I want no plan correction offered on a Prepaid SIM, and one refused if attempted, so that a Prepaid SIM never gains a monthly fee by accident.
15. As a Manager or a Tester, I want only the active plans of the SIM's own Carrier offered, and none at all when that Carrier is archived or the SIM names no Carrier, so that a SIM can never be put on a plan its Carrier does not sell.
16. As a Manager or a Tester correcting a Postpaid SIM from before the Carrier catalog, which names a Carrier but no plan, I want to give it a plan, with its monthly fee becoming that plan's price, so that an old SIM can be brought onto the catalog.
17. As a Company Manager, I want the SIM's monthly fee in the Fleet table to show the corrected plan's price as soon as I save, so that I see what the SIM now costs.
18. As an Agent, I want the current month's Client Invoice draft, and any earlier draft never sent, to bill the corrected monthly fee the next time it is read, so that an invoice still being prepared bills the right plan.
19. As an Agent, I want my draft Agent Invoice's Local Support Fees to follow a corrected fee on a draft Client Invoice, so that my pay matches what the Client is billed.
20. As a Company Manager, I want a plan correction never to change the amounts of a Client Invoice already sent, approved or sent back, nor an Agent Invoice already sent, approved or paid, so that money already shown, reviewed or paid never moves silently.
21. As anyone reading a unit anywhere (Fleet, Requests, Agent Stock, returned units, a Client Invoice's SIM lines), I want the corrected number or model shown, so that the record reads the same everywhere.
22. As a Company Manager, I want the model a Request asked for, and the model a Replace Request recorded for the phone it replaced, kept as recorded, so that history says what happened then.
23. As a Company Manager, I want an owner correction refused while an open Return names that Smartphone, with a message saying why, so that a Return already in progress never changes path halfway.
24. As a Company Manager, I want a Smartphone's corrected owner to decide every Return raised after it, so that a Return of a Company-owned phone comes to me for approval and a Client-owned one goes back to the Client.
25. As a Company Manager, I want every correction audit-logged with the Contract, the unit, the old value, the new value, who made it and their role, so that I can trace any change after the fact.
26. As a Manager or a Tester, I want saving the value already held to change nothing and log nothing, so that an idle Save leaves no trace.
27. As a Manager or a Tester, I want Cancel to leave the value as it was, and a failed save to show "Couldn't update. Try again." with the row unchanged, so that nothing changes unless I mean it to and it worked.
28. As a Manager or a Tester working by keyboard or on a phone, I want each correction reachable and operable by keyboard, with visible focus, inside the horizontally scrolling table, so that it works without a mouse and at any width.
29. As the team, I want `PRODUCT.md`, the glossary and the journey to say that a Tester corrects these four things, so that the next agent reading them does not undo this feature.

## Solution

### What can be corrected, by whom

| Unit | Field | Manager | Tester (own Client) | Agent |
|---|---|---|---|---|
| SIM Card | number | yes | yes | no |
| SIM Card (Postpaid) | Postpaid plan | yes | yes | no |
| Smartphone | model | yes | yes | no |
| Smartphone | owner | yes | yes | no |
| either | status, serial, Installed in | unchanged (Manager, own Agent) | no | unchanged |

Every Tester of the Client can correct. CONTEXT.md settles the Primary
Contact as "a visible designation, not an extra permission".

A correction reaches a unit only on a Contract's Fleet, in any status. It goes
through the Contract-scoped routes that already find a unit by its id and its
Contract within the caller's Tenant. A unit in Agent Stock belongs to no
Contract, so it cannot be reached.

### Backend: authorization

`FleetAccessGuard` gains one method, `requireCanCorrect(contract, principal)`:

```
MANAGER → allowed, any Contract of the Tenant
TESTER  → allowed only on a Contract of their own Client (the existing
          requireOwnsContractAsTester check)
AGENT   → refused (403)
other   → refused (403)
```

`requireCanChangeStatus` and its Javadoc ("a Tester never may") are left as
they are: status, serial and "Installed in" stay closed to a Tester.
`SecurityConfig` needs no change. `/api/contracts/*/smartphones/**` and
`/api/contracts/*/sim-cards/**` are already open to any authenticated caller,
and object rules are left to the guard. The non-`POST` matcher already sits
ahead of the broad Manager-only `/api/contracts/**` rule. A test makes sure a
Tester's `PATCH` reaches the guard rather than being refused by that broad
rule.

### Backend: the four endpoints

One `PATCH` per field, under the existing routes, in the shape of
`PATCH …/{smartphoneId}/serial`. Each finds the Contract within the caller's
Tenant, calls `requireCanCorrect`, finds the unit with
`findByIdAndContractId`, applies the change in one service-layer transaction,
audit-logs it, and returns the unit's existing response DTO.

```
PATCH /api/contracts/{contractId}/sim-cards/{simCardId}/number         { "number": "+1 555 0100" }
PATCH /api/contracts/{contractId}/sim-cards/{simCardId}/postpaid-plan  { "postpaidPlanId": "<uuid>" }
PATCH /api/contracts/{contractId}/smartphones/{smartphoneId}/model     { "model": "Pixel 8" }
PATCH /api/contracts/{contractId}/smartphones/{smartphoneId}/owner     { "owner": "CLIENT" | "COMPANY" }

200 — the unit, as SimCardResponse / SmartphoneResponse
400 — invalid value (below), body { "code", "message" }
403 — the guard refused (an Agent; a Tester outside their Client)
404 — no such Contract in the Tenant, or no such unit on that Contract
409 — owner change while an open Return names the Smartphone,
      { "code": "SMARTPHONE_ON_OPEN_RETURN", … }
401 — no token
```

The rules for each value:

- **Number.** Trimmed. Refused when blank (`NUMBER_BLANK`) or longer than 64
  characters after trimming (`NUMBER_TOO_LONG`); 64 is the column's length.
  Not checked against other SIM Cards' numbers (`## Decisions taken`).
- **Model.** Trimmed. Refused when blank (`MODEL_BLANK`) or longer than 255
  characters (`MODEL_TOO_LONG`); 255 is the column's length.
- **Postpaid plan.** Refused on a Prepaid SIM (`PREPAID_HAS_NO_PLAN`). Refused
  when the SIM names no Carrier, or its Carrier is archived
  (`CARRIER_NOT_USABLE`). Refused when the plan is not one of the SIM's own
  Carrier's (`PLAN_NOT_ON_CARRIER`) or is archived (`PLAN_ARCHIVED`). On
  success, the SIM's `postpaidPlan` becomes the plan and its
  `monthlyFeeAmount` becomes the plan's current price. The plan rule is
  `SimCardFactory.requireUsablePlan`, reused rather than restated: it already
  enforces "an active plan of the SIM's own Carrier". The Carrier check reuses
  the archived half of `requireUsableCarrier`, not its "Country of the
  Contract" half: the SIM's Carrier is not being chosen again.
- **Owner.** `CLIENT` or `COMPANY`. Refused with `409
  SMARTPHONE_ON_OPEN_RETURN` while any Return Request whose returned units
  include this Smartphone is open, that is Pending Approval, Submitted or In
  Progress. Once that Return is Completed, Cancelled or Rejected, the owner can
  be corrected again. A Return is the only Request whose path the owner
  decides. The Provision and Replace rule that a phone reached through them is
  Company-owned is about how a unit arrives. A correction may change that
  afterwards (decided by the human, 2026-10-14).
- **The same value as now** succeeds with `200`, writes nothing and logs
  nothing.

Each controller turns its coded `400` and `409` into a body with an
`@ExceptionHandler` of its own (Backend rule 7). Bean Validation on the
request DTOs checks presence. The trim-then-length rule is one small value
rule per field, kept with the service.

### Backend: audit

`AuditLog` gains one event per field, beside `smartphoneSerialChanged`. Each
logs the unit's id, the Contract's id, the old value, the new value, the
actor's user id, their role and the Tenant:

- `simCardNumberCorrected`
- `simCardPostpaidPlanCorrected`, which also logs the old and new plan ids and
  the old and new monthly fee
- `smartphoneModelCorrected`
- `smartphoneOwnerCorrected`

The role is logged because a Tester's write on the Fleet is new.

### What a plan correction does to money

This applies the rules already in place (ADR 0003, ADR 0004, and the human's
answer of 2026-10-14 in `docs/inbox/month-close-paid-pay.md`). It adds no rule
of its own:

| Where | Effect of correcting a Postpaid SIM's plan |
|---|---|
| The SIM's monthly fee | Re-copied from the new plan's price, now. Later plan re-pricing still leaves it alone, as before |
| A Client Invoice draft with no stored line for that SIM (the current month, or an earlier one never sent) | Its `POSTPAID_SIM` line for the SIM, and so its base amount and total, show the new fee on the next read |
| A Client Invoice draft that stores a line for that SIM (an Agent's edited line, or a sent-back invoice, which keeps every line it stored) | Unchanged. The stored line wins, as ADR 0004 says. The Agent edits the line if it should follow |
| A sent or approved Client Invoice | Unchanged. It serves its stored lines |
| A draft Agent Invoice | Its Local Support Fees follow the payable amount, so they move with a draft Client Invoice that moved |
| A sent, approved or paid Agent Invoice | Unchanged. Its figures are frozen. A sent one moves only through a Client Invoice line edit, as today |

The number and the model are not money. They show live wherever the unit is
read, on a sent or approved invoice's SIM lines and its on-demand PDF too
(`## Decisions taken`). Only the amounts are frozen.

The SIM's past is not changed. A Postpaid SIM has one monthly fee, not one per
month. So a plan correction moves every invoice still computed live, including
an earlier never-sent draft that is Late. Under `month-closes-on-the-fifth`
(draft), whatever is open stays open and computes the same way. A closed month
is corrected by an adjustment, as that spec says.

### Frontend: the shared Fleet tables

The shared tables today use `contractId` for two things: whether the serial and
"Installed in" can be edited, and the route to call. A Tester's page will now
need `contractId` for corrections while staying read-only for those two. So
the tables split the axis:

- `SmartphoneFleetTable` gains `canCorrect`. Its `contractId` stays required
  for any control. The serial control is shown only when `canEditSerial` is
  given, which the Manager and the Agent pass. Model and owner become inline
  correction controls when `canCorrect` is given.
- `SimCardFleetTable` gains `canCorrect` and `carriers` (the Contract's
  catalog). Number and plan become inline correction controls when
  `canCorrect` is given. The plan control appears only on a Postpaid SIM whose
  Carrier is in `carriers` and active. Otherwise the plan cell reads as it
  does today. "Installed in" stays editable only where it is today.

What each page passes:

| Page | Passes |
|---|---|
| Manager, Contract Fleet | `contractId`, `canEditSerial`, `canCorrect`, `carriers` (already loaded for the create dialog); no `changeStatus` |
| Agent, Fleet | as today; no `canCorrect` |
| Tester, Fleet | `contractId` (the switcher's current Contract), `canCorrect`, `carriers` read from `GET /api/contracts/{id}/carriers` for each of the Client's Contracts on the server |

Four new inline controls follow `SmartphoneSerialControl`'s edit-toggle
shape: the value, a row "Edit" button, then a field with Save and Cancel.

- `SimCardNumberControl`: a text `Input`, `aria-label="SIM number"`,
  `inputMode="tel"`.
- `SmartphoneModelControl`: a text `Input`, `aria-label="Smartphone model"`.
- `SmartphoneOwnerControl`: a two-option select (Client, Company) built from
  the existing `PickerSelect`, `aria-label="Owner"`.
- `SimCardPlanControl`: the existing `PostpaidPlanPicker` at its small size.
  It shows the chosen plan's monthly fee before Save, on every page, the
  Tester's included (`## Decisions taken`).

Each trims before sending and refuses a blank value in the browser, sending
no request ("Enter a number." / "Enter a model."). It maps the backend's codes
to messages at the field and keeps what was typed:

| Code | Message |
|---|---|
| `NUMBER_TOO_LONG` | "A SIM number can be at most 64 characters." |
| `MODEL_TOO_LONG` | "A model can be at most 255 characters." |
| `PLAN_ARCHIVED`, `PLAN_NOT_ON_CARRIER`, `CARRIER_NOT_USABLE` | "That plan can't be chosen any more. Pick another." |
| `SMARTPHONE_ON_OPEN_RETURN` | "This smartphone is on an open Return. Change its owner once the Return is done." |
| anything else | "Couldn't update. Try again." |

On success the control leaves editing and calls `router.refresh()`, as the
serial control does.

### Frontend: route handlers

Four new route handlers, one per endpoint, all `PATCH`, under
`app/api/contracts/[contractId]/smartphones/[smartphoneId]/{model,owner}` and
`…/sim-cards/[simCardId]/{number,postpaid-plan}`. Each is built with
`forwardJson` (Frontend rule 13). The four hand-written fleet pass-throughs
next to them (`smartphones/…/{serial,status}`,
`sim-cards/…/{status,installed-in}`) switch to `forwardJson` first, as a
prefactor. The rule says hand-written pass-throughs switch when a feature next
touches them, and this feature reworks the controls that call them.

### Product and glossary amendments

The spec states them here. They are applied in the ticket that first lets a
Tester correct (`## Execution order`). `PRODUCT.md` is edited in that ticket.
The glossary changes are declared and applied by the merger.

- `PRODUCT.md`, Users, Client Tester, now reads: "an individual login
  belonging to a Client company; submits support Requests, views their
  Client's Fleet, Contracts and Invoices, and corrects a mistyped SIM number,
  a SIM's Postpaid plan, a Smartphone's model or its owner."
- `PRODUCT.md`, Users, Company Manager: "configures Clients, Agents, Contracts
  and Fleets" already covers it. Unchanged.
- The glossary terms (`terms` in the result): **Correction** (added),
  **Tester** (narrowed), **Owner** (narrowed), **Carrier** (narrowed, the
  monthly fee clause).

## Design direction

Conforms to `DESIGN.md`; no `DESIGN.md` change. The surfaces are the
Manager's Contract Fleet and the Tester's Fleet, both **Operate**.

Each correction is an inline row action, like the serial and "Installed in"
controls already in the same tables. There is no dialog: craft-floor's "no
modal for a task that needs neither interruption nor protected focus" is how
both siblings are built. The "Edit" trigger is the **Row** button (soft-indigo
tint, 8px radius, 28px `sm` height, DESIGN.md "row actions"). The field, Save
and Cancel sit in the cell, at the `h-7` height of the serial control. The
plan field shows the monthly fee under the select through `Money`, with
tabular figures. Errors use the `danger` ink under the field. The tables keep
`overflow-x-auto` scrolling at narrow widths and do not reflow into cards.

New and changed lines use `text-label-sm` / `text-label`, never `text-[11px]`,
`text-[12px]` or `text-[13px]` (Frontend rule 8). The two sibling controls'
untouched lines stay as known debt. The `design` slot does not need to be
engaged.

## Constraints

- Inherits every constraint in the `remote-support-mvp` spec: the stack,
  Tenant scoping, and an explicit currency on every amount.
- Every correction acts only on a unit of a Contract in the caller's Tenant,
  found by unit id and Contract id together. A Tester acts only on a Contract
  of their own Client. An Agent never corrects.
- `requireCanChangeStatus` is unchanged. A Tester still gets `403` on status,
  serial and "Installed in".
- Number: trimmed, 1–64 characters. Model: trimmed, 1–255 characters. Owner:
  `CLIENT` or `COMPANY`. Plan: an active plan of the SIM's own, non-archived
  Carrier, on a Postpaid SIM only.
- A plan correction writes `postpaid_plan_id` and `monthly_fee_amount` in the
  same transaction. The fee always equals the new plan's price at that moment.
- No stored Client Invoice line, no `linesStored` flag, and no Agent Invoice
  snapshot column is written by a correction. No invoice is created by one.
- An owner correction is refused while a Return naming the Smartphone is
  Pending Approval, Submitted or In Progress.
- No schema change and no migration.
- Every correction that changes a value emits exactly one audit event with
  the old and new value. A no-op emits none.
- Backend tests run under `IntegrationTest`. Mocking a repository there is
  banned.
- e2e and visual runs use an isolated stack on ports other than
  3000/8080/5432, with `E2E_DATABASE_URL` set, per
  `docs/agents/implementer-notes.md`. No test writes to the user's compose
  stack or the dev database.
- Every visual change conforms to `DESIGN.md`. A golden this feature moves is
  deleted before it is recaptured, and its diff shows only the intended
  change.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, what a later read
returns, the rendered accessibility tree. They never assert repository calls
or component internals.

1. **Backend: the HTTP API seam, which already exists.** `IntegrationTest`
   with MockMvc against real Postgres. Prior art:
   - `SmartphoneApiTest`: create, status and serial; access per role.
   - `SimCardApiTest`: create, status; per-role refusals.
   - `SimCardInstalledInApiTest`: a Fleet `PATCH` by the Manager and the
     Agent.
   - `PostpaidSimPlanApiTest`: plan and fee at creation. Its re-pricing tests
     at :147 and :173 show the SIM and a sent invoice left alone.
   - `ClientInvoiceStoredLinesApiTest` and `ClientInvoiceSendBackApiTest`:
     stored lines against live ones.
   - `AgentInvoiceApiTest`: the draft Local Support Fees.
   - `ReturnRequestsApiTest`: building an open Return.

   New tests sit beside them (`SmartphoneCorrectionApiTest`,
   `SimCardCorrectionApiTest`). They cover:
   - for each field, the Manager on any Contract and a Tester on their own
     Client's Contract get `200`, and the next `GET` of the Fleet shows the
     new value, trimmed;
   - a Tester on another Client's Contract gets `403`; an Agent, the
     Contract's own included, gets `403`; no token gets `401`; another
     Tenant's Contract (`OtherTenantFixture`) gets `404`;
   - a Tester still gets `403` on status, serial and installed-in;
   - blank and over-long values at the limit boundaries (64/65 and 255/256)
     return the coded `400`, and the value is left unchanged;
   - a plan on a Prepaid SIM, from another Carrier, archived, or on a SIM whose
     Carrier is archived or absent, returns its code; a plan given to a
     pre-catalog Postpaid SIM that has a Carrier succeeds and sets the fee;
   - after a plan correction, the SIM's fee equals the plan's price. The
     current month's draft Client Invoice's base amount and the draft Agent
     Invoice's Local Support Fees move by the difference. A sent Client
     Invoice, a sent-back one, and a sent Agent Invoice return exactly what
     they returned before;
   - a corrected number shows on a sent Client Invoice's SIM line, with its
     amount unchanged;
   - an owner change with a Pending Approval or Submitted Return naming the
     phone returns `409 SMARTPHONE_ON_OPEN_RETURN`. After that Return is
     cancelled, it succeeds. A Return raised after a COMPANY→CLIENT correction
     goes to `POSTED_TO_CLIENT` with no approval step;
   - saving the current value returns `200` and changes nothing.

   Audit events are not asserted in integration tests, as for the serial
   change (structured logs, not a read model). The walkthrough checks them.
2. **Frontend: component tests.** Vitest and Testing Library, beside the
   files. Prior art: `smartphone-serial-control.test.tsx`,
   `sim-card-installed-in-control.test.tsx`. Each of the four controls
   covers: Edit opens the field holding the current value; Cancel restores
   it; blank is refused with no request; each code maps to its message with
   the typed value kept; success leaves editing and refreshes. The plan
   control covers: only active plans of the SIM's Carrier are offered, and
   the fee shows for the chosen plan. The two tables cover: with
   `canCorrect`, the controls render; without it, plain text as today; a
   Prepaid SIM has no plan control; `canEditSerial` alone governs the serial
   control.
3. **Frontend: Playwright e2e against the real backend**, added to
   `tests/e2e/fleet-management.spec.ts`, the journey's proof. Prior art:
   - its "manager adds a client-owned smartphone … the agent sets it from
     the fleet page" test (:140), for the inline-edit flow;
   - its "a tester sees their client's fleet" test (:116);
   - its "rejected from a contract that isn't theirs" test (:227).

   New tests:
   - a Manager corrects a SIM's number and plan, and a Smartphone's model
     and owner, and sees each after a reload;
   - a Tester does the same on their own Client's Fleet;
   - an Agent's Fleet page offers no correction.

   Each test creates its own units, so it never depends on another spec's
   data.
4. **Visual goldens: the existing backend-stubbed suite.** The Manager's
   Contract Fleet and the Tester's Fleet are not captured today. The first
   ticket adds them as two surfaces with new goldens, which later tickets
   delete and recapture as the "Edit" row buttons arrive. The stub serves the Tester's
   `/api/contracts/{id}/carriers`.
5. **No new seam.** Each behaviour is tested at the highest seam that already
   exists and can see it.

## Decisions taken

- **Every Tester of the Client may correct, not only the Primary Contact.** Reason: CONTEXT.md says the Primary Contact is "a visible designation, not an extra permission"; the human said "Tester".
- **Corrections are audit-logged with old value, new value, actor and role, one event per field.** Reason: the serial change sets the pattern, and a Tester's Fleet write is new. The log is internal; nobody using the product sees it.
- **One `PATCH` per field (`/number`, `/postpaid-plan`, `/model`, `/owner`), not one general update endpoint.** Reason: it mirrors `/serial`, `/status` and `/installed-in`, gives each field its own rules and audit event, and lets each inline control call one route. It is additive.
- **A new guard method, `requireCanCorrect`, rather than opening `requireCanChangeStatus` to Testers.** Reason: a Tester gets the four corrections and nothing else. One method per permission keeps that visible, and the Agent question (decided by the human, 2026-10-14) becomes one line.
- **A correction reaches a unit in any status, as long as it is on a Contract's Fleet.** Reason: a retired SIM still billing through its cancellation month, or a past invoice's SIM line, can carry the typo too. The route already scopes to the Fleet. A status limit is one line to add.
- **A plan correction re-copies the new plan's price into the monthly fee.** Reason: the plan exists to set the fee (CONTEXT.md "Carrier"). A plan change that kept the old fee would fix nothing. "Never re-read" still holds for plan re-pricing.
- **A pre-catalog Postpaid SIM that names a Carrier may be given a plan; one with no Carrier gets no plan control.** Reason: the plan must belong to the SIM's Carrier, and the Carrier is not correctable (decided by the human, 2026-10-14).
- **The Carrier is checked only for being archived, not for being in the Contract's Country.** Reason: the SIM's Carrier is not being chosen again, and a SIM's Carrier never leaves its Country.
- **A SIM number is not checked for duplicates.** Reason: creation does not check either, and nothing in the data model makes numbers unique. Refusing only on correction would be a rule that applies to half the paths. A uniqueness rule is its own decision about the data already stored, and is recorded in `docs/tech-debt.md` by the SIM ticket.
- **Number and model are trimmed; their limits are the columns' (64 and 255).** Reason: a stray space would make a value that looks the same but differs. The limits need no migration.
- **The corrected number and model show on sent and approved invoices' SIM lines and their PDF; amounts never move.** Reason: they are read live by reference today, and the correction fixes a typo, so the right number on the invoice is the point. ADR 0004 freezes amounts, not labels. Freezing labels would need a migration that copies them, and can be added later without losing data, since the audit log keeps the old value.
- **Recorded history is not rewritten: a Request's requested model, and a Replace's recorded old model, stay.** Reason: they record what happened at that moment, not what the unit is.
- **An owner correction is refused while an open Return names the Smartphone.** Reason: the Return's approval and its Client-owned Disposition were fixed at submission from the owner. Changing it mid-way would leave the Return and the unit disagreeing. The Return can be cancelled first. The rule is one check, and cheap to drop.
- **Saving the current value is a `200` no-op with no audit event.** Reason: an idle Save should leave no trace. The control cannot tell whether the value changed until the server compares.
- **Every correction uses the edit-toggle shape (Edit → field → Save/Cancel), the owner select and the plan picker included, not the immediate-apply select of "Installed in".** Reason: a plan change moves money and an owner change moves a Return's path. A misclick on an immediate-apply select would commit either one. Save shows the new fee first.
- **The plan picker shows the monthly fee to the Tester too, though the Tester's Fleet table has no fee column.** Reason: the Tester already sees each Postpaid SIM's billed amount on their Client Invoices. Choosing a plan without its price would be guessing. Hiding the fee would need a picker variant.
- **The shared tables split their `contractId` axis into `canEditSerial` and `canCorrect`.** Reason: the Tester needs a `contractId` for corrections while staying read-only on serial and "Installed in". One prop per capability keeps the three roles readable.
- **The Tester's page reads each Contract's catalog from `GET /api/contracts/{id}/carriers` on the server.** Reason: that route already serves a Tester their Contract's Country's active catalog, for Requests. The Country-scoped `/api/carriers` routes stay Agent and Manager only.
- **The four existing hand-written fleet route handlers switch to `forwardJson` first, as a prefactor.** Reason: Frontend rule 13 switches them "when a feature next touches them", and this feature reworks every control that calls them. They are recorded debt.
- **Not fixed here: the loading button's lost focus, and the dialog-shell Escape guard (`docs/tech-debt.md`).** Reason: the new controls are inline, with no dialog. They reset their state as the serial control does, so they meet neither defect in a new way.
- **The PRODUCT.md edit lands in the first ticket that lets a Tester correct; the glossary changes are declared for the merger.** Reason: the line must not claim a power before the code grants it, and the merger is the one writer of `CONTEXT.md`.
- **Code goes in the existing `web` / `dto` / `security` / `logging` layout.** Reason: Backend rule 13's transition clause; the Fleet cluster has not moved.
- **Testing: the existing HTTP seam for every rule and every money effect, component tests for the controls and tables, e2e added to the journey's own spec, and the stubbed visual suite for layout.** Reason: each is the highest existing seam that can see its behaviour, and no seam is added.
- **(answered by the human, 2026-10-14)** Money follows the existing rules: a plan correction moves this month's unsent Client Invoice and the draft Agent Invoice that follows it; sent or approved Client Invoices, and sent, approved or paid Agent Invoices, do not move. Corrected labels show everywhere, amounts never move.
- **(answered by the human, 2026-10-14)** The Manager's Contract page does not gain the status control in this feature.
- **(answered by the human, 2026-10-14)** The Carrier and Postpaid ↔ Prepaid are not correctable in this feature; a SIM on the wrong Carrier or flavor is retired and added again.
- **(answered by the human, 2026-10-14)** The Contract's Agent does not get the four corrections; only the Manager and the Tester.
- **(answered by the human, 2026-10-14)** A Tester may change a Smartphone's owner in both directions, every change audit-logged with who made it; refused while an open Return names the phone.
- **(during implementation)** Saving the owner already held is a `200` no-op even while an open Return names the phone: the same-value check runs before the open-Return check, since nothing changes.
- **(during implementation)** A SIM whose current plan is archived opens the plan control with no plan chosen; Save stays disabled until one is picked.
- **(during implementation)** A Tester's plan control gets no "Add one on the Carriers page" pointer, since a Tester has no Carriers page; a Carrier with no active plan gives the Tester no plan control.
- **(during implementation)** The correction "Edit" buttons carry accessible names per field ("Edit owner", "Edit plan", …) so they stay distinct inside one row.

## Open questions

None.

## Acceptance walkthrough

Run against an isolated stack with the backend on the `demo` profile (its own
fresh database, seeded by `DemoDataLoader`), never the user's compose stack.
Logins are in the root `README.md`: the Manager (`manager@example.com`), Jordan
Ellis (Agent, US), Dana Whitfield and Marco Diaz (Testers, Solstice), and Noah
Kim (Tester, Harbor Line).

1. [agent] As the Manager, open Solstice's UK Contract Fleet. Show an "Edit" row button beside each Smartphone's model and owner and each SIM Card's number, and beside the plan of each Postpaid SIM only. Show there is still no status control. Correct a SIM's number to "  +44 7700 900123  ", save, and show "+44 7700 900123" in the row, kept after a reload. (stories: 1, 13, 14)
2. [agent] As the Manager, open that SIM's plan control. Show only the active plans of its own Carrier offered, and the monthly fee changing as you pick one. Note the UK Client Invoice draft's base amount, save the new plan, and show the Fleet's Monthly fee column reading the new plan's price. Read the draft through the API and show its base amount moved by the price difference. Read Priya's Agent Invoice: if it is a draft, show its Local Support Fees moved by the same amount; if the demo story has it sent, show it unchanged and point at the backend test that covers the draft case. (stories: 2, 15, 17, 18, 19)
3. [agent] On Solstice's US Contract, whose Client Invoice for this month is sent, correct a Postpaid SIM's plan as the Manager. Show the sent invoice's lines and total exactly as before, and its SIM line showing the SIM's current number. Show Jordan's sent Agent Invoice unchanged. (stories: 20, 21)
4. [agent] As the Manager, correct a Smartphone's model to "Pixel 8 Pro" and its owner to "Client". Show both after a reload. Open a Request naming that phone and show "Pixel 8 Pro" there. Correct the model of a demo phone that a Replace or Provision Smartphone Request recorded, and show that Request's recorded model unchanged (the backend test covers a returned unit's view). (stories: 3, 4, 21, 22)
5. [agent] Sign in as Dana (Tester, Primary Contact). On each of Solstice's two Contracts, correct a SIM's number, a Postpaid SIM's plan (showing the fee before Save), a Smartphone's model and its owner, and show each after a reload. Sign in as Marco (not Primary Contact) and correct one model. Show that neither Tester's page offers status, serial or "Installed in" controls, and that a `PATCH` to `…/status`, `…/serial` and `…/installed-in` as Dana returns `403`. (stories: 5, 6, 7, 8, 9, 11)
6. [agent] Through the API as Dana, `PATCH` a unit of Harbor Line's Contract and show `403`. As Jordan, `PATCH` each of the four fields on his own Contract and show `403`, and show his Fleet page offers no correction. With no token, show `401`. Run `SmartphoneCorrectionApiTest` and `SimCardCorrectionApiTest`, which seed `OtherTenantFixture`, and show them green. (stories: 10)
7. [agent] As the Manager, submit an empty number and an empty model and show "Enter a number." / "Enter a model." with no request sent. Submit 65 characters and 256 characters and show the length messages with the text kept. Cancel an open edit and show the old value. Save a value unchanged and show nothing changes. Stop the backend, save, and show "Couldn't update. Try again." with the row unchanged. (stories: 12, 26, 27)
8. [agent] On a Prepaid SIM, show no plan control, and a `PATCH …/postpaid-plan` returns `400 PREPAID_HAS_NO_PLAN`. Archive a plan on the Carriers page as an Agent and show it no longer offered. A `PATCH` naming it returns `400 PLAN_ARCHIVED`, and one naming another Carrier's plan returns `PLAN_NOT_ON_CARRIER`. Using the backend test's pre-catalog SIM (a Carrier, no plan), show a plan can be given and the fee becomes its price. (stories: 14, 15, 16)
9. [agent] As Noah (Harbor Line Tester), open the Fleet while Harbor Line's Return awaits Dispositions. Try to correct the owner of a Smartphone on that Return (if it names none, first submit a Return of a Smartphone as Noah) and show "This smartphone is on an open Return. Change its owner once the Return is done." with the owner unchanged. As the Manager, on a phone with no open Return, change the owner from Company to Client, then submit a Return of it as a Tester and show it is not Pending Approval and its Disposition is "Posted to Client". (stories: 23, 24)
10. [agent] Show the backend log's audit lines for steps 1, 2, 4 and 5: one per changed field, each with the unit, the Contract, the old and new value, the actor and their role (MANAGER or TESTER), the plan line also with the old and new fee. Show no line for the unchanged save of step 7. (stories: 25, 26)
11. [agent] Drive the Manager's and Dana's Fleet pages by keyboard only, at desktop and at 375px wide: Tab to an "Edit" button, open it, change the value, save with Enter, and cancel with the Cancel button. Show visible focus at every step and the table scrolling sideways, not reflowing. (stories: 28)
12. [agent] Show `PRODUCT.md`'s Client Tester line reads as amended, and that the declared terms match the spec. Show the four old fleet route handlers and the four new ones use `forwardJson`. Show no `text-[11px]`/`text-[12px]`/`text-[13px]` in a new or changed line. Run the visual suite and show that the only goldens added are the Manager Contract Fleet and Tester Fleet ones, and that later changes to them were deleted first. Show no migration was added. Then run `docs/agents/sdlc.json`'s `verify` green. (stories: 29)
13. [human] As the Manager and as Dana, correct a SIM's plan and a Smartphone's owner on the demo data. Confirm the controls read clearly and every message reads right to that person: "Enter a number.", "Enter a model.", the two length messages, "That plan can't be chosen any more. Pick another.", the open-Return message, "Couldn't update. Try again.". Confirm the Tester seeing the plan's fee is acceptable to you. (stories: 2, 6, 12, 23, 27)
14. [human] Look at the Solstice UK draft invoice after step 2 and the sent US invoice after step 3. Confirm the draft billing the corrected plan, and the sent invoice keeping its amounts but showing the corrected SIM number, are what you want. (stories: 18, 20, 21)

## Execution order

Five tickets. The prefactor runs in parallel with everything: the new route
handlers are their own files. The model ticket adds the guard the others reuse.
The owner and SIM-number slices each wait for it; the plan slice follows the
number slice, since both edit the SIM table and `SimCardCorrectionApiTest`.

1. `fleet-route-handlers-forward-json` (enabler): switch the hand-written `smartphones/…/{serial,status}` and `sim-cards/…/{status,installed-in}` route handlers to `forwardJson`, with no behaviour change. Modules: `app/api` (fleet routes). No dependency. (stories: none; enables the correction tickets' route handlers to sit beside consistent siblings)
2. `correct-a-smartphone-model`: `requireCanCorrect`; `PATCH …/model` with its codes, trimming and audit event; `SmartphoneCorrectionApiTest` (model half); the `canEditSerial`/`canCorrect` split on `SmartphoneFleetTable`; `SmartphoneModelControl` with its component test and route handler; the controls on the Manager's Contract Fleet and the Tester's Fleet; e2e for both roles and the Agent's absence; the two Fleet-page visual surfaces and their goldens added; the `PRODUCT.md` Tester line and the declared terms Correction, Tester and Owner. Modules: backend `security`, `web`, `dto`, `logging`; `components/fleet`, `components/manager`, `components/client`, `app/api`, e2e, visual suite. No dependency. (stories: 3, 7, 9, 10, 11, 12, 13, 21, 22, 25, 26, 27, 28, 29)
3. `correct-a-smartphone-owner`: `PATCH …/owner`, the open-Return refusal, the Return-path tests and audit event; `SmartphoneOwnerControl` with its component test and route handler; e2e; goldens recaptured. Depends on `correct-a-smartphone-model`. (stories: 4, 8, 23, 24, 25, 26, 27, 28)
4. `correct-a-sim-card-number`: `PATCH …/number` with its codes, trimming and audit event; the number half of `SimCardCorrectionApiTest`; `canCorrect` on `SimCardFleetTable`; `SimCardNumberControl` with its component test and route handler, on both pages; e2e for both roles; goldens recaptured; the duplicate-number gap recorded in `docs/tech-debt.md`. Depends on `correct-a-smartphone-model`. (stories: 1, 5, 9, 10, 12, 13, 21, 25, 26, 27, 28)
5. `correct-a-postpaid-plan`: `PATCH …/postpaid-plan` reusing `requireUsablePlan`, re-copying the fee, its codes and audit event; the plan and money half of `SimCardCorrectionApiTest`; `carriers` on `SimCardFleetTable`; `SimCardPlanControl` with its component test and route handler; the Tester page reading each Contract's catalog; the stub's Tester carriers; e2e for both roles; goldens recaptured; the declared Carrier term. Depends on `correct-a-sim-card-number`. (stories: 2, 6, 9, 10, 14, 15, 16, 17, 18, 19, 20, 25, 26, 27, 28)
