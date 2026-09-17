import { test, expect, type Page } from "@playwright/test";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";

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
  // The three surfaces sit behind middleware.ts's session-cookie gate (auth-login-flow). This
  // suite renders demo data with no backend running at all (see playwright.config.ts), so it
  // sets a placeholder session cookie directly rather than driving a real login — middleware
  // only checks the cookie's *presence*, never its validity (the backend is the real authority
  // on that, exercised separately by tests/e2e/login.spec.ts against a live backend).
  await page.context().addCookies([
    {
      name: SESSION_COOKIE_NAME,
      value: "visual-regression-placeholder-session",
      url: "http://127.0.0.1:4173",
    },
  ]);
  await page.goto(path);
  // Dashboards demonstrate the Operate-mode skeleton-loading convention;
  // wait for the real content to land before capturing.
  await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
  await page.evaluate(() => document.fonts.ready);
}

/**
 * Regions that read the real backend, which this suite never runs: they render their unavailable
 * state here, so they're masked rather than frozen into a golden (manager-invoice-review-queue
 * spec, Testing decisions 4). The Manager Dashboard's Pending approvals card and its count stat
 * both read the Review Queue.
 */
function backendDrivenRegions(page: Page, surface: Surface) {
  if (surface.slug !== "manager") return [];
  return [page.getByTestId("pending-approvals-stat"), page.getByTestId("pending-approvals-card")];
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
          { fullPage: true, mask: backendDrivenRegions(page, surface) },
        );
      });
    }
  }
}
