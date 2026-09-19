import { test, expect, type Page } from "@playwright/test";

/**
 * A Return of a Client-owned Smartphone needs no approval, and completing it retires the
 * Smartphone and uninstalls its SIM Cards (return-client-owned-smartphones ticket), driven
 * against a real backend + Postgres (see playwright.e2e.config.ts), at the accessibility-tree
 * level per spec.md's Testing decisions. Mirrors tests/e2e/sim-swap-moves.spec.ts's pattern. The
 * seeded agent@example.com login resolves to the "Jordan Ellis" Agent (V5 migration), USD.
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

/** Adds a Smartphone owned by the Client (spec.md Solution — Fleet model), for a Return fixture. */
async function addClientOwnedSmartphone(page: Page, contractId: string, model: string, serial: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  await page.getByLabel("Owner").selectOption({ label: "Client" });
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

test.describe("return client-owned smartphones", () => {
  test("a tester returns a client-owned smartphone, the agent completes it, and the fleet shows it retired with its sim card uninstalled", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Harborlight Retail ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const serial = `SN-${RUN_ID}`;
    await addClientOwnedSmartphone(page, contractId, "Pixel 8", serial);
    const number = `+1-555-${RUN_ID}`;
    await addSimCard(page, contractId, number);
    await installSimCard(page, contractId, number, `Pixel 8 — ${serial}`);

    const testerEmail = `imane.diallo+${RUN_ID}@harborlight.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Request type").selectOption({ label: "Return" });
    await dialog.getByRole("listbox", { name: "Units to return" }).selectOption({ label: `Pixel 8 — ${serial}` });
    await dialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    await expect(page.getByRole("row", { name: /Return/ })).toContainText("Submitted");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Return/ });
    await expect(row).toContainText("Posted to Client");
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");
    await row.getByRole("button", { name: "Mark Completed" }).click();
    await expect(row).toContainText("Completed");

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    // API-level assertions for the Fleet's post-completion state (mirrors sim-swap-moves.spec.ts):
    // a Smartphone's Fleet row shows any SIM Card installed in it (sim-installed-in-smartphone
    // ticket), so a text-content assertion on the Smartphone row could pass on a stale row while
    // the fetched state itself is the ground truth.
    const smartphones = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      return (await response.json()) as { id: string; serial: string; status: string }[];
    }, contractId);
    const returnedPhone = smartphones.find((phone) => phone.serial === serial)!;
    expect(returnedPhone.status).toBe("RETIRED");

    const simCards = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/sim-cards`);
      return (await response.json()) as { id: string; number: string; status: string; installedInSmartphoneId?: string }[];
    }, contractId);
    const returnedSim = simCards.find((sim) => sim.number === number)!;
    expect(returnedSim.status).toBe("ACTIVE");
    expect(returnedSim.installedInSmartphoneId).toBeUndefined();
  });
});
