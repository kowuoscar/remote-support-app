import { test, expect } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/** Every Manager figure is real, so no surface carries a "Demo data" footer. Non-golden assertion. */
test("manager-dashboard-shows-no-demo-footer", async ({ page }) => {
  await gotoWithSession(page, "/manager", "visual-manager-session");
  await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
  await expect(page.getByText("Demo data", { exact: false })).toHaveCount(0);
});
