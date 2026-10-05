import { test, expect, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addTester,
  createClientAndContractWithSeededAgent,
  login,
  readRevealedPasswordAndClose,
} from "./helpers";

/**
 * A Manager switches off a departed person's Login and back on (deactivate-a-login spec), driven
 * against a real backend + Postgres at the accessibility-tree level. This is the Agent half
 * (deactivate-an-agents-login-ui ticket): the Manager creates its own Agent through the UI, never
 * a seeded Login; a second browser context signs in as that Agent. The Tester half
 * (deactivate-a-testers-login-ui ticket) does the same from the Client's page, on its own Client.
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

async function clientWithTester(page: Page, label: string) {
  const email = `${label}.tester+${RUN_ID}@client.example`;
  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  const { clientId } = await createClientAndContractWithSeededAgent(page, `${label} Client ${RUN_ID}`);
  const password = await addTester(page, clientId, email);
  return { clientId, email, password };
}

test.describe("manager deactivates a Tester's Login", () => {
  test("manager-deactivates-tester-second-context-lands-on-sign-in-then-reactivate-signs-in", async ({
    page,
    browser,
    baseURL,
  }) => {
    const { email, password } = await clientWithTester(page, "Deactivate");

    const testerContext = await browser.newContext({ baseURL: baseURL ?? undefined });
    const testerPage = await testerContext.newPage();
    await login(testerPage, email, password);
    await expect(testerPage).toHaveURL(/\/client$/);

    const row = page.getByRole("row").filter({ hasText: email });
    const deactivate = page.getByRole("button", { name: `Deactivate login for ${email}` });
    await expect(page.getByRole("button", { name: `Reset password for ${email}` })).toBeVisible();
    const roleBadge = ((await row.getByRole("cell").nth(1).textContent()) ?? "").trim();
    expect(roleBadge).not.toBe("");

    // Escape sends nothing: the Tester still reaches the console.
    let changeRequests = 0;
    page.on("request", (request) => {
      if (/\/testers\/[^/]+\/(de|re)activate$/.test(request.url())) changeRequests += 1;
    });
    await deactivate.click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("heading", { name: `Deactivate login for ${email}` })).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(deactivate).toBeFocused();
    expect(changeRequests).toBe(0);
    await testerPage.goto("/client");
    await expect(testerPage).toHaveURL(/\/client$/);

    // Confirm: the dialog closes, the status announces, the tag appears, focus keeps the row's place.
    await deactivate.click();
    await dialog.getByRole("button", { name: "Deactivate login" }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByText(`Login deactivated for ${email}.`)).toBeAttached();
    await expect(page.getByText(/^Deactivated since \d{1,2} \w{3} \d{4}$/)).toBeAttached();
    const reactivate = page.getByRole("button", { name: `Reactivate login for ${email}` });
    await expect(reactivate).toBeVisible();
    await expect(reactivate).toBeFocused();
    await expect(deactivate).toHaveCount(0);
    // The row is otherwise unchanged: same role badge, same email.
    await expect(row.getByText(roleBadge, { exact: true })).toBeVisible();
    await expect(row.getByText(email, { exact: true })).toBeVisible();

    // The Tester's next navigation lands on sign-in; the right password says why.
    await testerPage.goto("/client");
    await expect(testerPage).toHaveURL(/\/login/);
    await submitSignIn(testerPage, email, password);
    await expect(
      testerPage
        .getByRole("alert")
        .filter({ hasText: "This login has been deactivated. Ask your Manager if you need access again." }),
    ).toBeVisible();

    // Reactivate by keyboard: the tag goes, the same password reaches the Client console.
    await reactivate.focus();
    await page.keyboard.press("Enter");
    await expect(dialog.getByRole("heading", { name: `Reactivate login for ${email}` })).toBeVisible();
    await expect(dialog.getByRole("button", { name: "Cancel" })).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(dialog.getByRole("button", { name: "Reactivate login" })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(dialog).toBeHidden();
    await expect(page.getByText(`Login reactivated for ${email}.`)).toBeAttached();
    await expect(page.getByText("Deactivated", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: `Deactivate login for ${email}` })).toBeFocused();

    await submitSignIn(testerPage, email, password);
    await expect(testerPage).toHaveURL(/\/client$/);
    await testerContext.close();
  });

  test("keyboard walk: Tab to the row action, open, Escape, open again, confirm, with a focus ring at each stop", async ({
    page,
  }) => {
    const { email } = await clientWithTester(page, "Keyboard");
    const deactivate = page.getByRole("button", { name: `Deactivate login for ${email}` });
    const dialog = page.getByRole("dialog");

    await page.getByRole("button", { name: `Reset password for ${email}` }).focus();
    await page.keyboard.press("Tab");
    await expect(deactivate).toBeFocused();
    await expect(deactivate).toHaveCSS("outline-style", "solid");

    await page.keyboard.press("Enter");
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole("button", { name: "Cancel" })).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(deactivate).toBeFocused();

    await page.keyboard.press("Enter");
    await expect(dialog.getByRole("button", { name: "Cancel" })).toBeFocused();
    await page.keyboard.press("Tab");
    const confirm = dialog.getByRole("button", { name: "Deactivate login" });
    await expect(confirm).toBeFocused();
    await expect(confirm).toHaveCSS("outline-style", "solid");
    await page.keyboard.press("Enter");
    await expect(dialog).toBeHidden();
    await expect(page.getByRole("button", { name: `Reactivate login for ${email}` })).toBeFocused();
  });

  test("mobile viewport: the tag, both row actions and the dialog fit inside the viewport", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    const { email } = await clientWithTester(page, "Mobile");
    await page.getByRole("button", { name: `Deactivate login for ${email}` }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Deactivate login" }).click();
    await expect(page.getByRole("dialog")).toBeHidden();

    const reactivate = page.getByRole("button", { name: `Reactivate login for ${email}` });
    await expect(reactivate).toBeVisible();
    for (const target of [
      page.getByRole("button", { name: `Reset password for ${email}` }),
      reactivate,
      page.getByText("Deactivated", { exact: true }),
    ]) {
      await target.scrollIntoViewIfNeeded();
      const box = (await target.boundingBox())!;
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(390);
    }

    await reactivate.click();
    const box = (await page.getByRole("dialog").boundingBox())!;
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(390);
    expect(box.y + box.height).toBeLessThanOrEqual(844);
  });
});
