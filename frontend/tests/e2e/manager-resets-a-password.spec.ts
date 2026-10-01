import { test, expect, type Page } from "@playwright/test";
import { SEEDED_USERS, login, logout, readRevealedPasswordAndClose } from "./helpers";

/**
 * A Manager resets a locked-out person's password (manager-resets-a-password spec), driven against
 * a real backend + Postgres at the accessibility-tree level. This is the Agent half
 * (reset-an-agents-password-ui ticket): create an Agent through the UI keeping the revealed
 * password, reset it from the Agent's page keeping the new one, sign in with it, and be refused
 * with the creation password. The Tester half joins this file with its own ticket.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

async function createAgentThroughTheUi(page: Page, name: string, email: string): Promise<string> {
  await page.goto("/manager/agents");
  await page.getByRole("button", { name: "Add agent" }).first().click();
  await page.getByLabel("Agent name").fill(name);
  await page.getByLabel("Country").selectOption("FRANCE");
  await page.getByLabel("Standing monthly salary").fill("2400");
  await page.getByLabel("Email").fill(email);
  await page.getByRole("dialog").getByRole("button", { name: "Add agent" }).click();
  return readRevealedPasswordAndClose(page);
}

async function openAgentPage(page: Page, name: string) {
  await page.goto("/manager/agents");
  await page.getByRole("link", { name }).click();
  await expect(page).toHaveURL(/\/manager\/agents\/.+/);
}

test.describe("manager resets an Agent's password", () => {
  test("the Agent signs in with the new password; the creation password is refused; the Agent can then change it", async ({
    page,
  }) => {
    const name = `Reset Agent ${RUN_ID}`;
    const email = `reset.agent+${RUN_ID}@agents.example`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const creationPassword = await createAgentThroughTheUi(page, name, email);

    await openAgentPage(page, name);
    const trigger = page.getByRole("button", { name: "Reset password" });
    await trigger.click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("heading", { name: `Reset password for ${name}` })).toBeVisible();
    await dialog.getByRole("button", { name: "Reset password" }).click();
    const newPassword = await dialog.getByLabel("Generated password").inputValue();
    expect(newPassword).not.toBe("");
    expect(newPassword).not.toBe(creationPassword);
    await dialog.getByRole("button", { name: "Done" }).click();

    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
    await expect(page.getByText(`Password reset for ${email}.`)).toBeAttached();
    await expect(page.getByText(newPassword)).toHaveCount(0);
    await expect(page.locator(`input[value="${newPassword}"]`)).toHaveCount(0);

    await logout(page);

    await login(page, email, newPassword);
    await expect(page).toHaveURL(/\/agent$/);

    // The Agent changes it to one of their own from the viewer-chip menu; the revealed one is refused.
    const ownPassword = "MyOwnPassw0rd!42";
    await page.getByTestId("viewer-menu-trigger").click();
    await page.getByRole("menuitem", { name: "Change password" }).click();
    const changeDialog = page.getByRole("dialog");
    await changeDialog.getByLabel("Current password").fill(newPassword);
    await changeDialog.getByLabel("New password", { exact: true }).fill(ownPassword);
    await changeDialog.getByLabel("Confirm new password").fill(ownPassword);
    await changeDialog.getByRole("button", { name: "Change password" }).click();
    await expect(page).toHaveURL(/\/login\?passwordChanged=1$/);

    await expectRefused(page, email, newPassword);
    await expectRefused(page, email, creationPassword);
    await login(page, email, ownPassword);
    await expect(page).toHaveURL(/\/agent$/);
  });

  test("Cancel sends nothing: the old password still signs in", async ({ page }) => {
    const name = `Cancel Agent ${RUN_ID}`;
    const email = `cancel.agent+${RUN_ID}@agents.example`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const creationPassword = await createAgentThroughTheUi(page, name, email);
    await openAgentPage(page, name);

    let resetRequests = 0;
    page.on("request", (request) => {
      if (request.url().endsWith("/login/password")) resetRequests += 1;
    });
    await page.getByRole("button", { name: "Reset password" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Cancel" }).click();
    await expect(page.getByRole("dialog")).toBeHidden();
    expect(resetRequests).toBe(0);

    await logout(page);
    await login(page, email, creationPassword);
    await expect(page).toHaveURL(/\/agent$/);
  });

  test("the reset response is never cacheable", async ({ page }) => {
    const name = `NoStore Agent ${RUN_ID}`;
    const email = `nostore.agent+${RUN_ID}@agents.example`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createAgentThroughTheUi(page, name, email);
    await openAgentPage(page, name);

    await page.getByRole("button", { name: "Reset password" }).click();
    const responded = page.waitForResponse(
      (response) => response.url().endsWith("/login/password") && response.request().method() === "POST",
    );
    await page.getByRole("dialog").getByRole("button", { name: "Reset password" }).click();
    const response = await responded;

    expect(response.status()).toBe(200);
    expect(response.headers()["cache-control"]).toContain("no-store");
  });

  test("the reset opens, confirms, copies and closes by keyboard", async ({ page }) => {
    const name = `Keyboard Agent ${RUN_ID}`;
    const email = `keyboard.agent+${RUN_ID}@agents.example`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createAgentThroughTheUi(page, name, email);
    await openAgentPage(page, name);

    const trigger = page.getByRole("button", { name: "Reset password" });
    await trigger.focus();
    await page.keyboard.press("Enter");
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();

    // Cancel is first in tab order, then the confirming pill.
    await page.keyboard.press("Tab");
    await page.keyboard.press("Tab");
    await expect(dialog.getByRole("button", { name: "Reset password" })).toBeFocused();
    await page.keyboard.press("Enter");

    await expect(dialog.getByLabel("Generated password")).toBeFocused();
    await page.keyboard.press("Tab");
    const copy = dialog.getByRole("button", { name: "Copy password" });
    await expect(copy).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(dialog.getByRole("status")).toHaveText(/Copied|Couldn.t copy/);

    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
  });
});

async function expectRefused(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Incorrect email or password." })).toBeVisible();
}
