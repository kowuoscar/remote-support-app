import { defineConfig } from "@playwright/test";

/**
 * Visual regression baseline for the three Operate surfaces (Manager
 * Console, Agent Console, Client Portal) across light/dark theme and
 * desktop/mobile breakpoint — 3 × 2 × 2 = 12 golden screenshots, committed
 * under tests/visual/__screenshots__/.
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
      maxDiffPixelRatio: 0.02,
      animations: "disabled",
    },
  },
  use: {
    baseURL: "http://127.0.0.1:4173",
  },
  webServer: {
    command: "npm run build && npm run start -- -p 4173",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
