import { test, expect, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  createClientAndContractWithSeededAgent,
  addSimCard,
  login,
  logout,
  fleetTab,
  selectInSideList,
} from "./helpers";

/**
 * ui-side-lists-and-tabs, walkthrough step 3 (F23): a correction on the Agent's Fleet shows in its row
 * without a reload, even when the Agent moves on at once - to the other tab or to another Client - and
 * comes back. Driven in a real browser against the production build, because the fault lived between the
 * app's address write and Next's router, which no unit test sees.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const SHORT = { timeout: 3_000 };

function simTable(page: Page) {
  return page.locator("table").filter({ hasText: "Installed in" });
}

async function seedTwoClients(page: Page, number: string) {
  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  const first = `Race Alpha ${RUN_ID} ${number}`;
  const second = `Race Beta ${RUN_ID} ${number}`;
  const { contractId } = await createClientAndContractWithSeededAgent(page, first);
  await addSimCard(page, contractId, number, "Verizon", "POSTPAID", "Unlimited Welcome");
  const other = await createClientAndContractWithSeededAgent(page, second);
  await addSimCard(page, other.contractId, `${number}9`, "Verizon", "POSTPAID", "Unlimited Welcome");
  await logout(page);
  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  // A fresh page that has written nothing to its address yet: the race is there only before the first write.
  await page.goto(`/agent/fleet?contract=${contractId}`);
  await expect(simTable(page).locator("tbody tr").first()).toContainText(number);
  return { first, second };
}

async function correctPlanToPlus(page: Page, number: string) {
  const row = simTable(page).locator("tbody tr").first();
  await expect(row).toContainText("Unlimited Welcome");
  await row.getByRole("button", { name: `Edit plan for ${number}`, exact: true }).click();
  await row.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: "Unlimited Plus" });
  // No wait for the form to close: the Agent moves on the moment Save is clicked, while the write and
  // the page refresh that follows it are still on their way.
  await row.getByRole("button", { name: "Save", exact: true }).click();
}

test.describe("a Fleet correction followed at once by navigation", () => {
  test("save, switch to Smartphones and back: the SIM row shows the saved plan", async ({ page }) => {
    test.setTimeout(120_000);
    const number = "+1-555-0711";
    await seedTwoClients(page, number);
    await correctPlanToPlus(page, number);

    await fleetTab(page, "Smartphones");
    await fleetTab(page, "SIM Cards");

    const row = simTable(page).locator("tbody tr").first();
    await expect(row).toContainText("Unlimited Plus", SHORT);
    await expect(row.getByRole("cell", { name: "$80.00" })).toBeVisible(SHORT);
  });

  test("save, pick another Client and back: the SIM row shows the saved plan", async ({ page }) => {
    test.setTimeout(120_000);
    const number = "+1-555-0712";
    const { first, second } = await seedTwoClients(page, number);
    await correctPlanToPlus(page, number);

    await selectInSideList(page, "Clients", second);
    await selectInSideList(page, "Clients", first);

    const row = simTable(page).locator("tbody tr").first();
    await expect(row).toContainText("Unlimited Plus", SHORT);
    await expect(row.getByRole("cell", { name: "$80.00" })).toBeVisible(SHORT);
  });
});
