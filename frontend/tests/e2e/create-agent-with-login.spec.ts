import { test, expect } from "@playwright/test";
import { logout } from "./helpers";

/**
 * A Manager creates an Agent together with its login (agent-login-on-creation spec,
 * create-agent-with-login ticket), driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level. Mirrors
 * tests/e2e/manager-entity-setup.spec.ts and tests/e2e/login.spec.ts.
 */
const MANAGER = { username: "manager@example.com", password: "ChangeMe123!" } as const;

async function login(page: import("@playwright/test").Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

// Unique per run so re-runs against a persistent dev database don't collide on the username.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("create agent with login", () => {
  test("manager creates an agent with a login, and that agent signs in to the Agent Console", async ({
    page,
  }) => {
    await login(page, MANAGER.username, MANAGER.password);
    await expect(page).toHaveURL(/\/manager$/);

    const agentName = `Inès Carvalho ${RUN_ID}`;
    const agentEmail = `ines.carvalho+${RUN_ID}@agents.example`;

    await page.goto("/manager/agents");
    await page.getByRole("button", { name: "Add agent" }).first().click();
    await page.getByLabel("Agent name").fill(agentName);
    await page.getByLabel("Country").selectOption("FRANCE");
    await page.getByLabel("Standing monthly salary").fill("2400");
    await page.getByLabel("Email").fill(agentEmail);
    await expect(page.getByRole("dialog").getByLabel("Temporary password")).toHaveCount(0);
    await page.getByRole("dialog").getByRole("button", { name: "Add agent" }).click();

    const dialog = page.getByRole("dialog");
    await expect(dialog.getByText(`${agentEmail} can now sign in`)).toBeVisible();
    const agentPassword = await dialog.getByLabel("Generated password").inputValue();
    await dialog.getByRole("button", { name: "Done" }).click();
    await expect(page.getByText(agentPassword)).toHaveCount(0);

    await page.getByRole("link", { name: agentName }).click();
    await expect(page).toHaveURL(/\/manager\/agents\/.+/);
    await expect(page.getByText(agentEmail)).toBeVisible();

    await logout(page);

    await login(page, agentEmail, agentPassword);
    await expect(page).toHaveURL(/\/agent$/);
  });

  test("an email already in use is rejected with a clear message and creates no agent", async ({
    page,
  }) => {
    await login(page, MANAGER.username, MANAGER.password);
    await expect(page).toHaveURL(/\/manager$/);

    const agentName = `Duplicate Email ${RUN_ID}`;

    await page.goto("/manager/agents");
    await page.getByRole("button", { name: "Add agent" }).first().click();
    await page.getByLabel("Agent name").fill(agentName);
    await page.getByLabel("Standing monthly salary").fill("1000");
    await page.getByLabel("Email").fill(MANAGER.username);
    await page.getByRole("dialog").getByRole("button", { name: "Add agent" }).click();

    await expect(page.getByRole("dialog").getByRole("alert")).toContainText(
      "That email is already in use",
    );
    await expect(page.getByRole("dialog").getByLabel("Email")).toHaveAttribute("aria-invalid", "true");

    await page.goto("/manager/agents");
    await expect(page.getByRole("link", { name: agentName })).toHaveCount(0);
  });

});
