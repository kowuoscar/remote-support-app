import { test, expect, type Page } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/**
 * The "Demo data" footer is opt-in (surface-demo-note-opt-in): only the Manager dashboard, the one
 * surface still showing synthetic figures, sets `demoData` on `SurfacePage`. Non-golden assertions;
 * the recaptured full-page goldens evidence the other surfaces. `/client` is deliberately not
 * visited here: later tickets move it onto a Tester session.
 */
const footer = (page: Page) => page.getByText("Demo data", { exact: false });

test("manager-dashboard-shows-the-demo-footer", async ({ page }) => {
  await gotoWithSession(page, "/manager", "visual-manager-session");
  await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
  await expect(footer(page)).toBeVisible();
});

test("agent-dashboard-shows-no-demo-footer", async ({ page }) => {
  await gotoWithSession(page, "/agent", "visual-agent-session");
  await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
  await expect(footer(page)).toHaveCount(0);
});

test("manager-carriers-shows-no-demo-footer", async ({ page }) => {
  await gotoWithSession(page, "/manager/carriers", "visual-manager-session");
  await page.getByTestId("surface-main").waitFor({ state: "visible" });
  await expect(footer(page)).toHaveCount(0);
});
