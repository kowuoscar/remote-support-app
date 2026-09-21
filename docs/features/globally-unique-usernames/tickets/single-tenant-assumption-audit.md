---
id: single-tenant-assumption-audit
title: Record the single-tenant-assumption audit that closes the epic
status: in-progress
depends_on: [refuse-taken-username-on-agent-login, refuse-taken-username-on-tester-login]
labels: [docs]
stories: [14]
---

## Context

`spec.md` Solution ("The audit") and Journeys ("It is the second and last
feature of that epic and closes it, together with the audit the epic asks
for"). `docs/roadmap/tenant-scoped-sign-in.md`'s epic closes on this
evidence, not only on the fix, so it is written last, against the codebase
this feature actually leaves behind — depending on both other fix tickets
so the sweep describes the finished state, not a mid-branch one.

The sweep is of every place that looks up a row without scoping it to a
Tenant — a broader question than this feature's own fix. Three findings are
known going in and must appear, each with the reasoning already given in
spec.md: `UserRepository.findByUsername` (the sign-in lookup — fixed by this
feature, since the global index means at most one row can ever match any
spelling now); `UserRepository.findByAgentId` (safe, left alone — a UUID
does not collide across Tenants); `CallerIdentityResolver`'s
`findById(principal.userId())` (safe, left alone, same reasoning). A useful
starting point for anything beyond those three: grep `repository` for a
derived-query method or a hand-written `@Query` whose name or JPQL does not
mention `tenantId`/`TenantId`.

## Acceptance criteria

- [ ] `delivery.md` gains an `## Audit` section listing every finding the sweep turns up, each named by its class and method (or query).
- [ ] The three known findings appear by name — `UserRepository.findByUsername`, `UserRepository.findByAgentId`, `CallerIdentityResolver`'s `findById(principal.userId())` — each with the disposition and reasoning spec.md already gives.
- [ ] Every finding in the section is tagged with exactly one of three dispositions: fixed-by-this-feature, safe-as-is, or recorded-as-debt — each with one sentence of reasoning, so a reader can enumerate the section's findings by disposition without interpreting prose.
- [ ] If the sweep turns up nothing beyond the three known findings, the section says so explicitly (e.g. "the sweep found no further instance"), rather than silently listing only the three and leaving the reader to guess whether more were looked for.
- [ ] Any finding not fixed inside this feature is recorded as debt with a stated reason it was left — never left unmentioned.

## Tests

- **Seam:** the delivery report itself, read by a human at the epic's close — spec.md's walkthrough step 11 ([human]): "confirm every remaining single-tenant assumption is named with what was done about it, including `findByAgentId` and `CallerIdentityResolver`."
- Cases (each checkable by reading the section, not by running anything):
  - The three known findings are present, named, and each carries one of the three dispositions.
  - Any additional finding the sweep turns up carries a disposition and a reason.
  - The section states outright whether the sweep found anything beyond the three known findings.
- No backend or frontend test is added or changed by this ticket — it is prose, not behaviour.

## Regression

N/A — no code is touched. `delivery.md` is a report read by a human, not
consumed by any test or by application code.

## Observability

N/A — a written report, not a deployed behaviour; nothing here runs in an
environment for anything to observe.
