import { test, expect, type Page } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/**
 * The Manager's reset-password dialog on an Agent's page (reset-an-agents-password-ui ticket): its
 * Confirm step and its one-time reveal step (a fixed stub password), against
 * tests/visual/stub-backend.mjs's fixture Agent with a Login (Jordan Ellis). Its own file, for the
 * reason change-password-dialog.spec.ts gives: it must interact with the page before capturing.
 */
const AGENT_PAGE = "/manager/agents/a0000000-0000-0000-0000-000000000001";

const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

async function openResetDialog(page: Page) {
  await gotoWithSession(page, AGENT_PAGE, "visual-manager-session");
  await page.evaluate(() => document.fonts.ready);
  await page.getByRole("button", { name: "Reset password" }).click();
  await expect(page.getByRole("dialog").getByRole("heading", { name: "Reset password for Jordan Ellis" })).toBeVisible();
}

for (const breakpoint of breakpoints) {
  for (const theme of themes) {
    test(`reset-password dialog — confirm — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await openResetDialog(page);

      // Viewport, not fullPage: a native <dialog> lives in the top layer (see change-password-dialog.spec.ts).
      await expect(page).toHaveScreenshot(`reset-password-dialog-confirm-${breakpoint.name}-${theme}.png`);
    });

    test(`reset-password dialog — reveal — ${breakpoint.name} — ${theme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: theme });
      await page.setViewportSize(breakpoint.viewport);
      await openResetDialog(page);
      await page.getByRole("dialog").getByRole("button", { name: "Reset password" }).click();
      await expect(page.getByRole("dialog").getByLabel("Generated password")).toHaveValue("k7mq-x3vh-p9te");

      await expect(page).toHaveScreenshot(`reset-password-dialog-reveal-${breakpoint.name}-${theme}.png`);
    });
  }
}
