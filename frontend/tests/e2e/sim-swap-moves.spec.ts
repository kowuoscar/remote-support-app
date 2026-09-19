import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addSimCard,
  addSmartphone,
  addTester,
  createClientAndContractWithSeededAgent,
  installSimCard,
  login,
  logout,
  selectContractInSwitcher,
  simCardsTable,
} from "./helpers";

/**
 * A SIM Swap Request's move/exchange details and its no-Agent-input completion effect
 * (sim-swap-moves ticket), driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/agent-request-fulfillment.spec.ts's pattern. The seeded agent@example.com
 * login resolves to the "Jordan Ellis" Agent (V5 migration), USD.
 */

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("sim swap moves", () => {
  test("a tester submits an exchange between two smartphones, the agent completes it, and both fleet rows swap", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Solene Cosmetics ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const serialA = `SN-A-${RUN_ID}`;
    const serialB = `SN-B-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 8", serialA);
    await addSmartphone(page, contractId, "iPhone 15", serialB);
    const numberA = `+1-555-A${RUN_ID}`;
    const numberB = `+1-555-B${RUN_ID}`;
    await addSimCard(page, contractId, numberA);
    await addSimCard(page, contractId, numberB);

    await installSimCard(page, contractId, numberA, `Pixel 8 — ${serialA}`);
    await installSimCard(page, contractId, numberB, `iPhone 15 — ${serialB}`);

    const testerEmail = `elise.fabron+${RUN_ID}@solene.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Request type").selectOption({ label: "SIM Swap" });
    await dialog.getByRole("radio", { name: /Exchange the SIM Cards/ }).check();
    await dialog.getByLabel("First SIM Card").selectOption({ label: `${numberA} — Verizon` });
    await dialog.getByLabel("Second SIM Card").selectOption({ label: `${numberB} — Verizon` });
    await dialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /SIM Swap/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");
    await row.getByRole("button", { name: "Mark Completed" }).click();
    await expect(row).toContainText("Completed");

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const smartphones = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      return (await response.json()) as { id: string; model: string; serial: string }[];
    }, contractId);
    const phoneA = smartphones.find((phone) => phone.serial === serialA)!;
    const phoneB = smartphones.find((phone) => phone.serial === serialB)!;

    const rowA = simCardsTable(page).getByRole("row", { name: new RegExp(numberA.replace(/\+/g, "\\+")) });
    const rowB = simCardsTable(page).getByRole("row", { name: new RegExp(numberB.replace(/\+/g, "\\+")) });
    await expect(rowA.getByLabel("Installed in")).toHaveValue(phoneB.id);
    await expect(rowB.getByLabel("Installed in")).toHaveValue(phoneA.id);
  });
});
