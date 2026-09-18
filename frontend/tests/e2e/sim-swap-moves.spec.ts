import { test, expect, type Page } from "@playwright/test";

/**
 * A SIM Swap Request's move/exchange details and its no-Agent-input completion effect
 * (sim-swap-moves ticket), driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/agent-request-fulfillment.spec.ts's pattern. The seeded agent@example.com
 * login resolves to the "Jordan Ellis" Agent (V5 migration), USD.
 */
const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!" },
  agent: { username: "agent@example.com", password: "AgentDemo123!" },
} as const;

const SEEDED_AGENT_LABEL = "Jordan Ellis · USD";

async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

async function logout(page: Page) {
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
}

/** Creates a fresh Client, then a Contract linking it to the seeded Agent. Returns both ids. */
async function createClientAndContractWithSeededAgent(
  page: Page,
  clientName: string,
): Promise<{ clientId: string; contractId: string }> {
  await page.goto("/manager/clients");
  await page.getByRole("button", { name: "Add client" }).first().click();
  await page.getByLabel("Client name").fill(clientName);
  await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("cell", { name: clientName })).toBeVisible();
  const clientHref = await page.getByRole("link", { name: clientName }).getAttribute("href");
  const clientId = clientHref!.split("/").pop()!;

  await page.goto("/manager/contracts");
  await page.getByRole("button", { name: "Add contract" }).first().click();
  await page.getByLabel("Client").selectOption({ label: clientName });
  await page.getByLabel("Agent").selectOption({ label: SEEDED_AGENT_LABEL });
  await page.getByRole("dialog").getByRole("button", { name: "Add contract" }).click();
  await expect(page.getByRole("row", { name: new RegExp(clientName) })).toBeVisible();

  await page.getByRole("link", { name: clientName }).click();
  await expect(page).toHaveURL(/\/manager\/contracts\/.+/);
  const contractId = page.url().split("/").pop()!;

  return { clientId, contractId };
}

async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

async function addSmartphone(page: Page, contractId: string, model: string, serial: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
  await expect(page.getByRole("cell", { name: serial })).toBeVisible();
}

async function addSimCard(page: Page, contractId: string, number: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add SIM card" }).first().click();
  await page.getByLabel("Number").fill(number);
  await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
  await page.getByLabel("Flavor").selectOption("PREPAID");
  await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
  await expect(page.getByRole("cell", { name: number })).toBeVisible();
}

/** Scopes to the SIM Cards table specifically — a Smartphone's own row also shows an installed SIM's number. */
function simCardsTable(page: Page) {
  return page.getByRole("heading", { name: "SIM Cards" }).locator("xpath=following::table[1]");
}

/** Sets a SIM Card's Installed-in Smartphone from the Manager's Contract Fleet view. */
async function installSimCard(page: Page, contractId: string, number: string, smartphoneOptionLabel: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  const row = simCardsTable(page).getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) });
  await row.getByLabel("Installed in").selectOption({ label: smartphoneOptionLabel });
  await expect(row.getByLabel("Installed in")).not.toHaveValue("");
}

async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

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
