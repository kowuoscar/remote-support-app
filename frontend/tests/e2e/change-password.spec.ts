import { test, expect, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addTester,
  createClientAndContractWithSeededAgent,
  login,
  logout,
} from "./helpers";

/**
 * The whole self-service-password-change journey, end to end (change-password-dialog ticket),
 * driven against a real backend + Postgres — spec.md `## Testing decisions`, "one e2e spec ...
 * one file for this journey step": create a Login through the UI as a Manager, sign in as that
 * person, open the chip menu, change the password, land back on sign-in with the confirmation,
 * sign in with the new password, and be refused with the old.
 *
 * A Tester Login (not a seeded one — spec.md `## Constraints` forbids changing a seeded user's
 * password, since e2e state never rolls back) is the "Login through the UI as a Manager" this
 * journey names: `addTester` is the Manager-facing UI path that creates one.
 */
/**
 * Signs in as the Manager, creates a Client+Contract and a Tester under it with `password`, logs
 * out, then signs back in as that Tester and lands on `/client` — the six-line fixture both tests
 * below shared verbatim (review finding F8). Returns the Tester's email for the caller's own
 * assertions.
 */
async function signedInTesterWithPassword(
  page: Page,
  options: { runId: string; emailPrefix: string; clientLabel: string; password: string },
): Promise<string> {
  const { runId, emailPrefix, clientLabel, password } = options;
  const testerEmail = `${emailPrefix}+${runId}@client.example`;

  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  const { clientId } = await createClientAndContractWithSeededAgent(page, `${clientLabel} ${runId}`);
  await addTester(page, clientId, testerEmail, password);
  await logout(page);

  await login(page, testerEmail, password);
  await expect(page).toHaveURL(/\/client$/);

  return testerEmail;
}

test.describe("change password", () => {
  test("a Tester changes their own password, then must sign in with the new one", async ({ page }) => {
    const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
    const oldPassword = "OldPassw0rd!1";
    const newPassword = "NewPassw0rd!2";

    const testerEmail = await signedInTesterWithPassword(page, {
      runId,
      emailPrefix: "password.change",
      clientLabel: "Password Change",
      password: oldPassword,
    });

    await page.getByTestId("viewer-menu-trigger").click();
    await page.getByRole("menuitem", { name: "Change password" }).click();

    const dialog = page.getByRole("dialog");
    await expect(dialog.getByLabel("Current password")).toBeFocused();
    await dialog.getByLabel("Current password").fill(oldPassword);
    await dialog.getByLabel("New password", { exact: true }).fill(newPassword);
    await dialog.getByLabel("Confirm new password").fill(newPassword);
    await dialog.getByRole("button", { name: "Change password" }).click();

    await expect(page).toHaveURL(/\/login\?passwordChanged=1$/);
    await expect(page.getByRole("status")).toContainText("Your password was changed");

    await login(page, testerEmail, newPassword);
    await expect(page).toHaveURL(/\/client$/);
    await logout(page);

    // Refused with the sign-in page's existing error — no new wording introduced for this case.
    // Scoped by text, not just role="alert": Next's own (empty, aria-live) route announcer div
    // also carries role="alert" once a client-side navigation has happened, which every prior
    // step in this journey (login/logout/the dialog's own redirect) already triggered.
    await page.goto("/login");
    await page.getByLabel("Email").fill(testerEmail);
    await page.getByLabel("Password").fill(oldPassword);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByRole("alert").filter({ hasText: "Incorrect email or password." })).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test("a wrong current password is refused inline, keeps the form and the session", async ({ page }) => {
    const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
    const password = "OriginalPassw0rd!";

    await signedInTesterWithPassword(page, {
      runId,
      emailPrefix: "password.wrong",
      clientLabel: "Wrong Password",
      password,
    });

    await page.getByTestId("viewer-menu-trigger").click();
    await page.getByRole("menuitem", { name: "Change password" }).click();

    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Current password").fill("NotTheRealPassword!");
    await dialog.getByLabel("New password", { exact: true }).fill("BrandNewPassw0rd!");
    await dialog.getByLabel("Confirm new password").fill("BrandNewPassw0rd!");
    await dialog.getByRole("button", { name: "Change password" }).click();

    await expect(dialog.getByRole("alert")).toContainText("not your current password");
    await expect(dialog.getByLabel("Current password")).toHaveAttribute("aria-invalid", "true");
    // Neither signed out nor navigated away: still on the same page, still signed in.
    await expect(page).not.toHaveURL(/\/login/);
    await expect(dialog.getByLabel("New password", { exact: true })).toHaveValue("BrandNewPassw0rd!");

    await page.goto("/client");
    await expect(page).toHaveURL(/\/client$/);
  });
});
