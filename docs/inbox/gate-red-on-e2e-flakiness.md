---
id: gate-red-on-e2e-flakiness
type: alert
status: open
blocks: [self-service-password-change]
created: 2026-09-22
---

## Question

`self-service-password-change` is finished and reviewed but **is not on
`main`**, because the merge gate returned `ok: false` with `verify: failed`
and **no blocking findings**. The feature branch
`feature/self-service-password-change` holds all of it, green in every stage
except the isolated e2e suite.

The e2e failure is environmental, not a regression. Isolating the stages on
the same tree: backend `mvn verify` 533 tests green, lint green,
`tsc --noEmit` green, vitest 186 green, `npm run build` green. Only the e2e
suite fails, and it fails differently every time:

| run | result | note |
|---|---|---|
| fixer, 1st | 58 passed, 1 failed | `manager-entity-setup` |
| fixer, 2nd | **59 passed, 0 failed** | same tree, no changes |
| gate | failed | |
| mine | 57 passed, 2 failed | `sim-swap-moves`, `tester-request-submission` |
| mine, again | 56 passed, 3 failed | **33.6 minutes**, against a normal 2.4 |

Every failure is a timeout waiting for an element to become "visible, enabled
and stable" — never an assertion mismatch — and always in a spec the diff does
not touch. No orphaned containers or agent processes were left behind; load
average was 6.5 and rising when I stopped. This machine has been running
Maven and Playwright stacks continuously for hours.

## Recommendation

**Re-run the gate on an idle machine**, from the repository root:

```
sdlc-merge-gate self-service-password-change
```

On `ok: true`, the feature merges and delivers normally. I did not merge on a
red gate, and did not re-run until it happened to pass, because both are how a
real regression gets through — this same feature already had one caught only
because a merger refused to accept "unrelated flake" without an experiment.

If it stays red on an idle machine, that is a different problem and the e2e
failures should be treated as real.

Two harness defects behind this are recorded in `docs/tech-debt.md`:

1. The e2e suite's result depends on what else the machine is doing. A suite
   like that cannot honestly gate a merge — it will either block good work or
   teach everyone to re-run until green.
2. `scripts/run-e2e-isolated.sh` ends `exec npx playwright test --config=…`
   with no `"$@"`, so passing a spec name silently runs the whole suite. That
   is how narrowing two failures cost a 33-minute run.

## Blocks

`self-service-password-change` only. Nothing else in the loop is waiting on
it, and the two epics behind it are unaffected.

## Meanwhile

Nothing — this was the session's last feature and its budget is spent.

## Answer
