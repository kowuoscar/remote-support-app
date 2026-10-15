import { test, expect } from "@playwright/test";
import { createContractViaDialog, login, logout, readRevealedPasswordAndClose, SEEDED_USERS } from "./helpers";

/**
 * The single-Contract chip of the Contract Switcher truncates a long Client name instead of
 * pushing the page wider than a phone (client-invoices-side-list spec, "The Contract Switcher's
 * chip"; ticket switcher-chip-truncation). Driven against a real backend + Postgres.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("contract switcher chip", () => {
  test("a 60-character Client name truncates in the single-Contract chip at 390px with no sideways scroll", async ({
    page,
  }) => {
    // 60 characters, unique per run.
    const clientName = `Longname Holdings ${RUN_ID} `.padEnd(60, "x").slice(0, 60);
    expect(clientName).toHaveLength(60);
    const agentName = `Chip Person ${RUN_ID}`;
    const agentEmail = `chip.person+${RUN_ID}@agents.example`;

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);

    await page.goto("/manager/clients");
    await page.getByRole("button", { name: "Add client" }).first().click();
    await page.getByLabel("Client name").fill(clientName);
    await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
    await expect(page.getByRole("cell", { name: clientName })).toBeVisible();

    await page.goto("/manager/agents");
    await page.getByRole("button", { name: "Add agent" }).first().click();
    await page.getByLabel("Agent name").fill(agentName);
    await page.getByLabel("Country").selectOption("FRANCE");
    await page.getByLabel("Standing monthly salary").fill("1000");
    await page.getByLabel("Email").fill(agentEmail);
    await page.getByRole("dialog").getByRole("button", { name: "Add agent" }).click();
    const agentPassword = await readRevealedPasswordAndClose(page);

    await createContractViaDialog(page, clientName, `${agentName} · EUR`);

    await logout(page);
    await login(page, agentEmail, agentPassword);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/agent/requests");

    const chip = page.getByText(clientName).first();
    await expect(chip).toBeVisible();
    // Truncated: the label's text is wider than the box that shows it, and ends in an ellipsis.
    await expect.poll(() => chip.evaluate((el) => el.scrollWidth > el.clientWidth)).toBe(true);
    await expect(chip).toHaveCSS("text-overflow", "ellipsis");
    // The full name stays readable on hover.
    await expect(chip).toHaveAttribute("title", new RegExp(clientName));
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth === window.innerWidth))
      .toBe(true);
  });
});
