---
id: accept-second-tenant-test-seam
type: acceptance
status: open
blocks: []
created: 2026-09-21
---

## Question

`second-tenant-test-seam` is delivered and on `main` (merge `159df08`, PR #15).
Walk it through:
`docs/features/second-tenant-test-seam/delivery.md`.

Seven of the eight walkthrough steps were played by an agent with evidence on
disk. **Step 8 is yours**: read
`backend/src/test/java/com/remotesupport/backend/web/CollidingUsernameSignInApiTest.java`
and confirm it pins the defect you actually care about, and that deleting it
when `globally-unique-usernames` lands is right.

Two things in the report deserve your attention more than the code does:

1. **The defect is not what the epic predicted.** A username in two Tenants
   gets **401 Unauthorized**, not a sign-in to the wrong Tenant. It is a
   lockout, not a cross-tenant leak — nobody reaches another Tenant's data,
   the affected user just cannot sign in and cannot tell it from a wrong
   password. The epic is corrected; `globally-unique-usernames` will be
   written against that.
2. **`verify` changed, so every future merge is checked differently.** Its
   e2e stage now runs against a throwaway database instead of your
   docker-compose Postgres, which it had been mutating on every gate run.
   That was a decision taken at the gate, and it is listed under Decisions
   taken alone for you to veto.

## Recommendation

Accept. The feature does what its spec asked and nothing more, and the one
blocking review finding — a tenant-isolation assertion that could never fail —
was fixed and independently re-confirmed.

## Blocks

Nothing. The loop never waits on a walkthrough.

## Meanwhile

`globally-unique-usernames` is next: the spec phase for the second feature of
`tenant-scoped-sign-in`.

## Answer
