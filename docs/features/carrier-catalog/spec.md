---
feature: carrier-catalog
status: approved
date: 2026-09-18
---

# Carrier catalog

## Problem

An Agent works with a handful of local mobile carriers, each with its own
topup options and postpaid plans. The app knows none of them: a SIM Card's
carrier is optional free text, a Postpaid SIM's monthly fee is typed in by hand
every time one is created, and a Topup Fee's amount is free-typed from memory.
The same carrier ends up spelled three ways, the same plan ends up at three
prices, and nothing on a Client Invoice can be traced back to what the carrier
actually offers.

The upcoming Request-types feature makes this worse. A Tester will choose a
Topup Option when submitting a Topup, and a carrier and a plan when asking for a
Provision SIM. Neither choice is possible without a catalog to pick from.

## Goals / Non-goals

**Goals**

- One **Carrier** catalog per country, holding **Topup Options** and
  **Postpaid Plans**, each with a name and a price in that country's currency.
- The Agents of a country maintain their country's catalog. The Company Manager
  can see and edit every country's catalog.
- Catalog entries are archived, never deleted. An archived entry disappears
  from pickers but stays readable wherever it's already used.
- Every new SIM Card names a Carrier. Every new Postpaid SIM names a Postpaid
  Plan, and its monthly fee is copied from the Plan's price at that moment.
- When the Agent logs a Topup Fee, they can pick a Topup Option. The option
  suggests the amount, and the Agent can still adjust it.
- Existing SIM Cards are linked to Carriers created from the free-text carrier
  names already in use.

**Non-goals**

- Choosing a Topup Option or a Carrier and Plan when a **Request** is
  submitted. That is the Request-types feature.
- A Tester seeing the catalog. Testers get read access in the Request-types
  feature, when they need it.
- Re-pricing SIM Cards already on a Plan when the Plan's price changes. The
  copied monthly fee is final.
- Editing one SIM Card's monthly fee by hand. No such edit exists today, and
  this feature doesn't add one.
- Restoring an archived entry. Archiving is one-way; a mistaken archive is
  fixed by creating the entry again.
- Narrowing Topup Options to the carrier of a specific SIM Card. A Topup Fee
  isn't linked to a SIM Card yet (it becomes linked in the Request-types
  feature), so the picker lists every Topup Option in the Contract's country.

## User stories

1. As an Agent, I want a Carriers page listing my country's Carriers, so that I can see which carriers the app knows about.
2. As an Agent, I want to add a Carrier for my country, so that SIM Cards from that carrier can be recorded.
3. As an Agent, I want to rename a Carrier, so that a typo or rebrand doesn't linger.
4. As an Agent, I want to archive a Carrier my country no longer works with, so that nobody picks it for a new SIM Card.
5. As an Agent, I want to see a Carrier's Topup Options, so that I know what the carrier sells.
6. As an Agent, I want to add a Topup Option with a name and a price, so that I don't retype the amount on every Topup Fee.
7. As an Agent, I want to edit a Topup Option's name or price, so that the catalog follows the carrier's changes.
8. As an Agent, I want to archive a Topup Option, so that a discontinued offer can't be picked.
9. As an Agent, I want to see a Carrier's Postpaid Plans, so that I know what the carrier's plans cost.
10. As an Agent, I want to add a Postpaid Plan with a name and a monthly price, so that new Postpaid SIMs get the right monthly fee.
11. As an Agent, I want to edit a Postpaid Plan's name or price, so that new SIM Cards on it get the current price.
12. As an Agent, I want a Plan price change to leave SIM Cards already on that Plan untouched, so that past and in-progress Client Invoices don't move.
13. As an Agent, I want to archive a Postpaid Plan, so that nobody puts a new SIM Card on a plan the carrier no longer sells.
14. As an Agent, I want prices shown and entered in my country's currency, so that there's never any doubt about which currency a price is in.
15. As an Agent, I want to be unable to see or change another country's catalog, so that each country's catalog stays under the control of the people who know its carriers.
16. As an Agent, I want every other Agent of my country to share the same catalog, so that we don't each keep a copy of it.
17. As an Agent completing a Provision SIM Request, I want to pick a Carrier and, for a Postpaid SIM, a Postpaid Plan, so that the new SIM Card's carrier and monthly fee come from the catalog.
18. As an Agent completing a Provision SIM Request for a Postpaid SIM, I want the monthly fee filled in from the chosen Plan, so that I don't type it in.
19. As an Agent completing a Provision SIM Request for a Prepaid SIM, I want no Plan picker, so that I'm not asked for something that doesn't apply.
20. As an Agent logging a Topup Fee, I want to pick a Topup Option that pre-fills the amount, so that the Fee matches the carrier's price.
21. As an Agent logging a Topup Fee, I want to adjust the pre-filled amount, so that I can record what was actually paid when it differs.
22. As an Agent logging a Topup Fee, I want the option to be optional, so that I can still log a topup that matches no catalog entry.
23. As an Agent, I want archived Carriers, Topup Options and Postpaid Plans left out of every picker, so that I can't choose one by mistake.
24. As a Company Manager, I want a Carriers page with a country filter, so that I can see every country's catalog.
25. As a Company Manager, I want to create, edit and archive any country's catalog entries, so that I can step in when an Agent can't.
26. As a Company Manager creating a SIM Card on a Contract's Fleet page, I want to pick a Carrier and, for a Postpaid SIM, a Postpaid Plan from the Contract's country, so that the SIM Card matches the catalog.
27. As a Company Manager, I want existing SIM Cards linked to Carriers named after their old free-text carrier, so that the Fleet doesn't lose information.
28. As a Company Manager, I want an existing Postpaid SIM to keep its current monthly fee even though it has no Plan, so that no Client Invoice total moves because of the migration.
29. As a Company Manager, I want a SIM Card that references an archived Carrier or Plan to still show that Carrier's or Plan's name, so that the Fleet stays readable.
30. As a Company Manager, I want every catalog change recorded in the audit log with who made it, so that I can trace a surprising price back to its author.
31. As a Tester, I want the Fleet to keep showing my SIM Cards' carriers, so that nothing I already see goes missing.
32. As a developer, I want seed data with Carriers, Topup Options and Postpaid Plans for the seeded Agent's country, with existing seeded SIM Cards linked to them, so that local testing covers the catalog right away.

## Solution

A tenant-scoped **Carrier catalog**, keyed by Country (the existing fixed
Country list, which already fixes the currency). Each Carrier belongs to one
Country and owns a list of Topup Options and a list of Postpaid Plans. Carrier,
Topup Option and Postpaid Plan each carry `archivedAt` (empty while active).
Names are unique among active entries: a Carrier within its Country, an Option
or Plan within its Carrier. Prices are positive amounts. Currency is never
stored on a catalog entry; it is always the Country's currency.

**Why per-country, maintained by Agents.** Carriers and their prices are
local knowledge. The Manager keeps full rights as a fallback, which matches
their full rights on the Fleet, but day-to-day upkeep belongs to the people who
buy the topups.

**Why archive rather than delete.** SIM Cards and Fees will reference
catalog entries. Deleting would either break those references or force a
cascade that rewrites history.

**Why copy the price.** A Postpaid SIM's monthly fee is copied from its
Plan when the SIM Card is created, and stays as it is afterwards. The
alternative, re-pricing every SIM Card on a Plan from a given month (the
Standing amount pattern), was rejected for now: it adds a history table and
billing questions that carriers' repricing frequency doesn't justify yet.
A Topup Fee likewise stores its own amount, as it does today; the Option only
suggests it.

**Access**

| | Read catalog | Edit catalog |
|---|---|---|
| Company Manager | every Country | every Country |
| Agent | own Country only | own Country only |
| Tester | no (until Request-types) | no |

An Agent asking for another Country gets a 403. The list endpoints return
active entries by default and include archived ones on request, so the
Carriers page can show them and pickers can leave them out.

**API shape**, under the existing `/api` prefix and tenant scoping:

- Carriers: list by country, create, rename, archive.
- Topup Options and Postpaid Plans, nested under a Carrier: list, create,
  edit name/price, archive.

**SIM Card changes**

- The carrier becomes a reference to a Carrier. The free-text column is
  removed once the migration has moved its data.
- A SIM Card gains an optional reference to a Postpaid Plan.
- Creating a SIM Card, through any path, now needs a Carrier from the
  Contract's Country that isn't archived. A Postpaid SIM also needs a Postpaid
  Plan of that Carrier that isn't archived, and its monthly fee is taken from
  the Plan. The request no longer accepts a free-typed monthly fee. A Prepaid
  SIM rejects any Plan.
- Every SIM-creation path goes through the same validation: the Manager's
  Fleet page, completing a Provision SIM Request, and an Agent logging a
  Provision SIM Request or Fee proactively that starts at Completed.
- The SIM Card response carries the Carrier's id and name and the Plan's id and
  name, so a reader never needs the catalog.

**Fee changes**

- A Fee gains an optional reference to a Topup Option. It is allowed only on a
  Topup Fee. The Option must belong to a Carrier of the Contract's Country, and
  neither the Option nor its Carrier may be archived. The amount stays
  required and is whatever the Agent submits.

**Migration**

- For each Country, create one Carrier per distinct carrier name found on that
  Country's SIM Cards. Names are grouped case-insensitively after trimming,
  keeping the first spelling found. A SIM Card's Country is its Contract's
  Agent's Country.
- Link every SIM Card to its Carrier. SIM Cards with no carrier stay unlinked.
- Existing Postpaid SIMs get no Plan and keep their monthly fee.
- A SIM Card created before this feature may therefore have no Carrier or
  Plan. Only new SIM Cards need them.

**Seed data.** Add Carriers, with Topup Options and Postpaid Plans, for the
seeded Agent's Country. Link the seeded SIM Cards to them.

**Frontend**

- **Agent Carriers page.** Adds a Carriers item to the Agent's navigation. It
  lists the Agent's Country's Carriers, and each Carrier shows its Topup
  Options and Postpaid Plans, with create, edit and archive dialogs. Archived
  entries are hidden behind a "show archived" toggle.
- **Manager Carriers page.** The same page, in the Manager's navigation, with
  a Country filter.
- **Manager create-SIM dialog** (Contract Fleet page) and the **Agent's
  Provision SIM completion form.** The free-text carrier input and the monthly
  fee input become a Carrier picker. For a Postpaid SIM, a Plan picker appears
  and shows the monthly fee it sets, read-only.
- **Agent's log-Fee dialog.** For a Topup Fee, an optional Topup Option picker
  pre-fills the amount, which stays editable.
- **Fleet tables.** Carrier and Plan names are shown from the new
  references, and an archived entry is marked as archived.

## Design direction

The feature follows the committed `DESIGN.md`. It sets no new direction, and no
`DESIGN.md` change is expected. The MVP pinned that direction against the
Stripe reference (see the `remote-support-mvp` spec, Design direction). New
layouts reuse existing tokens and components: Card, Table, Badge, Dialog, Money,
EmptyState and SurfacePage. An archived entry uses the existing muted Badge.

Surfaces:

- Carriers page (Agent and Manager): **Operate**. The user maintains the
  catalog.
- Create-SIM dialog, Provision SIM completion form, log-Fee dialog: **Operate**.
  The user picks from the catalog while completing a task.
- Fleet tables: **Operate**. They show the new references in existing columns.

## Constraints

- Inherits every constraint in the `remote-support-mvp` spec: stack, tenant
  scoping, explicit currency, no payment integration.
- A catalog entry's currency is always its Country's currency. It is never
  stored and never chosen.
- An Agent reads and writes only their own Country's catalog. A Tester has no
  catalog access. Both are enforced in the API, not only hidden in the UI.
- Archiving is one-way. No endpoint deletes a Carrier, Topup Option or
  Postpaid Plan.
- Changing a Plan's price never changes any existing SIM Card's monthly fee.
  Changing a Topup Option's price never changes any existing Fee.
- The migration must not change any existing SIM Card's monthly fee or any
  Client Invoice total, whether in draft or frozen.

## Testing decisions

Tests assert external behaviour through the API and the browser, never entity
internals.

- **Backend API tests** (MockMvc over Testcontainers Postgres, on the
  `IntegrationTest` base). Prior art: `RequestApiTest` and `FeeApiTest`, with
  `OtherTenantFixture` for tenant isolation. Cover:
  - catalog CRUD and archive
  - the access matrix: Agent in their own Country versus another Country,
    Manager in any Country, Tester denied, other tenant invisible
  - uniqueness among active entries
  - archived entries leaving the default list
  - SIM Card creation on every path, requiring a Carrier (and a Plan for a
    Postpaid SIM) that isn't archived and is from the Contract's Country
  - the monthly fee copied from the Plan, and a later Plan price change
    leaving it unchanged
  - a Topup Fee keeping its submitted amount and referencing its Option, and
    an Option rejected on a non-Topup Fee
  - the migration's grouping and linking
- **Playwright end-to-end tests.** Prior art:
  `fee-logging-and-provisioning.spec.ts`. Cover:
  - an Agent adds a Carrier with a Postpaid Plan, then completes a Provision
    SIM Request by picking them
  - an Agent logs a Topup Fee from a Topup Option and adjusts the amount
  - a Manager creates a SIM Card from the catalog on the Contract Fleet page
- **Vitest component tests** only where a picker holds logic of its own, such
  as the Plan picker appearing only for a Postpaid SIM and showing the fee it
  sets. Prior art: `frontend/components/manager/*.test.tsx`.
- **Visual goldens** for the new Carriers page, per the frontend Definition of
  Done.

## Open questions

None

## Execution order

1. `agent-maintains-carriers`: Carriers exist; Agent and Manager Carriers pages; access matrix; seed.
2. `topup-options-and-postpaid-plans`: Topup Options and Postpaid Plans on the Carriers page. Blocked by 1.
3. `sim-card-carrier`: every new SIM Card names a Carrier; migration of the free-text carrier. Blocked by 1; can run in parallel with 2.
4. `postpaid-sim-plan`: a Postpaid SIM's monthly fee is copied from its Plan. Blocked by 2 and 3.
5. `topup-fee-from-option`: a Topup Fee is picked from a Topup Option. Blocked by 2; can run in parallel with 3 and 4.
