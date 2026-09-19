---
feature: returns-and-agent-stock
status: draft
date: 2026-09-18
---

# Returns and Agent Stock

> Draft. Decisions below were settled in the 2026-09-18 grilling session;
> user stories, testing decisions and the full solution are still to be
> written. Depends on `request-types-and-flow`.

## Problem

A unit leaves a Client's Fleet only when someone edits the Fleet by hand, and
no Request records why it left. Company-owned units that could serve another
Client have nowhere to go, and a cancelled Postpaid SIM has no recorded date
at which its billing stops.

## Goals / Non-goals

**Goals (settled)**

- A **Return** Request type names which Smartphone(s) and SIM Card(s) are
  leaving the Fleet.
- **Disposition:**
  - A Client-owned Smartphone is always posted back to the Client, and needs
    no approval.
  - A company-owned Smartphone is either posted back to the company or kept in
    the Agent's Stock.
  - A SIM Card is either cancelled or kept in the Agent's Stock.
- A Return that holds any company-owned unit starts at Pending Approval. The
  Company Manager picks each unit's Disposition when approving.
- A SIM Card cancellation needs the Agent to record the effective cancellation
  date.
- **Billing a cancelled Postpaid SIM.** It is billed its full monthly fee for
  every month it was active on any day, with no part-month charges, and drops
  out from the month after its cancellation date.
- **Agent Stock.** Company-owned units an Agent holds that belong to no
  Contract. An Agent may fulfil a Provision or Replace Request from their
  Stock instead of acquiring a new unit. If they don't pick one, a new unit is
  created.

**Non-goals**

To be written.

## User stories

To be written.

## Solution

To be written.

## Design direction

The feature follows the committed `DESIGN.md`. Every surface is **Operate**.

## Constraints

Inherits `remote-support-mvp`, `carrier-catalog` and `request-types-and-flow`.

## Testing decisions

To be agreed.

## Open questions

- Agree the test seams, and write the user stories and solution. Owner: the
  user, when this feature enters the pipeline.
- How a Client Invoice's live base amount finds Postpaid SIMs that were
  cancelled earlier in the billing month, given that it currently counts only
  SIMs that are Active when the invoice is viewed. Owner: to be settled during
  this feature's spec.

## Execution order

To be filled when tickets are written.
