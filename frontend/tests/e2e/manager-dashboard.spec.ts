import { test, expect } from "@playwright/test";
import { SEEDED_USERS, login } from "./helpers";

/**
 * The Manager dashboard against a real backend + Postgres (real-manager-dashboard spec; ticket
 * manager-dashboard-identity-and-counts). The e2e database is shared across specs, so the counts
 * are compared with the lists the API returns, never with absolute figures.
 */
test.describe("the Manager dashboard's identity and counts", () => {
  test("the chip names the signed-in login and the counts equal the Tenant's lists", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager");
    await expect(page.getByTestId("dashboard-ready")).toBeVisible();

    // The seeded Manager has no Manager name, so the chip falls back to the login.
    await expect(page.getByTestId("viewer-menu-trigger")).toContainText(`${SEEDED_USERS.manager.username} · Manager`);
    await expect(page.getByText("Priya Ashford")).toHaveCount(0);

    // The Clients, Agents and Contracts pages count the same three list routes ("N in this tenant").
    const lengths: Record<string, number> = {};
    for (const [key, path] of [
      ["clients", "/manager/clients"],
      ["agents", "/manager/agents"],
      ["contracts", "/manager/contracts"],
    ] as const) {
      await page.goto(path);
      const subtitle = await page.getByText(/^\d+ in this tenant$/).textContent();
      lengths[key] = Number(subtitle!.split(" ")[0]);
    }
    await page.goto("/manager");
    await expect(page.getByTestId("dashboard-ready")).toBeVisible();

    await expect(page.getByTestId("clients-stat")).toContainText("In this tenant");
    // The figure itself is the card's second span (label, value, meta).
    for (const [testId, expected] of [
      ["clients-stat", lengths.clients],
      ["agents-stat", lengths.agents],
      ["contracts-stat", lengths.contracts],
    ] as const) {
      await expect(page.getByTestId(testId).locator(":scope > span").nth(1)).toHaveText(String(expected));
    }
  });
});
