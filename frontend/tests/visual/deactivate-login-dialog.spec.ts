import { test, expect, type Page } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/**
 * The Manager's deactivate and reactivate confirm steps on an Agent's page
 * (deactivate-an-agents-login-ui ticket), against tests/visual/stub-backend.mjs's fixture Agents:
 * Jordan Ellis (active Login) and Ana Costa (deactivated Login). Its own file, for the reason
 * reset-password-dialog.spec.ts gives: it must interact with the page before capturing.
 */
const ACTIVE_AGENT_PAGE = "/manager/agents/a0000000-0000-0000-0000-000000000001";
const DEACTIVATED_AGENT_PAGE = "/manager/agents/a0000000-0000-0000-0000-000000000003";

const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

async function openConfirm(page: Page, path: string, action: string, title: string) {
  await gotoWithSession(page, path, "visual-manager-session");
  await page.evaluate(() => document.fonts.ready);
  await page.getByRole("button", { name: action }).click();
  await expect(page.getByRole("dialog").getByRole("heading", { name: title })).toBeVisible();
}

for (const breakpoint of breakpoints) {
  for (const theme of themes) {
    test(`deactivate-login dialog — confirm — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await openConfirm(page, ACTIVE_AGENT_PAGE, "Deactivate login", "Deactivate login for Jordan Ellis");

      // Viewport, not fullPage: a native <dialog> lives in the top layer (see change-password-dialog.spec.ts).
      await expect(page).toHaveScreenshot(`deactivate-login-dialog-confirm-${breakpoint.name}-${theme}.png`);
    });

    test(`reactivate-login dialog — confirm — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await openConfirm(page, DEACTIVATED_AGENT_PAGE, "Reactivate login", "Reactivate login for Ana Costa");

      await expect(page).toHaveScreenshot(`reactivate-login-dialog-confirm-${breakpoint.name}-${theme}.png`);
    });
  }
}
