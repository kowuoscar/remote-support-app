import { test, expect } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/**
 * The Manager's reset-password dialog on a Client's page (reset-a-testers-password-ui ticket): its
 * Confirm step, opened from a Tester row of the table, against tests/visual/stub-backend.mjs's
 * fixture Client. Its own file for the reason reset-password-dialog.spec.ts gives.
 */
const CLIENT_PAGE = "/manager/clients/55555555-0000-0000-0000-000000000001";

const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

for (const breakpoint of breakpoints) {
  for (const theme of themes) {
    test(`reset-tester-password dialog — confirm — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await gotoWithSession(page, CLIENT_PAGE, "visual-manager-session");
      await page.evaluate(() => document.fonts.ready);
      await page.getByRole("button", { name: "Reset password for nadia.okafor@aurora.example" }).scrollIntoViewIfNeeded();
      await page.getByRole("button", { name: "Reset password for nadia.okafor@aurora.example" }).click();
      await expect(
        page.getByRole("dialog").getByRole("heading", { name: "Reset password for nadia.okafor@aurora.example" }),
      ).toBeVisible();

      await expect(page).toHaveScreenshot(`reset-tester-password-dialog-confirm-${breakpoint.name}-${theme}.png`);
    });
  }
}
