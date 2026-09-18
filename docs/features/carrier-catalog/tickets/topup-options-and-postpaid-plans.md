---
id: topup-options-and-postpaid-plans
title: Maintain a Carrier's Topup Options and Postpaid Plans
status: done
depends_on: [agent-maintains-carriers]
labels: [backend, frontend, fleet]
---

## Context

A Carrier gets its offers: Topup Options and Postpaid Plans, each with a name
and a price in the Country's currency. It implements `spec.md` Solution (the
catalog shape and API shape for Topup Options and Postpaid Plans) and user
stories 5-11, 13-14 and 25.

## Acceptance criteria

- [x] On the Carriers page, each Carrier shows its active Topup Options and Postpaid Plans, with their prices in the Country's currency.
- [x] An Agent (own Country) or the Manager (any Country) can create a Topup Option or a Postpaid Plan, edit its name and price, and archive it.
- [x] A price must be a positive amount. A currency is never entered and never stored.
- [x] A name is unique among the active Options, or the active Plans, of the same Carrier.
- [x] Nothing can be added to an archived Carrier. Its Options and Plans are hidden along with it.
- [x] Archived Options and Plans appear only under "show archived", marked as archived.
- [x] The access matrix is the same as for Carriers: an Agent in another Country or a Tester gets 403, and another tenant's id returns 404.
- [x] No route deletes an Option or a Plan.
- [x] The seed data gives each seeded active Carrier a few Topup Options and Postpaid Plans, with at least one of each archived.

## Tests

- **API seam:**
  - create, edit, archive
  - a zero or negative price is refused
  - a duplicate active name is refused
  - adding to an archived Carrier is refused
  - the default and archived lists
  - the access matrix and the tenant cases
- **Component seam:** only if the price input has logic of its own, such as
  showing the Country's currency.
- **E2E:** An Agent adds a Topup Option and a Postpaid Plan to a Carrier,
  edits the Plan's price, and archives the Option.

## Regression

The Carrier routes and page from `agent-maintains-carriers` must keep working,
which its API tests and e2e protect.

## Observability

Write audit log events for Topup Option and Postpaid Plan created, edited and
archived: entry id, Carrier id, actor, tenant, and the old and new name and
price on an edit.
