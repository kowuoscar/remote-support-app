---
feature: request-types-and-flow
status: draft
date: 2026-09-18
---

# Request types and flow

> Draft. Decisions below were settled in the 2026-09-18 grilling session;
> user stories, testing decisions and the full solution are still to be
> written. Depends on `carrier-catalog` shipping first.

## Problem

Every Request type shares a single blank form and a single flow.
- **No details.** A Tester can only pick a type, so the Agent has to go back
  and ask which phone, which SIM, which topup or which model.
- **No approval.** Costly Requests (provisioning, replacement) start without
  anyone approving them.
- **The Fleet falls out of date.** Completing a Request doesn't reliably
  update the Fleet: nothing records which SIM Card sits in which Smartphone,
  so a SIM Swap changes nothing.

## Goals / Non-goals

**Goals (settled)**

- The Request types are Reboot, Topup, SIM Swap, Provision Smartphone,
  Provision SIM, Replace Smartphone, Replace SIM and Other. Repair is removed
  and becomes Other. Return is added by `returns-and-agent-stock`, not here.
- Each type has its own required details at submission, plus an optional note
  on every type:

  | Type | Required at submission | Agent enters at completion |
  |---|---|---|
  | Reboot | which Smartphone | — |
  | Topup | which SIM Card, which Topup Option (from its Carrier) | — |
  | SIM Swap | one move (SIM Card → target Smartphone) or an exchange between two Smartphones | — |
  | Provision Smartphone | brand/model | — (serial optional) |
  | Provision SIM | prepaid/postpaid, Carrier; for a postpaid SIM, a Postpaid Plan; optional target Smartphone | SIM number |
  | Replace Smartphone | which Smartphone | — |
  | Replace SIM | which SIM Card | the new SIM's details |
  | Other | a description | — |

- A Request can only target Active units of its Contract's Fleet. Topup is
  allowed on any Active SIM Card, prepaid or postpaid.
- **Approval.** Provision Smartphone, Provision SIM, Replace Smartphone and
  Replace SIM start at Pending Approval, whether a Tester or an Agent raised
  them.
  - The Company Manager approves, which moves the Request to Submitted, or
    rejects it, which ends it at Rejected with a required reason.
  - The Manager approves or rejects only, and never edits the Request.
  - The Agent can see a Request that is Pending Approval but can't start it.
- **Fee-capable types:** Topup, Provision Smartphone, Provision SIM, Replace
  Smartphone, Replace SIM and Other.
- **No proactive Fees on approval types.** A Fee on an approval-required type
  is no longer auto-created proactively, because that would skip approval. The
  Agent logs the Request, it is approved, and the Fee is then logged against
  it. Topup and Other keep the proactive Fee.
- **Fleet changes on completion:**
  - **Provision Smartphone** creates a company-owned Smartphone from the
    requested model. The serial number becomes optional, and the Agent can
    fill it in later from the Fleet page.
  - **Provision SIM** needs the SIM's details before it can complete, and
    places the SIM Card in the target Smartphone, if one was named.
  - **Replace** retires the named unit and adds its replacement. A
    replacement SIM Card takes the old SIM Card's Smartphone.
  - **SIM Swap** updates which Smartphone each SIM Card is **Installed in**,
    instantly.
- **Installed in.** A SIM Card is Installed in at most one Smartphone, and a
  Smartphone holds at most two SIM Cards. The Agent and the Manager can also
  set a SIM Card's Smartphone by hand on the Fleet page.
- **Ownership.** A Smartphone has an Owner: the Client or the company. A SIM
  Card is always company-owned. The free-text "assigned to" field is removed,
  and Fleet visibility stays per Contract, so nobody loses sight of any unit.
- **Other.** Both Testers and Agents can submit it. It takes a description,
  needs no approval, and can carry a Fee.
- **Pending Requests page.** A new Manager page lists Requests that are
  Pending Approval, with a count on the Manager's dashboard. The Review Queue
  stays invoices-only.
- **Rollout of existing data:**
  - Requests that exist when this ships are left as they are: no retroactive
    approval, and missing details stay empty.
  - Existing Repair Requests and their Fees become Other, with the
    description "Repair".
  - New seed data covers every type and every status, including Pending
    Approval and Rejected, plus Installed-in links and Owners.

**Non-goals (settled)**

- A Tester cancelling their own Request.
- Return, Agent Stock and SIM Card cancellation, which belong to
  `returns-and-agent-stock`.
- Request states that differ by type, beyond Pending Approval and Rejected.

## User stories

To be written.

## Solution

To be written.

## Design direction

The feature follows the committed `DESIGN.md` and sets no new direction.
Pending Approval uses the warning Badge and Rejected the danger Badge. Every
surface is **Operate**: the Tester's submit dialog, the Agent's Requests view
and completion step, and the Manager's Pending Requests page.

## Constraints

Inherits `remote-support-mvp` and `carrier-catalog`.

## Testing decisions

To be agreed.

## Open questions

- Agree the test seams, and write the user stories and solution. Owner: the
  user, when this feature enters the pipeline.

## Execution order

To be filled when tickets are written.
