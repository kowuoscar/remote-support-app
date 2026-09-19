---
id: smartphone-owner-and-optional-serial
title: A Smartphone has an Owner, no assignee, and a serial that can come later
status: done
depends_on: []
labels: [backend, frontend, fleet]
---

## Context

Fleet groundwork. Implements `spec.md` Solution (Fleet model: Owner, "assigned to" removed, optional serial) and user stories 18 (Owner part), 26 and 43.

## Acceptance criteria

- [x] A Smartphone has an Owner, Client or company; every existing Smartphone becomes company-owned
- [x] The Manager's add-Smartphone form asks for the Owner, defaulting to company
- [x] A Smartphone created by completing a Provision Smartphone Request or a proactive Provision Smartphone Fee is company-owned
- [x] "Assigned to" is gone from the API, every form and every Fleet table; every role still sees exactly the Smartphones it saw before
- [x] A Smartphone can be created without a serial; the Agent (own Contract) or the Manager can set or change the serial later from the Fleet page; a Tester cannot
- [x] Every Fleet table (Agent, Manager, Tester) shows the Owner, and "—" for a missing serial
- [x] Seed data holds both Owners

## Tests

- **API seam:** Owner defaults and round-trips; serial optional at creation; set-serial allowed for the Contract's Agent and the Manager, 403 for a Tester and another Agent, 404 other tenant; the migration defaults existing rows to company and drops "assigned to"; visibility per role unchanged.
- **E2E:** the Manager adds a Client-owned Smartphone without a serial; the Agent sets the serial from the Fleet page.

## Regression

Fleet visibility and Smartphone provisioning. `SmartphoneApiTest`, `FleetAccessGuard` cases and `fleet-management.spec.ts` must pass, changed only where they sent "assigned to".

## Observability

Audit event for a serial set or changed: Smartphone id, actor, tenant. Owner added to the Smartphone created and provisioned events.
