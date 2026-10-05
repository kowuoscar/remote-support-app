import { test, expect, type Page } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/**
 * The Manager's deactivate and reactivate confirm steps on a Client's page
 * (deactivate-a-testers-login-ui ticket), opened from a Tester row against
 * tests/visual/stub-backend.mjs's fixture Client: Nadia Okafor's Login is active, Tomas
 * Lindqvist's is deactivated. Its own file, for the reason reset-password-dialog.spec.ts gives.
 */
const CLIENT_PAGE = "/manager/clients/55555555-0000-0000-0000-000000000001";

const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

async function openConfirm(page: Page, action: string, title: string) {
  await gotoWithSession(page, CLIENT_PAGE, "visual-manager-session");
  await page.evaluate(() => document.fonts.ready);
  const trigger = page.getByRole("button", { name: action });
  await trigger.scrollIntoViewIfNeeded();
  await trigger.click();
  await expect(page.getByRole("dialog").getByRole("heading", { name: title })).toBeVisible();
}

for (const breakpoint of breakpoints) {
  for (const theme of themes) {
    test(`deactivate-tester-login dialog — confirm — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await openConfirm(
        page,
        "Deactivate login for nadia.okafor@aurora.example",
        "Deactivate login for nadia.okafor@aurora.example",
      );

      await expect(page).toHaveScreenshot(`deactivate-tester-login-dialog-confirm-${breakpoint.name}-${theme}.png`);
    });

    test(`reactivate-tester-login dialog — confirm — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await openConfirm(
        page,
        "Reactivate login for tomas.lindqvist@aurora.example",
        "Reactivate login for tomas.lindqvist@aurora.example",
      );

      await expect(page).toHaveScreenshot(`reactivate-tester-login-dialog-confirm-${breakpoint.name}-${theme}.png`);
    });
  }
}
