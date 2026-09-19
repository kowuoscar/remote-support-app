import { randomUUID } from "node:crypto";
import { test, expect, type Page } from "@playwright/test";
import { Client } from "pg";

/**
 * A Manager gives a login to an Agent that has none, from the Agent's detail view
 * (agent-login-on-creation spec, create-login-for-existing-agent ticket), driven against a real
 * backend + Postgres (see playwright.e2e.config.ts), at the accessibility-tree level.
 *
 * The API can no longer create a login-less Agent, so that fixture is inserted straight into the
 * e2e database — with the backend AgentLoginApiTest's fixture, the only places setup bypasses
 * the API (spec.md "Testing decisions"), into the database E2E_DATABASE_URL names.
 */
const MANAGER = { username: "manager@example.com", password: "ChangeMe123!" } as const;
// No fallback, on purpose. playwright.e2e.config.ts names docker-compose's Postgres for the
// standard flow; any other config must name its own database. A silent default here wrote test
// Agents into a developer's live database five times before this guard replaced a warning.
const DATABASE_URL = process.env.E2E_DATABASE_URL;
if (!DATABASE_URL) {
  throw new Error(
    "E2E_DATABASE_URL is not set. This spec inserts fixtures straight into Postgres: run it through " +
      "playwright.e2e.config.ts, or set E2E_DATABASE_URL to the database your stack under test uses.",
  );
}

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
    const inserted = await client.query(
      `INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)
       SELECT $1, tenant_id, $2, 'FRANCE', 'EUR', 2000.00 FROM users WHERE username = $3`,
      [agentId, name, MANAGER.username],
    );
    if (inserted.rowCount !== 1) {
      throw new Error(
        `Expected to insert 1 login-less agent, inserted ${inserted.rowCount}: is ${MANAGER.username} ` +
          `seeded in the database at E2E_DATABASE_URL (${DATABASE_URL})?`,
      );
    }
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

    // Exact: the status announcement below contains the email too.
    await expect(page.getByText(agentEmail, { exact: true })).toBeVisible();
    // The Create login trigger is gone, so focus lands on the new email instead of the page body,
    // and the change is announced.
    await expect(page.getByText(agentEmail, { exact: true })).toBeFocused();
    await expect(page.getByRole("status")).toContainText(`can now sign in with ${agentEmail}`);
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
    await expect(dialog.getByLabel("Email")).toHaveAttribute("aria-invalid", "true");

    await page.goto(`/manager/agents/${agentId}`);
    await expect(page.getByText("No login")).toBeVisible();
  });

  test("an agent given a login since the page loaded is reported as such, not as an email in use", async ({
    page,
  }) => {
    const agentId = await insertLoginLessAgent(`Stale Page ${RUN_ID}`);

    await login(page, MANAGER.username, MANAGER.password);
    await expect(page).toHaveURL(/\/manager$/);

    const dialog = await openCreateLoginDialog(page, agentId);
    // The agent gets its login elsewhere while this dialog is open. A real in-page fetch, not
    // page.request, so it carries the httpOnly session cookie (see manager-entity-setup.spec.ts).
    const elsewhereStatus = await page.evaluate(
      async ({ id, username }) => {
        const response = await fetch(`/api/agents/${id}/login`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password: "Passw0rd!23" }),
        });
        return response.status;
      },
      { id: agentId, username: `stale.first+${RUN_ID}@agents.example` },
    );
    expect(elsewhereStatus).toBe(201);

    await dialog.getByLabel("Email").fill(`stale.second+${RUN_ID}@agents.example`);
    await dialog.getByLabel("Temporary password").fill("Passw0rd!23");
    await dialog.getByRole("button", { name: "Create login" }).click();

    await expect(dialog.getByRole("alert")).toContainText("This agent already has a login. Refresh the page");
    await expect(dialog.getByRole("alert")).not.toContainText("email is already in use");
  });

  test("a temporary password of only spaces is refused before anything is sent", async ({ page }) => {
    const agentId = await insertLoginLessAgent(`Blank Password ${RUN_ID}`);

    await login(page, MANAGER.username, MANAGER.password);
    await expect(page).toHaveURL(/\/manager$/);

    const dialog = await openCreateLoginDialog(page, agentId);
    await dialog.getByLabel("Email").fill(`blank.password+${RUN_ID}@agents.example`);
    await dialog.getByLabel("Temporary password").fill("   ");
    await dialog.getByRole("button", { name: "Create login" }).click();

    await expect(dialog.getByRole("alert")).toContainText("can't be only spaces");
    await expect(dialog.getByLabel("Temporary password")).toHaveAttribute("aria-invalid", "true");
    await expect(dialog.getByLabel("Temporary password")).toBeFocused();

    await page.goto(`/manager/agents/${agentId}`);
    await expect(page.getByText("No login")).toBeVisible();
  });
});
