import { test, expect, type Page } from "@playwright/test";

/**
 * An Agent logs a Topup Fee from a Topup Option (carrier-catalog spec; topup-fee-from-option
 * ticket), driven against a real backend + Postgres at the accessibility-tree level. Mirrors
 * fee-logging-and-provisioning.spec.ts. The seeded agent@example.com login is the United States
 * Agent "Jordan Ellis", whose catalog seeds AT&T's "Prepaid Refill 25" at $25.00.
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

/** Creates a fresh Client with one Tester, and a Contract linking it to the seeded Agent. */
async function createContractWithTester(page: Page, clientName: string, testerEmail: string) {
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

  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(testerEmail);
  await page.getByLabel("Temporary password").fill("Passw0rd!23");
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: testerEmail })).toBeVisible();
}

/** Selects the Contract matching `clientName` in a Contract switcher, if more than one exists. */
async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("topup fee from a topup option", () => {
  test("an agent logs a topup fee by picking an option and adjusting the amount", async ({ page }) => {
    const clientName = `Lumen Outfitters ${RUN_ID}`;
    const testerEmail = `ana.ortiz+${RUN_ID}@lumen.example`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createContractWithTester(page, clientName, testerEmail);
    await logout(page);

    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    await page.getByRole("button", { name: "Log a fee" }).click();
    const dialog = page.locator("dialog[open]");
    await dialog.getByLabel("Tester").selectOption({ label: testerEmail });
    await expect(dialog.getByLabel("Fee type")).toHaveValue("TOPUP");

    await dialog.getByLabel("Topup option (optional)").selectOption({ label: "AT&T — Prepaid Refill 25 · $25.00" });
    const amount = dialog.getByLabel("Amount (USD)");
    await expect(amount).toHaveValue("25.00");

    await amount.fill("27.50");
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      dialog.getByRole("button", { name: "Log fee" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);

    // The Fee shows the adjusted amount, not the Option's price.
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("cell", { name: "Topup" })).toBeVisible();
    await expect(page.getByText("$27.50", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("$25.00", { exact: true })).toHaveCount(0);
  });
});
