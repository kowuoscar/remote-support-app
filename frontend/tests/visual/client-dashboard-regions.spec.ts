import { test, expect } from "@playwright/test";
import { gotoWithSession } from "./helpers";

// real-client-dashboard (ticket client-dashboard-fleet-and-requests): the Client dashboard's stat
// cards against tests/visual/stub-backend.mjs. No golden: these assert the figures a user reads,
// including the degraded states, where a failed read must never look like a low count.

test.describe("the Client dashboard's stat regions", () => {
  test("healthy-tester-counts-active-fleet-and-open-requests", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-session");

    // Smartphones Active x2 and In Repair x1 plus SIM Cards Active x3; the two Retired units are out.
    const fleet = page.getByTestId("active-fleet-stat");
    await expect(fleet).toContainText("6");
    await expect(fleet).toContainText("Smartphones + SIM Cards, all Contracts");
    // Pending Approval, Submitted and In Progress; Completed, Rejected and Cancelled are out.
    const requests = page.getByTestId("open-requests-stat");
    await expect(requests).toContainText("3");
    await expect(requests).toContainText("Pending Approval, Submitted or In Progress");
    await expect(page.getByTestId("dashboard-ready")).toBeVisible();
  });

  test("degraded-tester-shows-unavailable-never-zero", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-degraded-session");

    await expect(page.locator("header").first()).toContainText("Solstice Retail Group");
    const fleet = page.getByTestId("active-fleet-stat");
    await expect(fleet).toContainText("—");
    await expect(fleet).toContainText("Couldn't load your Fleet");
    await expect(fleet).not.toContainText("0");
    const requests = page.getByTestId("open-requests-stat");
    await expect(requests).toContainText("—");
    await expect(requests).toContainText("Couldn't load your Requests");
    await expect(requests).not.toContainText("0");
  });

  test("contract-list-failure-shows-both-stats-unavailable-never-zero", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-contracts-failing-session");

    await expect(page.locator("header").first()).toContainText("Solstice Retail Group");
    const fleet = page.getByTestId("active-fleet-stat");
    await expect(fleet).toContainText("—");
    await expect(fleet).toContainText("Couldn't load your Fleet");
    await expect(fleet).not.toContainText("0");
    const requests = page.getByTestId("open-requests-stat");
    await expect(requests).toContainText("—");
    await expect(requests).toContainText("Couldn't load your Requests");
    await expect(requests).not.toContainText("0");
  });
});
