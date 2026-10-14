import { test, expect, type Page } from "@playwright/test";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";
import { gotoWithSession } from "./helpers";

interface Surface {
  slug:
    | "manager"
    | "agent"
    | "client"
    | "agent-carriers"
    | "manager-carriers"
    | "manager-requests"
    | "agent-stock"
    | "manager-stock";
  path: string;
  // The placeholder session token; the Carriers pages' tokens tell tests/visual/stub-backend.mjs
  // which role is asking, every other surface's token is unknown to it (see that file).
  session?: string;
  // What marks the page as settled, before capturing.
  ready?: string;
}

const surfaces: Surface[] = [
  { slug: "manager", path: "/manager", session: "visual-manager-session" },
  { slug: "agent", path: "/agent", session: "visual-agent-session" },
  { slug: "client", path: "/client", session: "visual-tester-session" },
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
  // agent-stock ticket: the Agent's own Stock page and the Manager's (every Agent's, unfiltered),
  // backed by tests/visual/stub-backend.mjs's fixture units — both non-empty, so both tables
  // (Smartphones, SIM Cards) and the Manager's own Agent column are covered.
  { slug: "agent-stock", path: "/agent/stock", session: "visual-agent-session", ready: "stock" },
  { slug: "manager-stock", path: "/manager/stock", session: "visual-manager-session", ready: "stock" },
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
  } else if (surface.ready === "stock") {
    // Two tables (Smartphones, SIM Cards) render on this page — .first() avoids a strict-mode
    // violation from a bare getByRole("table").
    await page.getByRole("table").first().waitFor({ state: "visible" });
  } else {
    // Dashboards demonstrate the Operate-mode skeleton-loading convention;
    // wait for the real content to land before capturing.
    await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
  }
  await page.evaluate(() => document.fonts.ready);
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
          { fullPage: true, mask: timeDrivenRegions(page, surface) },
        );
      });
    }
  }
}

// real-agent-dashboard: states no golden captures. The stub answers GET /api/me/agent with a 404
// for the unlinked token and a 500 for the failing-identity token (tests/visual/stub-backend.mjs).
test.describe("the Agent dashboard's identity states", () => {
  test("unlinked-agent-shows-not-linked-message", async ({ page }) => {
    await gotoWithSession(page, "/agent", "visual-agent-unlinked-session");

    await expect(page.getByText("Your login isn't linked to an Agent record yet")).toBeVisible();
    await expect(page.getByTestId("dashboard-ready")).toHaveCount(0);
  });

  test("failing-identity-shows-page-level-message", async ({ page }) => {
    await gotoWithSession(page, "/agent", "visual-agent-failing-identity-session");

    await expect(page.getByText(/Couldn.t load your dashboard/)).toBeVisible();
    await expect(page.getByText("Couldn't load your dashboard — reload the page to try again")).toHaveCount(1);
    await expect(page.getByTestId("dashboard-ready")).toHaveCount(0);
  });

  test("degraded-invoice-shows-both-invoice-cards-unavailable", async ({ page }) => {
    await gotoWithSession(page, "/agent", "visual-agent-degraded-session");

    await expect(page.getByTestId("dashboard-ready")).toBeVisible();
    for (const testId of ["local-support-fees-stat", "invoice-status-stat"]) {
      const card = page.getByTestId(testId);
      await expect(card).toContainText("—");
      await expect(card).toContainText("Couldn't load your invoice");
    }
    // The header and the standing amounts do not depend on the invoice.
    await expect(page.getByText("+ $500 Rollout Advance")).toBeVisible();
  });
});

// real-client-dashboard: states no golden captures. The stub answers GET /api/me/client with the
// Client for the linked Tester, a 404 for the unlinked token and a 500 for the failing-identity
// token (tests/visual/stub-backend.mjs).
test.describe("the Client dashboard's identity states", () => {
  test("tester-header-and-chip-name-their-own-client-and-login", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-session");

    const header = page.locator("header").first();
    await expect(header).toContainText("Solstice Retail Group");
    await expect(header).toContainText("dana.whitfield@solsticeretail.example · Tester");
    await expect(header).not.toContainText("Aurora Retail Group");
    await expect(header).not.toContainText("Nadia Okafor");
  });

  test("tester-header-keeps-the-full-client-name-at-390px", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await gotoWithSession(page, "/client", "visual-tester-session");

    await expect(page.locator("header").first()).toContainText("Solstice Retail Group");
  });

  test("unlinked-tester-shows-not-linked-message", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-unlinked-session");

    await expect(page.getByText("Your login isn't linked to a Client yet")).toBeVisible();
    await expect(
      page.getByText("Ask your Manager to link your login to your Client before you can see your dashboard."),
    ).toBeVisible();
    await expect(page.getByTestId("dashboard-ready")).toHaveCount(0);
  });

  test("failing-identity-shows-the-single-dashboard-error", async ({ page }) => {
    await gotoWithSession(page, "/client", "visual-tester-failing-identity-session");

    await expect(page.getByText("Couldn't load your dashboard — reload the page to try again")).toHaveCount(1);
    await expect(page.locator("header").first()).not.toContainText("Solstice Retail Group");
    await expect(page.getByTestId("dashboard-ready")).toHaveCount(0);
  });
});

// real-manager-dashboard: states no golden captures. The stub answers the degraded Manager's
// GET /api/me and GET /api/agents with 500, and the empty Manager's GET /api/me without a `name`
// (tests/visual/stub-backend.mjs).
test.describe("the Manager dashboard's identity and count states", () => {
  test("named-manager-chip-and-counts-come-from-the-backend", async ({ page }) => {
    await gotoWithSession(page, "/manager", "visual-manager-session");

    await expect(page.getByTestId("dashboard-ready")).toBeVisible();
    await expect(page.locator("header").first()).toContainText("Yazan Abuhassan · Manager");
    await expect(page.getByText("Priya Ashford")).toHaveCount(0);
    await expect(page.getByTestId("clients-stat")).toContainText("1");
    await expect(page.getByTestId("clients-stat")).toContainText("In this tenant");
    await expect(page.getByTestId("agents-stat")).toContainText("3");
    await expect(page.getByTestId("contracts-stat")).toContainText("2");
  });

  test("unnamed-manager-chip-reads-the-username", async ({ page }) => {
    await gotoWithSession(page, "/manager", "visual-manager-empty-session");

    await expect(page.getByTestId("dashboard-ready")).toBeVisible();
    await expect(page.locator("header").first()).toContainText("manager@example.com · Manager");
  });

  test("manager-money-cards-show-one-line-per-currency-with-the-reads-month", async ({ page }) => {
    await gotoWithSession(page, "/manager", "visual-manager-session");

    await expect(page.getByTestId("dashboard-ready")).toBeVisible();
    const billed = page.getByTestId("billed-stat");
    await expect(billed).toContainText("£2,310.00");
    await expect(billed).toContainText("$1,284.50");
    await expect(billed).toContainText("Sent or approved · September 2026");
    const payout = page.getByTestId("payout-stat");
    await expect(payout).toContainText("$2,835.99");
    await expect(payout).toContainText("Approved or paid · September 2026");
  });

  test("empty-manager-money-cards-read-a-plain-zero", async ({ page }) => {
    await gotoWithSession(page, "/manager", "visual-manager-empty-session");

    await expect(page.getByTestId("dashboard-ready")).toBeVisible();
    for (const [id, meta] of [
      ["billed-stat", "Sent or approved · September 2026"],
      ["payout-stat", "Approved or paid · September 2026"],
    ] as const) {
      const card = page.getByTestId(id);
      await expect(card).toHaveText(new RegExp(`^.*0${meta}$`));
      await expect(card).not.toContainText(/[$£€]|USD|GBP/);
    }
  });

  test("degraded-manager-shows-plain-chip-and-agents-unavailable-with-other-cards-showing", async ({ page }) => {
    await gotoWithSession(page, "/manager", "visual-manager-degraded-session");

    await expect(page.getByTestId("dashboard-ready")).toBeVisible();
    const chip = page.locator('[data-testid="viewer-menu-trigger"]');
    await expect(chip).toHaveText("Manager");
    const agents = page.getByTestId("agents-stat");
    await expect(agents).toContainText("—");
    await expect(agents).toContainText("Couldn’t load — open Agents");
    for (const id of ["billed-stat", "payout-stat"]) {
      const card = page.getByTestId(id);
      await expect(card).toContainText("—");
      await expect(card).toContainText("Couldn’t load this month’s totals");
    }
    await expect(page.getByTestId("clients-stat")).toContainText("In this tenant");
    await expect(page.getByTestId("contracts-stat")).toContainText("Client × Agent pairs");
    await expect(page.getByTestId("pending-approvals-stat")).toContainText("2");
    await expect(page.getByTestId("pending-requests-stat")).toContainText("2");
  });
});
