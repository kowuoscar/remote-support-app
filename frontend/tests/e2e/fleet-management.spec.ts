import { test, expect, type Page } from "@playwright/test";

/**
 * Fleet management (fleet-management ticket), driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/manager-entity-setup.spec.ts's pattern. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration) — the only Agent login that exists, so
 * every Contract exercised from the Agent side uses that Agent.
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
  // Wait for the post-login redirect before the caller navigates anywhere else — otherwise a
  // page.goto() right after this can race and cancel the in-flight session-cookie exchange,
  // leaving no session and every following action silently bounced back to /login.
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

/** Selects the Contract matching `clientName` in a Fleet page's Contract switcher, if more than one exists. */
async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

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
    await page.getByLabel("Model").fill("iPhone 14");
    await page.getByLabel("Serial").fill(`SN-${RUN_ID}`);
    await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
    await expect(page.getByRole("cell", { name: "iPhone 14" })).toBeVisible();
    await expect(page.getByRole("row", { name: /iPhone 14/ })).toContainText("Active");

    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number").fill(`+1-555-${RUN_ID}`);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("POSTPAID");
    // postpaid-sim-plan ticket: the monthly fee comes from a Postpaid Plan of the chosen Carrier.
    await page.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: "Unlimited Welcome" });
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("cell", { name: `+1-555-${RUN_ID}` })).toBeVisible();
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
    await page.getByLabel("Model").fill("Pixel 8");
    await page.getByLabel("Serial").fill(`SN-agent-${RUN_ID}`);
    await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
    await expect(page.getByRole("cell", { name: "Pixel 8" })).toBeVisible();

    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number").fill(`+1-555-agent-${RUN_ID}`);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("PREPAID");
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("cell", { name: `+1-555-agent-${RUN_ID}` })).toBeVisible();

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
    await createClientAndContractWithSeededAgent(page, clientName);

    await page.getByRole("button", { name: "Add SIM card" }).first().click();
    await page.getByLabel("Number").fill(`+34-91-${RUN_ID}`);
    await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await page.getByLabel("Flavor").selectOption("PREPAID");
    await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
    await expect(page.getByRole("cell", { name: `+34-91-${RUN_ID}` })).toBeVisible();

    const testerEmail = `helena.voss+${RUN_ID}@kessler.example`;
    await page.goto("/manager/clients");
    await page.getByRole("link", { name: clientName }).click();
    await page.getByRole("button", { name: "Add tester" }).first().click();
    await page.getByLabel("Email").fill(testerEmail);
    await page.getByLabel("Temporary password").fill("Passw0rd!23");
    await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
    await expect(page.getByRole("cell", { name: testerEmail })).toBeVisible();

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await expect(page).toHaveURL(/\/client$/);

    await page.goto("/client/fleet");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("cell", { name: `+34-91-${RUN_ID}` })).toBeVisible();
  });

  test("an agent and a tester are rejected from a contract that isn't theirs", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);

    // A Contract the seeded Agent (Jordan Ellis) does NOT hold: a different Client, a different Agent.
    const otherClientName = `Bright Path Clinics ${RUN_ID}`;
    await page.goto("/manager/clients");
    await page.getByRole("button", { name: "Add client" }).first().click();
    await page.getByLabel("Client name").fill(otherClientName);
    await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();

    const otherAgentName = `Priya Nair ${RUN_ID}`;
    await page.goto("/manager/agents");
    await page.getByRole("button", { name: "Add agent" }).first().click();
    await page.getByLabel("Agent name").fill(otherAgentName);
    await page.getByLabel("Country").selectOption("PHILIPPINES");
    await page.getByLabel("Standing monthly salary").fill("1500");
    await page.getByLabel("Email").fill(`priya.nair+${RUN_ID}@agents.example`);
    await page.getByLabel("Temporary password").fill("Passw0rd!23");
    await page.getByRole("dialog").getByRole("button", { name: "Add agent" }).click();
    await expect(page.getByRole("row", { name: new RegExp(otherAgentName) })).toBeVisible();

    await page.goto("/manager/contracts");
    await page.getByRole("button", { name: "Add contract" }).first().click();
    await page.getByLabel("Client").selectOption({ label: otherClientName });
    await page.getByLabel("Agent").selectOption({ label: `${otherAgentName} · PHP` });
    await page.getByRole("dialog").getByRole("button", { name: "Add contract" }).click();
    await page.getByRole("link", { name: otherClientName }).click();
    await expect(page).toHaveURL(/\/manager\/contracts\/.+/);
    const otherContractId = page.url().split("/").pop()!;

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
    await page.getByLabel("Temporary password").fill("Passw0rd!23");
    await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
    await expect(page.getByRole("cell", { name: testerEmail })).toBeVisible();

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    const testerStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      return response.status;
    }, otherContractId);
    expect(testerStatus).toBe(403);
  });
});
