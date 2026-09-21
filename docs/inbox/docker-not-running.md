---
id: docker-not-running
type: alert
status: open
blocks: []
created: 2026-09-21
---

## Question

Docker is not running on this machine, so the `verify` command in
`docs/agents/sdlc.json` cannot pass: the backend's integration tests start
their Postgres through Testcontainers, and the frontend e2e suite starts one
through `scripts/run-backend-for-e2e.sh` (`docker compose up -d postgres`).
Until Docker is up, the merge gate stays shut for every feature.

## Recommendation

Start Docker Desktop. No repository change is warranted — this is a machine
state, not a defect, and the suites are correct to refuse to run without a
real database.

Proof, from a full run at init:

- `mvn -f backend/pom.xml verify` → `Tests run: 43, Failures: 0, Errors: 43`,
  every one of them `ExceptionInInitializerError` caused by
  `IllegalStateException: Could not find a valid Docker environment`, each
  failing in ~0.003s (i.e. before touching any application code).
- `docker info` → exits non-zero.

The rest of `verify` passes on a clean tree and needs no Docker:

- `npm run lint` → exit 0
- `npm run build` → exit 0
- `npx tsc --noEmit` → exit 0
- `npm test` (Vitest) → `Test Files 32 passed (32)`, `Tests 168 passed (168)`

One caveat found while proving this: `npx tsc --noEmit` fails against a stale
`.next` directory, because `frontend/tsconfig.json` includes
`.next/types/**` and `.next/dev/types/**`, and a stale
`.next/dev/types/validator.ts` cites route files that no longer exist
(`app/api/agents/[agentId]/invoice/approve|override|paid`). `rm -rf .next`
followed by `npm run build` clears it. This is why `verify` runs
`npm run build` before `npx tsc --noEmit`.

## Blocks

Nothing is specced or in flight yet, so no feature is frozen today. The first
feature to reach delivery will be, unless Docker is running by then.

## Meanwhile

The loop continues into the `intention` phase, which writes `docs/journeys.md`
and needs no test run.

## Answer
