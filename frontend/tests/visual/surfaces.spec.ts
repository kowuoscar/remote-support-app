import { test, expect, type Page } from "@playwright/test";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";

interface Surface {
  slug: "manager" | "agent" | "client" | "agent-carriers" | "manager-carriers" | "manager-requests";
  path: string;
  // The placeholder session token; the Carriers pages' tokens tell tests/visual/stub-backend.mjs
  // which role is asking, every other surface's token is unknown to it (see that file).
  session?: string;
  // What marks the page as settled, before capturing.
  ready?: string;
}

const surfaces: Surface[] = [
  { slug: "manager", path: "/manager" },
  { slug: "agent", path: "/agent" },
  { slug: "client", path: "/client" },
  { slug: "agent-carriers", path: "/agent/carriers", session: "visual-agent-session", ready: "carriers" },
  {
    slug: "manager-carriers",
    // Archived shown too, so the muted Badge is covered.
    path: "/manager/carriers?country=UNITED_STATES&archived=1",
    session: "visual-manager-session",
    ready: "carriers",
  },
  // manager-approves-requests ticket: the Pending Requests page, backed by
  // tests/visual/stub-backend.mjs's two fixture Requests (a Tester-raised Provision Smartphone, an
  // Agent-authored Replace SIM) — covers the Pending Approval badge, both a "raised by" and a
  // "logged by" row, and both a Provision and a Replace details summary.
  {
    slug: "manager-requests",
    path: "/manager/requests",
    session: "visual-manager-session",
    ready: "requests",
  },
];

const breakpoints = [
  { name: "desktop", viewport: { width: 1440, height: 900 } },
  { name: "mobile", viewport: { width: 390, height: 844 } },
] as const;

const themes = ["light", "dark"] as const;

async function gotoAndSettle(page: Page, surface: Surface) {
  // The three surfaces sit behind middleware.ts's session-cookie gate (auth-login-flow). This
  // suite renders demo data with no backend running at all (see playwright.config.ts), so it
  // sets a placeholder session cookie directly rather than driving a real login — middleware
  // only checks the cookie's *presence*, never its validity (the backend is the real authority
  // on that, exercised separately by tests/e2e/login.spec.ts against a live backend).
  await page.context().addCookies([
    {
      name: SESSION_COOKIE_NAME,
      value: surface.session ?? "visual-regression-placeholder-session",
      url: "http://127.0.0.1:4173",
    },
  ]);
  await page.goto(surface.path);
  if (surface.ready === "carriers") {
    await page.getByRole("list", { name: "Carriers" }).waitFor({ state: "visible" });
  } else if (surface.ready === "requests") {
    await page.getByRole("table").waitFor({ state: "visible" });
  } else {
    // Dashboards demonstrate the Operate-mode skeleton-loading convention;
    // wait for the real content to land before capturing.
    await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
  }
  await page.evaluate(() => document.fonts.ready);
}

/**
 * Regions that read the real backend, which this suite never runs: they render their unavailable
 * state here, so they're masked rather than frozen into a golden (manager-invoice-review-queue
 * spec, Testing decisions 4). The Manager Dashboard's Pending approvals card and both its count
 * stats (Review Queue, and manager-approves-requests ticket's own Pending Requests) read the
 * backend.
 */
function backendDrivenRegions(page: Page, surface: Surface) {
  if (surface.slug !== "manager") return [];
  return [
    page.getByTestId("pending-approvals-stat"),
    page.getByTestId("pending-requests-stat"),
    page.getByTestId("pending-approvals-card"),
  ];
}

/**
 * The Pending Requests page's own "Waiting" column resolves its fixture Requests' fixed
 * `createdAt` against the *real* current time on every render, so it drifts by a day every day
 * the suite runs — masked here rather than frozen into the golden, the same reasoning
 * `backendDrivenRegions` above uses for backend-unavailable regions, but for a time-drift reason
 * rather than an availability one.
 */
function timeDrivenRegions(page: Page, surface: Surface) {
  if (surface.slug !== "manager-requests") return [];
  return [page.locator("time")];
}

for (const surface of surfaces) {
  for (const breakpoint of breakpoints) {
    for (const theme of themes) {
      test(`${surface.slug} — ${breakpoint.name} — ${theme}`, async ({ page }) => {
        await page.emulateMedia({ colorScheme: theme });
        await page.setViewportSize(breakpoint.viewport);
        await gotoAndSettle(page, surface);

        await expect(page).toHaveScreenshot(
          `${surface.slug}-${breakpoint.name}-${theme}.png`,
          { fullPage: true, mask: [...backendDrivenRegions(page, surface), ...timeDrivenRegions(page, surface)] },
        );
      });
    }
  }
}
