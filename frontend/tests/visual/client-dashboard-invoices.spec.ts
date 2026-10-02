import { test, expect } from "@playwright/test";
import { gotoWithSession } from "./helpers";

// real-client-dashboard (ticket client-dashboard-latest-invoices): the Latest Client Invoice card
// against tests/visual/stub-backend.mjs. No golden: these assert what a Tester reads, including
// the degraded states.

test.describe("the Client dashboard's Latest Client Invoice card", () => {
  test("healthy-tester-rows-show-month-status-and-currency-total", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-session");

    const card = page.getByTestId("latest-invoices-card");
    await expect(card.getByText("Most recent month per Contract")).toBeVisible();
    const first = card.getByRole("listitem").filter({ hasText: "Solstice Retail Group — United States" });
    await expect(first).toContainText("August 2026");
    await expect(first).toContainText("Awaiting approval");
    await expect(first).toContainText("$1,284.50");
    const second = card.getByRole("listitem").filter({ hasText: "Solstice Retail Group — United Kingdom" });
    await expect(second).toContainText("August 2026");
    await expect(second).toContainText("Approved");
    await expect(second).toContainText("£912.00");
    await expect(card).not.toContainText("Draft");
    await expect(card).not.toContainText("No invoices yet");
    await expect(page.getByText("Demo data", { exact: false })).toHaveCount(0);
  });

  test("open-invoices-link-goes-to-client-invoices", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-session");

    const link = page.getByTestId("latest-invoices-card").getByRole("link", { name: "Open Invoices" });
    await expect(link).toHaveAttribute("href", "/client/invoices");
    await expect(page.getByRole("link", { name: "View all" })).toHaveCount(0);
  });

  test("degraded-tester-shows-invoices-unavailable-with-the-rest-of-the-page-intact", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-degraded-session");

    const card = page.getByTestId("latest-invoices-card");
    await expect(card).toContainText("Couldn't load your invoices");
    await expect(card).toContainText("Reload the page to try again.");
    await expect(card.getByText("Latest Client Invoice")).toBeVisible();
    await expect(card.getByRole("link", { name: "Open Invoices" })).toBeVisible();
    await expect(page.getByTestId("active-fleet-stat")).toBeVisible();
    await expect(page.getByTestId("open-requests-stat")).toBeVisible();
    await expect(page.locator("header").first()).toContainText("Solstice Retail Group");
  });

  test("contract-list-failure-shows-invoice-card-unavailable", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-contracts-failing-session");

    const card = page.getByTestId("latest-invoices-card");
    await expect(card).toContainText("Couldn't load your invoices");
    await expect(card).toContainText("Reload the page to try again.");
    await expect(card.getByRole("link", { name: "Open Invoices" })).toBeVisible();
    await expect(page.getByTestId("active-fleet-stat")).toBeVisible();
  });
});
