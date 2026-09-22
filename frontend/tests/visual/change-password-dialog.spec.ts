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
  // Same placeholder-session approach as surfaces.spec.ts/viewer-menu.spec.ts: middleware only
  // checks the session cookie's presence, never its validity, and this suite runs with no
  // backend at all.
  await page.context().addCookies([
    {
      name: SESSION_COOKIE_NAME,
      value: "visual-regression-placeholder-session",
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

      // Unlike surfaces.spec.ts/viewer-menu.spec.ts's fixed three-testid mask list: Playwright
      // paints a mask at its target's page position regardless of what's stacked on top at
      // capture time, and this dialog covers a different part of the page at each breakpoint.
      // "pending-approvals-card" sits directly under it everywhere and is dropped outright; on
      // mobile the narrower stacked layout also puts "pending-requests-stat" underneath it, so
      // only "pending-approvals-stat" — never covered at either breakpoint — is masked there.
      // Masking either covered element would paint an opaque box over the dialog itself instead
      // of the (already backdrop-dimmed, already invisible) content actually behind it.
      const mask =
        breakpoint.name === "mobile"
          ? [page.getByTestId("pending-approvals-stat")]
          : [page.getByTestId("pending-approvals-stat"), page.getByTestId("pending-requests-stat")];

      await expect(page).toHaveScreenshot(`change-password-dialog-open-${breakpoint.name}-${theme}.png`, {
        // NOT fullPage: a native `<dialog>` lives in the browser's top layer, fixed to the
        // viewport rather than scrolling with the document — a fullPage capture (which renders
        // beyond the viewport in one shot) recentres the UA's auto-margins against that taller
        // captured height and crops the dialog's own bottom half. The viewport alone always
        // contains the whole dialog, since `showModal()` is exactly what centres it there.
        mask,
      });
    });
  }
}
