---
id: proposal-after-tenant-scoped-sign-in
type: proposal
status: open
blocks: []
created: 2026-09-22
---

## Question

Four proposals from closing `tenant-scoped-sign-in`. They are gathered into
one item because they share a cause, and none of them blocks anything.

### 1. Three rules in `coding-standards.md` are false of this codebase

I installed that file at init from the plugin's Java/Spring stack template and
copied its rules verbatim. Independent reviewers have now cited three of them
as describing a project this isn't:

- **Rule 7** — "translated to HTTP status by one `@ControllerAdvice`". There is
  **no** `@ControllerAdvice` or `@RestControllerAdvice` anywhere in
  `backend/src/main`. Four controllers translate via their own
  `@ExceptionHandler`. The rule's *prohibition* (no ad hoc `ResponseEntity`
  from a `try/catch`) is obeyed everywhere and worth keeping; its
  *prescription* would block four existing controllers. Flagged twice, by two
  different reviewers.
- **Rule 13** — "package by feature, not by layer". The backend is packaged
  entirely by layer: `web`, `repository`, `dto`, `domain`, `security`,
  `logging`. Every diff in the repository violates it as written. Flagged
  twice.
- **Rule 10** — "constructor injection only, no field `@Autowired`". Written
  unqualified, but Spring test classes cannot practically constructor-inject,
  and the whole existing suite uses `@Autowired` fields.

**Proposal:** rewrite 7 to bless `@ExceptionHandler` alongside advice; scope 13
to new modules or delete it; add "in application beans" to 10. A rule nobody
can follow teaches reviewers to ignore the file.

**Recommendation:** do it. This is the highest-value item here — these rules
are consulted on every review, and two of them are already costing reviewer
attention.

### 2. Tickets keep omitting test files they only add methods to

Twice this epic, a merger refused a green, correct merge because
`sdlc-test-guard` flagged a test file the ticket's `## Regression` never named
— `IntegrationTest` in one feature, `AgentApiTest` in the other. Both diffs
were purely additive. Each cost a full merge round-trip.

The mergers were right to refuse: the gate is procedural, and an implementer
must not justify its own change to a shared test class. The defect is upstream,
in how tickets are written.

**Proposal:** `docs/agents/ticket-critic.md`'s R6 should require that a ticket
naming a test file in `## Tests` also names it in `## Regression` when the work
will add to an existing file.

**Recommendation:** do it. It is a one-line rule change that removes a repeated
two-agent round-trip.

### 3. Two pre-existing defects found, recorded as debt rather than fixed

Both are in `docs/tech-debt.md`, both pre-date this epic and were found by
reviewers working on something else:

- `TesterController` branches on domain state for the "one primary contact per
  Client" rule — the shape Backend rule 2 names.
- `AgentController` carries `@Transactional` on two controller methods, which
  Backend rule 3 forbids.

**Proposal:** leave them as debt, to be paid by whoever next touches those
controllers. **Recommendation:** agreed — neither is a live defect, and a
refactoring epic for two entries would be disproportionate.

### 4. `tenant-administration` is now a `proposed` epic

Promoted from this epic's `## Later`: a Tenant is still created by a
hand-written `INSERT`, and the SuperAdmin role has no screens. Whether that
deserves building depends on how often you actually create a Tenant.

**Recommendation:** decide it when you next create one. If the honest answer
is "rarely, and psql is fine", drop the epic and say so in it.

## Recommendation

Take 1 and 2; they are cheap and they are already costing agent time. Leave 3
as debt. Park 4 until you next create a Tenant.

## Blocks

Nothing. The loop continues into `login-lifecycle` regardless.

## Meanwhile

`login-lifecycle`, the next `planned` epic, already cut into three features:
self-service password change, Manager reset for Agents and Testers, and
deactivating a login.

## Answer
