# Tech debt

<!-- sdlc:template tech-debt 1 -->

Entries are opinions, not obligations — a later reader may dismiss one with
a reason instead of paying it. Debt on a module is paid when a later feature
touches that module, not on a schedule. The orchestrator is the single
writer, at delivery; nobody else appends here.

One level-2 heading per module path, one list line per entry: path in
backticks, `smell: <name>`, a note, the feature that recorded it, the date —
separated by ` · `.

## frontend

- `frontend/tests/e2e` · smell: Suite unreliable under load · the isolated e2e suite passes 59/59 on an idle machine but sheds unrelated specs as load rises — observed across four runs on one unchanged tree: 0, then 1 (`manager-entity-setup`), then 2 (`sim-swap-moves`, `tester-request-submission`), then 3, with the last run taking 33.6 minutes against a normal 2.4. Failures are timeouts waiting for an element to become "visible, enabled and stable", never assertion mismatches. A suite whose result depends on what else the machine is doing cannot gate a merge honestly: it will either block good work or teach everyone to re-run until green · self-service-password-change · 2026-09-22
- `scripts/run-e2e-isolated.sh` · smell: Arguments silently dropped · the script ends `exec npx playwright test --config=…` with no `"$@"`, so `npm run test:e2e:isolated -- some.spec.ts` silently runs the entire suite instead of the named spec. Costs a full 3-minute suite run every time someone tries to narrow a failure, and it looks like the filter simply did not match · self-service-password-change · 2026-09-22

- `frontend/lib/demo/agent.ts` · smell: Non-reproducible test data · request timestamps are fixed 2026-09 ISO dates while `frontend/app/agent/page.tsx` renders them through `formatRelativeAge`, which computes against wall-clock `new Date()` — so the Agent console's visual goldens drift ("5 days ago" becomes "7 days ago") and will spuriously change on any ticket that forces a recapture. Freeze the clock for that render or seed relative-safe dates · self-service-password-change · 2026-09-22
- `frontend/playwright.config.ts` · smell: A guard that hides what it should catch · `maxDiffPixelRatio: 0.005` makes `--update-snapshots` silently keep a stale baseline when the real diff is under threshold. Proved twice here: a full update run reported no rewrite for 32 genuinely-changed goldens, and `client-desktop-{light,dark}` were found already stale from an earlier feature, missing a log-out button nobody had noticed was gone · self-service-password-change · 2026-09-22
- `docs/agents/sdlc.json` · smell: Untested by the gate · `verify` runs lint, build, typecheck, unit tests and the isolated e2e suite, but **not** `npm run test:visual` — so no golden is checked by the merge gate, and visual correctness rests entirely on whoever happens to look. This is the orchestrator's own composition at init, not an implementer's doing · self-service-password-change · 2026-09-22

- `frontend/playwright.e2e.isolated.config.ts` · smell: Incomplete teardown · `gracefulShutdown: SIGTERM` is set on the backend `webServer` entry but not on the frontend one, so Playwright's default SIGKILL can orphan a `next start` grandchild under `sh -c "npm run build && npm run start"`; one such process was found reparented to PID 1 and killed by exact PID during this feature's merge · second-tenant-test-seam · 2026-09-21

## backend

- `backend/src/main/java/com/remotesupport/backend/web/TesterController.java` · smell: Business rule in a controller · the "one primary contact per Client" rule branches on a fresh `testerRepository.existsByClientIdAndPrimaryContactTrue` query and throws a domain conflict, which Backend rule 2 names; it predates this feature, which only changed the exception it throws, so it was recorded rather than fixed · globally-unique-usernames · 2026-09-22
- `backend/src/main/java/com/remotesupport/backend/web/AgentController.java` · smell: Transaction boundary on a controller · `@Transactional` sits on two controller methods, which Backend rule 3 forbids; found while re-reviewing this feature, pre-exists `main` and untouched by it · globally-unique-usernames · 2026-09-22
