import { test, expect, type Page } from "@playwright/test";

/**
 * Fee logging, its traceability rule, and the provisioning side-effect on Fleet
 * (fee-logging-and-provisioning ticket), driven against a real backend + Postgres (see
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

/** Adds a Tester under an existing Client (from its manager detail page) and returns their email. */
async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

/** Adds a Smartphone to a Contract's Fleet from the Manager's Contract detail page. Returns its serial. */
async function addSmartphone(page: Page, contractId: string, model: string, serial: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
  await expect(page.getByRole("cell", { name: serial })).toBeVisible();
}

/** Selects the Contract matching `clientName` in a Contract switcher, if more than one exists. */
async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

async function submitRequestAsTester(page: Page, requestTypeLabel: string) {
  await page.goto("/client/requests");
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  await page.getByLabel("Request type").selectOption({ label: requestTypeLabel });
  await page.getByRole("dialog").getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

/** Fetches a Contract's Fees from the browser — there's no dedicated Fees list view yet. */
async function fetchFees(page: Page, contractId: string) {
  return page.evaluate(async (contractId) => {
    const response = await fetch(`/api/contracts/${contractId}/fees`);
    return (await response.json()) as { requestId: string; feeType: string; amount: number }[];
  }, contractId);
}

// Date.now() alone can collide across spec files: Playwright's collection phase can
// import several spec files within the same millisecond, and more than one file in this
// suite picks the same literal client name (e.g. "Aurora Retail Group") for its first
// test, so an exact RUN_ID match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("fee logging and provisioning", () => {
  test("an agent logs a fee against an existing request while completing it", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Topup");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Topup/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");

    await row.getByRole("button", { name: "Mark Completed" }).click();
    await row.getByLabel(/Fee amount/).fill("45.00");
    // Completing this way fires two sequential requests (status PATCH, then Fee POST) before the
    // row re-renders — wait for the Fee POST itself to resolve rather than only the row's text,
    // so the assertion below can't race ahead of it.
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(row).toContainText("Completed");

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(1);
    expect(fees[0].feeType).toBe("TOPUP");
    expect(fees[0].amount).toBe(45);
  });

  test("an agent logs a proactive fee with no pre-existing request", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Meridian Logistics ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `owen.reyes+${RUN_ID}@meridian.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    await page.getByRole("button", { name: "Log a fee" }).click();
    // Both the "Log a request" and "Log a fee" <dialog> elements exist in the DOM at once (only
    // one is open at a time) — scope to the open one so a shared label like "Tester" doesn't
    // resolve to two elements (LogRequestDialog also has a Tester field).
    const dialog = page.locator("dialog[open]");
    await dialog.getByLabel("Tester").selectOption({ label: testerEmail });
    await dialog.getByLabel("Fee type").selectOption({ label: "Repair" });
    await dialog.getByLabel(/Amount/).fill("60.00");
    await dialog.getByLabel("Description (optional)").fill("On-site battery replacement");
    await dialog.getByRole("button", { name: "Log fee" }).click();

    // The linking Request was auto-created, already Completed, agent-authored.
    const row = page.getByRole("row", { name: /Repair/ });
    await expect(row).toContainText("Completed");
    await expect(row).toContainText(testerEmail);
    await expect(row).toContainText(`Logged by ${SEEDED_USERS.agent.username}`);

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(1);
    expect(fees[0].feeType).toBe("REPAIR");
    expect(fees[0].amount).toBe(60);
  });

  test("completing a provision smartphone request adds a fleet item and retires the replaced one", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Bright Path Clinics ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const oldSerial = `SN-OLD-${RUN_ID}`;
    await addSmartphone(page, contractId, "iPhone 13", oldSerial);
    const testerEmail = `marco.diaz+${RUN_ID}@brightpath.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Provision Smartphone");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Provision Smartphone/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    const newSerial = `SN-NEW-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("150.00");
    await row.getByLabel("New smartphone model").fill("iPhone 15");
    await row.getByLabel("New smartphone serial").fill(newSerial);
    await row.getByLabel(/Retiring which smartphone/).selectOption({ label: `iPhone 13 — ${oldSerial}` });
    // Completing this way fires two sequential requests (status PATCH, then Fee POST) before the
    // row re-renders — wait for the Fee POST itself to resolve rather than only the row's text.
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(row).toContainText("Completed");

    // Regression (fleet-management): the Fleet view reflects both the addition and the retirement.
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("row", { name: new RegExp(oldSerial) })).toContainText("Retired");
    await expect(page.getByRole("row", { name: new RegExp(newSerial) })).toContainText("Active");
  });

  test("a reboot or a like-for-like sim swap can never carry a fee", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Harbor & Finch Realty ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `charlotte.finch+${RUN_ID}@harborfinch.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Reboot");
    await submitRequestAsTester(page, "SIM Swap");

    const requestIds = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      const body = (await response.json()) as { id: string; type: string }[];
      return {
        reboot: body.find((r) => r.type === "REBOOT")!.id,
        simSwap: body.find((r) => r.type === "SIM_SWAP")!.id,
      };
    }, contractId);

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    // No UI path even offers a fee against these types (RequestStatusControl only shows the fee
    // form for a type that can carry one) — the boundary itself is verified directly at the API,
    // the same way the equivalent access-control boundaries are in agent-request-fulfillment's
    // suite.
    const rebootStatus = await page.evaluate(
      async ({ contractId, requestId }) => {
        const response = await fetch(`/api/contracts/${contractId}/fees`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ requestId, feeType: "TOPUP", amount: 10 }),
        });
        return response.status;
      },
      { contractId, requestId: requestIds.reboot },
    );
    expect(rebootStatus).toBe(400);

    const simSwapStatus = await page.evaluate(
      async ({ contractId, requestId }) => {
        const response = await fetch(`/api/contracts/${contractId}/fees`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ requestId, feeType: "PROVISION_SIM", amount: 15 }),
        });
        return response.status;
      },
      { contractId, requestId: requestIds.simSwap },
    );
    expect(simSwapStatus).toBe(400);

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(0);
  });
});
