import { test, expect, type Page } from "@playwright/test";

interface Surface {
  slug: "manager" | "agent" | "client";
  path: string;
}

const surfaces: Surface[] = [
  { slug: "manager", path: "/manager" },
  { slug: "agent", path: "/agent" },
  { slug: "client", path: "/client" },
];

const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

async function gotoAndSettle(page: Page, path: string) {
  await page.goto(path);
  // Dashboards demonstrate the Operate-mode skeleton-loading convention;
  // wait for the real content to land before capturing.
  await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
  await page.evaluate(() => document.fonts.ready);
}

for (const surface of surfaces) {
  for (const breakpoint of breakpoints) {
    for (const theme of themes) {
      test(`${surface.slug} — ${breakpoint.name} — ${theme}`, async ({ page }) => {
        await page.emulateMedia({ colorScheme: theme });
        await page.setViewportSize(breakpoint.viewport);
        await gotoAndSettle(page, surface.path);

        await expect(page).toHaveScreenshot(
          `${surface.slug}-${breakpoint.name}-${theme}.png`,
          { fullPage: true },
        );
      });
    }
  }
}
