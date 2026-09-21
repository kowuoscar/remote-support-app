---
id: accept-globally-unique-usernames
type: acceptance
status: open
blocks: []
created: 2026-09-22
---

## Question

`globally-unique-usernames` is delivered and on `main` (merge `d47a587`,
PR #17). It closes the `tenant-scoped-sign-in` epic. Walk it through:
`docs/features/globally-unique-usernames/delivery.md`.

Nine of the twelve walkthrough steps were played by an agent with evidence on
disk. **Three are yours**: read the inline dialog copy in the running app,
read and judge the `## Audit` section, and judge whether the refusal discloses
no more than you accepted.

Three things deserve your attention more than the code:

1. **A real bug was found in review, not by the tests.** Postgres `btrim`
   strips only the ASCII space — never a tab or a non-breaking space — while
   Java's `String.strip()` strips every Unicode whitespace character. A
   tab-padded username was normalised one way by the application and another
   by the index enforcing uniqueness, so the two disagreed about what counts
   as the same username. `Username.trim` now matches the database exactly.
2. **Reverting this one is not symmetrical.** `git revert` removes the code
   but not the migration: `uq_users_username_global` stays in any database
   that ran V55, so a reverted deployment keeps enforcing global uniqueness
   while the application stops pre-checking it. Dropping the index would be a
   new forward migration. The delivery report says so under How to undo.
3. **Two debt entries are pre-existing problems this work uncovered**, not
   things it caused: a business rule branching in `TesterController`, and
   `@Transactional` on two `AgentController` methods, which Backend rule 3
   forbids.

## Recommendation

Accept. The feature does what its spec asked; the three blocking review
findings were fixed and independently re-confirmed by the reviewers that
raised them, never by their authors.

## Blocks

Nothing. The loop never waits on a walkthrough.

## Meanwhile

The epic closes, and `login-lifecycle` is next — already planned into three
features.

## Answer
