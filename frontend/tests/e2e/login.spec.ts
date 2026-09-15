import { test, expect } from "@playwright/test";

/**
 * The real login flow (auth-login-flow ticket), driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions —
 * roles and labels, not pixels. Credentials match the seeded users (backend V2/V3 migrations).
 */
const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!", shell: "/manager" },
  agent: { username: "agent@example.com", password: "AgentDemo123!", shell: "/agent" },
  tester: { username: "tester@example.com", password: "TesterDemo123!", shell: "/client" },
} as const;

async function fillAndSubmit(page: import("@playwright/test").Page, username: string, password: string) {
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

test.describe("login", () => {
  test("visiting a protected route while unauthenticated redirects to login", async ({ page }) => {
    await page.goto("/manager");
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  });

  test("invalid credentials show an error and grant no access", async ({ page }) => {
    await page.goto("/login");
    await fillAndSubmit(page, SEEDED_USERS.manager.username, "wrong-password");

    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page).toHaveURL(/\/login/);

    // No access was granted: a protected route still redirects to login.
    await page.goto("/manager");
    await expect(page).toHaveURL(/\/login/);
  });

  for (const [role, creds] of Object.entries(SEEDED_USERS)) {
    test(`${role} login reaches their own shell`, async ({ page }) => {
      await page.goto("/login");
      await fillAndSubmit(page, creds.username, creds.password);
      await expect(page).toHaveURL(new RegExp(`${creds.shell}$`));
    });
  }

  test("logout ends the session, then a protected route redirects to login", async ({ page }) => {
    await page.goto("/login");
    await fillAndSubmit(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await expect(page).toHaveURL(/\/manager$/);

    await page.getByRole("button", { name: "Log out" }).click();
    await expect(page).toHaveURL(/\/login/);

    await page.goto("/manager");
    await expect(page).toHaveURL(/\/login/);
  });
});
