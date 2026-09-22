# Roadmap

<!-- sdlc:template roadmap-readme 1 -->

The epic order, worked top to bottom. Each item starts with the epic slug in
backticks, matching `docs/roadmap/<slug>.md`; the text after the em dash is
optional and only for a human skimming this list — the epic file is
authoritative on scope.

Ordered with the human at init (2026-09-21): the correctness defect first,
then the epic that shares its code, then new capability, then the surface fix.

1. `tenant-scoped-sign-in` — a second tenant can be operated without a user of one signing in to the other.
2. `login-lifecycle` — passwords can be changed and reset, and a login can be turned off.
3. `invoice-correction-and-history` — a Manager can send an invoice back with a reason, and browse finished ones.
4. `real-dashboards` — each role's home page shows their own real numbers.

Two epics are `proposed` and wait for the human to place them in this order,
or to drop them:

- `package-by-feature` — package the backend by feature rather than by layer.
  Asked for on 2026-09-22 and the norm for new code from that date; the
  question this list has to answer is whether it comes **before**
  `login-lifecycle` (refactor first, then build on the new shape) or after
  (more features onto the current layout, and a larger move later).
- `tenant-administration` — create and manage Tenants in the product instead
  of by hand-written `INSERT`. Promoted from `tenant-scoped-sign-in`'s
  `## Later` at its closure.
