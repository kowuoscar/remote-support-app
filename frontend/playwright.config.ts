import { defineConfig } from "@playwright/test";

/**
 * Visual regression baseline for the three Operate surfaces (Manager
 * Console, Agent Console, Client Portal) and the Agent and Manager Carriers
 * pages, across light/dark theme and desktop/mobile breakpoint — 5 × 2 × 2 =
 * 20 golden screenshots, committed under tests/visual/__screenshots__/.
 */
export default defineConfig({
  testDir: "./tests/visual",
  snapshotPathTemplate: "{testDir}/__screenshots__/{arg}{ext}",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  expect: {
    toHaveScreenshot: {
      // Tight on purpose: this is a token-driven design system, so a real
      // regression (a color, a status badge, an amount) is often a small
      // fraction of a full-page image. NOTE: when intentionally updating
      // the baseline, delete the affected files under __screenshots__/
      // before re-running --update-snapshots — Playwright only rewrites a
      // snapshot that differs from the stored one by MORE than this
      // threshold, so a small-but-real content change (e.g. one status
      // badge changing) can silently fail to update an existing baseline
      // otherwise.
      maxDiffPixelRatio: 0.005,
      animations: "disabled",
    },
  },
  use: {
    baseURL: "http://127.0.0.1:4173",
  },
  webServer: [
    {
      // Deterministic data for backend-driven pages (the Carriers pages) — see the file itself.
      command: "node tests/visual/stub-backend.mjs",
      url: "http://127.0.0.1:4174/health",
      reuseExistingServer: !process.env.CI,
      timeout: 10_000,
    },
    {
      command: "npm run build && npm run start -- -p 4173",
      url: "http://127.0.0.1:4173",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: { BACKEND_URL: "http://127.0.0.1:4174" },
    },
  ],
});
