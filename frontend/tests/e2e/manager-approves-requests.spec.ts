import { test, expect, type Page } from "@playwright/test";

/**
 * The Company Manager's approval gate on Provision and Replace Requests
 * (request-types-and-flow spec, Lifecycle/Manager approval; manager-approves-requests ticket),
 * driven against a real backend + Postgres (see playwright.e2e.config.ts), at the
 * accessibility-tree level per spec.md's Testing decisions. Mirrors
 * tests/e2e/fee-logging-and-provisioning.spec.ts's pattern. The seeded agent@example.com login
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

/** Adds a Tester under an existing Client (from its manager detail page) and returns their email. */
async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

/** Selects the Contract matching `clientName` in a Contract switcher, if more than one exists. */
async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

async function submitProvisionSim(page: Page) {
  await page.goto("/client/requests");
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Request type").selectOption({ label: "Provision SIM" });
  await dialog.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
  await dialog.getByLabel("Flavor").selectOption({ label: "Prepaid" });
  await dialog.getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

async function submitProvisionSmartphone(page: Page, requestedModel: string) {
  await page.goto("/client/requests");
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Request type").selectOption({ label: "Provision Smartphone" });
  await dialog.getByLabel("Requested model").fill(requestedModel);
  await dialog.getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

/** Scopes a Pending Requests page row by both type and Client name — see file's own RUN_ID note. */
function pendingRequestRow(page: Page, clientName: string, typeLabel: string) {
  return page.getByRole("row", { name: new RegExp(`${typeLabel}.*${clientName}`) });
}

// Date.now() alone can collide across spec files: Playwright's collection phase can import
// several spec files within the same millisecond, and more than one file in this suite picks the
// same literal client name (e.g. "Aurora Retail Group") for its first test, so an exact RUN_ID
// match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("manager approves requests", () => {
  test("a tester submits a provision sim, the manager approves it from pending requests, and the agent completes it", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    // The Manager's dashboard shows the pending count, linking to the page. The dashboard
    // demonstrates the Operate-mode skeleton-loading convention, so wait for the real stats.
    await page.goto("/manager");
    await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
    const pendingRequestsStat = page.getByTestId("pending-requests-stat");
    const pendingRequestsCount = pendingRequestsStat.locator(".tnum");
    const before = Number((await pendingRequestsCount.textContent())!.trim());

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitProvisionSim(page);

    // The Tester sees it's Pending Approval, not yet Submitted.
    await page.goto("/client/requests");
    await expect(page.getByRole("row", { name: /Provision SIM/ })).toContainText("Pending Approval");

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);

    // The dashboard's own count went up by exactly one.
    await page.goto("/manager");
    await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
    await expect(pendingRequestsCount).toHaveText(String(before + 1));

    await page.goto("/manager/requests");
    const row = pendingRequestRow(page, clientName, "Provision SIM");
    await expect(row).toContainText(clientName);
    await expect(row).toContainText(testerEmail);
    await expect(row).toContainText("Jordan Ellis");

    await Promise.all([
      page.waitForResponse((resp) => /\/api\/requests\/.+\/approve$/.test(resp.url())),
      row.getByRole("button", { name: "Approve" }).click(),
    ]);
    await expect(page.getByRole("row", { name: new RegExp(clientName) })).not.toBeVisible();

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const agentRow = page.getByRole("row", { name: /Provision SIM/ });
    await expect(agentRow).toContainText("Submitted");
    await agentRow.getByRole("button", { name: "Mark In Progress" }).click();
    await agentRow.getByRole("button", { name: "Mark Completed" }).click();

    const number = `+1-555-${RUN_ID}`;
    // provision-request-details ticket AC: completing a Provision SIM asks only for the number —
    // no Carrier/Flavor picker (those came from submission); the Fee amount field still shows
    // since Provision SIM can carry a Fee (fee-logging-and-provisioning ticket).
    await expect(agentRow.getByRole("combobox", { name: "Carrier" })).toHaveCount(0);
    await agentRow.getByLabel(/Fee amount/).fill("15.00");
    await agentRow.getByLabel("New SIM number").fill(number);
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      agentRow.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(agentRow).toContainText("Completed");

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    await expect(
      page.getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) }),
    ).toContainText("Verizon");
  });

  test("a manager rejects a request with a reason, and the tester sees it", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Meridian Logistics ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `owen.reyes+${RUN_ID}@meridian.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitProvisionSmartphone(page, "iPhone 15 Pro");

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    const row = pendingRequestRow(page, clientName, "Provision Smartphone");
    await row.getByRole("button", { name: "Reject" }).click();
    const reasonText = "Budget is tight this month — resubmit next quarter";
    await row.getByLabel("Rejection reason").fill(reasonText);
    await Promise.all([
      page.waitForResponse((resp) => /\/api\/requests\/.+\/reject$/.test(resp.url())),
      row.getByRole("button", { name: "Confirm reject" }).click(),
    ]);
    await expect(page.getByRole("row", { name: new RegExp(clientName) })).not.toBeVisible();

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    const testerRow = page.getByRole("row", { name: /Provision Smartphone/ });
    await expect(testerRow).toContainText("Rejected");
    await expect(testerRow).toContainText(reasonText);
  });
});
