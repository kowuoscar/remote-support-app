import { test, expect } from "@playwright/test";
import { gotoWithSession } from "./helpers";

// real-agent-dashboard (ticket agent-dashboard-requests): the Agent dashboard's Request regions
// against tests/visual/stub-backend.mjs, whose Request `createdAt`s are relative to the stub's own
// clock. No golden: these assert the text a user reads, including the degraded states.

test.describe("the Agent dashboard's Request regions", () => {
  test("healthy-agent-counts-open-requests-and-lists-the-five-newest", async ({ page }) => {
    await gotoWithSession(page, "/agent", "visual-agent-session");

    // Submitted x2 and In Progress x2 are open; Completed and Pending Approval are not.
    await expect(page.getByTestId("open-requests-stat")).toContainText("4");
    await expect(page.getByText("Across all your Contracts")).toBeVisible();
    const rows = page.getByTestId("recent-requests").getByRole("listitem");
    await expect(rows).toHaveCount(5);
    await expect(rows.first()).toContainText("Topup · Aurora Retail Group — United States");
    await expect(rows.first()).toContainText("Raised by nadia.okafor@aurora.example · raised today");
    await expect(rows.first()).toContainText("Submitted");
    await expect(rows.nth(1)).toContainText("raised 1 day ago");
    await expect(rows.nth(3)).toContainText("Pending Approval");
    await expect(page.getByRole("link", { name: /Open queue/ })).toHaveAttribute("href", "/agent/requests");
  });

  test("degraded-requests-show-unavailable-never-a-dropped-contract-count", async ({ page }) => {
    await gotoWithSession(page, "/agent", "visual-agent-degraded-session");

    const openRequests = page.getByTestId("open-requests-stat");
    await expect(openRequests).toContainText("—");
    await expect(openRequests).toContainText("Couldn't load your Requests");
    await expect(page.getByTestId("recent-requests")).toHaveCount(0);
    // Once in the stat card's meta and once in the Recent Requests card.
    await expect(page.getByText("Couldn't load your Requests")).toHaveCount(2);
  });
});
