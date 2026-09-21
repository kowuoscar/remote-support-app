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

- `frontend/playwright.e2e.isolated.config.ts` · smell: Incomplete teardown · `gracefulShutdown: SIGTERM` is set on the backend `webServer` entry but not on the frontend one, so Playwright's default SIGKILL can orphan a `next start` grandchild under `sh -c "npm run build && npm run start"`; one such process was found reparented to PID 1 and killed by exact PID during this feature's merge · second-tenant-test-seam · 2026-09-21
