import { expect, type Page } from "@playwright/test";

/**
 * Shared login and fixture setup for the e2e suite's real-backend specs (driven against a real
 * backend + Postgres, at the accessibility-tree level per spec.md's Testing decisions). Extracted
 * from the near-identical copies that had accumulated across topup-fee-from-option.spec.ts,
 * fee-logging-and-provisioning.spec.ts, client-invoice-generation.spec.ts,
 * client-invoice-submission-and-visibility.spec.ts, fleet-management.spec.ts and
 * carrier-catalog.spec.ts — no behaviour change, same steps and assertions each caller had inline.
 */
export const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!" },
  agent: { username: "agent@example.com", password: "AgentDemo123!" },
} as const;

/** The seeded agent@example.com login resolves to this Agent (V5 migration), USD. */
export const SEEDED_AGENT_LABEL = "Jordan Ellis · USD";

export async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  // Wait for the post-login redirect before the caller navigates anywhere else — otherwise a
  // page.goto() right after this can race and cancel the in-flight session-cookie exchange,
  // leaving no session and every following action silently bounced back to /login.
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

export async function logout(page: Page) {
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
}

/** Creates a fresh Client and returns its id. */
async function createClient(page: Page, clientName: string): Promise<string> {
  await page.goto("/manager/clients");
  await page.getByRole("button", { name: "Add client" }).first().click();
  await page.getByLabel("Client name").fill(clientName);
  await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("cell", { name: clientName })).toBeVisible();
  const clientHref = await page.getByRole("link", { name: clientName }).getAttribute("href");
  return clientHref!.split("/").pop()!;
}

/** Creates a Contract linking an existing Client (by name) to the seeded Agent. */
async function createContractForClient(page: Page, clientName: string): Promise<void> {
  await page.goto("/manager/contracts");
  await page.getByRole("button", { name: "Add contract" }).first().click();
  await page.getByLabel("Client").selectOption({ label: clientName });
  await page.getByLabel("Agent").selectOption({ label: SEEDED_AGENT_LABEL });
  await page.getByRole("dialog").getByRole("button", { name: "Add contract" }).click();
  await expect(page.getByRole("row", { name: new RegExp(clientName) })).toBeVisible();
}

/** Creates a fresh Client, then a Contract linking it to the seeded Agent. Returns both ids. */
export async function createClientAndContractWithSeededAgent(
  page: Page,
  clientName: string,
): Promise<{ clientId: string; contractId: string }> {
  const clientId = await createClient(page, clientName);
  await createContractForClient(page, clientName);

  await page.getByRole("link", { name: clientName }).click();
  await expect(page).toHaveURL(/\/manager\/contracts\/.+/);
  const contractId = page.url().split("/").pop()!;

  return { clientId, contractId };
}

/** Adds a Tester under an existing Client (from its manager detail page) and returns their email. */
export async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

/** Creates a fresh Client with one Tester, and a Contract linking it to the seeded Agent. */
export async function createContractWithTester(page: Page, clientName: string, testerEmail: string) {
  const clientId = await createClient(page, clientName);
  await createContractForClient(page, clientName);
  await addTester(page, clientId, testerEmail, "Passw0rd!23");
}

/** Adds a Smartphone to a Contract's Fleet from the Manager's Contract detail page. Returns its serial. */
export async function addSmartphone(page: Page, contractId: string, model: string, serial: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
  await expect(page.getByRole("cell", { name: serial })).toBeVisible();
}

/**
 * Adds a Postpaid Plan at a given price to the seeded Verizon Carrier, as the Manager, and returns
 * its name. A Postpaid SIM's monthly fee is copied from its Plan (postpaid-sim-plan ticket), so a
 * test that wants a specific fee puts that price in the catalog first.
 */
export async function addPostpaidPlanToVerizon(page: Page, planName: string, monthlyPrice: string) {
  await page.goto("/manager/carriers?country=UNITED_STATES");
  const verizon = page
    .getByRole("list", { name: "Carriers" })
    .getByRole("listitem")
    .filter({ has: page.getByText("Verizon", { exact: true }) })
    .first();
  await verizon.getByRole("button", { name: "Add a postpaid plan to Verizon" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Name").fill(planName);
  await dialog.getByLabel("Monthly price (USD)").fill(monthlyPrice);
  await dialog.getByRole("button", { name: "Add postpaid plan" }).click();
  await expect(dialog).toBeHidden();
  return planName;
}

/** Adds a Postpaid SIM card to a Contract's Fleet from the Manager's Contract detail page. */
export async function addPostpaidSimCard(page: Page, contractId: string, number: string, monthlyFee: string) {
  const planName = await addPostpaidPlanToVerizon(
    page,
    // Never derived from the SIM number: the Fleet table shows both, and a Plan name containing
    // the number would make a row locator for the number ambiguous.
    `Test plan ${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`,
    monthlyFee,
  );
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add SIM card" }).first().click();
  await page.getByLabel("Number").fill(number);
  await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
  await page.getByLabel("Flavor").selectOption({ label: "Postpaid" });
  await page.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: planName });
  await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
  await expect(page.getByRole("cell", { name: number })).toBeVisible();
}

/**
 * Adds a Tester, submits a Request of the given type as them, then logs back in as the seeded
 * Agent and opens the Contract's Requests page with it already selected in the switcher — ready
 * for the caller to act on the new Request's row. Shared by tests that set up a Contract, then
 * need an Agent-side Request row to act on.
 */
export async function addTesterAndSubmitRequestAsAgent(
  page: Page,
  clientId: string,
  clientName: string,
  testerEmail: string,
  requestTypeLabel: string,
) {
  await addTester(page, clientId, testerEmail, "Passw0rd!23");

  await logout(page);
  await login(page, testerEmail, "Passw0rd!23");
  await submitRequestAsTester(page, requestTypeLabel);

  await logout(page);
  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.goto("/agent/requests");
  await selectContractInSwitcher(page, clientName);
}

/** Selects the Contract matching `clientName` in a Contract switcher, if more than one exists. */
export async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

/** Submits a Request of the given type as the currently-logged-in Tester. */
export async function submitRequestAsTester(page: Page, requestTypeLabel: string) {
  await page.goto("/client/requests");
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  await page.getByLabel("Request type").selectOption({ label: requestTypeLabel });
  await page.getByRole("dialog").getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

/** Fetches a Contract's Fees from the browser — there's no dedicated Fees list view yet. */
export async function fetchFees(page: Page, contractId: string) {
  return page.evaluate(async (contractId) => {
    const response = await fetch(`/api/contracts/${contractId}/fees`);
    return (await response.json()) as { requestId: string; feeType: string; amount: number }[];
  }, contractId);
}
