import { defineConfig } from "@playwright/test";

/**
 * Isolated counterpart to playwright.e2e.config.ts (isolate-e2e-database-for-verify ticket): runs
 * the exact same specs (frontend/tests/e2e) against a throwaway Postgres + backend + frontend
 * stack that this run creates and destroys, so `verify` (docs/agents/sdlc.json) never touches the
 * developer's docker-compose Postgres on 5432, never shares a backend/frontend port with it
 * (8080/3000), and never accumulates state a later run could trip over.
 *
 * Always invoked through `npm run test:e2e:isolated` (scripts/run-e2e-isolated.sh), which sets the
 * four env vars read below to a fresh port/container name every run — this file throws immediately
 * if run any other way, on purpose: an isolated run that fell back to a default port/database would
 * reintroduce exactly the collision this ticket exists to remove.
 */
function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `${name} is not set. Run the isolated e2e suite via "npm run test:e2e:isolated" ` +
        "(scripts/run-e2e-isolated.sh), which sets it to a throwaway port/container every run.",
    );
  }
  return value;
}

const pgPort = requiredEnv("E2E_ISOLATED_PG_PORT");
const backendPort = requiredEnv("E2E_ISOLATED_BACKEND_PORT");
const frontendPort = requiredEnv("E2E_ISOLATED_FRONTEND_PORT");
const pgContainer = requiredEnv("E2E_ISOLATED_PG_CONTAINER");

// The same throwaway database this run's backend is pointed at below via DB_URL/DB_USERNAME/
// DB_PASSWORD (run-backend-for-e2e-isolated.sh) — the two specs that write fixtures directly
// (create-login-for-existing-agent, manager-invoice-review-queue) go through this instead. Never
// falls back to docker-compose's database: requiredEnv above already threw if pgPort is missing.
process.env.E2E_DATABASE_URL = `postgres://remote_support:remote_support@127.0.0.1:${pgPort}/remote_support`;

export default defineConfig({
  testDir: "./tests/e2e",
  // Same diagnosed cold-start flake playwright.e2e.config.ts guards against — see its own comment
  // and global-setup.ts's Javadoc-style comment for the full explanation.
  globalSetup: "./tests/e2e/global-setup.ts",
  fullyParallel: false,
  // Same shared-seeded-state hazard as playwright.e2e.config.ts (the Agent Invoice singleton) —
  // see its own comment.
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  timeout: 30_000,
  use: {
    baseURL: `http://127.0.0.1:${frontendPort}`,
  },
  webServer: [
    {
      // Starts the throwaway Postgres (plain `docker run`, not docker-compose) and the backend
      // against it, in one script — see run-backend-for-e2e-isolated.sh for why both live in one
      // process. `reuseExistingServer: false` unconditionally: a throwaway port has nothing
      // meaningful to reuse, and reusing would defeat "two consecutive runs never share state".
      command: `../scripts/run-backend-for-e2e-isolated.sh "${pgContainer}" "${pgPort}" "${backendPort}"`,
      url: `http://127.0.0.1:${backendPort}/api/health`,
      timeout: 180_000,
      reuseExistingServer: false,
      // Docker's own container-lifecycle contract: stopping a container needs SIGTERM, not
      // SIGKILL (Playwright's default) — see run-backend-for-e2e-isolated.sh's own comment on its
      // trap. Give Spring Boot + `docker stop` real time to shut down cleanly rather than being
      // killed mid-teardown.
      gracefulShutdown: { signal: "SIGTERM", timeout: 10_000 },
    },
    {
      // A production build, not `next dev` — same reasoning as playwright.e2e.config.ts (fast
      // hydration matters for the login-button race it documents).
      command: `npm run build && npm run start -- -p ${frontendPort}`,
      url: `http://127.0.0.1:${frontendPort}/login`,
      timeout: 120_000,
      reuseExistingServer: false,
      env: { BACKEND_URL: `http://127.0.0.1:${backendPort}` },
    },
  ],
});
