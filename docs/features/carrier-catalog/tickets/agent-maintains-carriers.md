---
id: agent-maintains-carriers
title: Agents maintain their Country's Carriers on a Carriers page
status: done
depends_on: []
labels: [backend, frontend, fleet]
---

## Context

This is the first slice of the catalog: Carriers exist and can be maintained,
before anything references them. It implements the parts of `spec.md`
Solution that cover the catalog shape, Access, API shape (Carriers only), Seed
data, and the Agent and Manager Carriers pages. It covers user stories 1-4,
14-16, 24-25 (Carriers only) and 30.

## Acceptance criteria

- [x] An Agent's navigation has a Carriers page that lists their own Country's active Carriers.
- [x] An Agent can create a Carrier, rename it and archive it. Each action updates the list in place.
- [x] A "show archived" toggle reveals archived Carriers, marked as archived. They have no edit or archive actions.
- [x] A Carrier name is unique among the active Carriers of its Country. A duplicate is refused with an inline error.
- [x] The Manager's navigation has the same Carriers page with a Country filter, and the Manager can create, rename and archive Carriers in any Country.
- [x] An Agent gets 403 when reading or writing another Country's Carriers. A Tester gets 403 on every Carrier route. Another tenant's Carrier id returns 404.
- [x] No route deletes a Carrier.
- [x] The seed data holds several Carriers for the seeded Agent's Country, one of them archived.

## Tests

- **API seam** (following `RequestApiTest`, and `OtherTenantFixture` for the
  tenant cases):
  - create, rename and archive
  - the default list leaves archived Carriers out, and the archived list
    includes them
  - a duplicate active name is refused, and reusing an archived Carrier's
    name is allowed
  - the full access matrix: Agent in their own and another Country, Manager
    in any Country, Tester, other tenant
- **E2E:**
  - An Agent adds a Carrier, renames it, archives it, and it disappears from
    the list. It reappears under "show archived".
  - The Manager switches the Country filter and sees that Country's Carriers.
- **Visual:** goldens for the Carriers page, for each theme × breakpoint.

## Regression

This ticket only adds new routes, pages and navigation entries, and changes no
existing behaviour. The existing Agent and Manager navigation must keep
working, which the existing e2e specs protect.

## Observability

Write audit log events for Carrier created, renamed and archived: Carrier id,
Country, actor, tenant, and the old and new name on a rename.
