import { test, expect } from "@playwright/test";
import { SEEDED_USERS, createContractWithTester, login, logout, selectContractInSwitcher } from "./helpers";

/**
 * An Agent logs a Topup Fee from a Topup Option (carrier-catalog spec; topup-fee-from-option
 * ticket), driven against a real backend + Postgres at the accessibility-tree level. Mirrors
 * fee-logging-and-provisioning.spec.ts. The seeded agent@example.com login is the United States
 * Agent "Jordan Ellis", whose catalog seeds AT&T's "Prepaid Refill 25" at $25.00.
 */

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("topup fee from a topup option", () => {
  test("an agent logs a topup fee by picking an option and adjusting the amount", async ({ page }) => {
    const clientName = `Lumen Outfitters ${RUN_ID}`;
    const testerEmail = `ana.ortiz+${RUN_ID}@lumen.example`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createContractWithTester(page, clientName, testerEmail);
    await logout(page);

    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    await page.getByRole("button", { name: "Log a fee" }).click();
    const dialog = page.locator("dialog[open]");
    await dialog.getByLabel("Tester").selectOption({ label: testerEmail });
    await expect(dialog.getByLabel("Fee type")).toHaveValue("TOPUP");

    await dialog.getByLabel("Topup option (optional)").selectOption({ label: "AT&T — Prepaid Refill 25 · $25.00" });
    const amount = dialog.getByLabel("Amount (USD)");
    await expect(amount).toHaveValue("25.00");

    await amount.fill("27.50");
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      dialog.getByRole("button", { name: "Log fee" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);

    // The Fee shows the adjusted amount, not the Option's price.
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("cell", { name: "Topup" })).toBeVisible();
    await expect(page.getByText("$27.50", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("$25.00", { exact: true })).toHaveCount(0);
  });
});
