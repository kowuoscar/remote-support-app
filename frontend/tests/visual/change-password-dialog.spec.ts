import { test, expect, type Page } from "@playwright/test";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";

/**
 * The change-password dialog, open (change-password-dialog ticket), against the same
 * stub-backend fixtures `tests/visual/surfaces.spec.ts` and `viewer-menu.spec.ts` use.
 * Deliberately its own file, the same reasoning `viewer-menu.spec.ts` gives for being separate
 * from `surfaces.spec.ts`: this needs to interact with the page (open the menu, then the dialog)
 * before capturing, which those files' shared `gotoAndSettle` has no hook for.
 *
 * One surface (the Manager dashboard) is enough: the dialog is identical in every console, so a
 * second surface would only re-prove the same `DialogShell` against a different page background.
 */
const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

async function openChangePasswordDialogOnManagerDashboard(page: Page) {
  // The stub backend's named Manager session (tests/visual/stub-backend.mjs), as in
  // surfaces.spec.ts/viewer-menu.spec.ts: every region has data, so nothing is masked.
  await page.context().addCookies([
    {
      name: SESSION_COOKIE_NAME,
      value: "visual-manager-session",
      url: "http://127.0.0.1:4173",
    },
  ]);
  await page.goto("/manager");
  await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
  await page.evaluate(() => document.fonts.ready);

  await page.locator('[aria-haspopup="menu"]').click();
  await page.getByRole("menuitem", { name: "Change password" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
}

for (const breakpoint of breakpoints) {
  for (const theme of themes) {
    test(`change-password dialog — open — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await openChangePasswordDialogOnManagerDashboard(page);

      await expect(page).toHaveScreenshot(`change-password-dialog-open-${breakpoint.name}-${theme}.png`, {
        // NOT fullPage: a native `<dialog>` lives in the browser's top layer, fixed to the
        // viewport rather than scrolling with the document — a fullPage capture (which renders
        // beyond the viewport in one shot) recentres the UA's auto-margins against that taller
        // captured height and crops the dialog's own bottom half. The viewport alone always
        // contains the whole dialog, since `showModal()` is exactly what centres it there.
      });
    });
  }
}
