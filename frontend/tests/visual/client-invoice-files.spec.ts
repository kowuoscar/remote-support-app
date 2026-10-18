import { test, expect, type Page } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/**
 * The Carrier invoice files states no surface golden captures (carrier-invoice-per-sim): the attach
 * dialog open, the inline Remove confirm open, the paused files section, and the Manager's Client
 * Invoice detail with its check mark. All read the stub backend's first Client Invoice (Aurora's
 * September draft: three Postpaid SIM lines, the first with its file, so one check mark, one
 * attached file and a Send held back by its note).
 */
const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

const FILE_NAME = "Aurora - September 2026 - Verizon - +44 7700 900123.pdf";
const MANAGER_INVOICE_PATH = "/manager/invoices/client/99999999-0000-0000-0000-000000000001";

async function openAgentInvoices(page: Page, session: string) {
  await gotoWithSession(page, "/agent/client-invoices", session);
  await page.getByTestId("client-invoice-card").waitFor({ state: "visible" });
  await page.evaluate(() => document.fonts.ready);
}

/** The page never scrolls sideways (DESIGN.md Layout). */
async function expectNoSidewaysScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

for (const breakpoint of breakpoints) {
  for (const theme of themes) {
    const suffix = `${breakpoint.name}-${theme}`;

    test.describe(suffix, () => {
      test.beforeEach(async ({ page }) => {
        await page.emulateMedia({ colorScheme: theme });
        await page.setViewportSize(breakpoint.viewport);
      });

      test("attach dialog — open", async ({ page }) => {
        await openAgentInvoices(page, "visual-agent-invoices-session");
        await page.getByRole("button", { name: "Attach carrier invoice" }).click();
        await expect(page.getByRole("dialog", { name: "Attach carrier invoice" })).toBeVisible();

        // Viewport only: a native dialog sits in the top layer (see change-name-dialog.spec.ts).
        await expect(page).toHaveScreenshot(`client-invoice-attach-dialog-open-${suffix}.png`);
      });

      test("remove confirm — open", async ({ page }) => {
        await openAgentInvoices(page, "visual-agent-invoices-session");
        await page.getByRole("button", { name: `Remove ${FILE_NAME}` }).click();
        await expect(page.getByText("Remove this file?")).toBeVisible();

        // The filename stays readable beside the open confirm and the page does not scroll sideways.
        const name = page.getByText(FILE_NAME, { exact: true });
        await expect(name).toBeVisible();
        const clipped = await name.evaluate((node) => node.scrollWidth > node.clientWidth);
        expect(clipped).toBe(false);
        await expectNoSidewaysScroll(page);

        // Clicking scrolled the page; capture from the top with no hover state baked in.
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.mouse.move(0, 0);

        await expect(page).toHaveScreenshot(`client-invoice-remove-confirm-open-${suffix}.png`, {
          fullPage: true,
        });
      });

      test("files section — paused", async ({ page }) => {
        await openAgentInvoices(page, "visual-agent-invoices-paused-session");
        await expect(page.getByText(/Attaching, removing and downloading are paused/)).toBeVisible();
        await expectNoSidewaysScroll(page);

        await expect(page).toHaveScreenshot(`client-invoice-files-paused-${suffix}.png`, { fullPage: true });
      });

      test("manager detail — check mark", async ({ page }) => {
        await gotoWithSession(page, MANAGER_INVOICE_PATH, "visual-manager-session");
        await expect(page.getByRole("img", { name: "Carrier invoice attached" })).toHaveCount(1);
        await page.evaluate(() => document.fonts.ready);

        await expect(page).toHaveScreenshot(`manager-client-invoice-check-mark-${suffix}.png`, {
          fullPage: true,
        });
      });
    });
  }
}
