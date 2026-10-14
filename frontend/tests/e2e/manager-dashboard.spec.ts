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

    // The chip names the Manager by their name, else their login. The shared e2e Manager is unnamed
    // until the rename test below runs, and a name can't be cleared, so the expectation comes from
    // the same read the page makes, keeping this test independent of order and of earlier runs.
    const me: { username: string; name?: string } = await page.evaluate(async () =>
      (await fetch("/api/me")).json(),
    );
    expect(me.username).toBe(SEEDED_USERS.manager.username);
    await expect(page.getByTestId("viewer-menu-trigger")).toHaveText(`${me.name || me.username} · Manager`);
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

  test("a Manager changes their name from the account menu and the Dashboard chip shows it", async ({ page }) => {
    const newName = `Yazan Abuhassan ${Date.now()}`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager");
    await expect(page.getByTestId("dashboard-ready")).toBeVisible();

    // Every Manager page's menu: Change name, Change password, Log out.
    await page.getByTestId("viewer-menu-trigger").click();
    await expect(page.getByRole("menuitem")).toHaveText(["Change name", "Change password", "Log out"]);
    await page.getByRole("menuitem", { name: "Change name" }).click();

    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();

    // A blank name is refused at the field.
    await dialog.getByLabel("Name").fill("");
    await dialog.getByRole("button", { name: "Change name" }).click();
    await expect(dialog.getByRole("alert")).toHaveText("Enter your name.");

    await dialog.getByLabel("Name").fill(newName);
    await dialog.getByRole("button", { name: "Change name" }).click();
    await expect(dialog).toBeHidden();

    // No sign-in in between: the chip shows the new name at once, and still after a reload.
    await expect(page.getByTestId("viewer-menu-trigger")).toHaveText(`${newName} · Manager`);
    await page.reload();
    await expect(page.getByTestId("viewer-menu-trigger")).toHaveText(`${newName} · Manager`);

    // The dialog reopens holding the saved name.
    await page.getByTestId("viewer-menu-trigger").click();
    await page.getByRole("menuitem", { name: "Change name" }).click();
    await expect(page.getByRole("dialog").getByLabel("Name")).toHaveValue(newName);

    // Other Manager pages keep the plain chip, with the same three-item menu.
    await page.goto("/manager/invoices");
    await expect(page.getByTestId("viewer-menu-trigger")).toHaveText("Manager");
    await page.getByTestId("viewer-menu-trigger").click();
    await expect(page.getByRole("menuitem")).toHaveText(["Change name", "Change password", "Log out"]);
  });
});
