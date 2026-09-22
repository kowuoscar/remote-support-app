import { test, expect, type Page } from "@playwright/test";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";

/**
 * The viewer chip's open menu (viewer-chip-menu ticket), against the same stub-backend fixtures
 * tests/visual/surfaces.spec.ts uses. Deliberately a **new file**, not an addition to that one:
 * `surfaces.spec.ts`'s `gotoAndSettle` only navigates and waits for a `ready` marker — it has no
 * hook for interacting with the page (opening the menu) before capture — and it is not touched by
 * this ticket. That file's 32 existing goldens are expected to recapture on their own (every
 * surface's top bar changes, see `## Regression`), not gain a 33rd golden here.
 *
 * One surface (the Manager dashboard) is enough to cover the menu's open panel per theme ×
 * breakpoint; every other surface shares the identical `ViewerMenu` component, so a second
 * surface would only re-prove the same panel against a different page background.
 */
const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

async function openMenuOnManagerDashboard(page: Page) {
  // Same placeholder-session approach as surfaces.spec.ts: middleware only checks the session
  // cookie's presence, never its validity, and this suite runs with no backend at all.
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
  await expect(page.getByRole("menu")).toBeVisible();
}

for (const breakpoint of breakpoints) {
  for (const theme of themes) {
    test(`manager top bar — menu open — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await openMenuOnManagerDashboard(page);

      await expect(page).toHaveScreenshot(`manager-menu-open-${breakpoint.name}-${theme}.png`, {
        fullPage: true,
        // Same backend-driven regions surfaces.spec.ts masks for the "manager" slug — this suite
        // never runs a real backend, so these render their unavailable state.
        mask: [
          page.getByTestId("pending-approvals-stat"),
          page.getByTestId("pending-requests-stat"),
          page.getByTestId("pending-approvals-card"),
        ],
      });
    });
  }
}
