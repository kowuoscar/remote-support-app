import { defineConfig } from "@playwright/test";

/**
 * Frontend browser seam for the real login flow (auth-login-flow ticket): drives the app
 * against a real backend + Postgres. Separate testDir/config from playwright.config.ts (the
 * visual-regression goldens), which renders demo data with no backend running at all — mixing
 * the two would make the goldens depend on a database, and would make this suite pay for a full
 * production build it doesn't need.
 */
export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  // agent-invoice-submission-and-approval ticket: only the seeded Agent (agent@example.com /
  // "Jordan Ellis") has a login, so any spec exercising its own Agent Invoice — a resource keyed
  // by (Agent, calendar month), not something a test can spin up fresh like a Client Invoice's
  // Contract — necessarily shares that one row with every other spec that reads it (e.g.
  // agent-standing-amounts-and-invoice-generation.spec.ts). Running spec *files* in parallel
  // across workers let one file's send/override/approve interleave with another file's
  // before/after read of the same invoice, producing a real (observed) flaky failure. Serializing
  // the whole run removes that hazard for this and any future spec touching shared seeded state,
  // at the cost of overall suite runtime.
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  timeout: 30_000,
  use: {
    baseURL: "http://127.0.0.1:3100",
  },
  webServer: [
    {
      command: "../scripts/run-backend-for-e2e.sh",
      url: "http://127.0.0.1:8080/api/health",
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      // A production build, not `next dev`: dev-mode Turbopack hydration is slow enough
      // (unminified bundles, on-demand compilation, dev overlay) that a fast click on the login
      // button can beat React attaching its submit handler, falling through to the browser's
      // native GET submission — visible as credentials landing in the URL query string. A
      // production build hydrates fast enough that this isn't a practical concern, and this
      // suite should exercise the same build the visual-regression suite does.
      command: "npm run build && npm run start -- -p 3100",
      url: "http://127.0.0.1:3100/login",
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      env: { BACKEND_URL: "http://127.0.0.1:8080" },
    },
  ],
});
