import { randomUUID } from "node:crypto";
import { test, expect, type Page } from "@playwright/test";
import { Client } from "pg";

/**
 * A Manager gives a login to an Agent that has none, from the Agent's detail view
 * (agent-login-on-creation spec, create-login-for-existing-agent ticket), driven against a real
 * backend + Postgres (see playwright.e2e.config.ts), at the accessibility-tree level.
 *
 * The API can no longer create a login-less Agent, so that fixture is inserted straight into the
 * e2e database — the only place setup bypasses the API (spec.md "Testing decisions"). The
 * connection defaults to docker-compose's Postgres; E2E_DATABASE_URL points it elsewhere.
 */
const MANAGER = { username: "manager@example.com", password: "ChangeMe123!" } as const;
const DATABASE_URL =
  process.env.E2E_DATABASE_URL ?? "postgres://remote_support:remote_support@127.0.0.1:5432/remote_support";

// Unique per run so re-runs against a persistent dev database don't collide on names or usernames.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

/** Inserts an Agent with no login into the Manager's tenant and returns its id. */
async function insertLoginLessAgent(name: string): Promise<string> {
  const agentId = randomUUID();
  const client = new Client({ connectionString: DATABASE_URL });
  await client.connect();
  try {
    await client.query(
      `INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)
       SELECT $1, tenant_id, $2, 'FRANCE', 'EUR', 2000.00 FROM users WHERE username = $3`,
      [agentId, name, MANAGER.username],
    );
  } finally {
    await client.end();
  }
  return agentId;
}

async function openCreateLoginDialog(page: Page, agentId: string) {
  await page.goto(`/manager/agents/${agentId}`);
  await expect(page.getByText("No login")).toBeVisible();
  await page.getByRole("button", { name: "Create login" }).click();
  return page.getByRole("dialog");
}

test.describe("create login for existing agent", () => {
  test("manager creates a login for a login-less agent, who can then sign in", async ({ page }) => {
    const agentId = await insertLoginLessAgent(`Noor Haddad ${RUN_ID}`);
    const agentEmail = `noor.haddad+${RUN_ID}@agents.example`;
    const agentPassword = "Passw0rd!23";

    await login(page, MANAGER.username, MANAGER.password);
    await expect(page).toHaveURL(/\/manager$/);

    const dialog = await openCreateLoginDialog(page, agentId);
    await dialog.getByLabel("Email").fill(agentEmail);
    await dialog.getByLabel("Temporary password").fill(agentPassword);
    await dialog.getByRole("button", { name: "Create login" }).click();

    await expect(page.getByText(agentEmail)).toBeVisible();
    await expect(page.getByText("No login")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Create login" })).toHaveCount(0);

    await page.getByRole("button", { name: "Log out" }).click();
    await expect(page).toHaveURL(/\/login/);

    await login(page, agentEmail, agentPassword);
    await expect(page).toHaveURL(/\/agent$/);
  });

  test("an email already in use is rejected with a clear message and the agent stays without a login", async ({
    page,
  }) => {
    const agentId = await insertLoginLessAgent(`Duplicate Login ${RUN_ID}`);

    await login(page, MANAGER.username, MANAGER.password);
    await expect(page).toHaveURL(/\/manager$/);

    const dialog = await openCreateLoginDialog(page, agentId);
    await dialog.getByLabel("Email").fill(MANAGER.username);
    await dialog.getByLabel("Temporary password").fill("Passw0rd!23");
    await dialog.getByRole("button", { name: "Create login" }).click();

    await expect(dialog.getByRole("alert")).toContainText("That email is already in use");

    await page.goto(`/manager/agents/${agentId}`);
    await expect(page.getByText("No login")).toBeVisible();
  });
});
