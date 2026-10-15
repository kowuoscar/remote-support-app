---
feature: agent-corrects-the-fleet
epic: fleet-corrections
status: draft
date: 2026-10-15
---

<!-- sdlc:template spec 1 -->

# The Agent corrects the Fleet; the Tester only views it

## Problem

`correct-a-fleet-unit` (delivered and accepted, PR #27) let the Company
Manager and a Client Tester correct four things on a unit already in a
Contract's Fleet: a SIM Card's number, a Postpaid SIM's Postpaid plan, a
Smartphone's model and a Smartphone's owner. The Contract's own Agent was left
out on purpose. That spec's non-goal read "No correction by an Agent".

Accepting that feature on 2026-10-15, the human found it gave the power to the
wrong role (verbatim):

> "As an agent I still can't correct mistyped sim number, postpaid plan or
> smartphone's detail. For a manager it's ok."

> "It is a mistake on my side, I didn't meant the tester to have the rights but
> the agent. Agent and Manager should be the ones to be able to do the edit.
> Remove the rights on tester and moved them on to the agent."

So today:

- **The Agent**, the person who handles the phones and SIMs in the field and
  is most likely to spot a mistyped number or model, cannot fix it. Their
  Fleet page offers status, serial and "Installed in", and nothing else. The
  backend refuses them with `403` on their own Contract.
- **A Tester**, who was meant only to view their Client's Fleet and ask for
  changes through Requests, can change a SIM's plan (which moves what the
  Client is billed) and a Smartphone's owner (which decides whether a Return
  needs the Manager's approval). `PRODUCT.md` and the glossary say a Tester
  corrects these four things.

What a correction does stays exactly as delivered: the same four fields, the
same rules, the same refusals, the same audit event, the same money effects.
This feature changes only who may make one.

## Journeys

Advances `docs/roadmap/fleet-corrections.md`, its second feature
(`agent-corrects-the-fleet`).

- **Put resources in the field** (`exists`, extended). Today the journey reads
  "A Manager builds a Contract's Fleet … and an Agent keeps it accurate —
  changing a status, installing a SIM Card into a Smartphone, setting a
  serial. A Tester sees their own Client's Fleet." It never named the
  Tester's corrections. After this feature the Agent's list gains "correcting
  a mistyped SIM number, a SIM's Postpaid plan, a Smartphone's model or its
  owner", the Manager corrects the same four on any Contract, and "A Tester
  sees their own Client's Fleet" stays as written. The proof stays
  `frontend/tests/e2e/fleet-management.spec.ts`. The orchestrator rewrites the
  journey's paragraph at delivery.

## Goals / Non-goals

Goals:

- The Contract's own Agent can make the four corrections (SIM number, Postpaid
  plan, Smartphone model, Smartphone owner) on any unit of their own
  Contracts' Fleets, from their Fleet page, with the controls, rules,
  messages and audit event the Manager already has.
- A Tester can no longer correct anything. The Tester's Fleet page shows the
  four values as plain text with no Edit. The backend refuses a Tester's
  correction with `403`.
- The Manager is unchanged: the four corrections on any Contract of the
  Tenant.
- `PRODUCT.md`, the glossary and the journey say the Manager and the Agent
  correct, and the Tester views.

Non-goals. Each is something a reasonable agent would otherwise build:

- **No change to what a correction does.** The same four fields, value rules,
  codes, messages, no-op rule, open-Return refusal and money effects as
  `correct-a-fleet-unit`. No new field becomes correctable.
- **No Carrier correction and no Postpaid ↔ Prepaid flip**, still (decided
  by the human, 2026-10-14).
- **No status control on the Manager's Contract page**, still (decided by the
  human, 2026-10-14).
- **No correction by an Agent outside their own Contracts.** Not another
  Agent's Contract, and not a unit in their own Agent Stock (it belongs to no
  Contract).
- **No new Agent power beyond the four corrections.** The Agent still cannot
  add a unit to a Fleet or retire a Smartphone from the Fleet page.
- **No Request-based way for a Tester to ask for a correction.** A Tester who
  spots a typo uses the Requests that exist today (an Other Request), or tells
  the Agent. No new Request type and no "suggest a correction" control.
- **No undoing of corrections a Tester already made.** Values a Tester
  corrected while they could stay as corrected. The audit log keeps the old
  values. No migration, no data change.
- **No uniqueness rule on SIM numbers**, still. The Agent is a new writer on
  that path; the gap stays recorded debt (`docs/tech-debt.md`).
- **No notification** to the Manager or the Client when an Agent corrects a
  unit. The audit log is the only record, as before.
- **No change history shown in the product**, still.
- **No payment of the backend correction debt (F9–F12).** The services are
  role-agnostic and are not touched.

## User stories

1. As an Agent, I want to correct a SIM Card's number on my own Contract's Fleet, so that a number I mistyped or misread is fixed without retiring the SIM and adding it again.
2. As an Agent, I want to correct a Postpaid SIM's Postpaid plan on my own Contract's Fleet, choosing from the active plans of the SIM's own Carrier and seeing the new monthly fee before I save, so that the SIM is billed on the plan it is really on.
3. As an Agent, I want to correct a Smartphone's model on my own Contract's Fleet, so that the phone is recorded as it is everywhere it appears.
4. As an Agent, I want to correct a Smartphone's owner between Client and Company on my own Contract's Fleet, so that a Client's own phone is not treated as the company's, and the reverse.
5. As an Agent whose SIM's Carrier has no active Postpaid plan, I want the plan control to point me to my Carriers page, so that I can add the plan I need and come back.
6. As the company, I want an Agent refused when correcting a unit of a Contract that is not theirs, so that each Agent acts only on their own Contracts.
7. As an Agent, I want the status, serial and "Installed in" controls on my Fleet page to keep working beside the new corrections, so that nothing I could do before is lost.
8. As an Agent, I want the Monthly fee column of my Fleet page to show the corrected plan's price as soon as I save, so that I see what the SIM now costs.
9. As an Agent, I want my Contract's current-month Client Invoice draft, and any earlier draft never sent, to bill the corrected monthly fee the next time it is read, so that the invoice I am preparing bills the right plan.
10. As an Agent, I want my draft Agent Invoice's Local Support Fees to follow a corrected fee on a draft Client Invoice, so that my pay matches what the Client is billed.
11. As a Company Manager, I want an Agent's plan correction never to change the amounts of a Client Invoice already sent, approved or sent back, nor of an Agent Invoice already sent, approved or paid, so that money already shown, reviewed or paid never moves silently.
12. As anyone reading a unit anywhere (Fleet, Requests, Agent Stock, returned units, a Client Invoice's SIM lines), I want a number or model an Agent corrected shown there, so that the record reads the same everywhere.
13. As a Company Manager, I want the model a Request asked for, and the model a Replace Request recorded for the phone it replaced, kept as recorded after an Agent's correction, so that history says what happened then.
14. As an Agent, I want an owner correction refused while an open Return names that Smartphone, with a message saying why, so that a Return already in progress never changes path halfway.
15. As a Company Manager, I want the owner an Agent corrected to decide every Return raised after it, so that a Return of a Company-owned phone comes to me for approval and a Client-owned one goes back to the Client.
16. As an Agent, I want a blank number or model, or one longer than its limit, refused at the field with what I typed kept, and surrounding spaces dropped, so that I can fix it in place and a stray space does not create a look-alike value.
17. As an Agent, I want no plan control on a Prepaid SIM or on a SIM whose Carrier is archived or absent, and a plan that is archived or of another Carrier refused, so that a SIM is never put on a plan its Carrier does not sell.
18. As an Agent correcting a Postpaid SIM from before the Carrier catalog, which names a Carrier but no plan, I want to give it a plan, with its monthly fee becoming that plan's price, so that an old SIM can be brought onto the catalog.
19. As an Agent, I want saving the value already held to change nothing and log nothing, Cancel to leave the value as it was, and a failed save to show "Couldn't update. Try again." with the row unchanged, so that nothing changes unless I mean it to and it worked.
20. As an Agent working by keyboard or on a phone, I want each correction reachable and operable by keyboard, with visible focus, inside the horizontally scrolling Fleet table, so that it works without a mouse and at any width.
21. As a Company Manager, I want every correction an Agent makes audit-logged with the Contract, the unit, the old value, the new value, the Agent's user id and the role AGENT, so that I can trace any change after the fact.
22. As a Client Tester, I want my Fleet page to show each unit's number, plan, model and owner as plain text with no Edit button, so that the page reads as what it is: a view of my Client's Fleet.
23. As the company, I want a Tester's correction refused with `403` on every Contract, their own Client's included, so that the Tester's power ends at the page and at the API alike.
24. As the company, I want a Tester still refused on status, serial and "Installed in", so that removing the corrections opens nothing else.
25. As a Company Manager, I want to keep correcting all four things on any Contract of the Tenant, exactly as today, so that moving the power from the Tester to the Agent costs me nothing.
26. As a Company Manager, I want values a Tester already corrected left as they are, so that removing the power does not rewrite data that is right.
27. As an Agent, I want the serial's Edit button to look and be named like the correction Edit buttons beside it ("Edit serial, {serial}"), so that one row does not mix two styles of the same action.
28. As the team, I want `PRODUCT.md`, the glossary and the journey to say that the Manager and the Agent correct these four things and a Tester only views them, so that the next agent reading them does not restore the Tester's power.

## Solution

### Who may correct, after this feature

| Unit | Field | Manager | Agent (own Contract) | Tester |
|---|---|---|---|---|
| SIM Card | number | yes | **yes** (was no) | **no** (was yes, own Client) |
| SIM Card (Postpaid) | Postpaid plan | yes | **yes** | **no** |
| Smartphone | model | yes | **yes** | **no** |
| Smartphone | owner | yes | **yes** | **no** |
| either | status, serial, Installed in | unchanged | unchanged | no, unchanged |

Everything else about a correction is the predecessor's, by reference:
`docs/features/correct-a-fleet-unit/spec.md`, sections "Backend: the four
endpoints", "Backend: audit", "What a plan correction does to money" and the
message table under "Frontend: the shared Fleet tables". This spec restates
none of it.

### Backend: authorization

`FleetAccessGuard.requireCanCorrect(contract, principal)` changes one arm, and
its Javadoc with it:

```
MANAGER → allowed, any Contract of the Tenant
AGENT   → allowed only on a Contract that is theirs (the existing
          requireOwnsContractAsAgent check)
TESTER  → refused (403)
other   → refused (403)
```

It becomes the same rule as `requireCanChangeStatus`, but stays its own
method (`## Decisions taken`). `SecurityConfig` needs no change: the Fleet
`PATCH` routes are already open to any authenticated caller and the Agent
already `PATCH`es `…/serial` and `…/installed-in` through them.

The four endpoints, the services, the value rules, the codes, the `409` on an
open Return and the money effects do not change. The services already take
the role only as an audit argument, so an Agent's correction logs
`role=AGENT` with no code change. The `AuditLog` Javadocs ("A Manager or
Tester correcting …") and the frontend route handlers' doc comments ("for a
Manager or Tester") are reworded to "Manager or Agent".

### Frontend: the three Fleet pages

The shared tables (`SmartphoneFleetTable`, `SimCardFleetTable`) and the four
correction controls are role-agnostic and do not change in behaviour. Only
what each page passes changes:

| Page | Passes, after this feature |
|---|---|
| Manager, Contract Fleet | unchanged: `contractId`, `canEditSerial`, `canCorrect`, `carriers`, `carriersHref` to the Manager's Carriers page |
| Agent, Fleet | as today (`contractId`, `canEditSerial`, `smartphones`, `changeStatus`, `showMonthlyFee`) **plus** `canCorrect`, `carriers` and `carriersHref="/agent/carriers"` |
| Tester, Fleet | `contractId` dropped where it only served corrections; no `canCorrect`, no `carriers`. The tables render plain text, as before `correct-a-fleet-unit` |

The Agent's Fleet page reads its Carrier catalog once on the server with the
existing `loadActiveCarriers()` (no Country: the Agent's own Country's active
Carriers with their Postpaid Plans), as the Agent's Requests page already
does. Every Contract of an Agent is in that Agent's Country, so one list
serves every Contract in the switcher. The Tester's Fleet page stops loading
`GET /api/contracts/{id}/carriers` per Contract, since nothing on it uses the
catalog any more.

Because the Agent passes `carriersHref`, a Postpaid SIM whose Carrier has no
active plan gets the plan control with the picker's existing "Add one on the
Carriers page" pointer, as the Manager's does. The table's comments that name
the Tester ("Omitted for a Tester", "(Manager, Tester)") are reworded; the
branch for a caller with no `carriersHref` stays, harmless.

### Prefactor: the serial Edit matches the correction Edits

The Agent's Smartphone row will hold the serial Edit beside the model and
owner Edits. The serial Edit is still the soft-indigo Row chip named "Edit";
the correction Edits are the quiet `ghost` variant named per unit (recorded
debt F21, `docs/tech-debt.md`). `SmartphoneSerialControl`'s trigger becomes
`ghost`, with the visible text "Edit" and the accessible name
"Edit serial, {serial}" (or "Edit serial, {model}" while no serial is set).
This also changes the Manager's Contract Fleet. The debt line is removed at
delivery.

### Product and glossary amendments

Applied in the ticket that moves the power (`## Execution order`).
`PRODUCT.md` is edited in that ticket; the glossary changes are declared for
the merger.

- `PRODUCT.md`, Users, Local Support Agent, now reads: "executes support
  Requests, logs Fees, keeps Fleet status accurate and corrects a mistyped SIM
  number, a SIM's Postpaid plan, a Smartphone's model or its owner on their
  own Contracts, assembles both monthly invoice types for their own
  Contracts."
- `PRODUCT.md`, Users, Client Tester, returns to: "an individual login
  belonging to a Client company; submits support Requests, views their
  Client's Fleet, Contracts and Invoices."
- `PRODUCT.md`, Company Manager: unchanged.
- Glossary (`terms` in the result): **Correction**, **Tester**, **Owner** and
  **Agent**, each narrowed.

## Design direction

Conforms to `DESIGN.md`; no `DESIGN.md` change. The surfaces are the Agent's
Fleet (gains the four corrections), the Tester's Fleet (loses them) and the
Manager's Contract Fleet (serial Edit only), all **Operate**.

The Agent's controls are the ones the Manager already has: inline row
actions, `ghost` "Edit" buttons named per unit, field with Save and Cancel in
the cell at `h-7`, the plan picker's fee under the select through `Money`,
errors in `danger` ink. The Agent's tables are the widest in the product
(Change status column, Monthly fee, Installed in); they keep
`overflow-x-auto` sideways scrolling at narrow widths and do not reflow into
cards, as on the Manager's page. The Tester's tables return to plain cells.

New and changed lines use `text-label-sm` / `text-label`, never
`text-[11px]`, `text-[12px]` or `text-[13px]` (Frontend rule 8). The `design`
slot does not need to be engaged.

## Constraints

- Inherits every constraint of `correct-a-fleet-unit` except the two it
  reverses: "A Tester acts only on a Contract of their own Client" and "An
  Agent never corrects". In their place: an Agent corrects only a unit of a
  Contract that is theirs; a Tester never corrects.
- `requireCanChangeStatus` is unchanged.
- No change to the four endpoints' paths, bodies, codes or response DTOs.
- No schema change and no migration. No stored value is rewritten.
- An Agent's correction emits exactly the same audit event as a Manager's,
  with `role=AGENT`. A no-op emits none.
- Backend tests run under `IntegrationTest`. Mocking a repository there is
  banned.
- e2e and visual runs use an isolated stack on ports other than
  3000/8080/5432, with `E2E_DATABASE_URL` set, per
  `docs/agents/implementer-notes.md`. No test writes to the user's compose
  stack or the dev database.
- Every visual change conforms to `DESIGN.md`. A golden this feature moves is
  deleted before it is recaptured, in the same commit as the code that moved
  it, and its diff shows only the intended change.

## Testing decisions

Tests assert external behaviour only: HTTP status and body, what a later read
returns, the rendered accessibility tree.

1. **Backend: the HTTP API seam, which already exists.** The predecessor's
   `SmartphoneCorrectionApiTest` and `SimCardCorrectionApiTest` are flipped,
   not duplicated. Every value-rule, money and open-Return test there is
   role-agnostic and keeps running as the Manager; the role tests change:
   - the Agent on their own Contract (`agentToken`, Contracts created with
     `SEEDED_AGENT_ID`) gets `200` on each of the four fields, and the next
     `GET` of the Fleet shows the new value (today these assert `403`);
   - the Agent on a Contract of another Agent gets `403`, built the way
     `SimCardInstalledInApiTest.anAgentOnAnotherContractCannotSetInstalledIn`
     builds it;
   - a Tester of the Contract's own Client (`linkedTesterToken`) gets `403` on
     each of the four fields, and the value is left unchanged (today `200`);
   - a Tester of another Client still `403`, no token still `401`, another
     Tenant (`OtherTenantFixture`) still `404`;
   - the Tester's `403` on status, serial and installed-in stays as it is;
   - one plan correction made by the Agent moves the current month's draft
     Client Invoice and the draft Agent Invoice, as the Manager's does, so
     the money path is proven for the new caller once;
   - one owner correction made by the Agent with an open Return naming the
     phone returns `409 SMARTPHONE_ON_OPEN_RETURN`.

   No `FleetAccessGuard` unit test is added: the HTTP tests already see every
   arm of the rule.
2. **Frontend: component tests.** Vitest and Testing Library, beside the
   files. The tables' tests (`smartphone-fleet-table.test.tsx`,
   `sim-card-fleet-table.test.tsx`) rename their Tester-flavoured cases
   ("canCorrect without smartphones (Tester)") to the roles that now pass
   those props, and gain one case: `canCorrect` together with `canEditSerial`
   and `changeStatus` (the Agent's combination) renders every control in one
   row. `smartphone-serial-control.test.tsx` covers the new accessible name.
   The four correction controls' own tests are untouched.
3. **Frontend: Playwright e2e against the real backend**, in
   `tests/e2e/fleet-management.spec.ts`, the journey's proof. The
   predecessor's tests flip:
   - the four Tester correction tests (:292, :362, :428, :519) become one
     "a Tester sees the four values with no Edit" test per table;
   - the four "the Agent's Fleet page offers no correction" tests (:320,
     :386, :454, :546) become the Agent correcting each field on their own
     Contract and seeing it after a reload;
   - the Manager's correction tests (:273, :340, :411, :485) and the
     "rejected from a contract that isn't theirs" test (:227) stay.
   Each test creates its own units, so it never depends on another spec's
   data.
4. **Visual goldens: the existing backend-stubbed suite.** The
   `client-fleet` golden is deleted and recaptured without the Edit buttons;
   `manager-contract-fleet` is recaptured for the serial Edit. The Agent's
   Fleet is not captured today; it is added as an `agent-fleet` surface,
   since it now carries the most controls per row. The stub already serves
   `GET /api/carriers` for the Agent; the Tester's
   `/api/contracts/{id}/carriers` stub stays for the Requests page.
5. **No new seam.** Each behaviour is tested at the highest seam that already
   exists and can see it.

## Decisions taken

- **`requireCanCorrect` stays its own method, now with the same rule as `requireCanChangeStatus`, rather than the four endpoints calling `requireCanChangeStatus`.** Reason: the two permissions were already split once for a reason and may split again; one method per permission keeps each a one-line change.
- **"Own Contract" for the Agent is the existing `requireOwnsContractAsAgent` check.** Reason: it is the rule the Agent's status, serial and "Installed in" writes already use; an Agent sees only their own Contracts' Fleets anyway.
- **The Agent's Fleet page loads one Carrier catalog with `loadActiveCarriers()`, not one per Contract.** Reason: every Contract of an Agent is in the Agent's Country, and the Agent's Requests page already serves every Contract from one load.
- **The Agent's plan control points to `/agent/carriers` when the SIM's Carrier has no active plan.** Reason: the Agent maintains their Country's catalog and every other Agent plan picker already points there; the Manager's control does the same.
- **The Tester's Fleet page stops loading the Contract catalog.** Reason: nothing on the page reads it once the plan control is gone; the Requests page keeps its own load.
- **The Tester's page drops `contractId` from the tables where it served only corrections.** Reason: with no `canCorrect` the tables render plain text either way; passing nothing makes the read-only page read as read-only.
- **Corrections a Tester made before this feature are left as they are, with no data change.** Reason: they fix typos and were allowed when made; the audit log keeps the old values.
- **No new code or message for a Tester's refused correction; the guard's existing `403` "Not allowed to correct this Contract's Fleet" stands.** Reason: no Tester screen offers a correction any more, so only a hand-made request meets it.
- **The predecessor's correction tests are flipped in place, not copied into new Agent tests.** Reason: the rule under test is one guard arm; one set of role tests per field keeps the matrix readable.
- **One money test and one open-Return test are repeated with the Agent's token.** Reason: those are the two effects a person would notice if the new caller took another path; the rest of the rules are role-agnostic and stay proven through the Manager.
- **An `agent-fleet` visual surface is added.** Reason: it is the surface this feature changes most and no golden covers it today.
- **The `PRODUCT.md` edit lands in the ticket that moves the power; the glossary changes are declared for the merger.** Reason: the line must not claim a power before the code grants it, and the merger is the one writer of `CONTEXT.md`.
- **(settled by the human, 2026-10-15)** The Manager and the Agent correct; the Tester only views and has no Edit. The Agent corrects the same four things on their own Contracts with the predecessor's rules: audit-logged with old and new value, actor and role; owner in both directions, refused while a Return naming the phone is open; drafts move on a plan change, sent and approved invoices do not. `PRODUCT.md` and `CONTEXT.md` say so.

- **(answered by the human, 2026-10-15: "both directions")** The Agent may correct a Smartphone's owner in both directions, Company to Client included, though a Client-owned phone's Return then skips the Manager's approval; every change is audit-logged with the Agent's login and role.

- **(answered by the human, 2026-10-15: "ok")** The serial Edit becomes the `ghost` variant named "Edit serial, {serial}", as a prefactor ticket `serial-edit-matches-corrections`, paying debt F21. Reason: this feature puts the chip and the ghost Edits on the same row for the first time.

## Open questions

None.

## Acceptance walkthrough

Steps 1–10 run against an isolated stack with the backend on the `demo`
profile (its own fresh database, seeded by `DemoDataLoader`), never the user's
compose stack. Steps 11–13 are the human's, hands-on on their own stack.
Logins are in the root `README.md`: the Manager (`manager@example.com`),
Jordan Ellis (Agent, US: Solstice's US Contract and Harbor Line's), Priya Shah
(Agent, UK: Solstice's UK Contract), Dana Whitfield and Marco Diaz (Testers,
Solstice), and Noah Kim (Tester, Harbor Line).

1. [agent] Sign in as Priya and open the Fleet on Solstice's UK Contract. Show an "Edit" button beside each Smartphone's model and owner and each SIM Card's number, and beside the plan of each Postpaid SIM only; show the status, serial and "Installed in" controls still in their rows, and change one SIM's "Installed in" to prove it still works. Correct a SIM's number to "  +44 7700 900123  ", save, and show "+44 7700 900123" in the row, kept after a reload. (stories: 1, 7, 16, 17)
2. [agent] As Priya, open the plan control of a Postpaid SIM. Show only the active plans of its own Carrier offered and the monthly fee changing as you pick one. Note the UK Client Invoice draft's base amount and Priya's draft Agent Invoice's Local Support Fees, save the new plan, show the Fleet's Monthly fee column reading the new plan's price, then read both drafts through the API and show each moved by the price difference. (stories: 2, 8, 9, 10)
3. [agent] As Priya, archive every Postpaid plan of one UK Carrier on the Carriers page (or use the backend test's Carrier with none) and show the plan control pointing to "Add one on the Carriers page", linking to `/agent/carriers`. Restore the plans. Show a Prepaid SIM has no plan control. (stories: 5, 17)
4. [agent] Sign in as Jordan. On Solstice's US Contract, whose Client Invoice for this month is sent, correct a Postpaid SIM's plan. Show the sent invoice's lines and total exactly as before, its SIM line showing the SIM's current number, and Jordan's sent Agent Invoice unchanged. (stories: 11, 12)
5. [agent] As Jordan, correct a Smartphone's model to "Pixel 8 Pro" and its owner from Company to Client on Solstice's US Contract; show both after a reload, and "Pixel 8 Pro" on a Request naming that phone. Correct the model of a demo phone a Replace or Provision Smartphone Request recorded, and show that Request's recorded model unchanged. Submit a Return of the phone now Client-owned as Dana and show it is not Pending Approval and its Disposition is "Posted to Client". (stories: 3, 4, 12, 13, 15)
6. [agent] As Jordan, open Harbor Line's Fleet while its Return awaits Dispositions and try to change the owner of a Smartphone on that Return. Show "This smartphone is on an open Return. Change its owner once the Return is done." with the owner unchanged. Then, through the API as Jordan, `PATCH` each of the four fields on a unit of Solstice's UK Contract (Priya's) and show `403` each time. (stories: 6, 14)
7. [agent] As Jordan, submit an empty number and an empty model and show "Enter a number." / "Enter a model." with no request sent; submit 65 and 256 characters and show the length messages with the text kept. Cancel an open edit and show the old value. Save a value unchanged and show nothing changes. Stop the backend, save, and show "Couldn't update. Try again." with the row unchanged. Run `SimCardCorrectionApiTest` and show the pre-catalog SIM case (a Carrier, no plan) green. (stories: 16, 18, 19)
8. [agent] Sign in as Dana. On both of Solstice's Contracts, show the Smartphones and SIM Cards tables with the number, plan, model and owner as plain text and no "Edit", status, serial or "Installed in" control anywhere. Sign in as Marco and show the same. Through the API as Dana, `PATCH` each of the four fields on a unit of Solstice's US Contract and show `403` with the value unchanged after a read; `PATCH` `…/status`, `…/serial` and `…/installed-in` and show `403`. With no token show `401`. (stories: 22, 23, 24)
9. [agent] As the Manager, correct a SIM's number, a Postpaid SIM's plan, a Smartphone's model and its owner on Harbor Line's Contract and show each after a reload. Show each Smartphone row's serial Edit is the same quiet button as the correction Edits, and its accessible name reads "Edit serial, {serial}"; show the same on Jordan's page. Show the backend log's audit lines for steps 1, 2, 5 and this step: one per changed field, each with the unit, the Contract, the old and new value, the actor and their role (AGENT for Priya and Jordan, MANAGER here), the plan lines also with the old and new fee; and no line for step 7's unchanged save. Run `SmartphoneCorrectionApiTest` and `SimCardCorrectionApiTest`, which seed `OtherTenantFixture`, and show them green. (stories: 21, 25, 27)
10. [agent] Drive Jordan's Fleet page by keyboard only, at desktop and at 375px wide: Tab to each "Edit" button, open it, change the value, save with Enter, and cancel with the Cancel button. Show visible focus at every step and the table scrolling sideways, not reflowing. Show `PRODUCT.md`'s Agent and Client Tester lines read as amended and the declared terms match this spec. Show no `text-[11px]`/`text-[12px]`/`text-[13px]` in a new or changed line. Run the visual suite and show the only goldens changed are `client-fleet`, `manager-contract-fleet` and the new `agent-fleet`, each deleted before recapture. Show no migration was added and no stored Fleet value is rewritten by the change (the diff touches no repository write or data script). Run `docs/agents/sdlc.json`'s `verify` green. (stories: 20, 26, 28)
11. [human] On your own stack, sign in as an Agent and open the Fleet. Correct a SIM's number, a Postpaid SIM's plan, a Smartphone's model and its owner on one of that Agent's Contracts. Confirm the controls are where you expect them on the Agent's page beside status, serial and "Installed in", and the messages read right. (stories: 1, 2, 3, 4, 7, 19, 27)
12. [human] On your own stack, sign in as a Tester and open the Fleet. Confirm there is nothing to edit, and that this is what you meant by "only views". (stories: 22)
13. [human] On your own stack, after the Agent's plan correction in step 11, open that Contract's current Client Invoice draft as the Agent and confirm it bills the corrected plan. Sign in as the Manager and confirm the Contract page's corrections still work as before. (stories: 9, 25)

## Execution order

Two tickets. The prefactor touches the serial control and the goldens the
main ticket recaptures, so it goes first.

1. `serial-edit-matches-corrections`: `SmartphoneSerialControl`'s trigger becomes the `ghost` variant with the accessible name "Edit serial, {serial}" (model while no serial is set); its component test; the `manager-contract-fleet` golden recaptured. Modules: `components/fleet`, visual suite. No dependency. (stories: 27)
2. `move-corrections-to-the-agent`: `requireCanCorrect`'s Agent and Tester arms swapped, with its Javadoc, the `AuditLog` Javadocs and the route handlers' comments; `SmartphoneCorrectionApiTest` and `SimCardCorrectionApiTest` flipped, with the Agent-on-another-Contract, Agent money and Agent open-Return cases; the Agent's Fleet page passing `canCorrect`, `carriers` (one `loadActiveCarriers()` load) and `carriersHref`; the Tester's Fleet page dropping `canCorrect`, `carriers` and its catalog load; the table tests' renamed and added cases; the e2e flips in `fleet-management.spec.ts`; the `client-fleet` golden recaptured and the `agent-fleet` surface added; `PRODUCT.md`'s Agent and Tester lines and the declared terms. Modules: backend `security`, `logging`; `components/agent`, `components/client`, `components/fleet`, `app/agent`, `app/client`, `app/api`, e2e, visual suite. Depends on `serial-edit-matches-corrections`. (stories: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 28)
