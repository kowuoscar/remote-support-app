---
id: accept-self-service-password-change
type: acceptance
status: open
blocks: []
created: 2026-09-22
---

## Question

`self-service-password-change` is delivered and on `main` (merge `ae12292`,
PR #18). Walk it through:
`docs/features/self-service-password-change/delivery.md`.

Eighteen of the twenty-three walkthrough steps were played by an agent with
evidence on disk. **Five are yours** — steps 19 to 23, including your own live
password change and the second-browser check that the other session survives
until it expires, which is the decision you took at the spec gate and the one
thing no agent can verify for you.

Two things worth your attention beyond the code:

1. **The gate was red for an hour, and it was not the code.** The e2e suite
   sheds unrelated specs as the machine loads — 0, 1, 2 then 3 failures across
   four runs on one unchanged tree, the last taking 33.6 minutes against a
   normal 2.4. On an idle machine it passed 59/59 first time. Both that and
   the argument-dropping bug in `run-e2e-isolated.sh` are in
   `docs/tech-debt.md`. A suite whose answer depends on what else is running
   cannot honestly gate a merge.
2. **`DialogShell` still renders children unconditionally.** That is the
   general form of the regression this feature caught and fixed in the
   consumer. The next dialog whose trigger lives in the shared shell
   reproduces it.

## Recommendation

Accept. The three blocking findings were fixed and independently re-confirmed
by the reviewers that raised them, never by their authors.

## Blocks

Nothing.

## Meanwhile

The session ended here at your request. `manager-resets-a-password` is next
when you resume.

## Answer
