---
id: merge-sdlc-handover-pr
type: approval
status: open
blocks: [second-tenant-test-seam, tenant-scoped-sign-in, login-lifecycle]
created: 2026-09-21
---

## Question

Merge https://github.com/kowuoscar/remote-support-app/pull/13
(`chore/sdlc-v2-handover` → `main`), which carries the sdlc harness, the
recorded intention, the epic plans, and the approved spec and tickets for
`second-tenant-test-seam`.

Nothing in it touches production source, schema or frontend — documentation,
agent rules and configuration only.

## Recommendation

Merge it. Feature work cannot start until it lands: the loop branches
`feature/*` from `main` and pushes only `feature/*` branches, never `main`.
Branching the first feature from anywhere else would put these seven commits
inside that feature's own pull request, alongside what would otherwise be a
two-file diff.

This was the human's own choice at planning, over merging into `main` locally
or turning `pull_requests` off.

## Blocks

Everything. `second-tenant-test-seam` has approved spec and ready tickets and
is waiting only on this; `tenant-scoped-sign-in` and `login-lifecycle` sit
behind it.

## Meanwhile

Planning, which needs no branch and no `main`. While this waited,
`invoice-correction-and-history` was explored and cut into three features.
What cannot proceed is any code: every implementer works in a worktree off
`feature/<feature>`, and that branch comes off `main`.

## Answer
