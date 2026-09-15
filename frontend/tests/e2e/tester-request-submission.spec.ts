import { test, expect, type Page } from "@playwright/test";

/**
 * Request submission and visibility (tester-request-submission ticket), driven against a real
 * backend + Postgres (see playwright.e2e.config.ts), at the accessibility-tree level per
 * spec.md's Testing decisions. Mirrors tests/e2e/fleet-management.spec.ts's pattern. The seeded
 * agent@example.com login resolves to the "Jordan Ellis" Agent (V5 migration).
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

/** Adds a Tester under an existing Client (from its manager detail page) and returns their email. */
async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

/** Selects the Contract matching `clientName` in a Requests page's Contract switcher, if more than one exists. */
async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

async function submitRequest(page: Page, requestTypeLabel: string) {
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  await page.getByLabel("Request type").selectOption({ label: requestTypeLabel });
  await page.getByRole("dialog").getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

const RUN_ID = Date.now();
const REQUEST_TYPE_LABELS = [
  "Reboot",
  "Topup",
  "SIM Swap",
  "Provision Smartphone",
  "Provision SIM",
  "Repair",
];

test.describe("tester request submission", () => {
  test("a tester submits every request type, another tester at the same client sees them, and the agent sees them filtered by contract", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);

    const firstTesterEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, firstTesterEmail, "Passw0rd!23");
    const secondTesterEmail = `owen.reyes+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, secondTesterEmail, "Passw0rd!23");

    await logout(page);
    await login(page, firstTesterEmail, "Passw0rd!23");
    await expect(page).toHaveURL(/\/client$/);

    await page.goto("/client/requests");
    for (const typeLabel of REQUEST_TYPE_LABELS) {
      await submitRequest(page, typeLabel);
    }
    for (const typeLabel of REQUEST_TYPE_LABELS) {
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toBeVisible();
    }

    // A different Tester at the same Client sees every Request raised by anyone at that Client,
    // not just their own.
    await logout(page);
    await login(page, secondTesterEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    for (const typeLabel of REQUEST_TYPE_LABELS) {
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toBeVisible();
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toContainText(
        firstTesterEmail,
      );
    }

    // The Agent sees the same Requests queued for their Contract.
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);
    for (const typeLabel of REQUEST_TYPE_LABELS) {
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toBeVisible();
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toContainText(
        "Submitted",
      );
    }
  });

  test("a tester from a different client and an agent on a different contract are both rejected", async ({
    page,
  }) => {
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

    // The seeded Agent cannot see Requests on that Contract.
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    const agentStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      return response.status;
    }, otherContractId);
    expect(agentStatus).toBe(403);

    // A fresh Tester at an unrelated Client cannot submit a Request against that Contract, nor see it.
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const testerClientName = `Solene Cosmetics ${RUN_ID}`;
    await page.goto("/manager/clients");
    await page.getByRole("button", { name: "Add client" }).first().click();
    await page.getByLabel("Client name").fill(testerClientName);
    await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
    await page.getByRole("link", { name: testerClientName }).click();
    await expect(page).toHaveURL(/\/manager\/clients\/.+/);
    const testerClientId = page.url().split("/").pop()!;
    const testerEmail = `elise.fabron+${RUN_ID}@solene.example`;
    await addTester(page, testerClientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    const testerGetStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      return response.status;
    }, otherContractId);
    expect(testerGetStatus).toBe(403);

    const testerPostStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: "REBOOT" }),
      });
      return response.status;
    }, otherContractId);
    expect(testerPostStatus).toBe(403);
  });
});
