import { test, expect, type Page } from "@playwright/test";

/**
 * Kept in Stock: the Manager keeps a returned company-owned Smartphone in the Contract's Agent's
 * Stock instead of posting it back to the company (agent-stock ticket, spec.md Solution's Agent
 * Stock; Kept-in-Stock row of Disposition/Completion), driven against a real backend + Postgres
 * (see playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/manager-decides-return-disposition.spec.ts's pattern almost exactly, choosing
 * Kept in Stock instead of Posted to company/Cancelled. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration), USD.
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

/** Adds a company-owned Smartphone (the default Owner — spec.md Solution: Fleet model). */
async function addCompanyOwnedSmartphone(page: Page, contractId: string, model: string, serial: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
  await expect(page.getByRole("cell", { name: serial })).toBeVisible();
}

async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

/** Scopes a Pending Requests page row by both type and Client name — see manager-approves-requests.spec.ts's own note. */
function pendingRequestRow(page: Page, clientName: string, typeLabel: string) {
  return page.getByRole("row", { name: new RegExp(`${typeLabel}.*${clientName}`) });
}

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("agent stock", () => {
  test("the manager keeps a returned smartphone in stock; after completion the agent's stock page lists it and the fleet doesn't", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Cascade Field Ops ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const model = "Pixel 8";
    const serial = `SN-${RUN_ID}`;
    await addCompanyOwnedSmartphone(page, contractId, model, serial);

    const testerEmail = `remy.faure+${RUN_ID}@cascade.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Request type").selectOption({ label: "Return" });
    await dialog.getByRole("listbox", { name: "Units to return" }).selectOption({ label: `${model} — ${serial}` });
    await dialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    // A Return naming a company-owned unit is Pending Approval (return-client-owned-smartphones AC).
    await expect(page.getByRole("row", { name: /Return/ })).toContainText("Pending Approval");

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    const pendingRow = pendingRequestRow(page, clientName, "Return");
    await expect(pendingRow).toContainText(clientName);

    // The Manager picks Kept in Stock instead of the preselected Posted to company (ticket AC:
    // "The Manager can choose Kept in Stock for a company-owned Smartphone ... when approving").
    const picker = pendingRow.getByLabel(new RegExp(model));
    await expect(picker).toHaveValue("POSTED_TO_COMPANY");
    await picker.selectOption({ label: "Kept in Stock" });

    await Promise.all([
      page.waitForResponse((resp) => /\/api\/requests\/.+\/approve$/.test(resp.url())),
      pendingRow.getByRole("button", { name: "Approve" }).click(),
    ]);
    await expect(page.getByRole("row", { name: new RegExp(clientName) })).not.toBeVisible();

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Return/ });
    await expect(row).toContainText("Kept in Stock");
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");
    await row.getByRole("button", { name: "Mark Completed" }).click();
    await expect(row).toContainText("Completed");

    // The Fleet loses it entirely (ticket AC: "appears on no Fleet") — API-level, mirroring
    // return-client-owned-smartphones.spec.ts's own precedent.
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    const smartphones = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      return (await response.json()) as { id: string; serial: string }[];
    }, contractId);
    expect(smartphones.some((phone) => phone.serial === serial)).toBe(false);

    // The Agent's own Stock page lists it (ticket AC) — scoped by serial (unique per run), since
    // the Stock page can carry rows from other test runs/specs sharing this seeded Agent.
    await page.goto("/agent/stock");
    const stockRow = page.getByRole("row", { name: new RegExp(serial) });
    await expect(stockRow).toBeVisible();
    await expect(stockRow).toContainText(model);
    await expect(stockRow).toContainText(clientName);
  });

  // fulfil-from-stock ticket: extends the Stock e2e per its own Tests section ("extend the Stock
  // e2e: the Agent completes a Provision Smartphone from Stock and the Fleet shows that
  // Smartphone"). Builds its own Stock unit first (the same Return → Kept in Stock flow the test
  // above already covers end to end) rather than depending on test execution order.
  test("the agent fulfils a Provision Smartphone from Stock; the Fleet gains it and the Stock page loses it", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Beacon Field Services ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const model = "Pixel 7";
    const serial = `SN-STOCK-${RUN_ID}`;
    await addCompanyOwnedSmartphone(page, contractId, model, serial);

    const testerEmail = `dara.iwu+${RUN_ID}@beacon.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    // Return the company-owned Smartphone, kept in Stock (same flow as the test above).
    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    let dialog = page.getByRole("dialog");
    await dialog.getByLabel("Request type").selectOption({ label: "Return" });
    await dialog.getByRole("listbox", { name: "Units to return" }).selectOption({ label: `${model} — ${serial}` });
    await dialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    let pendingRow = pendingRequestRow(page, clientName, "Return");
    await pendingRow.getByLabel(new RegExp(model)).selectOption({ label: "Kept in Stock" });
    await Promise.all([
      page.waitForResponse((resp) => /\/api\/requests\/.+\/approve$/.test(resp.url())),
      pendingRow.getByRole("button", { name: "Approve" }).click(),
    ]);

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);
    let row = page.getByRole("row", { name: /Return/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();
    await expect(row).toContainText("Completed");

    // Now submit a fresh Provision Smartphone Request on the same Contract, and fulfil it from
    // the Stock unit the Return above just created.
    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    dialog = page.getByRole("dialog");
    await dialog.getByLabel("Request type").selectOption({ label: "Provision Smartphone" });
    await dialog.getByLabel("Requested model").fill("Galaxy S24");
    await dialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    pendingRow = pendingRequestRow(page, clientName, "Provision Smartphone");
    await Promise.all([
      page.waitForResponse((resp) => /\/api\/requests\/.+\/approve$/.test(resp.url())),
      pendingRow.getByRole("button", { name: "Approve" }).click(),
    ]);

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);
    row = page.getByRole("row", { name: /Provision Smartphone/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    // The "from my Stock" picker offers the unit the earlier Return just kept (ticket AC: "offers
    // a 'from my Stock' picker only when a matching unit exists").
    await row.getByLabel(/From my Stock/).selectOption({ label: `${model} — ${serial}` });
    // The Fee amount field is required whenever a Provision Smartphone completes (it's fee
    // capable) and has a min="0.01" — 0 fails native HTML validation and silently blocks submit.
    await row.getByLabel(/Fee amount/).fill("0.01");
    const [statusResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/status") && resp.request().method() === "PATCH"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(statusResponse.status()).toBe(200);
    await expect(row.getByText("Completed", { exact: true })).toBeVisible();

    // The Fleet gains the Stock Smartphone, Active, at its original serial (ticket AC: "the unit
    // leaves the Stock page and appears in the Fleet") — API-level, mirroring the precedent above.
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    const smartphones = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      return (await response.json()) as { id: string; serial: string; status: string }[];
    }, contractId);
    const fulfilled = smartphones.find((phone) => phone.serial === serial);
    expect(fulfilled?.status).toBe("ACTIVE");

    // It's gone from the Agent's own Stock page.
    await page.goto("/agent/stock");
    await expect(page.getByRole("row", { name: new RegExp(serial) })).not.toBeVisible();
  });
});
