---
id: isolate-e2e-database-for-verify
title: The verify command's e2e stage runs against its own throwaway database
status: in-progress
depends_on: []
labels: [enabler, backend, frontend, testing]
stories: []
---

## Context

The merge gate is red, and not because of this feature: its diff touches no
frontend file at all. `sdlc-merge-gate second-tenant-test-seam` reported
`verify: failed`, and the failing stage is `npm run test:e2e`.

`frontend/playwright.e2e.config.ts` defaults `E2E_DATABASE_URL` to
`postgres://…@127.0.0.1:5432/remote_support`, and its `webServer` starts
`scripts/run-backend-for-e2e.sh`, which runs `docker compose up -d postgres`
and points the backend at that same database. So the e2e suite — and
therefore every merge gate — drives Playwright against **the developer's live
docker-compose Postgres**.

That database accumulates state, and `docs/agents/implementer-notes.md`
already warns about exactly this: "`agent-invoice-submission-and-approval`
needs a fresh DB. Its state also persists across e2e runs against the same
container — re-running the full suite against a DB another run already wrote
to (e.g. Agent Invoice's one-per-calendar-month row) produces real flakes."

That is the observed failure. `agent-invoice-submission-and-approval.spec.ts`
failed on `expect(getByText('Draft')).toBeVisible()` — the Agent Invoice was
not a draft, because an earlier run had already sent and approved it.

The human chose, at this gate, to make the e2e stage use an isolated database
per run rather than drop it from `verify` or reset the shared one.

This is an `enabler`: it carries no user story and changes no product
behaviour. It exists so that `verify` means something.

## Acceptance criteria

- [ ] `verify` in `docs/agents/sdlc.json` runs the e2e suite against a **throwaway** database that the run creates and destroys, never the docker-compose `postgres` service on port 5432. Running it twice in a row passes twice, with no manual reset in between.
- [ ] Running the e2e stage leaves the developer's docker-compose Postgres **byte-identical**: same container, same volume, no rows added, changed or removed. Demonstrate this, do not assert it — for example by recording a checksum or row counts of a table the suite writes to, before and after.
- [ ] The backend under e2e takes the throwaway database through the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` environment variables that `backend/src/main/resources/application.yml:5-7` already reads, and the specs take it through `E2E_DATABASE_URL`. Both name the same database; neither falls back to a default that would reach port 5432.
- [ ] The throwaway Postgres, the backend and the frontend bind ports that do not collide with the developer's stack (which holds 5432, 8080 and 3000) or with a second run on the same machine. Say in the ticket result which ports were chosen and why they cannot collide.
- [ ] Teardown happens even when the suite fails: the container is removed by its exact name, and any process started is stopped by its exact PID. No broad `pkill`. After a deliberately failed run, nothing is left listening and no container remains.
- [ ] The committed `frontend/playwright.e2e.config.ts` keeps working as it does today for a developer running e2e by hand against docker-compose — this ticket adds an isolated path, it does not take the existing one away. If the config is changed, its existing comment explaining why it names the database explicitly stays true.
- [ ] The whole `verify` command from `docs/agents/sdlc.json` runs green end to end, from a clean checkout state, and its real output is in the ticket result.

## Tests

- **Seam:** the `verify` command itself, run for real. This ticket's product *is* the command, so the proof is running it — twice in succession, without resetting anything between.
- Cases: two consecutive full runs both green; a run whose e2e stage fails still tears down its container and processes; the docker-compose database unchanged across a run.

## Regression

- Every existing e2e spec under `frontend/tests/e2e` must keep passing — they are the suite being relocated, not rewritten. No spec's assertions may be weakened to make an isolated database work; if a spec depends on docker-compose seed data that the throwaway database lacks, that is a real finding to report, not something to paper over.
- `scripts/run-backend-for-e2e.sh` is used by developers by hand. If it changes, its documented behaviour for that use must be preserved.
- `backend/src/main` must not change. `application.yml` already reads the environment variables this ticket needs.

## Observability

The ticket result states the chosen ports, the container name, and the before/after evidence that the docker-compose database was untouched.
