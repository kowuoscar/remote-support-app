import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  createClientAndContractWithSeededAgent,
  login,
  logout,
} from "./helpers";

/**
 * The Agent picks a Client's invoice from a side list on a desktop (client-invoices-side-list spec,
 * contract-side-list-on-desktop ticket), driven against a real backend + Postgres. Prior art:
 * send-a-client-invoice-back.spec.ts for the `setViewportSize` switches. Never put "Agent" in a
 * Client name here: getByLabel("Agent") matches option text elsewhere.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const ALPHA = `Sidelist Alpha ${RUN_ID}`;
const ZULU = `Sidelist Zulu ${RUN_ID}`;
const SIM_NUMBER = `+1-555-${RUN_ID.slice(-4)}`;

test.describe("client invoices side list", () => {
  test("a desktop Agent picks an invoice from the column; a phone keeps the switcher", async ({ page, context }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    // Created in reverse alphabetical order, so the column's order is not creation order.
    await createClientAndContractWithSeededAgent(page, ZULU);
    const { contractId: alphaContractId } = await createClientAndContractWithSeededAgent(page, ALPHA);
    // Only Alpha's invoice carries a Postpaid SIM line: its row is how a swap is seen.
    await addPostpaidSimCard(page, alphaContractId, SIM_NUMBER, "25.00");
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);

    const requests: string[] = [];
    page.on("request", (request) => {
      if (request.isNavigationRequest() || request.url().includes("/api/") || request.url().includes("_rsc")) {
        requests.push(request.url());
      }
    });

    await page.goto("/agent/client-invoices");
    const clients = page.getByRole("navigation", { name: "Clients" });
    await expect(clients).toBeVisible();
    await expect(page.locator('[aria-haspopup="listbox"]')).toBeHidden();

    // Both rows, in alphabetical order; the first row in the column is the selected one.
    const names = await clients.getByRole("link").allTextContents();
    expect(names.indexOf(ALPHA)).toBeGreaterThanOrEqual(0);
    expect(names.indexOf(ALPHA)).toBeLessThan(names.indexOf(ZULU));
    await expect(clients.getByRole("link").first()).toHaveAttribute("aria-current", "true");
    await expect.poll(async () => (await clients.boundingBox())?.width).toBe(256);

    // A click swaps the invoice in place: no document, no backend request.
    const alpha = clients.getByRole("link", { name: ALPHA, exact: true });
    const zulu = clients.getByRole("link", { name: ZULU, exact: true });
    await alpha.click();
    await expect(alpha).toHaveAttribute("aria-current", "true");
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toBeVisible();
    requests.length = 0;
    await zulu.click();
    await expect(zulu).toHaveAttribute("aria-current", "true");
    await expect(alpha).not.toHaveAttribute("aria-current");
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toHaveCount(0);
    await page.waitForTimeout(300);
    expect(requests).toEqual([]);
    await alpha.click();
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toBeVisible();

    // Tab and Enter: every row is reachable by keyboard and shows the focus ring.
    await alpha.focus();
    await expect(alpha).toHaveCSS("outline-style", "solid");
    await page.keyboard.press("Tab");
    const next = clients.getByRole("link").nth(names.indexOf(ALPHA) + 1);
    await expect(next).toBeFocused();
    await expect(next).toHaveCSS("outline-style", "solid");
    await page.keyboard.press("Enter");
    await expect(next).toHaveAttribute("aria-current", "true");
    await alpha.click();

    // A modified click and a middle click fall through to the href: a new tab, no swap in place.
    for (const click of [
      () => zulu.click({ modifiers: ["ControlOrMeta"] }),
      () => zulu.click({ button: "middle" }),
    ]) {
      const [opened] = await Promise.all([context.waitForEvent("page"), click()]);
      await expect(alpha).toHaveAttribute("aria-current", "true");
      expect(opened.url()).toMatch(/\/agent\/client-invoices\?contract=.+/);
      await opened.close();
    }

    // Phone: no "Clients" landmark, the switcher present and working, nothing scrolls sideways.
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(clients).toHaveCount(0);
    await page.locator('[aria-haspopup="listbox"]').click();
    await page.getByRole("option", { name: new RegExp(ZULU) }).click();
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toHaveCount(0);
    await page.locator('[aria-haspopup="listbox"]').click();
    await page.getByRole("option", { name: new RegExp(ALPHA) }).click();
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toBeVisible();
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth === window.innerWidth))
      .toBe(true);
  });

  test("picking a row returns the document to the top; the column keeps its own scroll", async ({ page }) => {
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    // A short window: the invoice is taller than it, and the column's rows overflow its height.
    await page.setViewportSize({ width: 1440, height: 200 });
    await page.goto("/agent/client-invoices");
    const clients = page.getByRole("navigation", { name: "Clients" });
    await expect(clients).toBeVisible();

    const metrics = () =>
      clients.evaluate((el) => ({ scrollHeight: el.scrollHeight, clientHeight: el.clientHeight, scrollTop: el.scrollTop }));
    const before = await metrics();
    expect(before.scrollHeight).toBeGreaterThan(before.clientHeight);

    const target = clients.getByRole("link", { name: ZULU, exact: true });
    await target.scrollIntoViewIfNeeded();
    const columnScroll = (await metrics()).scrollTop;
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0);

    await target.click();
    await expect(target).toHaveAttribute("aria-current", "true");
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
    expect((await metrics()).scrollTop).toBe(columnScroll);
  });
});
