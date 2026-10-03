import { test, expect, type Page } from "@playwright/test";
import { SEEDED_USERS, login, readRevealedPasswordAndClose } from "./helpers";

/**
 * A Manager switches off a departed person's Login and back on (deactivate-a-login spec), driven
 * against a real backend + Postgres at the accessibility-tree level. This is the Agent half
 * (deactivate-an-agents-login-ui ticket): the Manager creates its own Agent through the UI, never
 * a seeded Login; a second browser context signs in as that Agent. The Tester half follows with
 * the Client's page.
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

async function submitSignIn(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

test.describe("manager deactivates an Agent's Login", () => {
  test("manager-deactivates-agent-second-context-lands-on-sign-in-then-reactivate-signs-in", async ({
    page,
    browser,
    baseURL,
  }) => {
    const name = `Deactivate Agent ${RUN_ID}`;
    const email = `deactivate.agent+${RUN_ID}@agents.example`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const password = await createAgentThroughTheUi(page, name, email);

    const agentContext = await browser.newContext({ baseURL: baseURL ?? undefined });
    const agentPage = await agentContext.newPage();
    await login(agentPage, email, password);
    await expect(agentPage).toHaveURL(/\/agent$/);

    // The Agent's page offers Reset password and Deactivate login, and no Create login.
    await page.goto("/manager/agents");
    await page.getByRole("link", { name }).click();
    await expect(page).toHaveURL(/\/manager\/agents\/.+/);
    await expect(page.getByRole("button", { name: "Reset password" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Create login" })).toHaveCount(0);
    const deactivate = page.getByRole("button", { name: "Deactivate login" });

    // Escape sends nothing: the Agent still signs in.
    let changeRequests = 0;
    page.on("request", (request) => {
      if (/\/login\/(de|re)activate$/.test(request.url())) changeRequests += 1;
    });
    await deactivate.click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("heading", { name: `Deactivate login for ${name}` })).toBeVisible();
    await expect(dialog).toContainText(`${email} will no longer be able to sign in, and is signed out at once.`);
    await expect(dialog).toContainText("Their record, requests and invoices stay as they are.");
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(deactivate).toBeFocused();
    expect(changeRequests).toBe(0);
    await agentPage.goto("/agent");
    await expect(agentPage).toHaveURL(/\/agent$/);

    // Confirm: the dialog closes, focus stays put, the status announces, the tag appears.
    await deactivate.click();
    await dialog.getByRole("button", { name: "Deactivate login" }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByText(`Login deactivated for ${email}.`)).toBeAttached();
    await expect(page.getByText(/^Deactivated since \d{1,2} \w{3} \d{4}$/)).toBeAttached();
    const reactivate = page.getByRole("button", { name: "Reactivate login" });
    await expect(reactivate).toBeVisible();
    await expect(reactivate).toBeFocused();
    await expect(page.getByRole("button", { name: "Deactivate login" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Create login" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Reset password" })).toBeVisible();

    // The Agent's next navigation lands on sign-in; the right password says why, a wrong one does not.
    await agentPage.goto("/agent");
    await expect(agentPage).toHaveURL(/\/login/);
    await submitSignIn(agentPage, email, password);
    await expect(
      agentPage
        .getByRole("alert")
        .filter({ hasText: "This login has been deactivated. Ask your Manager if you need access again." }),
    ).toBeVisible();
    await submitSignIn(agentPage, email, `${password}-wrong`);
    await expect(agentPage.getByRole("alert").filter({ hasText: "Incorrect email or password." })).toBeVisible();

    // Reactivate by keyboard: the tag goes, the same password signs in again.
    await reactivate.focus();
    await page.keyboard.press("Enter");
    await expect(dialog.getByRole("heading", { name: `Reactivate login for ${name}` })).toBeVisible();
    await expect(dialog.getByRole("button", { name: "Cancel" })).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(dialog.getByRole("button", { name: "Reactivate login" })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(dialog).toBeHidden();
    await expect(page.getByText(`Login reactivated for ${email}.`)).toBeAttached();
    await expect(page.getByText("Deactivated", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Deactivate login" })).toBeFocused();

    await submitSignIn(agentPage, email, password);
    await expect(agentPage).toHaveURL(/\/agent$/);
    await agentContext.close();
  });
});
