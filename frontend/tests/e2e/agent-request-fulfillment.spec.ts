import { test, expect, type Page } from "@playwright/test";

/**
 * Request status lifecycle and Agent-authored/proactive logging (agent-request-fulfillment
 * ticket), driven against a real backend + Postgres (see playwright.e2e.config.ts), at the
 * accessibility-tree level per spec.md's Testing decisions. Mirrors
 * tests/e2e/tester-request-submission.spec.ts's pattern. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration).
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

/** reboot-and-topup-details ticket: a Reboot Request now names a Smartphone from the Contract's Fleet. */
async function addSmartphone(page: Page, contractId: string, model: string, serial: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
  await expect(page.getByRole("cell", { name: serial })).toBeVisible();
}

async function submitRequestAsTester(page: Page, requestTypeLabel: string, smartphoneOptionLabel?: string) {
  await page.goto("/client/requests");
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Request type").selectOption({ label: requestTypeLabel });
  if (requestTypeLabel === "Reboot") {
    await dialog.getByLabel("Smartphone to reboot").selectOption({ label: smartphoneOptionLabel! });
  }
  await dialog.getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

// Date.now() alone can collide across spec files: Playwright's collection phase can
// import several spec files within the same millisecond, and more than one file in this
// suite picks the same literal client name (e.g. "Aurora Retail Group") for its first
// test, so an exact RUN_ID match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("agent request fulfillment", () => {
  test("an agent progresses a request from submitted through in progress to completed", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 9", serial);
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    // Reboot, not Topup: this test is about the plain status-progression mechanics, and
    // fee-logging-and-provisioning ticket makes completing a fee-eligible type (Topup included)
    // prompt for a Fee amount first — covered by its own suite (fee-logging-and-provisioning.spec.ts).
    await submitRequestAsTester(page, "Reboot", `Pixel 9 — ${serial}`);

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Reboot/ });
    await expect(row).toContainText("Submitted");

    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");

    await row.getByRole("button", { name: "Mark Completed" }).click();
    await expect(row).toContainText("Completed");
    await expect(row.getByText("No further changes")).toBeVisible();
  });

  test("an agent cancels a request with a reason", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Meridian Logistics ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 9", serial);
    const testerEmail = `owen.reyes+${RUN_ID}@meridian.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Reboot", `Pixel 9 — ${serial}`);

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Reboot/ });
    await row.getByRole("button", { name: "Cancel" }).click();
    await row.getByLabel("Cancellation reason").fill("Tester no longer needs this");
    await row.getByRole("button", { name: "Confirm cancel" }).click();

    await expect(row).toContainText("Cancelled");
    await expect(row).toContainText("Tester no longer needs this");

    // The Tester's own list reflects the Agent's cancellation too (regression: still visible,
    // status still up to date).
    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await expect(page.getByRole("row", { name: /Reboot/ })).toContainText("Cancelled");
  });

  test("an agent logs a proactive request on a tester's behalf, starting immediately completed", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Bright Path Clinics ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `marco.diaz+${RUN_ID}@brightpath.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    await page.getByRole("button", { name: "Log a request" }).click();
    // fee-logging-and-provisioning ticket added a second dialog ("Log a fee") to this same page
    // with its own "Tester" field — both <dialog> elements exist in the DOM at once (only one
    // open), so scope to the open one rather than the page as a whole.
    const dialog = page.locator("dialog[open]");
    await dialog.getByLabel("Tester").selectOption({ label: testerEmail });
    await dialog.getByLabel("Request type").selectOption({ label: "Other" });
    // Other requires a description.
    await dialog.getByLabel("Description").fill("On-site battery replacement");
    await dialog.getByRole("radio", { name: /Completed/ }).check();
    await dialog.getByRole("button", { name: "Log request" }).click();

    const row = page.getByRole("row", { name: /Other/ });
    await expect(row).toContainText("Completed");
    await expect(row).toContainText(testerEmail);
    await expect(row).toContainText(`Logged by ${SEEDED_USERS.agent.username}`);

    // The Tester's own list shows this Agent-authored, already-Completed Request too
    // (regression: tester-visible list still shows Agent-authored Requests).
    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await expect(page.getByRole("row", { name: /Other/ })).toContainText("Completed");
  });

  test("a tester cannot change a request's status", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Kessler & Vance LLP ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `helena.voss+${RUN_ID}@kessler.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "SIM Swap");

    // No status controls exist on the Tester's own Requests view — verified directly at the API.
    const requestId = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      const body = (await response.json()) as { id: string }[];
      return body[0].id;
    }, contractId);

    const status = await page.evaluate(
      async ({ contractId, requestId }) => {
        const response = await fetch(`/api/contracts/${contractId}/requests/${requestId}/status`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status: "IN_PROGRESS" }),
        });
        return response.status;
      },
      { contractId, requestId },
    );
    expect(status).toBe(403);
  });

  test("an agent on a different contract is rejected from changing status or logging a request", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);

    // A Contract the seeded Agent (Jordan Ellis) does NOT hold: a different Client, a different Agent.
    const otherClientName = `Solene Cosmetics ${RUN_ID}`;
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

    const otherTesterEmail = `elise.fabron+${RUN_ID}@solene.example`;
    await page.goto(`/manager/clients`);
    await page.getByRole("link", { name: otherClientName }).click();
    await expect(page).toHaveURL(/\/manager\/clients\/.+/);
    const otherClientId = page.url().split("/").pop()!;
    await addTester(page, otherClientId, otherTesterEmail, "Passw0rd!23");
    const otherSerial = `SN-${RUN_ID}`;
    await addSmartphone(page, otherContractId, "Pixel 9", otherSerial);

    await logout(page);
    await login(page, otherTesterEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Reboot", `Pixel 9 — ${otherSerial}`);
    const otherRequestId = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      const body = (await response.json()) as { id: string }[];
      return body[0].id;
    }, otherContractId);

    // The seeded Agent cannot change status on that Contract's Request...
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    const statusChangeStatus = await page.evaluate(
      async ({ contractId, requestId }) => {
        const response = await fetch(`/api/contracts/${contractId}/requests/${requestId}/status`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status: "IN_PROGRESS" }),
        });
        return response.status;
      },
      { contractId: otherContractId, requestId: otherRequestId },
    );
    expect(statusChangeStatus).toBe(403);

    // ...nor log a Request proactively on it.
    const logStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: "TOPUP", testerId: crypto.randomUUID() }),
      });
      return response.status;
    }, otherContractId);
    expect(logStatus).toBe(403);
  });
});
