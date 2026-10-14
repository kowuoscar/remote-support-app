import { test, expect, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addTester,
  createClientAndContractWithSeededAgent,
  createUnrelatedContract,
  login,
  logout,
  readRevealedPasswordAndClose,
  selectContractInSwitcher,
} from "./helpers";

/**
 * Fleet management (fleet-management ticket), driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/manager-entity-setup.spec.ts's pattern. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration) — the only Agent login that exists, so
 * every Contract exercised from the Agent side uses that Agent.
 */

// Date.now() alone can collide across spec files: Playwright's collection phase can
// import several spec files within the same millisecond, and more than one file in this
// suite picks the same literal client name (e.g. "Aurora Retail Group") for its first
// test, so an exact RUN_ID match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("fleet management", () => {
  test("manager adds a smartphone and a SIM card to a contract's fleet", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await expect(page).toHaveURL(/\/manager$/);

    const clientName = `Aurora Retail Group ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);

    await page.getByRole("button", { name: "Add smartphone" }).first().click();
    await page.getByLabel("Model", { exact: true }).fill("iPhone 14");
    await page.getByLabel("Serial").fill(`SN-${RUN_ID}`);
    await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
    await expect(page.getByRole("cell").filter({ hasText: "iPhone 14" })).toBeVisible();
    await expect(page.getByRole("row", { name: /iPhone 14/ })).toContainText("Active");

    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number", { exact: true }).fill(`+1-555-${RUN_ID}`);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("POSTPAID");
    // postpaid-sim-plan ticket: the monthly fee comes from a Postpaid Plan of the chosen Carrier.
    await page.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: "Unlimited Welcome" });
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("cell").filter({ hasText: `+1-555-${RUN_ID}` })).toBeVisible();
    // sim-card-carrier ticket: the Carrier was picked from the catalog, and the Fleet names it.
    const simRow = page.getByRole("row", { name: new RegExp(`\\+1-555-${RUN_ID}`) });
    await expect(simRow).toContainText("Verizon");
    // postpaid-sim-plan ticket: the Fleet names the Plan and shows the fee it set.
    await expect(simRow).toContainText("Unlimited Welcome");
    await expect(simRow).toContainText("$65.00");
  });

  test("an agent sees their contract's fleet and changes a smartphone's status", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Meridian Logistics ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);

    await page.getByRole("button", { name: "Add smartphone" }).first().click();
    await page.getByLabel("Model", { exact: true }).fill("Pixel 8");
    await page.getByLabel("Serial").fill(`SN-agent-${RUN_ID}`);
    await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
    await expect(page.getByRole("cell").filter({ hasText: "Pixel 8" })).toBeVisible();

    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number", { exact: true }).fill(`+1-555-agent-${RUN_ID}`);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("PREPAID");
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("cell").filter({ hasText: `+1-555-agent-${RUN_ID}` })).toBeVisible();

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await expect(page).toHaveURL(/\/agent$/);

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    await expect(page.getByRole("row", { name: /Pixel 8/ })).toBeVisible();
    await expect(page.getByRole("row", { name: /Pixel 8/ })).toContainText("Active");

    // Regression check for the "stuck pending" bug: the status <select> is a Client Component
    // instance that survives the parent Server Component's re-render on router.refresh(), so a
    // successful change must reset its own pending/disabled state rather than relying on a full
    // remount. Changing status twice in a row is the only way to catch a reset that's missing on
    // the success path — a single change looks identical whether or not the bug is present.
    const smartphoneStatusSelect = page
      .getByRole("row", { name: /Pixel 8/ })
      .getByLabel("Change smartphone status");

    await smartphoneStatusSelect.selectOption("IN_REPAIR");
    await expect(page.getByRole("row", { name: /Pixel 8/ })).toContainText("In Repair");
    await expect(smartphoneStatusSelect).toBeEnabled();

    await smartphoneStatusSelect.selectOption("ACTIVE");
    await expect(page.getByRole("row", { name: /Pixel 8/ })).toContainText("Active");
    await expect(smartphoneStatusSelect).toBeEnabled();

    // Same regression check for the SIM Card toggle button: retire, then reactivate.
    const simCardRow = page.getByRole("row", { name: new RegExp(`\\+1-555-agent-${RUN_ID}`) });
    await expect(simCardRow).toContainText("Active");

    await simCardRow.getByRole("button", { name: "Retire" }).click();
    await expect(simCardRow).toContainText("Retired");
    await expect(simCardRow.getByRole("button", { name: "Reactivate" })).toBeEnabled();

    await simCardRow.getByRole("button", { name: "Reactivate" }).click();
    await expect(simCardRow).toContainText("Active");
    await expect(simCardRow.getByRole("button", { name: "Retire" })).toBeEnabled();
  });

  test("a tester sees their client's fleet", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Kessler & Vance LLP ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);

    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number", { exact: true }).fill(`+34-91-${RUN_ID}`);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("PREPAID");
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("cell").filter({ hasText: `+34-91-${RUN_ID}` })).toBeVisible();

    const testerEmail = `helena.voss+${RUN_ID}@kessler.example`;
    const testerPassword = await addTester(page, clientId, testerEmail);

    await logout(page);
    await login(page, testerEmail, testerPassword);
    await expect(page).toHaveURL(/\/client$/);

    await page.goto("/client/fleet");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("cell").filter({ hasText: `+34-91-${RUN_ID}` })).toBeVisible();
  });

  test("a manager adds a client-owned smartphone without a serial, and the agent sets it from the fleet page", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Harbor & Finch Realty ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);

    await page.getByRole("button", { name: "Add smartphone" }).first().click();
    await page.getByLabel("Model", { exact: true }).fill("iPhone 15");
    await page.getByRole("dialog").getByLabel("Owner").selectOption("CLIENT");
    await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();

    const managerRow = page.getByRole("row", { name: /iPhone 15/ });
    await expect(managerRow).toBeVisible();
    await expect(managerRow).toContainText("Client");
    await expect(managerRow).toContainText("—");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const agentRow = page.getByRole("row", { name: /iPhone 15/ });
    await expect(agentRow).toBeVisible();
    await agentRow.getByRole("button", { name: "Set serial" }).click();
    await agentRow.getByLabel("Smartphone serial").fill(`SN-SET-${RUN_ID}`);
    await agentRow.getByRole("button", { name: "Save" }).click();

    await expect(agentRow).toContainText(`SN-SET-${RUN_ID}`);
    await expect(agentRow.getByRole("button", { name: "Edit" })).toBeVisible();
  });

  test("an agent installs a SIM card into a smartphone from the fleet page and the tester's fleet shows it", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Solene Cosmetics ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);

    await page.getByRole("button", { name: "Add smartphone" }).first().click();
    await page.getByLabel("Model", { exact: true }).fill("iPhone 14");
    await page.getByLabel("Serial").fill(`SN-install-${RUN_ID}`);
    await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
    await expect(page.getByRole("cell").filter({ hasText: "iPhone 14" })).toBeVisible();

    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number", { exact: true }).fill(`+1-555-install-${RUN_ID}`);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("PREPAID");
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("cell").filter({ hasText: `+1-555-install-${RUN_ID}` })).toBeVisible();

    const testerEmail = `nadia.okafor+${RUN_ID}@solene.example`;
    const testerPassword = await addTester(page, clientId, testerEmail);

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const simRow = page.getByRole("row", { name: new RegExp(`\\+1-555-install-${RUN_ID}`) });
    await expect(simRow).toBeVisible();
    await simRow
      .getByLabel("Installed in")
      .selectOption({ label: `iPhone 14 — SN-install-${RUN_ID}` });

    // Scoped to the Smartphones table specifically: the SIM Cards table's own row for this SIM
    // now also contains "iPhone 14" text (its "Installed in" control's selected option), so an
    // unscoped role query would match both rows.
    const smartphonesTable = page.locator("table").first();
    const smartphoneRow = smartphonesTable.getByRole("row", { name: /iPhone 14/ });
    await expect(smartphoneRow).toContainText(`+1-555-install-${RUN_ID}`);

    await logout(page);
    await login(page, testerEmail, testerPassword);
    await page.goto("/client/fleet");
    await selectContractInSwitcher(page, clientName);

    // Scoped to the SIM Cards table: the Smartphones table's own row for this Smartphone now also
    // contains the SIM Card's number (its "SIM Cards" column), so an unscoped role query would
    // match both rows.
    const simCardsTable = page.locator("table").nth(1);
    await expect(
      simCardsTable.getByRole("row", { name: new RegExp(`\\+1-555-install-${RUN_ID}`) }),
    ).toContainText("iPhone 14");
  });

  test("an agent and a tester are rejected from a contract that isn't theirs", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const { contractId: otherContractId } = await createUnrelatedContract(page, RUN_ID);

    // A Contract a fresh Tester's Client does NOT hold — reuse the same "other" Contract above.

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    const agentStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      return response.status;
    }, otherContractId);
    expect(agentStatus).toBe(403);

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const testerClientName = `Solene Cosmetics ${RUN_ID}`;
    await page.goto("/manager/clients");
    await page.getByRole("button", { name: "Add client" }).first().click();
    await page.getByLabel("Client name").fill(testerClientName);
    await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
    await page.getByRole("link", { name: testerClientName }).click();
    const testerEmail = `elise.fabron+${RUN_ID}@solene.example`;
    await page.getByRole("button", { name: "Add tester" }).first().click();
    await page.getByLabel("Email").fill(testerEmail);
    await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
    const testerPassword = await readRevealedPasswordAndClose(page);
    await expect(page.getByRole("cell", { name: testerEmail })).toBeVisible();

    await logout(page);
    await login(page, testerEmail, testerPassword);
    const testerStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      return response.status;
    }, otherContractId);
    expect(testerStatus).toBe(403);
  });

  // correct-a-smartphone-model ticket: each test creates its own unit and Client.
  async function addSmartphone(page: Page, model: string) {
    await page.getByRole("button", { name: "Add smartphone" }).first().click();
    await page.getByLabel("Model", { exact: true }).fill(model);
    await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
    await expect(page.getByRole("row", { name: new RegExp(model) })).toBeVisible();
  }

  test("a manager corrects a smartphone's model and sees it after a reload", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createClientAndContractWithSeededAgent(page, `Typo Model Traders ${RUN_ID}`);
    await addSmartphone(page, "Pixle 8");

    // Not located by its text: while editing, the model sits in a field, not in the row's name.
    const row = page.locator("table").first().locator("tbody tr").first();
    await row.getByRole("button", { name: "Edit model, Pixle 8", exact: true }).click();
    await row.getByLabel("Smartphone model").fill("  Pixel 8 Pro  ");
    await row.getByRole("button", { name: "Save" }).click();

    await expect(page.getByRole("row", { name: /Pixel 8 Pro/ })).toBeVisible();
    await page.reload();
    await expect(page.getByRole("row", { name: /Pixel 8 Pro/ })).toBeVisible();
    await expect(page.getByRole("cell", { name: /Pixle 8/ })).toHaveCount(0);
    // No status control on the Manager's Contract page.
    await expect(page.getByLabel("Change smartphone status")).toHaveCount(0);
  });

  test("a tester corrects a smartphone's model on their own client's fleet", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Tester Typo Retail ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    await addSmartphone(page, "Galxy S24");
    const testerEmail = `typo.tester+${RUN_ID}@typo.example`;
    const testerPassword = await addTester(page, clientId, testerEmail);

    await logout(page);
    await login(page, testerEmail, testerPassword);
    await page.goto("/client/fleet");
    await selectContractInSwitcher(page, clientName);

    const row = page.locator("table").first().locator("tbody tr").first();
    await row.getByRole("button", { name: "Edit model, Galxy S24", exact: true }).click();
    await row.getByLabel("Smartphone model").fill("Galaxy S24");
    await row.getByRole("button", { name: "Save" }).click();

    await expect(page.getByRole("row", { name: /Galaxy S24/ })).toBeVisible();
    await page.reload();
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("row", { name: /Galaxy S24/ })).toBeVisible();
    // The Tester's Fleet shows no serial or status control.
    await expect(page.getByLabel("Smartphone serial")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Set serial" })).toHaveCount(0);
    await expect(page.getByLabel("Change smartphone status")).toHaveCount(0);
  });

  test("an agent's fleet offers no model correction", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    // Not "Agent …": the Contract form's getByLabel("Agent") also matches a Client name containing it.
    const clientName = `Read Only Fleet ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);
    await addSmartphone(page, "Moto G");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Moto G/ });
    await expect(row).toBeVisible();
    await expect(row.getByLabel("Smartphone model")).toHaveCount(0);
    // The model cell holds no button: only the serial control's own are in the row.
    await expect(row.getByRole("cell", { name: "Moto G" }).getByRole("button")).toHaveCount(0);
  });

  // correct-a-smartphone-owner ticket.
  test("a manager corrects a smartphone's owner in both directions and sees it after a reload", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createClientAndContractWithSeededAgent(page, `Owner Fix Traders ${RUN_ID}`);
    await addSmartphone(page, "Nokia 3310");

    const row = page.locator("table").first().locator("tbody tr").first();
    await expect(row.getByRole("cell", { name: /^Company/ })).toBeVisible();
    await row.getByRole("button", { name: "Edit owner of Nokia 3310", exact: true }).click();
    await row.getByLabel("Owner").selectOption("CLIENT");
    await row.getByRole("button", { name: "Save" }).click();
    await expect(row.getByRole("cell", { name: /^Client/ })).toBeVisible();
    await page.reload();
    await expect(row.getByRole("cell", { name: /^Client/ })).toBeVisible();

    await row.getByRole("button", { name: "Edit owner of Nokia 3310", exact: true }).click();
    await row.getByLabel("Owner").selectOption("COMPANY");
    await row.getByRole("button", { name: "Save" }).click();
    await expect(row.getByRole("cell", { name: /^Company/ })).toBeVisible();
    await page.reload();
    await expect(row.getByRole("cell", { name: /^Company/ })).toBeVisible();
  });

  test("a tester corrects a smartphone's owner on their own client's fleet", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Tester Owner Retail ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    await addSmartphone(page, "Fairphone 4");
    const testerEmail = `owner.tester+${RUN_ID}@owner.example`;
    const testerPassword = await addTester(page, clientId, testerEmail);

    await logout(page);
    await login(page, testerEmail, testerPassword);
    await page.goto("/client/fleet");
    await selectContractInSwitcher(page, clientName);

    const row = page.locator("table").first().locator("tbody tr").first();
    await row.getByRole("button", { name: "Edit owner of Fairphone 4", exact: true }).click();
    await row.getByLabel("Owner").selectOption("CLIENT");
    await row.getByRole("button", { name: "Save" }).click();
    await expect(row.getByRole("cell", { name: /^Client/ })).toBeVisible();

    await page.reload();
    await selectContractInSwitcher(page, clientName);
    await expect(row.getByRole("cell", { name: /^Client/ })).toBeVisible();
  });

  test("an agent's fleet offers no owner correction", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Read Only Owner ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);
    await addSmartphone(page, "Moto E");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Moto E/ });
    await expect(row).toBeVisible();
    await expect(row.getByRole("button", { name: /^Edit owner/ })).toHaveCount(0);
  });

  async function addPrepaidSim(page: Page, number: string) {
    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number", { exact: true }).fill(number);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("PREPAID");
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("row", { name: new RegExp(number.replace("+", "\\+")) })).toBeVisible();
  }

  test("a manager corrects a SIM card's number and sees it after a reload", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createClientAndContractWithSeededAgent(page, `Typo Number Traders ${RUN_ID}`);
    await addPrepaidSim(page, "+44 7700 900100");

    // Not located by its text: while editing, the number sits in a field, not in the row's name.
    const row = page.locator("table").filter({ hasText: "Installed in" }).locator("tbody tr").first();
    await row.getByRole("button", { name: "Edit number, +44 7700 900100", exact: true }).click();
    await row.getByLabel("SIM number").fill("  +44 7700 900123  ");
    await row.getByRole("button", { name: "Save" }).click();

    await expect(page.getByRole("row", { name: /\+44 7700 900123/ })).toBeVisible();
    await page.reload();
    await expect(page.getByRole("row", { name: /\+44 7700 900123/ })).toBeVisible();
    await expect(page.getByRole("cell", { name: /900100/ })).toHaveCount(0);
  });

  test("a tester corrects a SIM card's number on their own client's fleet", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Tester Number Retail ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    await addPrepaidSim(page, "+44 7700 900200");
    const testerEmail = `number.tester+${RUN_ID}@typo.example`;
    const testerPassword = await addTester(page, clientId, testerEmail);

    await logout(page);
    await login(page, testerEmail, testerPassword);
    await page.goto("/client/fleet");
    await selectContractInSwitcher(page, clientName);

    const row = page.locator("table").filter({ hasText: "Installed in" }).locator("tbody tr").first();
    await row.getByRole("button", { name: "Edit number, +44 7700 900200", exact: true }).click();
    await row.getByLabel("SIM number").fill("+44 7700 900223");
    await row.getByRole("button", { name: "Save" }).click();

    await expect(page.getByRole("row", { name: /\+44 7700 900223/ })).toBeVisible();
    await page.reload();
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("row", { name: /\+44 7700 900223/ })).toBeVisible();
    // The Tester still gets no Installed-in or status control.
    await expect(page.getByLabel("Installed in")).toHaveCount(0);
  });

  test("an agent's fleet offers no SIM number correction", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Read Only Sim Fleet ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);
    await addPrepaidSim(page, "+44 7700 900300");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /\+44 7700 900300/ });
    await expect(row).toBeVisible();
    await expect(row.getByLabel("SIM number")).toHaveCount(0);
    await expect(row.getByRole("cell", { name: "+44 7700 900300" }).getByRole("button")).toHaveCount(0);
  });

  async function addPostpaidSim(page: Page, number: string) {
    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number", { exact: true }).fill(number);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("POSTPAID");
    await page.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: "Unlimited Welcome" });
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("row", { name: new RegExp(number.replace("+", "\\+")) })).toBeVisible();
  }

  function simTable(page: Page) {
    return page.locator("table").filter({ hasText: "Installed in" });
  }

  test("a manager corrects a Postpaid SIM's plan, sees the fee before Save and in the Monthly fee column", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createClientAndContractWithSeededAgent(page, `Typo Plan Traders ${RUN_ID}`);
    await addPostpaidSim(page, "+1-555-0310");

    // Located by the table, not by text: the plan name leaves the row while its select is open.
    const row = simTable(page).locator("tbody tr").first();
    await expect(row).toContainText("$65.00");
    await row.getByRole("button", { name: "Edit plan for +1-555-0310", exact: true }).click();
    const picker = row.getByRole("combobox", { name: "Postpaid plan" });
    // Only the Carrier's own active plans are offered, and the chosen one's fee shows before Save.
    await expect(picker.locator("option")).toContainText(["Choose a plan", "Unlimited Welcome", "Unlimited Plus"]);
    await picker.selectOption({ label: "Unlimited Plus" });
    await expect(row).toContainText("$80.00");
    await row.getByRole("button", { name: "Save", exact: true }).click();

    await expect(row.getByRole("combobox", { name: "Postpaid plan" })).toHaveCount(0);
    await expect(row).toContainText("Unlimited Plus");
    await expect(row.getByRole("cell", { name: "$80.00" })).toBeVisible();
    await page.reload();
    await expect(simTable(page).locator("tbody tr").first()).toContainText("Unlimited Plus");
    await expect(simTable(page).locator("tbody tr").first().getByRole("cell", { name: "$80.00" })).toBeVisible();
  });

  test("a manager's Prepaid SIM has no plan control", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createClientAndContractWithSeededAgent(page, `No Plan Prepaid ${RUN_ID}`);
    await addPrepaidSim(page, "+44 7700 900400");

    await expect(simTable(page).locator("tbody tr").first().getByRole("button", { name: /^Edit plan/ })).toHaveCount(0);
  });

  test("a tester corrects a Postpaid SIM's plan on their own client's fleet", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Tester Plan Retail ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    await addPostpaidSim(page, "+1-555-0320");
    const testerEmail = `plan.tester+${RUN_ID}@typo.example`;
    const testerPassword = await addTester(page, clientId, testerEmail);

    await logout(page);
    await login(page, testerEmail, testerPassword);
    await page.goto("/client/fleet");
    await selectContractInSwitcher(page, clientName);

    const row = simTable(page).locator("tbody tr").first();
    await row.getByRole("button", { name: "Edit plan for +1-555-0320", exact: true }).click();
    await row.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: "Unlimited Plus" });
    // The Tester sees the fee the plan sets before saving, though their table has no fee column.
    await expect(row).toContainText("$80.00");
    await row.getByRole("button", { name: "Save", exact: true }).click();

    await expect(row).toContainText("Unlimited Plus");
    await page.reload();
    await selectContractInSwitcher(page, clientName);
    await expect(simTable(page).locator("tbody tr").first()).toContainText("Unlimited Plus");
    await expect(page.getByLabel("Installed in")).toHaveCount(0);
  });

  test("an agent's fleet offers no plan correction", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Read Only Plan Fleet ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);
    await addPostpaidSim(page, "+1-555-0330");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /\+1-555-0330/ });
    await expect(row).toBeVisible();
    await expect(row).toContainText("Unlimited Welcome");
    await expect(row.getByRole("button", { name: /^Edit plan/ })).toHaveCount(0);
  });
});
