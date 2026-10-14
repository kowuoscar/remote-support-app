# Roadmap

<!-- sdlc:template roadmap-readme 1 -->

The epic order, worked top to bottom. Each item starts with the epic slug in
backticks, matching `docs/roadmap/<slug>.md`; the text after the em dash is
optional and only for a human skimming this list — the epic file is
authoritative on scope.

Ordered with the human at init (2026-09-21): the correctness defect first,
then the epic that shares its code, then new capability, then the surface fix.

1. `fleet-corrections` — a Manager or a Tester corrects a mistyped SIM number, Postpaid plan, Smartphone model or owner.
2. `agent-client-invoices-side-list` — on desktop, the Agent picks a Client's invoice from a side list instead of a dropdown.
3. `tenant-scoped-sign-in` — a second tenant can be operated without a user of one signing in to the other.
4. `login-lifecycle` — passwords can be changed and reset, and a login can be turned off.
5. `invoice-correction-and-history` — a Manager can send an invoice back with a reason, and browse finished ones.
6. `real-dashboards` — each role's home page shows their own real numbers.
7. `package-by-feature` — package the backend by feature rather than by layer.
8. `frontend-cleanup` — pay down the frontend debt review keeps catching: a text-size lint rule, a busy button that keeps focus, then duplicated layouts and styles.
9. `tenant-administration` — create and manage Tenants in the product instead of by hand-written `INSERT`.

Both placed at the end by the human on 2026-09-30: new capability first, the
layout move and tenant administration after. `frontend-cleanup` placed by the
human on 2026-10-08, right after `package-by-feature`, from the
`login-lifecycle` retro.

`fleet-corrections` and `agent-client-invoices-side-list` placed first by the
human on 2026-10-14, raised while testing a production-like stack.

Proposed, waiting for the human to place or drop:

- `login-onboarding` — change a login's email, force a password change on first sign-in; invitation emails need a mail service decision first. Promoted from `login-lifecycle`'s `## Later` (2026-10-06).
